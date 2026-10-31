# MykytaDu API — Procedimentos de Backup, Restauração e Recuperação de Desastres (DR)

> **Status:** Vigente  
> **Versão:** 1.0.0  
> **Data de referência:** 31 de outubro de 2026  
> **Escopo:** Estratégia de backup, recuperação e continuidade operacional da persistência PostgreSQL da API MykytaDu

Este documento define os Procedimentos Operacionais Padrão (SOP) para criação de cópias de segurança (backup) e restauração (restore) dos dados persistidos da API MykytaDu. As diretrizes respeitam a arquitetura de monólito modular com schemas dedicados (`identity` e `translation`), o ciclo de migrações gerenciado estritamente pelo Flyway e a política de retenção e privacidade ([ADR-014](../adr/ADR-014-politica-de-retencao-e-exclusao.md) e [ADR-008](../adr/ADR-008-nomear-projeto-e-repositorio-mykytadu-api.md)).

---

## 1. Parâmetros de Continuidade de Negócio (RPO e RTO)

| Parâmetro | Definição | Meta Operacional | Justificativa Técnica |
|---|---|---|---|
| **RPO (Recovery Point Objective)** | Perda máxima tolerada de dados em caso de desastre | **<= 24 horas** (rotina diária)<br>**0 segundos** (janela planejada de migração) | Backups diários automatizados cobrem desastres globais; backups imediatos pré-migration eliminam perda em manutenções. |
| **RTO (Recovery Time Objective)** | Tempo máximo para restabelecer a operação a partir do backup | **<= 30 minutos** | Restauração de dump customizado do PostgreSQL em nova instância isolada e apontamento de DNS/variáveis. |

---

## 2. Tipos de Backup e Política de Retenção

1. **Backup Lógico Customizado (`pg_dump -Fc`):**
   - Formato binário proprietário do PostgreSQL (`custom`), com compressão nativa e suporte à restauração seletiva e paralela com `pg_restore`.
   - Executado obrigatoriamente antes de qualquer avanço de migration em staging e produção.
2. **Backups Diários Automatizados da Infraestrutura:**
   - Snapshots contínuos ou agendados providos pelo provedor PostgreSQL gerenciado (Render ou RDS).
3. **Retenção e Descarte (ADR-014):**
   - Os arquivos de backup são retidos por no máximo **30 dias**.
   - Backups com mais de 30 dias são descartados permanentemente para conformidade com a LGPD e a política de minimização.

---

## 3. Procedimento Operacional de Backup Pré-Mudança

O operador autorizado deve executar a rotina de extração via terminal seguro:

```powershell
# 1. Definir credenciais e timestamp do snapshot
$TIMESTAMP = (Get-Date).ToString("yyyyMMddHHmmss")
$BACKUP_FILE = "mykytadu-backup-$TIMESTAMP.dump"
$env:PGPASSWORD = "$MYKYTADU_DATABASE_PASSWORD"

# 2. Executar pg_dump no formato custom (-Fc)
pg_dump --format=custom `
        --no-owner `
        --no-privileges `
        --host="$DB_HOST" `
        --port="$DB_PORT" `
        --username="$DB_USER" `
        --file="$BACKUP_FILE" `
        "$DB_NAME"

# 3. Remover senha do ambiente imediatamente
Remove-Item Env:\PGPASSWORD

# 4. Validar integridade básica do arquivo gerado
if ((Get-Item $BACKUP_FILE).Length -gt 0) {
    Write-Host "Backup gerado com sucesso: $BACKUP_FILE ($( (Get-Item $BACKUP_FILE).Length ) bytes)"
} else {
    Write-Error "Falha: Arquivo de backup vazio ou não encontrado!"
}
```

> [!IMPORTANT]
> **Nunca inclua dados pessoais ou credenciais no nome do arquivo de backup.** O nome deve conter apenas identificadores técnicos e o instante ISO em UTC.

---

## 4. Procedimento de Restauração em Ambiente Isolado (Ensaio de DR)

**Regra inegociável:** Jamais realize restore de teste diretamente sobre a base ativa de produção. A validação ocorre sempre em uma base de dados isolada (`_recovery`).

### 4.1 Passo a passo de Restauração

```powershell
# 1. Injetar credencial temporária
$env:PGPASSWORD = "$MYKYTADU_DATABASE_PASSWORD"

# 2. Criar banco isolado para validação
createdb --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" "${DB_NAME}_recovery"

# 3. Executar restauração com pg_restore
pg_restore --exit-on-error `
           --no-owner `
           --no-privileges `
           --host="$DB_HOST" `
           --port="$DB_PORT" `
           --username="$DB_USER" `
           --dbname="${DB_NAME}_recovery" `
           "$BACKUP_FILE"

# 4. Limpar credencial do ambiente
Remove-Item Env:\PGPASSWORD
```

### 4.2 Checklist de Verificação de Integridade Pós-Restore

Execute as seguintes consultas na base restaurada para atestar a conformidade:

1. **Histórico de Migrações Flyway:**
   ```sql
   SELECT version, description, success, installed_on
   FROM flyway_schema_history
   ORDER BY installed_rank DESC;
   ```
   *Critério:* Todas as versões devem reportar `success = true` e o checksum deve coincidir.

2. **Isolamento de Schemas Modulares:**
   ```sql
   SELECT table_schema, count(*) as total_tables
   FROM information_schema.tables
   WHERE table_schema IN ('identity', 'translation', 'public')
   GROUP BY table_schema;
   ```
   *Critério:* Schemas `identity` e `translation` devem conter suas tabelas de domínio; `public` deve conter unicamente `flyway_schema_history` e zero tabelas de negócio.

3. **Auditoria de Minimização e Privacidade:**
   ```sql
   SELECT column_name
   FROM information_schema.columns
   WHERE table_schema = 'translation' AND table_name = 'translations';
   ```
   *Critério:* A coluna `content_hash` deve existir; nenhuma coluna contendo texto original da descrição pode estar presente (ADR-008).

### 4.3 Descarte Seguro da Base de Ensaio

Após a validação bem-sucedida, destrua a base de recuperação e higienize os arquivos locais:

```powershell
$env:PGPASSWORD = "$MYKYTADU_DATABASE_PASSWORD"
dropdb --host="$DB_HOST" --port="$DB_PORT" --username="$DB_USER" "${DB_NAME}_recovery"
Remove-Item Env:\PGPASSWORD
Remove-Item -Force "$BACKUP_FILE"
```

---

## 5. Plano de Desastre: Falha Completa de Infraestrutura

Se a instância de banco primária sofrer perda física irreversível:

1. **Provisionar Nova Instância PostgreSQL:**
   - Criar novo banco PostgreSQL gerenciado no Render ou nuvem alvo, com mesma versão de runtime (PostgreSQL 18+).
2. **Restaurar Último Backup Verificado:**
   - Aplicar o dump mais recente via `pg_restore --clean --if-exists`.
3. **Verificar Conectividade da API:**
   - Atualizar as variáveis de ambiente `MYKYTADU_DATABASE_URL`, `MYKYTADU_DATABASE_USERNAME` e `MYKYTADU_DATABASE_PASSWORD`.
   - Reiniciar o serviço da API e consultar `GET /actuator/health/readiness`.
4. **Smoke Test Funcional:**
   - Realizar teste de login (`POST /api/v1/auth/login`) e teste de cache hit de tradução (`POST /api/v1/translations`).
