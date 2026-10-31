# MykytaDu API — Inventário de Dados, Privacidade e Ciclo de Retenção (LGPD)

> **Status:** Vigente  
> **Versão:** 1.0.0  
> **Data de referência:** 31 de outubro de 2026  
> **Escopo:** Conformidade com a LGPD, minimização de dados, mapeamento de schemas e política de descarte

Este documento consolida o inventário de dados de persistência da API MykytaDu, detalhando a finalidade jurídica, a classificação de sensibilidade e as regras de retenção e descarte para cada entidade de domínio, em estrito cumprimento às diretrizes dos [ADR-008](../adr/ADR-008-nomear-projeto-e-repositorio-mykytadu-api.md) e [ADR-014](../adr/ADR-014-politica-de-retencao-e-exclusao.md).

---

## 1. Princípios de Privacidade por Design (Privacy by Design)

1. **Não Persistência de Texto Original (ADR-008):** As descrições em língua estrangeira enviadas pelos usuários para tradução são processadas em memória e **jamais persistidas em disco, banco de dados ou logs**.
2. **Pseudonimização Criptográfica:** O cache de tradução armazena unicamente o hash SHA-256 do texto normalizado (`content_hash`), desacoplado de qualquer identificador de usuário (`user_id`).
3. **Minimização de Cardinalidade:** Identificadores pessoais diretos (e-mail, nome) residem estritamente no schema `identity`. O schema `translation` não possui chaves estrangeiras nem referências diretas a contas de usuário.
4. **Deny-by-Default em Exposição:** Nenhum segredo, token em texto plano ou hash de senha é retornado em endpoints da API ou impresso em telemetria.

---

## 2. Inventário de Tabelas e Mapeamento de Dados

### 2.1 Schema `identity`

#### Tabela `identity.users`
Armazena a entidade raiz da conta de usuário.

| Coluna | Tipo | Classificação | Finalidade / Base Legal (LGPD) | Prazo de Retenção |
|---|---|---|---|---|
| `id` | UUID (v7) | Pseudonimizado | Identificador imutável da conta (Art. 7, V) | Até exclusão da conta + purga |
| `email` | TEXT | Dado Pessoal | Comunicação e autenticação (Art. 7, V) | Até exclusão da conta |
| `normalized_email`| TEXT | Dado Pessoal | Garantia de unicidade de e-mail (Art. 7, V) | Até exclusão da conta |
| `display_name` | TEXT | Dado Pessoal | Personalização da experiência (Art. 7, V) | Até exclusão ou alteração |
| `status` | TEXT | Técnico | Ciclo de vida (`pending`, `active`, `blocked`, `deleted`) | Indefinido (tombstone técnico) |
| `email_verified_at`| TIMESTAMPTZ | Técnico | Comprovação de posse do e-mail | Até exclusão da conta |
| `created_at` | TIMESTAMPTZ | Técnico | Registro temporal de cadastro | Até exclusão da conta |
| `updated_at` | TIMESTAMPTZ | Técnico | Controle de concorrência e auditoria | Até exclusão da conta |

#### Tabela `identity.password_credentials`
Credenciais de senha com hashing adaptativo Argon2id.

| Coluna | Tipo | Classificação | Finalidade / Base Legal | Prazo de Retenção |
|---|---|---|---|---|
| `user_id` | UUID | Sensível (FK) | Vínculo com a conta | Removido imediatamente na exclusão |
| `password_hash` | TEXT | Altamente Sensível | Autenticação por senha (hash não reversível) | Removido na exclusão ou redefinição |
| `algorithm` | TEXT | Técnico | Versão do algoritmo (`argon2id`) | Acompanha a credencial |
| `updated_at` | TIMESTAMPTZ | Técnico | Rastreabilidade de troca de senha | Acompanha a credencial |

#### Tabela `identity.action_tokens`
Tokens descartáveis de ativação de conta e redefinição de senha.

| Coluna | Tipo | Classificação | Finalidade / Base Legal | Prazo de Retenção |
|---|---|---|---|---|
| `id` | UUID | Técnico | Identificador de transação | Até 24h após expiração ou consumo |
| `token_hash` | VARCHAR(64) | Sensível (Hash) | Validação segura de token enviado por e-mail | Até 24h após expiração ou consumo |
| `user_id` | UUID | Sensível (FK) | Usuário associado ao token | Até 24h após expiração ou consumo |
| `type` | TEXT | Técnico | Tipo de ação (`VERIFICATION`, `PASSWORD_RESET`) | Até 24h após expiração ou consumo |
| `expires_at` | TIMESTAMPTZ | Técnico | Prazo de expiração (1h reset, 24h ativação) | Limpeza automática por job de purga |

#### Tabela `identity.sessions`
Controle de sessões ativas e histórico para detecção de reuso de refresh token.

| Coluna | Tipo | Classificação | Finalidade / Base Legal | Prazo de Retenção |
|---|---|---|---|---|
| `id` | UUID (v7) | Pseudonimizado | Identificador da sessão | Até `max(expires_at, revoked_at) + 7d` |
| `user_id` | UUID | Pseudonimizado | Usuário titular da sessão | Até expiração da janela de reuso |
| `refresh_token_hash`| TEXT | Altamente Sensível | Validação segura do refresh token rotativo | Até expiração da janela de reuso |
| `token_family_id`| UUID | Técnico | Agrupamento de sessões para revogação em cascata | Até expiração da janela de reuso |
| `revoked_at` | TIMESTAMPTZ | Técnico | Data de término da sessão | Necessário para detecção de reuso |
| `revocation_reason`| TEXT | Técnico | Auditoria de segurança (`rotated`, `logout`, etc.) | 90 dias para auditoria |

---

### 2.2 Schema `translation`

#### Tabela `translation.translations`
Cache canônico global de traduções de sinopses de animes.

| Coluna | Tipo | Classificação | Finalidade / Base Legal | Prazo de Retenção |
|---|---|---|---|---|
| `id` | UUID (v7) | Técnico | Chave primária do registro | **30 dias exatos** a partir de `created_at` |
| `content_hash` | VARCHAR(64) | Pseudonimizado | Hash SHA-256 da descrição normalizada | **30 dias** (Purga física após `expires_at`) |
| `source_language`| TEXT | Técnico | Código ISO do idioma original (`EN`, `JA`) | 30 dias |
| `target_language`| TEXT | Técnico | Código ISO do idioma traduzido (`PT_BR`) | 30 dias |
| `content_type` | TEXT | Técnico | Tipo de conteúdo (`PLAIN_TEXT`, `HTML`) | 30 dias |
| `translation` | TEXT | Domínio | Texto traduzido para exibição | 30 dias |
| `provider` | TEXT | Técnico | Provedor de tradução (`libretranslate`) | 30 dias |
| `model_version` | TEXT | Técnico | Versão do modelo usado | 30 dias |
| `created_at` | TIMESTAMPTZ | Técnico | Data de tradução original | Não é estendido por cache hit |
| `expires_at` | TIMESTAMPTZ | Técnico | Data de expiração (`created_at + 30 days`) | Indexado para purga rápida |

#### Tabela `translation.usage_daily`
Controle agregado de quota diária para prevenção de abuso e controle de custos.

| Coluna | Tipo | Classificação | Finalidade / Base Legal | Prazo de Retenção |
|---|---|---|---|---|
| `id` | UUID | Técnico | Identificador de medição | 90 dias |
| `user_id` | UUID | Pseudonimizado | Principal responsável pelo consumo | Desvinculado em caso de exclusão de conta |
| `usage_date` | DATE | Técnico | Data do consumo (UTC) | 90 dias |
| `characters_count`| BIGINT | Técnico | Volume de caracteres consumidos no dia | 90 dias |
| `request_count` | INT | Técnico | Total de requisições de tradução | 90 dias |

---

## 3. Política de Eliminação e Purga Automática

1. **Purga de Cache Expirado:**
   - O caso de uso `PurgeExpiredTranslationsUseCase` executa:
     ```sql
     DELETE FROM translation.translations WHERE expires_at <= :now;
     ```
   - Registros expirados são deletados fisicamente do disco, liberando espaço e garantindo que traduções obsoletas não persistam além de 30 dias.
2. **Purga de Tokens e Sessões:**
   - Tokens de ação com mais de 24 horas após expiração são purgados periodicamente.
   - Famílias de sessões revogadas são eliminadas após a expiração da janela de 7 dias de tolerância de replay e detecção de reuso.

---

## 4. Direito de Exclusão de Conta (Art. 18 LGPD)

Quando um usuário solicita a exclusão definitiva de sua conta:

1. **Etapa Síncrona:**
   - A conta é transacionada para `status = 'deleted'`.
   - Todas as sessões ativas são revogadas com `revocation_reason = 'account_deleted'`.
   - Credenciais em `identity.password_credentials` são deletadas.
2. **Etapa de Anonimização:**
   - O e-mail e nome de exibição são descaracterizados de qualquer identificação pessoal.
   - Os registros em `translation.translations` **não são afetados**, pois não contêm vínculo com o usuário nem armazenam o texto original da sinopse.
