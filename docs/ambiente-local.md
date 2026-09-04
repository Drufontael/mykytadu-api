# MykytaDu API — Ambiente local

> **Status:** configuração inicial da B-1
> **Versão:** 0.2
> **Data de referência:** 4 de setembro de 2026

## 1. Pré-requisitos

- Java 25;
- Docker Desktop ou Docker Engine com Docker Compose v2;
- portas locais necessárias disponíveis.

O PostgreSQL local é exclusivo para desenvolvimento. Nenhuma credencial deste ambiente deve ser reutilizada em staging ou produção.

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

| Sintoma | Verificação |
| --- | --- |
| daemon indisponível | confirmar que Docker Desktop/Engine está em execução |
| porta 5432 ocupada | definir `POSTGRES_PORT` com uma porta livre |
| container não saudável | executar `docker compose logs postgres` |
| credenciais antigas após alteração do Compose | o cluster existente preserva os valores da primeira inicialização; avaliar reset local consciente |
| dados desapareceram | confirmar o mount em `/var/lib/postgresql` e inspecionar o volume nomeado |

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
