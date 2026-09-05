# MykytaDu API — Problem Details

> **Status:** contrato técnico inicial
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Formato

Erros HTTP da aplicação usam `application/problem+json`, conforme RFC 9457 e o tipo `ProblemDetail` do Spring Framework.

| Campo | Finalidade |
| --- | --- |
| `type` | URN estável da categoria do problema |
| `title` | título humano curto |
| `status` | status HTTP numérico |
| `detail` | explicação humana; não é contrato para decisões do cliente |
| `instance` | path da requisição, sem query string |
| `code` | código estável usado pelo cliente |
| `traceId` | identificador para correlação e suporte |
| `errors` | lista de violações específicas; vazia quando não aplicável |

Exemplo de validação:

```json
{
  "type": "urn:mykytadu:problem:request_validation_failed",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/example",
  "code": "request_validation_failed",
  "traceId": "2d46eea5-6ff7-4ee8-95c9-5c71c9f82484",
  "errors": [
    {
      "field": "name",
      "code": "invalid",
      "message": "must not be blank"
    }
  ]
}
```

## 2. Códigos técnicos iniciais

| Código | Status | Uso |
| --- | --- | --- |
| `request_validation_failed` | 400 | corpo recebido e parseado, mas com campos inválidos |
| `internal_error` | 500 | falha inesperada sem detalhe interno exposto |
| `authentication_required` | 401 | credencial válida ausente em uma superfície protegida |

O catálogo de domínio será definido na B0.2 e evoluído junto ao OpenAPI. Não reutilizar `detail`, título ou mensagem de campo como chave de decisão no cliente.

## 3. Segurança e correlação

- respostas nunca incluem classe da exceção, stack trace ou mensagem interna inesperada;
- a falha inesperada é registrada no servidor com o mesmo `traceId` devolvido ao cliente;
- enquanto a correlação HTTP da B-1-T12 não estiver instalada, um UUID é gerado como fallback;
- mensagens de validação podem ser exibidas a humanos, mas seus códigos devem permanecer estáveis;
- o path em `instance` não inclui query parameters nem dados do corpo.

## 4. Limites atuais

O handler MVC cobre validação de corpo e exceções lançadas por controllers. Falhas de autenticação produzidas antes do MVC usam um `AuthenticationEntryPoint` dedicado e o mesmo factory de Problem Details.

Não foram criados endpoints de negócio para esta infraestrutura. Os endpoints sob `/test/problem-details` existem somente como classes privadas no source set de teste.
