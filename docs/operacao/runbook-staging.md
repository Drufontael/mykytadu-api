# MykytaDu API — Runbook operacional de staging e contingências

> **Status:** Vigente
> **Versão:** 1.0.0
> **Data de referência:** 24 de outubro de 2026
> **Escopo:** Operação, monitoramento e contingências da API MykytaDu em staging e pré-produção

Este documento estabelece os procedimentos operacionais padrão (SOP) e os planos de contingência acionáveis para o ambiente de staging da API MykytaDu. Os procedimentos respeitam os limites arquiteturais do monólito modular, as diretrizes de persistência incremental via Flyway e a política estrita de privacidade (ADR-008).

---

## 1. Monitoramento e saúde dos serviços

### 1.1 Endpoints de diagnóstico

A API expõe probes de liveness e readiness compatíveis com o Actuator e o Render:

| Endpoint | Finalidade | Critério de sucesso | Ação em caso de falha |
|---|---|---|---|
| `GET /actuator/health/liveness` | Verifica se o processo JVM está responsivo | HTTP 200 `{"status":"UP"}` | Reiniciar o container/serviço |
| `GET /actuator/health/readiness` | Verifica conectividade ativa com o PostgreSQL | HTTP 200 `{"status":"UP"}` | Verificar rede e saúde do PostgreSQL |

### 1.2 Headers e rastreamento

- **`X-Trace-Id`**: Toda requisição HTTP recebe ou gera um identificador único de rastreamento retornado no header `X-Trace-Id` e embutido no payload RFC 9457 em caso de erro (`traceId`).
- Use o `traceId` para filtrar logs no painel do Render ou agregador de logs sem expor credenciais ou dados pessoais.

---

## 2. Contingência: Indisponibilidade do provedor de tradução

### 2.1 Sintomas e diagnóstico

- **Sintomas:** Clientes recebem HTTP 502 Bad Gateway (`code: translation_provider_failed`) ou HTTP 504 Gateway Timeout (`code: translation_provider_timeout`).
- **Comportamento resiliente:** Requisições para conteúdos já armazenados em cache (`translation.translations`) continuam respondendo HTTP 200 com `cached: true` normalmente.

### 2.2 Procedimento de mitigação

1. **Verificar o serviço privado do LibreTranslate:**
   - Acesse o painel do Render e verifique o status do serviço `mykytadu-libretranslate`.
   - Consulte os logs do serviço para identificar falta de memória (OOM), modelo de idioma corrompido ou sobrecarga de requisições.
2. **Reinício do serviço de tradução:**
   - Se o serviço estiver travado ou consumindo 100% de CPU/RAM, execute o reinício manual pelo painel de controle.
3. **Validação de conectividade interna:**
   - Confirme se a URL interna `http://mykytadu-libretranslate:5000` está acessível a partir da rede privada da API.
4. **Verificação de Quota e Rate Limit:**
   - Se os usuários estiverem recebendo HTTP 429 (`code: translation_quota_exceeded`), o provedor está saudável; trata-se de esgotamento da quota diária (50.000 caracteres/dia por usuário). Instrua o cliente a respeitar o header `Retry-After`.

---

## 3. Contingência: Comprometimento de refresh token ou sessão

### 3.1 Vazamento de credenciais de usuário individual

Caso haja suspeita de comprometimento da conta de um usuário:

1. **Revogação imediata via API:**
   - Se o usuário ou suporte possuir um access token válido, chame:
     `POST /api/v1/auth/logout-all` com o header `Authorization: Bearer <token>`.
2. **Revogação de emergência no banco de dados:**
   - Caso o access token não esteja disponível, acesse o banco via console protegido e revogue todas as sessões do usuário:
     ```sql
     UPDATE identity.sessions
     SET revoked_at = NOW(),
         revocation_reason = 'security_incident'
     WHERE user_id = :compromised_user_id
       AND revoked_at IS NULL;
     ```
   - O usuário será forçado a realizar novo login e, se necessário, recuperar a senha via `/api/v1/auth/password/forgot`.

### 3.2 Vazamento da chave HMAC de refresh token

Caso a chave secreta HMAC de derivação de refresh tokens (`MYKYTADU_REFRESH_ACTIVE_KEY_BASE64`) tenha sido exposta:

1. **Substituição emergencial de chaves:**
   - Gere imediatamente uma nova chave HMAC de 32 bytes (256 bits) em Base64:
     ```bash
     openssl rand -base64 32
     ```
   - Cadastre o novo `kid` (`MYKYTADU_REFRESH_ACTIVE_KEY_ID`) e a nova chave no gerenciador de segredos.
   - **Não mantenha** a chave comprometida como `MYKYTADU_REFRESH_PREVIOUS_*`.
2. **Invalidação global de sessões:**
   - Como a integridade dos refresh tokens anteriores está comprometida, revogue todas as sessões ativas:
     ```sql
     UPDATE identity.sessions
     SET revoked_at = NOW(),
         revocation_reason = 'key_compromise'
     WHERE revoked_at IS NULL;
     ```
3. **Reinício da API:**
   - Reinicie a API para carregar a nova chave ativa. Todos os usuários serão solicitados a se autenticar novamente.

---

## 4. Procedimento operacional: Rotação programada de chaves (sem downtime)

A rotação de chaves criptográficas deve ser realizada periodicamente sem interromper o serviço aos usuários ativos.

### 4.1 Rotação de chaves JWT (assinatura assimétrica RSA)

1. **Geração do novo par de chaves:**
   ```bash
   # Gerar chave privada RSA PKCS#8
   openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048
   # Extrair chave pública X.509 correspondente
   openssl rsa -pubout -in private_key.pem -out public_key.pem
   ```
2. **Configuração de rotação suave:**
   - Mova a chave pública atual para as variáveis de transição:
     - `MYKYTADU_IDENTITY_JWT_PREVIOUS_KEY_ID = <kid_antigo>`
     - `MYKYTADU_IDENTITY_JWT_PREVIOUS_PUBLIC_KEY_PEM = <conteudo_public_key_antiga>`
   - Defina o novo par de chaves como ativo:
     - `MYKYTADU_JWT_ACTIVE_KEY_ID = <novo_kid_ex_staging_202611>`
     - `MYKYTADU_JWT_PRIVATE_KEY_PEM = <conteudo_nova_private_key>`
     - `MYKYTADU_JWT_PUBLIC_KEY_PEM = <conteudo_nova_public_key>`
3. **Deploy e período de carência:**
   - Aplique as alterações no ambiente.
   - Novos access tokens serão assinados com o novo `kid`.
   - Access tokens emitidos anteriormente continuarão válidos até expirarem (máximo 15 minutos).
4. **Limpeza pós-carência:**
   - Após 30 minutos, remova as variáveis `MYKYTADU_IDENTITY_JWT_PREVIOUS_*`.

### 4.2 Rotação de chaves de derivação HMAC de Refresh Token

1. **Geração da nova chave HMAC:**
   ```bash
   openssl rand -base64 32
   ```
2. **Configuração de transição:**
   - Defina a chave HMAC atual como:
     - `MYKYTADU_REFRESH_PREVIOUS_KEY_ID = <kid_hmac_antigo>`
     - `MYKYTADU_REFRESH_PREVIOUS_KEY_BASE64 = <chave_hmac_antiga>`
   - Defina a nova chave como ativa:
     - `MYKYTADU_REFRESH_ACTIVE_KEY_ID = <novo_kid_hmac>`
     - `MYKYTADU_REFRESH_ACTIVE_KEY_BASE64 = <nova_chave_hmac_base64>`
3. **Deploy e período de carência:**
   - A API valida refresh tokens emitidos com qualquer uma das duas chaves e emite os novos tokens rotacionados sob o novo `kid`.
   - Mantenha a chave anterior por 7 dias (período máximo de expiração de refresh tokens).
4. **Descomissionamento:**
   - Após 7 dias, remova as variáveis `MYKYTADU_REFRESH_PREVIOUS_*`.

---

## 5. Procedimento de Rollback de versão da API

### 5.1 Regra de compatibilidade de banco

Conforme a arquitetura da API MykytaDu:
- **Migrations são imutáveis e aditivas:** Uma migration aprovada nunca deve ser editada ou deletada.
- As alterações de banco seguem o padrão *expand-and-contract*, garantindo que a versão anterior da aplicação seja capaz de rodar contra o schema atualizado.

### 5.2 Rollback imediato da aplicação (sem alteração de banco)

Se a nova versão da API apresentar erros críticos após o deploy:

1. **Reverter a imagem OCI no Render:**
   - No painel do Render, selecione o deploy anterior com status saudável e execute **Rollback** para aquela versão específica da imagem.
2. **Validação de integridade pós-rollback:**
   - Consulte o endpoint `/actuator/health/readiness` para assegurar que a versão restaurada conectou com sucesso ao PostgreSQL.
   - Execute um teste funcional de autenticação (`/api/v1/auth/login`) e tradução (`/api/v1/translations`).

### 5.3 Rollback com migração compensatória

Se uma migration Flyway introduziu uma anomalia em dados ou índices:

1. **Nunca altere a tabela `flyway_schema_history` manualmente.**
2. Desenvolva uma nova migração compensatória estritamente aditiva:
   - Nome: `V<timestamp>-reverter_<descricao>.sql`
   - Teste a migração localmente contra Testcontainers com o comando:
     ```powershell
     .\gradlew.bat test --tests "br.com.mykytadu.integration.DatabaseMigrationIntegrationTests"
     ```
3. Gere nova imagem candidata pelo CI e aplique o deploy controlado.

---

## 6. Higienização e manutenção de cache de tradução

- **Expiração:** Registros na tabela `translation.translations` expiram automaticamente após 30 dias (`expires_at = created_at + 30 days`).
- **Rotina de Purga:** A purga de registros vencidos é executada periodicamente pelo caso de uso `PurgeExpiredTranslationsUseCase.purge()`, liberando espaço de banco sem afetar entradas válidas em cache.
- **Auditoria de Privacidade:** Periodicamente, verifique se nenhum texto original foi persistido executando a consulta:
  ```sql
  SELECT column_name
  FROM information_schema.columns
  WHERE table_schema = 'translation'
    AND table_name = 'translations';
  ```
  O retorno deve conter apenas metadados técnicos e `content_hash`, confirmando aderência ao ADR-008.

---

## 7. Matriz de escalonamento e resposta a incidentes

| Nível de Severidade | Cenário | Ação inicial | Tempo de resposta |
|---|---|---|---|
| **Sev-1 (Crítica)** | API totalmente inacessível, falha de integridade de dados ou vazamento de segredos | Ativar rollback imediato da aplicação; rotacionar segredos vazados; notificar equipe | < 15 minutos |
| **Sev-2 (Alta)** | Provedor LibreTranslate indisponível (erros 502/504 em cache miss); degradação de performance | Reiniciar container LibreTranslate; verificar recursos de rede privada | < 1 hora |
| **Sev-3 (Média)** | Alertas isolados de rate limit ou estouro de quotas diárias de usuários (HTTP 429) | Monitorar métricas de consumo; verificar se cliente KMP está realizando retries excessivos | < 4 horas |
