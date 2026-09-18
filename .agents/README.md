# Skills do repositório

Este diretório contém instruções específicas para o agente trabalhar no
`mykytadu-api`. O [`AGENTS.md`](../AGENTS.md) é a política principal do
repositório; as skills apenas operacionalizam fluxos específicos.

## Roteamento

| Necessidade | Skill | Efeito |
| --- | --- | --- |
| Auditar estado, coerência, progresso, riscos ou prontidão | [`mykytadu-api-audit`](skills/mykytadu-api-audit/SKILL.md) | Somente leitura |
| Commitar e publicar uma unidade aceita | [`mykytadu-api-commit-publish`](skills/mykytadu-api-commit-publish/SKILL.md) | Pode alterar Git e fazer push, mediante autorização explícita |
| Construir decisões semânticas tipadas com TypeSafe | [`typesafe-ai`](skills/typesafe-ai/SKILL.md) | Orienta integrações com julgamentos `Choice`, `Score` e `Noul` |

Implementação comum não autoriza commit ou push e não deve usar a skill de
publicação automaticamente.

## Regras de uso

- Leia o `SKILL.md` completo antes de executar a skill.
- Preserve as restrições do `AGENTS.md` mesmo quando a skill for mais específica.
- Se o pedido não indicar claramente o efeito esperado, prefira a opção somente leitura.
- Não considere a existência de `.codex/config.toml` necessária: ela é uma configuração local e ignorada pelo Git.

## Validação

Na raiz do repositório, valide a estrutura das skills com:

```powershell
.agents\scripts\validate-skills.ps1
```

O validador verifica nomes, frontmatter, placeholders e links relativos para
arquivos existentes dentro das skills e no índice. O workflow
`Agent configuration` executa a mesma verificação quando essa estrutura muda.
