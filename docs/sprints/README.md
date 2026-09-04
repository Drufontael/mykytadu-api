# Registro de evolução das sprints

> **Status:** convenção ativa
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Finalidade

Esta pasta mantém o histórico de execução das sprints do MykytaDu API. O [roadmap](../roadmap.md) preserva o plano e o estado consolidado; cada arquivo de sprint registra execução, evidências, bloqueios, decisões e encerramento.

O registro deve permitir responder:

- o que foi planejado e efetivamente entregue;
- quais critérios foram verificados e por qual evidência;
- o que mudou durante a sprint e por quê;
- quais decisões arquiteturais surgiram;
- quais riscos ou débitos permanecem;
- se o marco associado pode ser considerado atingido.

## 2. Nome e criação dos arquivos

- usar o identificador do roadmap: `B-1.md`, `B0.1.md`, `B1.1.md` etc.;
- criar o arquivo quando a sprint passar para `Planejada`;
- não reutilizar um arquivo para outra sprint;
- manter links relativos para ADRs, documentos e evidências;
- registrar datas no formato ISO `AAAA-MM-DD`.

## 3. Estados permitidos

| Estado | Significado |
| --- | --- |
| Não iniciada | existe apenas no roadmap e ainda não teve escopo comprometido |
| Planejada | objetivo, escopo e critérios foram preparados, mas a execução não começou |
| Em andamento | existe pelo menos uma tarefa em execução |
| Em validação | implementação terminou e os critérios de aceite estão sendo verificados |
| Concluída | todos os critérios obrigatórios possuem evidência e o encerramento foi registrado |
| Bloqueada | um impedimento externo ou decisão ausente impede progresso relevante |

Tarefas usam `Não iniciada`, `Em andamento`, `Em validação`, `Concluída`, `Bloqueada` ou `Removida do escopo`. Uma tarefa implementada, mas ainda não validada, permanece `Em validação`.

## 4. Fonte da verdade por informação

| Informação | Documento responsável |
| --- | --- |
| visão, sequência e escopo planejado | `docs/roadmap.md` |
| execução, estado detalhado e evidências | arquivo da sprint |
| decisão arquitetural e consequências | `docs/adr/ADR-NNN-*.md` |
| arquitetura e modelo vigentes | `docs/modelagem.md` |
| contrato HTTP vigente | futura especificação `docs/api/openapi.yaml` |

Se houver divergência, ela deve ser corrigida; não se mantém duplicação conflitante.

## 5. Atualização durante a sprint

Atualizar o arquivo quando:

- uma tarefa mudar de estado;
- surgir, mudar ou desaparecer um bloqueio;
- o escopo for adicionado, removido ou dividido;
- uma decisão relevante for tomada;
- uma evidência ficar disponível;
- um risco alterar probabilidade, impacto ou mitigação.

Não é necessário registrar cada ação cotidiana. O histórico deve capturar mudanças que expliquem o resultado da sprint.

## 6. Evidências

Evidências devem ser verificáveis, pequenas e livres de segredos. Podem ser:

- caminho de arquivo ou teste automatizado;
- comando executado, data e resultado resumido;
- link para uma execução do CI;
- migration aplicada e teste correspondente;
- relatório de qualidade ou dependências;
- ADR aprovado;
- log ou captura sanitizada quando não houver alternativa melhor.

Não copiar grandes logs para o registro. Nunca armazenar tokens, senhas, chaves, headers de autorização ou dados pessoais desnecessários.

Formato recomendado:

| Data | Tarefa/critério | Evidência | Resultado |
| --- | --- | --- | --- |
| AAAA-MM-DD | `B-X-TNN` | arquivo, teste, comando ou link | aprovado/reprovado e observação curta |

Artefatos auxiliares que precisem ser versionados podem ficar em `docs/evidencias/<sprint>/`, desde que sejam pequenos, sanitizados e não sejam saídas de build.

## 7. Mudanças de escopo

Toda mudança após o início deve registrar:

| Campo | Conteúdo |
| --- | --- |
| Data | quando a mudança foi acordada |
| Mudança | o que entrou, saiu ou foi dividido |
| Motivo | evidência ou contexto que motivou a alteração |
| Impacto | prazo, marco, risco, contrato ou dependência afetada |
| Decisão | ação aprovada e, se aplicável, link para ADR |

Itens removidos não desaparecem do histórico: ficam como `Removida do escopo` e apontam para o destino ou motivo.

## 8. Decisões e ADRs

O arquivo da sprint registra um resumo e aponta para o ADR. A decisão completa não deve ficar escondida no diário da sprint.

```text
descoberta → proposta → ADR → decisão → implementação → evidência → atualização da modelagem
```
Mudanças em contrato, dados, segurança, limites de módulo, dependências estruturais ou implantação normalmente exigem ADR.

## 9. Encerramento

Para encerrar uma sprint:

1. revisar todas as tarefas e critérios de aceite;
2. registrar evidências de sucesso ou falha;
3. separar entregue, não entregue e removido;
4. registrar débitos com responsável ou condição de retomada;
5. atualizar ADRs e `modelagem.md` quando necessário;
6. atualizar estado, datas e link no roadmap;
7. declarar explicitamente se o marco foi atingido.

Uma sprint não deve ser marcada como concluída por término de prazo. Caso o tempo termine sem os critérios obrigatórios, o encerramento registra o resultado real e o roadmap é replanejado.

## 10. Modelo para novas sprints

```markdown
# Sprint Bx.y — Nome

> **Estado:** Planejada
> **Início:** —
> **Conclusão:** —
> **Responsável:** —
> **Marco:** Mx

## Objetivo

## Escopo comprometido

| ID | Tarefa | Estado | Evidência | Observações |
| --- | --- | --- | --- | --- |

## Critérios de aceite

- [ ] Critério verificável

## Decisões

## Bloqueios e riscos

## Mudanças de escopo

## Evidências de validação

## Encerramento

### Entregue

### Não entregue

### Débitos técnicos

### Aprendizados

### Próximos passos
```
