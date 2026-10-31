# MykytaDu API — Relatório de Go / No-Go (Release Candidate v1.0.0)

> **Status:** Aprovado (GO)  
> **Versão:** 1.0.0-RC  
> **Data de referência:** 31 de outubro de 2026  
> **Escopo:** Avaliação formal de prontidão técnica, operacional e de segurança para release candidate da API MykytaDu

Este documento consolida a deliberação formal de **Go / No-Go** para a emissão do Release Candidate (RC) da API MykytaDu v1.0.0, em conclusão da Sprint B4.2 e encerramento da Fase B4 do roadmap de engenharia.

---

## 1. Comitê de Avaliação e Responsáveis

| Papel | Responsável | Parecer |
|---|---|---|
| **Liderança Técnica & Arquitetura** | Equipe de Engenharia MykytaDu | **GO** |
| **Operação & SRE (On-Call)** | Operação de Infraestrutura Staging | **GO** |
| **Segurança e Privacidade (DPO)** | Responsável de Governança e Dados | **GO** |

---

## 2. Checklist de Critérios de Corte (Cut Criteria)

| Domínio | Critério Avaliado | Evidência Técnica | Status |
|---|---|---|---|
| **Contrato HTTP** | OpenAPI 3.1 auditado sem erros e contrato congelado na v1.0.0 | Task `openApiLint` (Redocly CLI) aprovada e testes `KmpContractCompatibilityIntegrationTests` | **Aprovado** |
| **Arquitetura** | Monólito modular com fronteiras estritas e ausência de dependências cíclicas | Verificação automatizada Spring Modulith (`ApplicationModules.verify()`) | **Aprovado** |
| **Qualidade de Código** | Estilo e análise estática 100% aderentes às regras do projeto | Tasks `ktlintCheck` e `detekt` aprovadas sem suppressions indevidas | **Aprovado** |
| **Cobertura de Testes** | Suíte de testes abrangendo unidade, fatia, E2E e integração real com banco | 100% dos testes passando com Testcontainers PostgreSQL 18 e relatório Kover | **Aprovado** |
| **Persistência & Flyway** | Migrações determinísticas, isolamento de schemas `identity`/`translation` e zero tabelas em `public` | `DatabaseMigrationIntegrationTests` e validação de schema history | **Aprovado** |
| **Disaster Recovery** | Procedimento de dump lógico e restauração em banco isolado comprovado sob RPO/RTO | `DatabaseBackupRestoreIntegrationTests` e SOP `docs/operacao/backup-restore.md` | **Aprovado** |
| **Segurança & Segredos** | Deny-by-default, autenticação assimétrica RSA, HMAC de refresh, ausência de senhas hardcoded | `SecurityAuditIntegrationTests` e SOP `docs/operacao/revisao-seguranca.md` | **Aprovado** |
| **Rotação de Chaves** | Rotação suave de JWT e Refresh Token sem indisponibilidade para sessões ativas | `KeyRotationIntegrationTests` comprovando aceitação de chaves ativas e prévias simultâneas | **Aprovado** |
| **Privacidade & Retenção** | Não persistência de texto original (ADR-008) e purga física de cache em 30 dias (ADR-014) | `PrivacyAndRetentionIntegrationTests` e SOP `docs/operacao/inventario-privacidade-retencao.md` | **Aprovado** |
| **Observabilidade & SLOs** | Probes de liveness/readiness, telemetria Micrometer e matriz de alertas Sev-1/2/3 | `ActuatorMetricsIntegrationTests` e catálogo `docs/operacao/slos-e-alertas.md` | **Aprovado** |
| **Operação de Staging** | Topologia declarativa Render e playbook de contingência para incidentes e rollback | Blueprint `render.yaml` e runbook `docs/operacao/runbook-staging.md` | **Aprovado** |

---

## 3. Matriz de Riscos Residuais Aceitos

| Risco Residual | Probabilidade / Impacto | Mitigação Vigente | Responsável |
|---|---|---|---|
| **R-01: Sobrecarga em cache miss do LibreTranslate** | Média / Média | Respostas cacheadas continuam funcionando (200 OK); rate limit e quota diária de 50.000 caracteres/dia evitam esgotamento; alerta Sev-2 configurado | Engenharia Backend |
| **R-02: Janela de tolerância de replay de refresh token (2 min)** | Baixa / Alta | Rotação atômica com invalidação da família inteira de sessões ao primeiro sinal de reuso malicioso (alerta Sev-1 imediato) | Segurança / On-Call |
| **R-03: Janela de RPO em backup diário (até 24h)** | Baixa / Média | Em caso de manutenção planejada ou avanço de migration, o backup prévio imediato é obrigatório (RPO = 0s) | Operações |

---

## 4. Plano de Rollback e Contingência

Em caso de anomalia grave pós-lançamento da versão:

1. **Rollback Imediato da Imagem:** O orquestrador Render reverte para a tag anterior estável com um clique, sem quebra de compatibilidade com o PostgreSQL (schemas expand-and-contract).
2. **Mitigação de Sessão:** Procedimento de revogação global via `identity.sessions` documentado no Runbook Operacional.
3. **Escalonamento:** Incidentes classificados conforme a matriz de severidade do [Runbook Staging](runbook-staging.md) com tempo de resposta < 15 minutos para Sev-1.

---

## 5. Deliberação Final

Todos os 11 critérios de corte foram atendidos e comprovados tecnicamente por testes automatizados e documentação canônica. Não existem débitos técnicos bloqueantes nem vulnerabilidades críticas conhecidas.

**Decisão Unânime: GO**  
A API MykytaDu é formalmente declarada como **Release Candidate v1.0.0 (Marco M4 concluído e prontidão operacional atingida)**.
