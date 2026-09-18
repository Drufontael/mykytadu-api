# MykytaDu API — Estratégia de migrações por ambiente

> **Status:** vigente
> **Versão:** 0.1
> **Data de referência:** 17 de setembro de 2026

Este documento define como o schema evolui em cada ambiente. Flyway é o único
mecanismo de evolução; Hibernate somente valida o schema (`ddl-auto: validate`).
Nenhum procedimento desta página edita uma migration aplicada ou executa
rollback destrutivo automaticamente.

## 1. Matriz por ambiente

| Ambiente | Aplicação das migrations | Banco | Backup e recuperação | Estado nesta sprint |
|---|---|---|---|---|
| Local | Ao iniciar a aplicação com o profile `local`; o Compose fornece o PostgreSQL | volume Docker local, exclusivo para desenvolvimento | backup opcional para diagnóstico; reset é explicitamente destrutivo | reproduzível |
| Teste/CI | Durante o contexto de integração, em PostgreSQL efêmero do Testcontainers | container descartável por execução | não há backup operacional; falha a execução e o container é descartado | reproduzível |
| Staging | Durante a implantação da mesma imagem candidata, com variáveis externas | PostgreSQL não produtivo e isolado | backup antes do avanço e restore ensaiado em banco isolado | checklist sem provisionamento pago |
| Produção | Janela de mudança aprovada, com uma instância controlada executando Flyway antes de liberar tráfego incompatível | PostgreSQL gerenciado, com ownership dos schemas `identity` e `translation` | backup verificado antes da mudança; recuperação segue o runbook de incidente | fora do escopo de ativação remota |

Staging e produção não usam os valores do `compose.yaml`. URL, usuário,
senha, certificados e demais segredos entram somente no ambiente de execução.

## 2. Fluxo de avanço

Cada alteração de banco segue esta ordem:

1. conferir a maior versão no diretório de migrations e no
   `flyway_schema_history` do ambiente de referência;
2. revisar o SQL, seu owner lógico, schema-alvo, constraints, índices,
   `timestamptz`, UUID e compatibilidade com a versão anterior;
3. validar em banco limpo e avançar um banco na versão anterior, sem intervenção
   manual;
4. criar backup verificável antes do avanço em staging ou produção;
5. iniciar a versão candidata com as variáveis do ambiente; o Flyway aplica
   somente migrations pendentes e valida os checksums já registrados;
6. verificar o histórico do Flyway, probes de liveness/readiness e consultas
   técnicas sanitizadas;
7. liberar a aplicação somente depois de confirmar que o código antigo e o novo
   são compatíveis durante a janela de mudança.

Mudanças incompatíveis seguem o padrão expandir/contrair: primeiro são criados
objetos ou colunas compatíveis, depois o código passa a usá-los e, em uma
migration posterior, a estrutura antiga pode ser removida. Backfills devem ser
limitados, observáveis e separados de uma alteração estrutural de alto risco.

## 3. Backup

O backup lógico recomendado para uma mudança é o formato custom do PostgreSQL.
O segredo deve ser injetado pelo ambiente ou por um prompt seguro; não deve
aparecer na linha de comando, no histórico do shell ou na documentação.

Exemplo sanitizado, executado pelo operador autorizado:

```powershell
$env:PGPASSWORD = "<secret-injetado-fora-do-historico>"
pg_dump --format=custom --no-owner --no-privileges `
  --file="<caminho-seguro>/mykytadu-<timestamp>.dump" `
  --dbname="postgresql://<host>:<porta>/<database>"
Remove-Item Env:\PGPASSWORD
```

Antes de prosseguir, confirmar que o arquivo existe, possui tamanho não zero,
está protegido conforme a política do ambiente e pode ser lido por `pg_restore`.
O nome do arquivo deve conter somente o identificador da mudança e o instante;
não incluir e-mails, tokens ou outros dados pessoais.

## 4. Recuperação

O restore de ensaio deve ocorrer em uma base isolada, nunca sobre a base de
origem:

```powershell
$env:PGPASSWORD = "<secret-injetado-fora-do-historico>"
createdb --host="<host>" --port="<porta>" --username="<admin>" <database>_recovery
pg_restore --exit-on-error --no-owner --no-privileges `
  --dbname="postgresql://<host>:<porta>/<database>_recovery" `
  "<caminho-seguro>/mykytadu-<timestamp>.dump"
Remove-Item Env:\PGPASSWORD
```

Validar, sem imprimir dados de negócio:

```sql
SELECT version, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT table_schema, count(*)
FROM information_schema.tables
WHERE table_schema IN ('identity', 'translation')
GROUP BY table_schema
ORDER BY table_schema;
```

O resultado esperado é histórico completo e bem-sucedido do Flyway, os schemas
pertencentes aos módulos e nenhuma tabela de negócio em `public`. Depois da
validação, a base de recuperação e o arquivo temporário devem ser removidos de
forma segura conforme a política do ambiente.

Se o avanço falhar, interromper a liberação, preservar o diagnóstico e avaliar
uma nova migration corretiva. `flyway repair` só pode ser usado após revisão
explícita de checksum e não substitui restore. A recuperação operacional é a
reconstrução em uma base/instância isolada a partir do backup verificado, seguida
de troca controlada da conexão; ela não é um `clean`, `drop schema` ou rollback
automático.

## 5. Ensaio reproduzível B1.2-T3

O ensaio desta sprint usa um PostgreSQL 18.6 descartável e dados sintéticos.
Ele comprova a sequência baseline → migration funcional → backup → restore,
sem tocar no volume local nem em qualquer ambiente remoto:

1. iniciar um container PostgreSQL temporário;
2. iniciar a aplicação com `spring.flyway.target` apontando para a baseline e
   confirmar somente a versão `20260904184904`;
3. reiniciar a aplicação sem `target` e confirmar o avanço para
   `20260917193036`;
4. inserir um registro sintético, gerar um dump custom e restaurá-lo em uma
   base de recuperação isolada;
5. confirmar a presença do registro por contagem, o histórico bem-sucedido do
   Flyway e a ausência de tabelas de negócio em `public`;
6. remover o container e os artefatos temporários do ensaio.

Os comandos e o resultado sanitizado do ensaio ficam registrados no arquivo da
sprint, sem credenciais, URLs reais, e-mails reais ou payloads de negócio.

## 6. Limites e pendências

- provisionamento, backup gerenciado e restore no Render permanecem fora do
  escopo até orçamento e secrets serem aprovados;
- retenção e exclusão de dados seguem o [ADR-014](../adr/ADR-014-politica-de-retencao-e-exclusao.md);
- a ativação remota deve acrescentar o procedimento do provedor ao runbook
  operacional antes da primeira exposição pública.
