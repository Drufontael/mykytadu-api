# MykytaDu API — Checklist de staging

> **Status:** preparado, não ativado
> **Versão:** 0.1
> **Data de referência:** 17 de setembro de 2026

Este documento valida a composição de staging sem provisionar recursos remotos.
O profile `staging` desabilita Docker Compose, usa PostgreSQL externo e recebe
segredos somente do ambiente de execução. O Render continua sendo apenas o alvo
aprovado no [ADR-015](../adr/ADR-015-render-como-hospedagem-alvo-da-api.md).

## 1. Variáveis do ambiente

| Variável | Obrigatória | Classificação | Regra |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE=staging` | sim | configuração | ativa somente a composição de staging |
| `MYKYTADU_DATABASE_URL` | sim | sensível | URL JDBC do PostgreSQL de staging, sem senha embutida |
| `MYKYTADU_DATABASE_USERNAME` | sim | segredo operacional | usuário exclusivo do banco de staging |
| `MYKYTADU_DATABASE_PASSWORD` | sim | segredo | senha injetada pelo mecanismo protegido do ambiente |
| `MYKYTADU_SERVER_PORT` | não | configuração | porta HTTP; padrão sanitizado `8081` quando o ambiente não fornecer outra |

O banco deve ser não produtivo, isolado e possuir ownership separado nos
schemas `identity` e `translation`. Não reutilizar credenciais, URLs, volumes,
certificados ou grupos de secrets de produção.

## 2. Checklist antes da ativação

- [ ] orçamento, região, domínio e ambiente remoto aprovados;
- [ ] serviço da API usa a mesma imagem candidata validada pelo CI;
- [ ] PostgreSQL de staging foi criado separadamente e aceita conexões somente
      da API;
- [ ] `SPRING_PROFILES_ACTIVE` está definido como `staging`;
- [ ] as três variáveis `MYKYTADU_DATABASE_*` foram cadastradas no mecanismo de
      secrets e não aparecem no repositório, imagem ou logs;
- [ ] `MYKYTADU_DATABASE_URL` não contém usuário ou senha na URL;
- [ ] backup verificável foi criado antes da primeira aplicação de migration;
- [ ] migrations foram aplicadas pela versão candidata, sem editar versões já
      registradas;
- [ ] liveness e readiness foram consultados, e readiness só foi liberado após
      o banco responder;
- [ ] logs estruturados e métricas foram revisados para confirmar ausência de
      credenciais, tokens, dados pessoais e payloads de tradução;
- [ ] restore em banco isolado foi executado conforme o
      [runbook de migrações](migracoes.md);
- [ ] staging e produção usam ambientes, bancos, secrets e redes distintos.

## 3. Validação local com placeholders

O teste `StagingConfigurationTests` lê `application-staging.yaml` sem iniciar
uma conexão e confirma o profile, a desativação do Compose, os placeholders do
datasource e a porta configurável. Ele não precisa de credenciais nem de banco
remoto:

```powershell
.\gradlew.bat test --tests "br.com.mykytadu.app.configuration.StagingConfigurationTests"
```

Para uma execução manual autorizada, os valores devem ser fornecidos somente
pelo ambiente ou pelo secret manager:

```powershell
$env:SPRING_PROFILES_ACTIVE = "staging"
$env:MYKYTADU_DATABASE_URL = "jdbc:postgresql://<staging-host>:<porta>/<staging-db>"
$env:MYKYTADU_DATABASE_USERNAME = "<staging-user>"
$env:MYKYTADU_DATABASE_PASSWORD = "<secret-injetado-fora-do-historico>"
.\gradlew.bat bootRun
```

Depois da execução, remover as variáveis sensíveis da sessão conforme a política
do operador. Não registrar o comando com valores reais no histórico do shell.

## 4. Limites desta sprint

- nenhum serviço, banco, domínio ou secret remoto é provisionado nesta tarefa;
- variáveis de JWT, e-mail, tradução e rate limit serão adicionadas somente
  quando os respectivos casos de uso forem implementados;
- deploy, TLS público, CORS remoto e teste ponta a ponta permanecem nas tarefas
  responsáveis por staging e integração.
