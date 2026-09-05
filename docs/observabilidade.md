# MykytaDu API — Observabilidade

> **Status:** baseline técnico
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Health probes

| Endpoint | Dependências | Acesso | Interpretação |
| --- | --- | --- | --- |
| `/actuator/health/liveness` | somente estado interno `livenessState` | público, sem detalhes | reiniciar apenas quando o processo estiver irrecuperável |
| `/actuator/health/readiness` | `readinessState` e PostgreSQL | público, sem detalhes | retirar a instância do tráfego enquanto o banco estiver indisponível |
| `/actuator/health` | conjunto global de indicadores | autenticado | diagnóstico técnico controlado |

O banco nunca participa do liveness, evitando reinicializações em cascata durante uma indisponibilidade externa. No profile `test`, que deliberadamente não possui datasource, readiness inclui apenas o estado da aplicação. No profile `integration-test` e nos ambientes com banco, readiness inclui `db`.

## 2. Correlação

Cada requisição recebe um UUID gerado pelo servidor. O valor:

- entra no MDC como `traceId` antes da cadeia de segurança;
- é devolvido no header `X-Trace-Id`;
- aparece no Problem Details quando houver falha;
- é removido do MDC ao encerrar a requisição para impedir vazamento entre threads.

Valores enviados pelo cliente em `X-Trace-Id` não são confiados nem reutilizados. A adoção futura de tracing distribuído poderá substituir a origem do identificador mantendo o contrato externo.

## 3. Métricas HTTP

Spring MVC registra `http.server.requests`. A dimensão `uri` usa o template da rota, como `/api/v1/items/{id}`, e não o valor concreto. IDs, e-mails, texto traduzido e query parameters não devem ser adicionados como tags.

O endpoint `/actuator/metrics` é exposto para diagnóstico, mas permanece autenticado. Ele não substitui um backend de métricas nem deve ser liberado publicamente.

## 4. Dados sensíveis

O baseline não registra headers, cookies, tokens, credenciais nem corpos de requisição. Logs de falhas inesperadas contêm o `traceId` e a exceção no servidor, mas nunca devolvem stack trace ao cliente. Novos logs e métricas devem passar por revisão de dados pessoais e cardinalidade.
