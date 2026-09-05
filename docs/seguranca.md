# MykytaDu API — Segurança HTTP

> **Status:** baseline técnico
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Política inicial

A aplicação adota deny-by-default: toda requisição precisa estar autenticada, salvo liberação explícita adicionada à cadeia de segurança e coberta por teste.

O baseline atual:

- não cria sessão (`SessionCreationPolicy.STATELESS`);
- não habilita form login, HTTP Basic ou logout de navegador;
- não armazena a requisição para redirecionamento posterior;
- desabilita CSRF porque a API usará credencial Bearer explícita e não autenticação automática por cookie;
- mantém headers seguros padrão do Spring Security;
- responde falhas anônimas com `401 application/problem+json` e código `authentication_required`;
- envia o desafio `WWW-Authenticate: Bearer` nas respostas `401`;
- libera anonimamente apenas os probes específicos de liveness e readiness, sem detalhes;
- não libera o health global, métricas, OpenAPI ou Swagger UI anonimamente.

O mecanismo definitivo de emissão e validação de tokens pertence às sprints de Identity. Até ele existir, não há credencial produtiva capaz de autenticar uma requisição; isso é intencionalmente mais restritivo que criar um usuário ou senha temporários.

## 2. Superfícies técnicas

Os endpoints Actuator `health` e `metrics` estão na exposição web. Somente os probes específicos são públicos; o diagnóstico agregado e as métricas continuam autenticados.

| Superfície | Exposição | Acesso anônimo atual |
| --- | --- | --- |
| `/actuator/health` | incluída | negado com `401` |
| `/actuator/health/liveness` | incluída | permitido, sem detalhes |
| `/actuator/health/readiness` | incluída | permitido, sem detalhes |
| `/actuator/metrics` | incluída | negado com `401` |
| demais endpoints Actuator | não incluídos | inexistentes externamente |
| `/v3/api-docs` | disponível no classpath | negado com `401` |
| `/swagger-ui/**` | disponível no classpath | negado com `401` |

Qualquer liberação futura deve ser específica por path e método, ter justificativa operacional e possuir teste de autorização positivo e negativo. Não usar exclusão ampla da cadeia de filtros.

## 3. Respostas de segurança

Falhas produzidas antes do MVC usam o mesmo formato descrito em [Problem Details](problem-details.md). O código inicial é:

| Código | Status | Uso |
| --- | --- | --- |
| `authentication_required` | 401 | ausência de uma autenticação válida em recurso protegido |

O corpo não informa se uma rota de negócio existe, não cria sessão e não contém detalhes de autenticação.
