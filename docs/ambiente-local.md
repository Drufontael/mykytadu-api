# MykytaDu API — Ambiente local

> **Status:** vigente
> **Versão:** 0.4
> **Data de referência:** 4 de setembro de 2026

## 1. Pré-requisitos

- Java 25;
- Docker Desktop ou Docker Engine com Docker Compose v2;
- portas locais necessárias disponíveis.

O PostgreSQL local é exclusivo para desenvolvimento. Nenhuma credencial deste ambiente deve ser reutilizada em staging ou produção.

Validar os pré-requisitos no PowerShell:

```powershell
java -version
docker version
docker compose version
.\gradlew.bat --version
```

O Gradle deve informar a versão 9.5.0 e uma JVM 25. Falhas nos comandos Docker devem ser resolvidas antes de executar testes de integração ou iniciar o profile `local`.

## 2. PostgreSQL

O `compose.yaml` utiliza:

| Item | Valor local |
| --- | --- |
| Imagem | `postgres:18.6-trixie` |
| Database | `mykytadu` |
| Usuário | `mykytadu` |
| Senha | `mykytadu-local` |
| Porta padrão no host | `5432` |
| Porta no container | `5432` |
| Volume | `mykytadu-postgres-data` |
| Destino do volume | `/var/lib/postgresql` |

Para PostgreSQL 18 ou superior, a imagem oficial define o `PGDATA` em um subdiretório específico da versão e recomenda montar o volume em `/var/lib/postgresql`. O caminho histórico `/var/lib/postgresql/data` não deve ser usado nesta configuração.

A porta pode ser alterada sem editar o Compose:

```powershell
$env:POSTGRES_PORT = "55432"
docker compose up -d --wait postgres
```

## 3. Ciclo de uso

### 3.1 Aplicação

As configurações são separadas por finalidade técnica:

| Arquivo | Finalidade | Banco |
| --- | --- | --- |
| `application.yaml` | políticas comuns a todos os ambientes | não contém credenciais e não inicia Compose |
| `application-local.yaml` | execução no computador do desenvolvedor | PostgreSQL do `compose.yaml`, com valores locais sobrescrevíveis por ambiente |
| `application-test.yaml` | testes de contexto que não exercem persistência | desabilita Compose e auto-configurações de banco; integração real será coberta por Testcontainers |
| `application-integration-test.yaml` | testes de integração | desabilita Compose; a conexão é fornecida dinamicamente pelo Testcontainers |

Iniciar a aplicação local com o profile explícito:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Não é necessário iniciar o banco separadamente nesse fluxo: a integração do Spring Boot com Docker Compose inicia ou reutiliza o serviço `postgres` e aguarda o healthcheck. A aplicação fica disponível por padrão em `http://localhost:8080`.

Em outro terminal, verificar os probes públicos:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

Interromper a aplicação com `Ctrl+C`. Como o lifecycle local é `start-only`, o container e o volume continuam disponíveis para a próxima execução.

O profile `local` mantém o container em execução ao encerrar a aplicação (`start-only`). As variáveis opcionais `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` permitem sobrescrever somente os valores locais. Não armazenar segredos de ambientes remotos nesses arquivos.

O profile `test` é ativado nos testes que não precisam de persistência. Testes de integração com banco usam `integration-test` e Testcontainers, sem reutilizar o banco local nem o estado de outra execução.

Sem os profiles `local`, `test` ou `integration-test`, a aplicação exige `MYKYTADU_DATABASE_URL`, `MYKYTADU_DATABASE_USERNAME` e `MYKYTADU_DATABASE_PASSWORD`. Uma checagem de inicialização sem valores padrão interrompe a aplicação e identifica nominalmente cada variável faltante. Segredos devem ser injetados pelo ambiente de execução, nunca versionados.

Executar somente a prova de integração com PostgreSQL efêmero:

```powershell
.\gradlew.bat test --tests "br.com.mykytadu.integration.DatabaseMigrationIntegrationTests"
```

Esse teste requer Docker disponível, inicia a imagem `postgres:18.6-trixie` em porta dinâmica, aplica todas as migrations e descarta o container ao terminar. Ele não usa o serviço nem o volume definidos no `compose.yaml`.

Executar o gate obrigatório completo, equivalente ao CI:

```powershell
.\gradlew.bat check --no-daemon --stacktrace
```

O gate compila a aplicação, executa testes unitários, arquiteturais e de integração, verifica ktlint e Detekt e gera o relatório XML do Kover. Consulte [Integração contínua](ci.md) e [Qualidade estática e cobertura](qualidade.md) para detalhes.

### 3.2 Banco isolado

Validar a configuração resolvida:

```powershell
docker compose config --quiet
```

Iniciar o banco e aguardar o healthcheck:

```powershell
docker compose up -d --wait postgres
```

Consultar estado e logs:

```powershell
docker compose ps
docker compose logs postgres
```

Parar os containers preservando os dados:

```powershell
docker compose down
```

O volume nomeado não é removido por `docker compose down`. Ao iniciar novamente, o mesmo cluster PostgreSQL será reutilizado.

## 4. Armazenamento local

O volume `mykytadu-postgres-data` é gerenciado pelo Docker no computador local. Ele persiste:

- databases e schemas;
- tabelas e índices;
- dados inseridos durante o desenvolvimento;
- metadados internos do cluster PostgreSQL.

Inspecionar o volume:

```powershell
docker volume inspect mykytadu-postgres-data
```

O volume não deve ser versionado nem acessado diretamente pela aplicação. Backups de ambientes remotos não devem ser restaurados localmente sem sanitização.

## 5. Reset destrutivo

> **Atenção:** o procedimento abaixo apaga definitivamente todo o banco local do projeto.

Somente quando a perda dos dados locais for intencional:

```powershell
docker compose down --volumes
docker compose up -d --wait postgres
```

Antes de executar, confirmar que o volume resolvido é exatamente `mykytadu-postgres-data`:

```powershell
docker volume inspect mykytadu-postgres-data
```

Não usar comandos com nomes calculados, curingas ou remoção ampla de volumes.

## 6. Diagnóstico rápido

| Sintoma | Verificação | Ação |
| --- | --- | --- |
| `JAVA_HOME` ou versão JVM incorreta | `java -version` e `.\gradlew.bat --version` | selecionar uma distribuição Java 25 e abrir um novo terminal |
| daemon Docker indisponível | `docker version` | iniciar Docker Desktop/Engine e aguardar o servidor responder |
| porta 5432 ocupada | `docker compose ps` e inspeção dos processos locais | definir `$env:POSTGRES_PORT` com uma porta livre antes de iniciar |
| container não saudável | `docker compose ps` e `docker compose logs postgres` | corrigir a causa indicada no log; não aumentar tentativas para ocultar a falha |
| credenciais antigas após alteração do Compose | comparar o ambiente atual com `docker compose config` | o cluster preserva os valores da primeira inicialização; avaliar o reset local consciente |
| migration rejeitada pelo Flyway | localizar o primeiro erro no log e conferir nome, versão e checksum | não editar migration já aplicada; criar migration corretiva quando necessário |
| readiness retorna `503` | `docker compose ps` e `docker compose logs postgres` | restaurar a conexão com o banco; liveness pode continuar saudável |
| teste Testcontainers não inicia | `docker info` e relatório em `build/reports/tests/test/` | disponibilizar Docker ao processo Gradle e repetir o teste |
| gate diverge do CI | repetir `.\gradlew.bat check --no-daemon --stacktrace` | corrigir a causa no build ou na configuração; não criar exceção exclusiva no CI |
| dados desapareceram | confirmar o mount em `/var/lib/postgresql` e inspecionar o volume nomeado | verificar se o projeto/volume correto está sendo utilizado |

Para limpar apenas saídas do Gradle, sem afetar PostgreSQL ou seu volume:

```powershell
.\gradlew.bat clean
```

## 7. Migrations Flyway

Migrations versionadas usam o horário local de São Paulo com precisão de segundos:

```text
VyyyyMMddHHmmss-descricao.sql
```

Exemplo:

```text
V20260904184904-create_identity_and_translation_schemas.sql
```

Regras:

- `V` identifica uma migration versionada;
- `yyyyMMddHHmmss` é um `localDateTime` numérico e ordenável;
- `-` é o separador configurado em `spring.flyway.sql-migration-separator`;
- a descrição usa letras minúsculas, palavras separadas por `_` e termina em `.sql`;
- cada versão precisa ser única;
- conferir a maior versão existente antes de criar um arquivo;
- não editar uma migration já aplicada em ambiente permanente;
- correções usam uma nova migration e avançam o schema;
- Hibernate valida o modelo, mas não cria nem altera objetos (`ddl-auto: validate`).
