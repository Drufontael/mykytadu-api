# MykytaDu API — Segurança HTTP

> **Status:** baseline técnico
> **Versão:** 0.2
> **Data de referência:** 22 de setembro de 2026

## 1. Política inicial

A aplicação adota deny-by-default: toda requisição precisa estar autenticada, salvo liberação explícita adicionada à cadeia de segurança e coberta por teste.

O baseline atual:

- não cria sessão HTTP do servlet (`SessionCreationPolicy.STATELESS`); as
  sessões duráveis de Identity são independentes desse mecanismo;
- não habilita form login, HTTP Basic ou logout de navegador;
- não armazena a requisição para redirecionamento posterior;
- desabilita CSRF para superfícies autenticadas exclusivamente por Bearer; fluxos Web que usem cookie exigirão proteção CSRF dedicada;
- mantém headers seguros padrão do Spring Security;
- responde falhas anônimas com `401 application/problem+json` e código `authentication_required`;
- envia o desafio `WWW-Authenticate: Bearer` nas respostas `401`;
- libera anonimamente apenas os probes específicos de liveness e readiness, sem detalhes;
- não libera o health global, métricas, OpenAPI ou Swagger UI anonimamente.

Identity emite e valida access tokens JWT assimétricos com issuer, audience,
expiração e `kid` configurados externamente. Somente contas `active` podem
obter uma sessão inicial; credenciais inválidas e contas indisponíveis não
revelam o motivo da rejeição.

### Direção Web aprovada para adoção

Como o cliente também possui distribuição Web e não pode armazenar tokens sensíveis em `localStorage`, a B0.1-T2 aprovou a combinação de access token em memória e refresh token opaco em cookie `HttpOnly`, `Secure` e `SameSite=Lax`, assumindo Web e API no mesmo site. Refresh e logout usarão `POST`, synchronizer token em `X-CSRF-Token` e allowlist de `Origin`. Essa direção está registrada no [ADR-011](adr/ADR-011-sessao-web-com-refresh-token-em-cookie.md), em estado `Aprovado`.

Os parâmetros iniciais aprovados são access token de 10 minutos, refresh token de 30 dias, rotação a cada uso, revogação da família em caso de reuso, cookie host-only sem `Domain`, `Path=/api/v1/auth` e CORS local restrito a `http://localhost:8080`.

O login Web cria a sessão inicial e entrega o refresh token somente no cookie
protegido, além do synchronizer token no corpo. Cookies não autenticam
endpoints de negócio. Refresh, rotação, detecção de reutilização, logout e
revogação pertencem à B2.2 e ainda não estão expostos.

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

O corpo não informa se uma rota de negócio existe, não cria sessão de Identity
e não contém detalhes de autenticação.
