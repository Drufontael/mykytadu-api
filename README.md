# MykytaDu API

Backend modular do MykytaDu, desenvolvido com Kotlin, Spring Boot, Spring Modulith, PostgreSQL e Flyway.

## Começando

Pré-requisitos:

- Java 25 disponível no terminal;
- Docker Desktop ou Docker Engine em execução;
- Docker Compose v2;
- Node.js 22.12.0 ou superior com `npx`, usado somente pelo gate que valida o
  contrato OpenAPI com Redocly; ele não participa da execução da API;
- Git.

No PowerShell, valide a máquina e execute o gate completo:

```powershell
java -version
docker version
docker compose version
node --version
npx --version
.\gradlew.bat --version
.\gradlew.bat check --no-daemon --stacktrace
```

Em Linux ou macOS, use os comandos equivalentes:

```bash
java -version
docker version
docker compose version
node --version
npx --version
./gradlew --version
./gradlew check --no-daemon --stacktrace
```

Inicie a aplicação com o profile local no PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Em Linux ou macOS:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Durante o desenvolvimento, prefira testes e verificações direcionadas. O
`check` acima é o gate obrigatório antes de declarar uma alteração pronta;
consulte o [guia de contribuição](CONTRIBUTING.md) para os comandos rápidos e
a equivalência com o CI.

O Spring Boot inicia ou reutiliza o PostgreSQL definido em `compose.yaml`. A API local fica em `http://localhost:8081`; quando estiver pronta, consulte:

- `http://localhost:8081/actuator/health/liveness`;
- `http://localhost:8081/actuator/health/readiness`.

Para parar a aplicação, use `Ctrl+C`. O PostgreSQL e seus dados locais permanecem disponíveis até serem explicitamente parados ou removidos.

## Documentação

- [guia de contribuição, comandos locais e equivalência com o CI](CONTRIBUTING.md);
- [ambiente local, ciclo de uso, reset e troubleshooting](docs/ambiente-local.md);
- [construção e execução da imagem OCI](docs/operacao/imagem-oci.md);
- [estratégia de migrações por ambiente](docs/operacao/migracoes.md);
- [checklist e configuração de staging](docs/operacao/staging.md);
- [integração contínua](docs/ci.md);
- [observabilidade](docs/observabilidade.md);
- [estratégia de testes](docs/testes.md);
- [qualidade estática e cobertura](docs/qualidade.md);
- [arquitetura e modelagem evolutiva](docs/modelagem.md);
- [roadmap](docs/roadmap.md);
- [documento mestre do backend](docs/documento-mestre-backend.md);
- [registro das sprints](docs/sprints/README.md).

As credenciais presentes no Compose são exclusivamente locais. Não reutilize esses valores em staging ou produção e não versione segredos reais.
