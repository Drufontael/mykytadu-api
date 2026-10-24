---
name: mykytadu-api-post-merge
description: Sincroniza a branch principal (master) local após merge de PR no GitHub, deleta com segurança a branch local da sprint concluída e limpa referências remotas podadas. Use quando o usuário invocar explicitamente $mykytadu-api-post-merge ou solicitar retorno à master e exclusão de branch pós-merge.
---

# MykytaDu API Post-Merge

Conduza a sincronização segura da branch principal (`master`) e a limpeza da branch local da sprint após a conclusão do merge de Pull Request no repositório remoto (GitHub). Esta skill automatiza a transição de encerramento da sprint no ambiente local, assegurando que a branch `master` receba os commits integrados remotamente, a branch local concluída seja removida com segurança e as referências remotas podadas sejam sincronizadas.

## Escopo e aplicabilidade

Use esta skill quando:
- O usuário informar que realizou o merge do Pull Request no GitHub (ex.: *"Já fiz o merge no github, pode voltar pra master, fazer o pull e apagar a branch"*);
- O usuário invocar explicitamente `$mykytadu-api-post-merge`;
- For solicitado o retorno à `master` e a limpeza de branches de sprint já incorporadas.

Esta skill **não** executa merge remoto, não cria pull requests e não realiza `git push`. O merge ocorre exclusivamente pelo usuário no GitHub, e esta skill atua no ciclo subsequente de sincronização local.

## Pré-condições obrigatórias

Antes de executar qualquer comando de alternância de branch ou remoção:

1. **Inspeção do working tree:** Execute `git status`. O working tree deve estar limpo (`nothing to commit, working tree clean`). Se houver alterações não comitadas (staged ou unstaged) ou arquivos não rastreados que não façam parte de `.gitignore`, interrompa imediatamente e alerte o usuário para evitar perda de dados.
2. **Identificação da branch de origem:** Identifique o nome da branch da sprint ativa/concluída (ex.: `sprint-B3.2`).
   - Se o repositório estiver na branch da sprint, registre seu nome para exclusão posterior.
   - Se o repositório já estiver na branch `master`, consulte as branches locais com `git branch` para identificar a branch `sprint-*` correspondente que foi mergeada.
3. **Confirmação do merge:** Confirme que a solicitação do usuário expressa a conclusão do merge no remoto.

## Sequência de execução

A execução segue os passos estritamente nesta ordem:

### 1. Alternar para a branch principal

```bash
git checkout master
```

Confirme que a alternância para `master` foi concluída com sucesso.

### 2. Atualizar a branch principal com o remoto

```bash
git pull origin master
```

Obtenha as atualizações mais recentes do remoto. Verifique a saída do comando:
- Deve ser um avanço rápido (`Fast-forward`) ou atualização limpa trazendo o commit de merge do PR correspondente;
- Se houver conflito ou divergência local na `master`, interrompa imediatamente e relate a situação.

### 3. Excluir a branch local da sprint de forma segura

Execute a exclusão segura da branch local:

```bash
git branch -d <branch-da-sprint>
```

- **Caso padrão:** O Git confirma a exclusão segura (`Deleted branch <branch> (was <hash>)`).
- **Tratamento de Squash Merge / Rebase:** Se o merge no GitHub tiver sido realizado via *Squash and merge* ou *Rebase and merge*, o hash do commit na `master` será diferente do hash na branch da sprint, fazendo com que `git branch -d` retorne o aviso:
  `error: The branch '<branch>' is not fully merged.`
  Nesse caso:
  1. Inspecione o `git log -n 5 origin/master` para confirmar que as alterações da sprint estão de fato presentes na `master`;
  2. Somente após essa confirmação inequívoca, use `git branch -D <branch-da-sprint>` para concluir a remoção solicitada, informando a justificativa técnica no relatório.

### 4. Podar referências remotas obsoletas

Sincronize as referências remotas do Git local removendo ponteiros para branches que já foram excluídas no GitHub:

```bash
git remote prune origin
```

Confirme se referências antigas (como `origin/<branch-da-sprint>`) foram podadas.

### 5. Verificar o estado final

Execute:

```bash
git status
git branch -vv
```

Certifique-se de que a branch atual é a `master`, que ela está sincronizada com `origin/master` e que o diretório de trabalho está limpo.

## Preservação e proibições

- Nunca use `git reset --hard` ou `git clean` para forçar limpeza de working tree sujo.
- Nunca execute `git branch -d` ou `git branch -D` na branch `master` ou `main`.
- Nunca execute `git push` ou comandos de mutação remota a partir desta skill.
- Não inicie tarefas da próxima sprint dentro desta skill; seu objetivo encerra-se na sincronização e prontidão da `master`.

## Relatório final

Ao concluir, informe de forma objetiva:

1. **Branch ativa:** Confirmação de que o repositório está na `master`.
2. **Atualização da master:** Hash e mensagem do commit de merge integrado do remoto.
3. **Exclusão da branch local:** Confirmação da branch da sprint excluída (e método utilizado, `-d` padrão ou `-D` por squash merge).
4. **Referências podadas:** Resultado do `git remote prune origin`.
5. **Prontidão do repositório:** Confirmação de que o ambiente local está 100% atualizado, limpo e apto para o planejamento ou início da próxima sprint.
