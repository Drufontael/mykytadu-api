---
name: mykytadu-api-commit-publish
description: Revisa, valida, documenta, commita e publica alterações aceitas do mykytadu-api exclusivamente na branch da sprint atual. Use somente quando o usuário invocar explicitamente $mykytadu-api-commit-publish ou autorizar claramente commit e push; nunca cria pull request nem faz merge na master.
---

# MykytaDu API Commit Publish

Publique uma unidade coerente de trabalho na branch da sprint para acumular entregas que, ao final, serão submetidas pelo usuário a pull request e merge. A invocação explícita ou autorização textual clara para commit e push autoriza revisar alterações, atualizar a documentação diretamente afetada, preparar o stage, criar um commit novo e fazer push da branch atual. Não autoriza PR, merge, rebase, force push nem mudanças fora do trabalho aceito.

## Pré-condições da branch

1. Leia o `AGENTS.md`, o roadmap, o registro da sprint atual e o índice de skills em `.agents/README.md`.
2. Identifique branch, `HEAD`, remotes, upstream e relação local/remota.
3. Confirme que a branch corresponde à sprint atual e não é `master`, `main` nem outra branch padrão ou protegida.
4. Confirme que a task foi aceita explicitamente pelo usuário e que sua documentação de encerramento, quando aplicável, já foi atualizada.

Se estiver na branch padrão, se a sprint estiver ambígua ou se a branch não corresponder ao registro da sprint, interrompa antes de qualquer mutação e peça orientação. Se for necessário criar a branch da sprint, proponha o nome e o ponto de origem e aguarde autorização explícita.

## Inspecionar e classificar

Antes de editar, adicionar ao stage ou publicar:

1. Inspecione `git status`, alterações staged e unstaged, arquivos não rastreados, diff contra `HEAD` e commits locais ainda não publicados.
2. Classifique cada arquivo ou grupo como:
   - pertinente à task aceita;
   - legítimo, mas não relacionado;
   - gerado ou temporário;
   - possível segredo ou dado sensível;
   - duvidoso ou de autoria/finalidade indeterminável.
3. Compare a implementação e a documentação com os critérios da task e com as instruções aplicáveis.

Interrompa e peça orientação ao encontrar segredo, credencial, mudança não relacionada, autoria duvidosa, conflito, falha material ou incerteza sobre o escopo. Preserve todo trabalho preexistente.

## Documentar e validar

Atualize apenas documentos diretamente afetados e somente com resultados comprovados e aceitos. Não duplique fontes canônicas nem marque uma sprint inteira como concluída sem aceite específico e todas as evidências obrigatórias.

Execute validações proporcionais durante o desenvolvimento e, antes do commit, o gate exigido pelo `AGENTS.md`:

```bash
./gradlew check --no-daemon --stacktrace
```

No PowerShell, use `.\\gradlew.bat check --no-daemon --stacktrace`. Para alteração exclusivamente documental, valide links, caminhos, estados, formatação e consistência; o gate completo pode ser dispensado conforme o `AGENTS.md`, registrando a decisão.

Se uma validação necessária falhar, não faça commit nem push. Informe se a falha foi introduzida pela mudança ou já existia, com evidência, e aguarde orientação. Não desative testes, hooks ou gates.

## Preparar o commit

1. Adicione somente arquivos pertinentes, usando caminhos explícitos. Evite `git add .` e `git add -A`.
2. Revise `git diff --cached` integralmente.
3. Confirme que o stage não contém segredo, configuração local, artefato gerado ou mudança fora do escopo.
4. Se não houver alteração pertinente, encerre sem criar commit vazio.
5. Escreva a mensagem conforme o padrão de commits adotado no guia [Padrões de commits](https://github.com/iuricode/padroes-de-commits), combinando Conventional Commits com o emoji correspondente ao tipo:
   - formato obrigatório da primeira linha: `:emoji: tipo(escopo-opcional): descrição curta`;
   - use um tipo coerente com a alteração, preferencialmente entre `feat`, `fix`, `docs`, `test`, `build`, `perf`, `style`, `refactor`, `chore`, `ci`, `raw`, `cleanup` e `remove`;
   - o escopo deve identificar a task, módulo ou área quando isso tornar a mensagem mais precisa, por exemplo `B0.1-T5`;
   - a descrição deve ser sucinta, específica e fiel ao conteúdo; prefira no máximo quatro palavras na primeira linha quando isso não reduzir a clareza;
   - não use emoji ou tipo incompatíveis com os arquivos alterados;
   - corpo e rodapé são opcionais, devem ficar separados da primeira linha por uma linha em branco e podem registrar motivo, impacto, revisão ou referência da task.
   Exemplos para este repositório: `:books: docs(B0.1-T5): definir retenção`, `:lock: docs(B0.1-T2): proteger sessão Web`, `:bricks: ci: ajustar pipeline`.
6. Crie um commit novo. Não use `--amend` sem solicitação explícita.

Prefira um commit por unidade aceita e coerente. Não fragmente artificialmente arquivos que só fazem sentido juntos e não misture tasks independentes.

## Publicar na branch da sprint

- Faça push somente da branch atual para o remote canônico e para uma branch remota com o mesmo nome.
- Se o upstream correto já existir, use-o sem alterar sua configuração.
- Se a branch remota ainda não existir, apresente o comando `git push -u <remote> <branch>` e peça confirmação antes de criar o upstream.
- Nunca use `--force` ou `--force-with-lease`.
- Nunca faça push para `master`, `main` ou branch diferente da atual.
- Não abra pull request e não execute merge; essas ações pertencem ao encerramento da sprint e ficam com o usuário.

Em rejeição, divergência remota, branch protegida ou necessidade de merge/rebase, pare sem tentar corrigir automaticamente e explique a decisão necessária.

## Preservação e proibições

- Não descarte, restaure ou sobrescreva alterações do usuário.
- Não use `git reset --hard`, `git checkout --`, `git clean` ou equivalentes destrutivos.
- Não faça merge, rebase, cherry-pick, amend, tag ou release sem solicitação explícita separada.
- Não publique código não aceito ou validação pendente.
- Não trate a invocação como autorização para alterar outras tasks ou limpar o working tree.

## Relatório final

Informe:

- sprint e task publicadas;
- arquivos incluídos e arquivos deixados de fora, com motivo;
- documentação atualizada;
- validações e resultados;
- hash e mensagem do commit;
- branch, remote e upstream;
- resultado do push;
- pendências restantes no working tree;
- confirmação explícita de que nenhum PR ou merge foi realizado.

Se o fluxo parar antes da publicação, entregue o mesmo relatório com o ponto de interrupção e a decisão necessária.
