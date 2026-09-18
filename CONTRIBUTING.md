# Contribuindo com o MykytaDu API

> **Status:** vigente
> **Versão:** 0.1
> **Data de referência:** 17 de setembro de 2026

Este documento é o ponto de entrada para preparar o ambiente, executar os
gates e revisar uma alteração do `mykytadu-api`. Ele resume o fluxo comum e
encaminha os detalhes para os documentos canônicos.

## 1. Pré-requisitos

- Java 25;
- Docker Desktop ou Docker Engine com Docker Compose v2;
- Node.js 22.12.0 ou superior com `npx`;
- Git;
- acesso ao repositório e à branch da sprint correspondente.

Validar a ferramenta antes de alterar o código:

### PowerShell

```powershell
java -version
docker version
docker compose version
node --version
npx --version
.\gradlew.bat --version
```

### Shell Unix ou runner Linux

```bash
java -version
docker version
docker compose version
node --version
npx --version
./gradlew --version
```

O wrapper deve informar Gradle 9.5.0 e uma JVM Java 25. O Docker precisa estar
disponível para Testcontainers, PostgreSQL local e construção da imagem OCI.

## 2. Fluxo recomendado

1. Ler `README.md`, `docs/roadmap.md`, o registro da sprint ativa,
   `docs/modelagem.md` e os ADRs relacionados.
2. Confirmar a task, o critério de aceite e os arquivos sob responsabilidade da
   alteração.
3. Executar os testes ou gates proporcionais durante o desenvolvimento.
4. Atualizar contrato, documentação, evidências e migrations afetados na mesma
   mudança.
5. Revisar `git diff`, caminhos adicionados, segredos e artefatos gerados.
6. Executar o gate obrigatório antes de declarar a alteração pronta.

Não versionar senhas, tokens, chaves, headers de autorização, conteúdo integral
enviado para tradução, diretórios `build/`, volumes Docker ou configurações
locais do Codex.

## 3. Arquitetura e organização

O projeto é um monólito modular com um único módulo Gradle. Os módulos lógicos
são `app`, `api`, `identity`, `translation` e `shared`.

Domínios organizam-se por camadas:

```text
feature/
├── domain          # regras, entidades, value objects e eventos
├── application     # casos de uso, portas e transações
├── infrastructure  # JPA, banco e adapters externos
└── web             # controllers, DTOs e mapeamento HTTP
```

As APIs públicas entre módulos usam named interfaces mínimas. Não importar
pacotes internos, entidades JPA, DTOs HTTP ou DTOs de fornecedor de outro
módulo. Consulte [modelagem e arquitetura](docs/modelagem.md) antes de alterar
fronteiras.

## 4. Banco e aplicação local

O fluxo local usa o profile `local`, Docker Compose e PostgreSQL 18.6. Os
detalhes de credenciais locais, volume, reset e diagnóstico estão em
[Ambiente local](docs/ambiente-local.md).

### Iniciar a aplicação

PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Shell Unix ou runner Linux:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Os probes públicos da API ficam em `http://localhost:8081`:

PowerShell:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health/liveness
Invoke-RestMethod http://localhost:8081/actuator/health/readiness
```

Shell Unix:

```bash
curl --fail --silent --show-error http://localhost:8081/actuator/health/liveness
curl --fail --silent --show-error http://localhost:8081/actuator/health/readiness
```

Para alterar a porta do PostgreSQL sem editar o Compose:

PowerShell:

```powershell
$env:POSTGRES_PORT = "55432"
docker compose up -d --wait postgres
```

Shell Unix:

```bash
export POSTGRES_PORT=55432
docker compose up -d --wait postgres
```

Não misturar credenciais do profile `local` com ambientes remotos. A aplicação
sem `local`, `test` ou `integration-test` exige
`MYKYTADU_DATABASE_URL`, `MYKYTADU_DATABASE_USERNAME` e
`MYKYTADU_DATABASE_PASSWORD` no ambiente de execução.

## 5. Testes e qualidade

Os testes usam JUnit 5, AssertJ, MockK somente nas fronteiras necessárias,
Testcontainers para PostgreSQL e testes arquiteturais do Spring Modulith.
Consulte [estratégia de testes](docs/testes.md) para as convenções de doubles e
categorias.

Executar uma classe ou categoria:

```powershell
.\gradlew.bat test --tests "br.com.mykytadu.architecture.*"
.\gradlew.bat test --tests "br.com.mykytadu.integration.*"
```

```bash
./gradlew test --tests 'br.com.mykytadu.architecture.*'
./gradlew test --tests 'br.com.mykytadu.integration.*'
```

Verificações individuais e seus relatórios estão descritos em
[qualidade estática e cobertura](docs/qualidade.md). O comando oficial antes da
entrega é:

PowerShell:

```powershell
.\gradlew.bat check --no-daemon --stacktrace
```

Shell Unix ou CI:

```bash
./gradlew check --no-daemon --stacktrace
```

O gate inclui compilação, testes, Testcontainers, testes arquiteturais,
ktlint, Detekt, Kover e lint do OpenAPI.

## 6. Contrato HTTP

`docs/api/openapi.yaml` é a fonte da verdade. Alterações HTTP devem atualizar,
na mesma unidade:

- OpenAPI e exemplos;
- Problem Details e catálogo de erros, quando aplicável;
- testes HTTP ou de contrato;
- documentação de compatibilidade.

Lint local:

```powershell
npx --yes @redocly/cli@2.45.0 lint docs/api/openapi.yaml
```

```bash
npx --yes @redocly/cli@2.45.0 lint docs/api/openapi.yaml
```

O job `OpenAPI contract` repete o lint no GitHub Actions e compara a revisão
com a branch base em pull requests. A comparação de compatibilidade é um gate
remoto; warnings conhecidos do lint não bloqueiam, mas erros estruturais e
quebras incompatíveis bloqueiam.

## 7. Migrations

Flyway é o único mecanismo de evolução do banco. Antes de criar uma migration:

1. conferir a maior versão existente;
2. usar o horário local de `America/Sao_Paulo` com precisão de segundos;
3. seguir `VyyyyMMddHHmmss-descricao.sql`;
4. usar descrição em `snake_case` e o separador `-`;
5. nunca editar uma migration já aplicada;
6. validar a migration em PostgreSQL limpo e nos testes de integração.

Exemplo:

```text
V20260904184904-create_identity_and_translation_schemas.sql
```

Hibernate valida o schema, mas não o cria nem altera. Ownership e schemas
`identity` e `translation` permanecem separados. As regras completas estão em
[Ambiente local](docs/ambiente-local.md) e [modelagem](docs/modelagem.md).

## 8. Imagem OCI

Construir a imagem local somente depois do gate:

```powershell
.\gradlew.bat bootBuildImage --console=plain
```

```bash
./gradlew bootBuildImage --console=plain
```

O task executa `check`, usa o builder Paketo fixado por digest e Java 25. A
imagem não contém credenciais; banco e demais configurações entram no runtime.
Consulte [Imagem OCI](docs/operacao/imagem-oci.md) para executar a imagem e
validar liveness/readiness.

## 9. Correspondência com o CI

| Job | Equivalente local | Observação |
| --- | --- | --- |
| `Verify` | `check --no-daemon --stacktrace` | usa Gradle Wrapper; no Windows, `.\gradlew.bat` |
| `OpenAPI contract` | `npx ... redocly ... lint` | oasdiff compara com a base somente no PR |
| `Dependency review` | não há equivalente local completo | depende do Dependency graph e executa em PRs |
| `OCI image` | `bootBuildImage --console=plain` | só começa após os gates anteriores |
| `Reports` | relatórios em `build/reports/` e `build/test-results/` | artefatos são publicados pelo CI por 14 dias |

O runner do GitHub é Linux; por isso o CI usa `./gradlew`, Docker do runner e
shell Bash. Diferenças de sintaxe entre PowerShell e Bash devem ficar somente
na camada de comando, sem alterar regras, perfis ou critérios do build.

## 10. Checklist antes de solicitar revisão

- [ ] a task e o critério de aceite estão claros;
- [ ] a alteração respeita módulos, camadas e APIs públicas;
- [ ] contrato, testes, migrations e documentação afetados estão coerentes;
- [ ] nenhum segredo, token ou artefato gerado entrou no diff;
- [ ] `git diff --check` passou;
- [ ] `check --no-daemon --stacktrace` passou;
- [ ] riscos, limitações e validações pendentes estão registrados na sprint.

Branches protegidas exigem pull request e os checks definidos no ruleset
`protect-master`. Commit, push, pull request e merge seguem a autorização e o
processo do repositório; a documentação não substitui essas proteções.

## Documentos de referência

- [ambiente local](docs/ambiente-local.md);
- [integração contínua](docs/ci.md);
- [testes](docs/testes.md);
- [qualidade estática e cobertura](docs/qualidade.md);
- [imagem OCI](docs/operacao/imagem-oci.md);
- [modelagem e arquitetura](docs/modelagem.md);
- [roadmap](docs/roadmap.md);
- [registro da sprint B1.1](docs/sprints/B1.1.md).
