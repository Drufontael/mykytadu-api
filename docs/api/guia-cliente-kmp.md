# Guia de Integração — Clientes KMP e Web

> **Status:** Aprovado  
> **Versão:** 1.0.0  
> **Data de referência:** 2026-10-24  
> **Contrato canônico:** [OpenAPI 3.1](openapi.yaml) e [Catálogo de Erros](catalogo-de-erros.md)  
> **Público-alvo:** Equipes de desenvolvimento do aplicativo Kotlin Multiplatform (Android, Desktop, iOS) e Frontend Web.

---

## 1. Princípios de Arquitetura e Contrato

O backend MykytaDu API opera sob o princípio de **Contrato em Primeiro Lugar (Contract-First)**:
- A especificação OpenAPI 3.1 em [`docs/api/openapi.yaml`](openapi.yaml) é a **fonte única da verdade** do contrato HTTP.
- O backend e os clientes KMP **não compartilham modelos Kotlin compilados** ([ADR-007](../adr/ADR-007-nao-compartilhar-modelos-compilados-com-kmp.md)). Clientes KMP devem gerar ou manter seus próprios DTOs e serializadores (Kotlinx.serialization) alinhados aos esquemas do OpenAPI.
- A comunicação é estritamente sobre HTTPS (TLS 1.3), JSON em `camelCase`, identificadores UUID em formato string canônico (RFC 4122) e timestamps em UTC no padrão ISO-8601 (`YYYY-MM-DDTHH:mm:ssZ`).
- **Segurança estrita no cliente:** Nenhuma chave de infraestrutura, credencial de banco de dados ou token de provedor de tradução é embarcada no aplicativo cliente. O cliente comunica-se unicamente com os endpoints públicos da API.

---

## 2. Matriz de Conectividade por Plataforma

O prefixo da API é sempre `/api/v1`. A tabela abaixo orienta a URL base de acordo com a plataforma de execução:

| Plataforma / Ambiente | Ambiente Alvo | Base URL | Observações de Rede |
|---|---|---|---|
| **Android (Emulador)** | Desenvolvimento Local | `http://10.0.2.2:8081/api/v1` | O IP `10.0.2.2` é a rota padrão de loopback do emulador Android para o host de desenvolvimento. |
| **Android (Físico)** | Desenvolvimento Local (LAN) | `http://<IP-DO-HOST>:8081/api/v1` | Requer que o dispositivo esteja na mesma rede Wi-Fi e `usesCleartextTraffic="true"` no manifesto para testes locais. |
| **Desktop JVM** | Desenvolvimento Local | `http://localhost:8081/api/v1` | Conexão direta loopback padrão. |
| **iOS (Simulador)** | Desenvolvimento Local | `http://localhost:8081/api/v1` | Simulador iOS compartilha a pilha de rede do macOS. |
| **Web Browser** | Desenvolvimento Local | `http://localhost:8081/api/v1` | Requisições via `fetch`/`XMLHttpRequest` com credenciais incluídas (`credentials: "include"`). |
| **Todas as Plataformas** | **Staging Remoto** | `https://staging-api.mykytadu.com/api/v1` | Ambiente de homologação Render, com TLS obrigatório e certificados válidos. |
| **Todas as Plataformas** | **Produção** | `https://api.mykytadu.com/api/v1` | Ambiente produtivo final com alta disponibilidade. |

---

## 3. Modelo de Autenticação e Sessão

A API diferencia transparentemente clientes nativos de clientes Web, garantindo máxima segurança para navegadores contra ataques XSS/CSRF e portabilidade segura para aplicativos móveis/desktop:

```mermaid
flowchart TD
    Client[Cliente KMP / Web] --> Login["POST /auth/login"]
    Login --> PlatformCheck{Origem da Requisição}
    PlatformCheck -->|Nativo: Android / Desktop / iOS| NativeResp["Retorna Tokens no Corpo JSON<br/>(accessToken + refreshToken)"]
    PlatformCheck -->|Web: Header Origin presente| WebResp["Retorna accessToken no Corpo JSON<br/>+ Cookie HttpOnly __Host-refresh_token<br/>+ Header X-CSRF-Token"]
```

### 3.1. Clientes Nativos (Android, Desktop, iOS)

1. **Login (`POST /auth/login`):**
   - Requisição:
     ```json
     {
       "email": "usuario@exemplo.com",
       "password": "SenhaSegura123!",
       "platform": "NATIVE"
     }
     ```
   - Resposta de Sucesso (200 OK):
     ```json
     {
       "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6...",
       "tokenType": "Bearer",
       "expiresIn": 600,
       "refreshToken": "7f8b9c...opaque_refresh_token..."
     }
     ```
2. **Armazenamento Seguro de Tokens:**
   - `accessToken`: Armazenar **exclusivamente em memória volátil** (State/Repository). Possui tempo de vida de 10 minutos.
   - `refreshToken`: Armazenar em **armazenamento criptografado do sistema operacional**:
     - *Android:* `EncryptedSharedPreferences` ou Android Keystore;
     - *iOS:* Keychain Services;
     - *Desktop:* Secret Service API (Linux), Windows Credential Locker ou macOS Keychain.
3. **Chamadas Autenticadas:**
   - Enviar o cabeçalho:
     ```http
     Authorization: Bearer <accessToken>
     ```
4. **Renovação de Sessão (`POST /auth/session/refresh`):**
   - Ao receber `401 Unauthorized` com `code: authentication_required` ou preventivamente próximo à expiração dos 10 minutos:
     ```http
     POST /api/v1/auth/session/refresh
     Content-Type: application/json
     Idempotency-Key: 018d45f3-524c-7000-8000-000000000001
     ```
     ```json
     {
       "refreshToken": "7f8b9c...opaque_refresh_token..."
     }
     ```
   - A API rotaciona o token e retorna um **novo par** `accessToken` + `refreshToken`. O refresh token anterior é imediatamente invalidado.
   - **Atenção:** Em caso de tentativa de reutilização de um refresh token já consumido, a família inteira de sessões é revogada por segurança (`session_invalid`), exigindo novo login.
5. **Logout (`POST /auth/logout`):**
   - Enviar `{ "refreshToken": "..." }` para revogar a sessão ativa, ou `POST /auth/logout-all` autenticado com Bearer para revogar todas as sessões da conta.

---

## 4. Jornada de Tradução de Conteúdo

O aplicativo KMP exibe títulos e descrições de animes obtidos diretamente da API AniList em arquitetura *local-first*. Quando o usuário solicita a tradução de uma sinopse:

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant KMP as App KMP
    participant API as MykytaDu API
    participant Cache as Cache PostgreSQL
    participant Ext as LibreTranslate

    U->>KMP: Toca em "Traduzir Sinopse"
    KMP->>API: POST /api/v1/translations (Bearer JWT)
    API->>Cache: Busca por hash canônico SHA-256
    alt Cache Hit (dentro de 30 dias)
        Cache-->>API: Retorna tradução existente
        API-->>KMP: 200 OK (cached: true)
    else Cache Miss
        API->>Ext: Consulta LibreTranslate
        Ext-->>API: Retorna texto traduzido
        API->>Cache: Grava tradução e metadados
        API-->>KMP: 200 OK (cached: false)
    end
    KMP->>U: Exibe texto traduzido na UI
```

### 4.1. Exemplo de Chamada de Tradução

```http
POST /api/v1/translations
Authorization: Bearer eyJhbGciOiJSUzI1Ni...
Content-Type: application/json

{
  "text": "A high school student gains the powers of a Shinigami.<br><br>Now he must protect humans from evil spirits.",
  "sourceLanguage": "en",
  "targetLanguage": "pt-BR",
  "contentType": "ANIME_DESCRIPTION"
}
```

### 4.2. Resposta de Sucesso (200 OK)

```json
{
  "translation": "Um estudante do ensino médio ganha os poderes de um Shinigami.<br><br>Agora ele deve proteger os humanos dos espíritos malignos.",
  "sourceLanguage": "en",
  "targetLanguage": "pt-BR",
  "contentType": "ANIME_DESCRIPTION",
  "cached": false
}
```

- **Preservação de HTML:** A API preserva rigorosamente quebras de linha e tags HTML seguras (`<br>`, `<p>`, `<b>`, `<i>`, `<em>`, `<strong>`) para que o aplicativo KMP possa renderizar Rich Text sem corromper formatações.
- **Cache Transparente:** Quando `cached: true`, a requisição é atendida em milissegundos sem debitar a cota de consumo diário.

---

## 5. Tratamento Padronizado de Erros (RFC 9457 Problem Details)

Em qualquer falha HTTP (famílias 4xx e 5xx), a API responde impreterivelmente com o cabeçalho `Content-Type: application/problem+json`:

```json
{
  "type": "https://api.mykytadu.com/errors/translation-quota-exceeded",
  "title": "Translation Quota Exceeded",
  "status": 429,
  "code": "translation_quota_exceeded",
  "detail": "Daily translation limit reached for this account. Try again tomorrow.",
  "instance": "/api/v1/translations",
  "traceId": "c4b8e2a10f8976d3",
  "errors": []
}
```

### 5.1. Regra de Ouro para os Clientes KMP

> ⚠️ **NUNCA tome decisões no código com base no campo `detail` ou na mensagem de erro.**  
> O campo `detail` é puramente informativo para humanos e pode variar. As decisões do cliente devem ser baseadas estritamente em:
> 1. Código de Status HTTP (`status`): ex. `400`, `401`, `403`, `409`, `429`, `502`, `504`.
> 2. Código de Erro Estável (`code`): ex. `translation_quota_exceeded`, `authentication_required`, `invalid_action_token`.

### 5.2. Guia de Tratamento dos Principais Erros

| Status | Código (`code`) | Ação Recomendada no Cliente KMP |
|---|---|---|
| **401** | `authentication_required` | Chamar `POST /auth/session/refresh`. Se o refresh falhar, limpar o token local e exibir a tela de login. |
| **401** | `session_invalid` | Sessão expirada ou revogada por segurança. Limpar armazenamento seguro e redirecionar ao login. |
| **401** | `email_verification_required` | Exibir aviso na tela de login orientando a confirmação do link enviado ao e-mail. |
| **409** | `registration_rejected` | Exibir mensagem neutra de que o cadastro não pôde ser concluído (sem expor se o e-mail já existe). |
| **429** | `translation_quota_exceeded` | Ler o cabeçalho HTTP `Retry-After` (número de segundos até meia-noite UTC). Manter o texto original em inglês na UI e avisar o usuário que a cota diária foi atingida. |
| **502 / 504** | `translation_provider_failed` / `translation_provider_timeout` | Provedor externo indisponível ou lento. Manter o texto original na tela e permitir que o usuário tente novamente mais tarde. |

---

## 6. Checklist de Segurança para Release do App KMP

Antes de publicar o aplicativo Android, Desktop ou iOS:
- [ ] O app utiliza URLs base parametrizadas por Build Variant / Configuração de Ambiente (Local vs Staging vs Produção).
- [ ] O tráfego em texto claro (HTTP) está bloqueado em produção (Network Security Config no Android e App Transport Security no iOS).
- [ ] Nenhum segredo ou chave privada está comitado no código-fonte ou embutido em `BuildConfig`.
- [ ] O `traceId` retornado em respostas de erro é registrado no log local para auditoria e suporte ao usuário.
- [ ] Todas as chamadas de refresh utilizam `Idempotency-Key` (UUIDv7) para mitigar quedas de conexão móvel.
