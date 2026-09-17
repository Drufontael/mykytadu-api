# Catálogo inicial de erros HTTP

> **Status:** Aprovado
> **Versão:** 0.2
> **Data de referência:** 2026-09-17
> **Fonte contratual:** [OpenAPI 3.1](openapi.yaml)

## 1. Regra para o cliente

As respostas de erro usam `application/problem+json`, conforme RFC 9457. O
cliente deve tomar decisões somente por `status` e `code`. Os campos `title`,
`detail` e `errors[].message` são destinados a humanos e podem variar sem
quebrar o contrato.

O campo `traceId` deve ser exibido ou enviado ao suporte quando necessário, sem
expor credenciais, tokens ou conteúdo sensível. O campo `instance` contém
somente o path, sem query string.

Quando houver falhas de validação, `errors[]` lista os campos afetados:

```json
{
  "field": "text",
  "code": "too_long",
  "message": "must contain at most 10000 characters"
}
```

Para erros sem campo específico, `errors` é uma lista vazia.

## 2. Códigos estáveis

| Código | Status | Superfície | Ação esperada do cliente | `errors[]` |
| --- | ---: | --- | --- | --- |
| `request_validation_failed` | 400 | qualquer request inválido | corrigir os campos indicados | campos rejeitados |
| `registration_rejected` | 409 | cadastro | informar que o cadastro não pôde ser concluído; não inferir a causa | vazio |
| `invalid_action_token` | 400 | verificação de e-mail e reset de senha | solicitar novo fluxo de ação | vazio |
| `authentication_required` | 401 | recurso protegido sem credencial válida | iniciar login ou renovar sessão | vazio |
| `invalid_credentials` | 401 | login | informar falha genérica de autenticação | vazio |
| `email_verification_required` | 401 | login de conta pendente | orientar verificação de e-mail sem depender de `detail` | vazio |
| `session_invalid` | 401 | refresh ou sessão inválida | limpar sessão local e exigir novo login | vazio |
| `csrf_invalid` | 403 | refresh/logout Web | obter novo synchronizer token e repetir uma vez; depois exigir login | vazio |
| `authorization_denied` | 403 | principal autenticado sem permissão | informar acesso negado sem expor regra interna | vazio |
| `rate_limit_exceeded` | 429 | operações limitadas | respeitar `Retry-After` e aplicar retry controlado | vazio |
| `translation_quota_exceeded` | 429 | tradução | respeitar `Retry-After` e preservar o texto original | vazio |
| `translation_provider_failed` | 502 | tradução | preservar o texto original e informar indisponibilidade | vazio |
| `translation_provider_timeout` | 504 | tradução | preservar o texto original; retry somente conforme política | vazio |
| `internal_error` | 500 | qualquer endpoint | informar falha genérica e guardar `traceId` | vazio |

`registration_rejected` não revela se o e-mail já existe. Da mesma forma,
clientes não devem transformar diferenças de `detail` em decisões de UX ou
segurança.

## 3. Exemplos

### Validação

```json
{
  "type": "urn:mykytadu:problem:request_validation_failed",
  "title": "Request validation failed",
  "status": 400,
  "detail": "The translation request contains invalid content or parameters.",
  "instance": "/api/v1/translations",
  "code": "request_validation_failed",
  "traceId": "2e1f75f4-493c-4a43-a6c1-4f12f46a7d8e",
  "errors": [
    {
      "field": "text",
      "code": "too_long",
      "message": "must contain at most 10000 characters"
    }
  ]
}
```

### Sessão inválida

```json
{
  "type": "urn:mykytadu:problem:session_invalid",
  "title": "Session invalid",
  "status": 401,
  "detail": "The session is invalid, expired or no longer usable.",
  "instance": "/api/v1/auth/refresh",
  "code": "session_invalid",
  "traceId": "b0c5f889-8b82-4ad4-9fef-8f0b9c82f6c2",
  "errors": []
}
```

### Provedor indisponível

```json
{
  "type": "urn:mykytadu:problem:translation_provider_timeout",
  "title": "Translation provider timeout",
  "status": 504,
  "detail": "The translation provider did not respond in time.",
  "instance": "/api/v1/translations",
  "code": "translation_provider_timeout",
  "traceId": "9aa5a7f7-9a4c-42ec-bbf1-7d2f4c616d5e",
  "errors": []
}
```

Os exemplos completos e as respostas associadas a cada endpoint permanecem no
[OpenAPI](openapi.yaml). Este catálogo foi aprovado para adoção pelo
`mykytadu-app`.
