# MykytaDu API — SLOs, SLIs e Alertas Iniciais de Produção

> **Status:** Vigente  
> **Versão:** 1.0.0  
> **Data de referência:** 31 de outubro de 2026  
> **Escopo:** Indicadores de nível de serviço (SLIs), objetivos (SLOs) e catálogo de alertas operacionais da API MykytaDu

Este documento formaliza os compromissos de confiabilidade, disponibilidade e latência da API MykytaDu para pré-produção e produção. Os indicadores apoiam-se exclusivamente na instrumentação Micrometer / Spring Boot Actuator e respeitam as restrições arquiteturais de privacidade e isolamento do monólito modular.

---

## 1. Princípios e Estratégia de Confiabilidade

1. **Minimização de Cardinalidade e Privacidade:** Nenhuma métrica registra identificadores de usuários, e-mails, senhas, tokens ou textos enviados para tradução (ADR-008). As tags limitam-se a categorias estáveis (`result`, `module`, `outcome`, `status`).
2. **Separação de Dependências:** O SLO de cache hit da API (isolado no PostgreSQL local) é desacoplado do SLO de cache miss, que depende de chamada downstream ao provedor de tradução LibreTranslate.
3. **Decisão por Severidade Acionável:** Todo alerta disparado possui causa provável, procedimento de mitigação imediato e escalonamento pré-definido no [Runbook Operacional](runbook-staging.md).

---

## 2. Indicadores (SLIs) e Objetivos (SLOs)

### 2.1 Disponibilidade (Availability)

| Código | Descrição do SLI | Fórmula de Cálculo | SLO-Alvo (Janela 30d) | Severidade em Violação |
|---|---|---|---|---|
| **SLI-AVAIL-01** | Disponibilidade global da API (Taxa de sucesso non-5xx) | `sum(rate(http_server_requests_seconds_count{status!~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | **>= 99.5%** | Sev-1 se < 95% em 2m; Sev-2 se < 99.5% em 15m |
| **SLI-AVAIL-02** | Disponibilidade de Autenticação e Sessão (`/api/v1/auth/*`) | `sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/auth/.*",status!~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/auth/.*"}[5m]))` | **>= 99.9%** | Sev-1 |
| **SLI-AVAIL-03** | Disponibilidade de Tradução em Cache Hit | `sum(rate(mykytadu_translation_cache_total{result="hit"}[5m])) / sum(rate(mykytadu_translation_cache_total[5m]))` (respostas 200) | **>= 99.9%** | Sev-1 |
| **SLI-AVAIL-04** | Disponibilidade de Tradução em Cache Miss (Provedor) | `sum(rate(mykytadu_translation_provider_latency_seconds_count{success="true"}[5m])) / sum(rate(mykytadu_translation_provider_latency_seconds_count[5m]))` | **>= 99.0%** | Sev-2 |

### 2.2 Latência (Latency)

| Código | Descrição do SLI | Métrica / Percentil | SLO-Alvo | Severidade em Violação |
|---|---|---|---|---|
| **SLI-LAT-01** | Latência de Login (`POST /api/v1/auth/login`) | `http_server_requests_seconds` (p95) | **< 250 ms** (p99 < 500 ms) | Sev-2 se p95 > 500ms por 5m |
| **SLI-LAT-02** | Latência de Renovação de Sessão (`POST /api/v1/auth/refresh`) | `http_server_requests_seconds` (p95) | **< 150 ms** (p99 < 300 ms) | Sev-2 se p95 > 300ms por 5m |
| **SLI-LAT-03** | Latência de Tradução em Cache Hit | `http_server_requests_seconds{uri="/api/v1/translations"}` quando hit | **< 100 ms** (p99 < 200 ms) | Sev-2 se p95 > 250ms por 5m |
| **SLI-LAT-04** | Latência de Chamada ao Provedor LibreTranslate | `mykytadu_translation_provider_latency_seconds` (p95) | **< 1500 ms** (p99 < 3000 ms) | Sev-2 se p95 > 3000ms por 5m |

### 2.3 Segurança, Anomalias e Integridade

| Código | Descrição do SLI | Métrica Observada | Limiar / Tolerância | Severidade |
|---|---|---|---|---|
| **SLI-SEC-01** | Reutilização de Refresh Token (Alerta de Roubo de Sessão) | `mykytadu_identity_authentication_attempts_total{result="refresh_reuse_detected"}` | **0 tolerado** (qualquer evento > 0) | **Sev-1 imediato** |
| **SLI-SEC-02** | Tentativas Bloqueadas por Rate Limit | `mykytadu_identity_authentication_attempts_total{result="rate_limited"}` | > 50 eventos/minuto | **Sev-3** |
| **SLI-SEC-03** | Falhas Consecutivas de Credenciais (Força Bruta) | `mykytadu_identity_authentication_attempts_total{result="invalid_credentials"}` | > 100 eventos/minuto | **Sev-3** |

### 2.4 Capacidade e Recursos (Saturation)

| Código | Descrição do SLI | Métrica Observada | Limiar / Tolerância | Severidade |
|---|---|---|---|---|
| **SLI-CAP-01** | Conexões Pendentes no Pool PostgreSQL (HikariCP) | `hikaricp_connections_pending` | > 0 por mais de 30 segundos | **Sev-2** (Sev-1 se > 1 min) |
| **SLI-CAP-02** | Utilização de Memória Heap JVM | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` | > 85% sustentado por 15 minutos | **Sev-3** |
| **SLI-CAP-03** | Esgotamento de Quota Diária de Tradução (HTTP 429) | `http_server_requests_seconds_count{status="429"}` | > 5% do volume total de requisições | **Sev-3** |

---

## 3. Catálogo de Alertas Acionáveis

### 3.1 Alertas Sev-1 (Críticos — Resposta < 15 minutos)

#### `AlertApiHighErrorRate`
- **Condição:** Taxa de respostas HTTP 5xx superior a 5% por 2 minutos contínuos.
- **Impacto:** Degradação generalizada ou falha de componentes centrais da API.
- **Ação Imediata:**
  1. Verificar saúde das probes (`/actuator/health/liveness` e `/actuator/health/readiness`).
  2. Filtrar logs de erro por `traceId` recente.
  3. Consultar conectividade com PostgreSQL. Se falha após deploy, ativar **Rollback Imediato** ([Runbook §5](runbook-staging.md#5-procedimento-de-rollback-de-versão-da-api)).

#### `AlertRefreshTokenReuseDetected`
- **Condição:** Incremento de `mykytadu_identity_authentication_attempts_total{result="refresh_reuse_detected"} > 0`.
- **Impacto:** Potencial interceptação ou reutilização maliciosa de token de refresh de usuário.
- **Ação Imediata:**
  1. A API revoga automaticamente toda a família de sessões associada.
  2. Identificar `traceId` e auditar registros em `identity.sessions`.
  3. Seguir procedimento de mitigação de comprometimento ([Runbook §3.1](runbook-staging.md#31-vazamento-de-credenciais-de-usuário-individual)).

#### `AlertDatabaseConnectionPoolExhausted`
- **Condição:** `hikaricp_connections_pending > 0` persistente por mais de 60 segundos.
- **Impacto:** Esgotamento de conexões disponíveis com o PostgreSQL; requisições entram em fila e sofrem timeout.
- **Ação Imediata:**
  1. Verificar consultas lentas (`pg_stat_activity`) no banco gerenciado.
  2. Confirmar se conexões órfãs ou transações não comitadas estão travando o pool.
  3. Reiniciar a aplicação se necessário para recriar o pool HikariCP.

---

### 3.2 Alertas Sev-2 (Altos — Resposta < 1 hora)

#### `AlertTranslationProviderDown`
- **Condição:** Taxa de sucesso do provedor LibreTranslate < 90% em janela de 5 minutos (erros 502/504 em cache miss).
- **Impacto:** Clientes não conseguem traduzir novas descrições de anime; cache hits continuam funcionando.
- **Ação Imediata:**
  1. Verificar status do serviço privado `mykytadu-libretranslate` no Render.
  2. Reiniciar o serviço LibreTranslate conforme procedimento do [Runbook §2.2](runbook-staging.md#22-procedimento-de-mitigação).

#### `AlertHighLatencyAuth`
- **Condição:** Latência p95 de `/api/v1/auth/login` > 500 ms por 5 minutos contínuos.
- **Impacto:** Lentidão na experiência de entrada no aplicativo KMP.
- **Ação Imediata:**
  1. Analisar custo de computação do Argon2/BCrypt de senhas.
  2. Avaliar CPU do nó no Render e concorrência no banco de dados.

---

### 3.3 Alertas Sev-3 (Médios — Resposta < 4 horas)

#### `AlertAuthenticationRateLimited`
- **Condição:** Taxa de bloqueio por rate limit de login > 50 eventos/minuto.
- **Impacto:** Tentativa sistemática de adivinhação de senhas ou scripts mal configurados.
- **Ação Imediata:**
  1. Analisar IPs de origem via gateway/proxy reverso do Render.
  2. Confirmar se IPs de emuladores ou redes corporativas estão sendo afetados.

#### `AlertTranslationQuotaSpike`
- **Condição:** Respostas HTTP 429 (`translation_quota_exceeded`) ultrapassam 5% do tráfego diário.
- **Impacto:** Usuários atingindo o teto de 50.000 caracteres/dia.
- **Ação Imediata:**
  1. Verificar se cliente KMP está realizando retries em loop indevidamente.
  2. Analisar distribuição de consumo agregado no banco (`translation.user_daily_translation_usage`).

---

## 4. Rastreamento e Instrumentação Técnica

As métricas são coletadas nativamente pelo Micrometer e expostas pelo endpoint `/actuator/metrics` e `/actuator/prometheus` (quando habilitado).

### 4.1 Mapeamento de Métricas Micrometer

```text
mykytadu.identity.authentication.attempts (tag: result)
mykytadu.translation.cache (tag: result=hit|miss)
mykytadu.translation.characters.translated
mykytadu.translation.provider.latency (tag: provider, success=true|false)
http.server.requests (tag: uri, status, outcome)
hikaricp.connections.pending
hikaricp.connections.active
jvm.memory.used (tag: area=heap|nonheap)
```

---

## 5. Auditoria e Revisão Periódica

1. **Revisão Mensal:** A equipe de engenharia revisará o consumo do Error Budget de cada SLO.
2. **Reajuste com Evidência:** Nenhum SLO pode ser relaxado sem incidente documentado ou alteração de arquitetura aprovada em ADR.
