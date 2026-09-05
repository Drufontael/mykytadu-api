# MykytaDu API

Backend modular do MykytaDu, desenvolvido com Kotlin, Spring Boot, Spring Modulith, PostgreSQL e Flyway.

## Começando

Pré-requisitos:

- Java 25 disponível no terminal;
- Docker Desktop ou Docker Engine em execução;
- Docker Compose v2;
- Git.

No PowerShell, valide a máquina e execute todos os gates:

```powershell
java -version
docker version
docker compose version
.\gradlew.bat --version
.\gradlew.bat check --no-daemon --stacktrace
```

Inicie a aplicação com o profile local:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

O Spring Boot inicia ou reutiliza o PostgreSQL definido em `compose.yaml`. Quando a aplicação estiver pronta, consulte:

- `http://localhost:8080/actuator/health/liveness`;
- `http://localhost:8080/actuator/health/readiness`.

Para parar a aplicação, use `Ctrl+C`. O PostgreSQL e seus dados locais permanecem disponíveis até serem explicitamente parados ou removidos.

## Documentação

- [ambiente local, ciclo de uso, reset e troubleshooting](docs/ambiente-local.md);
- [integração contínua](docs/ci.md);
- [estratégia de testes](docs/testes.md);
- [qualidade estática e cobertura](docs/qualidade.md);
- [arquitetura e modelagem evolutiva](docs/modelagem.md);
- [roadmap](docs/roadmap.md);
- [documento mestre do backend](docs/documento-mestre-backend.md);
- [registro das sprints](docs/sprints/README.md).

As credenciais presentes no Compose são exclusivamente locais. Não reutilize esses valores em staging ou produção e não versione segredos reais.
