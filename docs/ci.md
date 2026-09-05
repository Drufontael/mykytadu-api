# MykytaDu API — Integração contínua

> **Status:** vigente
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Objetivo

O workflow `CI` aplica no GitHub Actions o mesmo gate obrigatório usado no desenvolvimento local. Nesta fase ele valida a fundação da aplicação; publicação de imagem OCI e deploy permanecem fora do escopo.

## 2. Disparos e concorrência

O pipeline é executado:

- em todo pull request;
- em pushes para `master`;
- manualmente por `workflow_dispatch`.

Execuções anteriores da mesma referência são canceladas quando uma nova revisão é enviada. O workflow possui somente permissão de leitura do conteúdo do repositório.

## 3. Gate obrigatório

| Ambiente | Comando |
| --- | --- |
| Windows local | `.\gradlew.bat check --no-daemon --stacktrace` |
| GitHub Actions | `./gradlew check --no-daemon --stacktrace` |

O lifecycle `check` concentra compilação, testes unitários, arquiteturais e de integração, ktlint, Detekt e geração do relatório XML do Kover. Qualquer falha interrompe o job `Verify` e deve impedir a integração da mudança.

O teste de integração inicia PostgreSQL efêmero por Testcontainers. O CI não declara um serviço PostgreSQL paralelo e não usa o banco persistente do Compose local.

## 4. Ambiente de execução

- runner Linux hospedado pelo GitHub;
- Java 25 Temurin;
- Gradle Wrapper versionado e validado antes da execução;
- cache gerenciado pela action oficial do Gradle;
- Docker disponibilizado pelo runner para Testcontainers;
- limite de 20 minutos para evitar execuções presas.

## 5. Diagnóstico e evidências

Os diretórios `build/reports/` e `build/test-results/` são publicados no artefato `verification-reports`, inclusive quando um gate falha, e mantidos por 14 dias.

Para reproduzir uma falha localmente, execute o comando equivalente da seção 3. Diferenças entre os ambientes devem ser tratadas como defeito de determinismo do build, não contornadas no workflow.

## 6. Proteção da branch

Após a primeira execução remota bem-sucedida, configurar nas regras da branch `master` o check `Verify` como obrigatório. O arquivo do workflow faz o job falhar diante de qualquer gate reprovado; a regra do repositório é o mecanismo que impede o merge.

## 7. Limites desta etapa

Ainda não fazem parte do pipeline:

- construção e publicação de imagem OCI;
- deploy em qualquer ambiente;
- validação de compatibilidade OpenAPI;
- análise de vulnerabilidades e assinatura de artefatos.

Esses gates serão adicionados nas tarefas do roadmap que introduzirem os respectivos artefatos e riscos.
