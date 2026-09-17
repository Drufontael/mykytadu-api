# MykytaDu API — Integração contínua

> **Status:** vigente
> **Versão:** 0.3
> **Data de referência:** 17 de setembro de 2026

## 1. Objetivo

O workflow `CI` aplica no GitHub Actions o mesmo gate obrigatório usado no desenvolvimento local. Nesta fase ele valida a fundação da aplicação e o contrato OpenAPI; publicação de imagem OCI e deploy permanecem fora do escopo.

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

O lifecycle `check` concentra compilação, testes unitários, arquiteturais e de integração, ktlint, Detekt, lint do OpenAPI e geração do relatório XML do Kover. Qualquer falha interrompe o job `Verify` e deve impedir a integração da mudança.

O task Gradle `openApiLint` usa o Redocly CLI `2.45.0` com `npx` e faz parte do `check`. O job `OpenAPI contract` repete esse lint para manter um status dedicado e, em pull requests, executa adicionalmente a comparação de compatibilidade com oasdiff. Os dois caminhos usam a mesma especificação e versão do linter.

O teste de integração inicia PostgreSQL efêmero por Testcontainers. O CI não declara um serviço PostgreSQL paralelo e não usa o banco persistente do Compose local.

## 4. Validação do contrato OpenAPI

O job `OpenAPI contract` executa em paralelo ao `Verify`:

- em todo pull request, push para `master` e execução manual, o [Redocly CLI](https://redocly.com/docs/cli/commands/lint) `2.45.0` executa `lint` sobre `docs/api/openapi.yaml`;
- em pull requests, o workflow busca a branch base e o [oasdiff Action](https://github.com/oasdiff/oasdiff-action) `v0.1.11` compara o contrato antigo com o revisado;
- se a branch base ainda não possuir `docs/api/openapi.yaml`, a comparação é registrada como ignorada; o lint do contrato revisado continua obrigatório;
- `fail-on: ERR` bloqueia alterações inequivocamente incompatíveis;
- referências externas são proibidas e o relatório não é enviado para revisão hospedada (`review: false`).

Warnings conhecidos do lint, como servidor local de desenvolvimento ou licença proprietária sem URL, são reportados sem bloquear. Erros de estrutura, referências inválidas ou mudanças incompatíveis bloqueiam o job.

Para reproduzir o lint localmente, usando Node.js `>=22.12.0`:

```bash
npx --yes @redocly/cli@2.45.0 lint docs/api/openapi.yaml
```

A comparação de compatibilidade depende da branch base de um pull request e, por isso, é exercitada no CI remoto. O oasdiff aceita referências Git no formato `origin/<branch>:<arquivo>`.

## 5. Ambiente de execução

- runner Linux hospedado pelo GitHub;
- Java 25 Temurin;
- Gradle Wrapper versionado e validado antes da execução;
- cache gerenciado pela action oficial do Gradle;
- Docker disponibilizado pelo runner para Testcontainers;
- limite de 20 minutos para evitar execuções presas.

## 6. Diagnóstico e evidências

Os diretórios `build/reports/` e `build/test-results/` são publicados no artefato `verification-reports`, inclusive quando um gate falha, e mantidos por 14 dias.

Para reproduzir uma falha localmente, execute o comando equivalente das seções 3 ou 4. Diferenças entre os ambientes devem ser tratadas como defeito de determinismo do build, não contornadas no workflow.

## 7. Proteção da branch

Após a primeira execução remota bem-sucedida, configurar nas regras da branch `master` os checks `Verify` e `OpenAPI contract` como obrigatórios. O arquivo do workflow faz os jobs falharem diante de qualquer gate reprovado; a regra do repositório é o mecanismo que impede o merge.

## 8. Limites desta etapa

Ainda não fazem parte do pipeline:

- construção e publicação de imagem OCI;
- deploy em qualquer ambiente;
- análise de vulnerabilidades e assinatura de artefatos.

Esses gates serão adicionados nas tarefas do roadmap que introduzirem os respectivos artefatos e riscos.
