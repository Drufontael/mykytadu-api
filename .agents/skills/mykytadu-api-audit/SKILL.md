---
name: mykytadu-api-audit
description: Audita o estado, a qualidade e a coerência do mykytadu-api com base na documentação canônica e, quando solicitado, em código, build e testes. Use para avaliar progresso, aderência à sprint ou roadmap, riscos, lacunas, inconsistências e prontidão para validação ou encerramento.
---

# MykytaDu API Audit

Produza uma avaliação rastreável sem transformar intenção em implementação. Esta skill é somente leitura: não corrija código ou documentação, não altere o Git e não declare aceite em nome do usuário.

## Escolher o modo

Use **auditoria documental** por padrão. Use **auditoria técnica** quando o usuário pedir inspeção ou validação contra código, build, testes, migrations, contrato ou estado do repositório.

- **Documental:** avalie as fontes canônicas e marque o que depende de comprovação técnica.
- **Técnica:** acrescente inspeções somente leitura e comandos locais seguros; não implemente correções.

Se o modo estiver ambíguo, execute a auditoria documental e informe o limite.

## Ler as fontes

Respeite o `AGENTS.md` da raiz e leia integralmente, quando aplicáveis:

1. `docs/documento-mestre-backend.md`;
2. `docs/roadmap.md`;
3. `docs/modelagem.md`;
4. registro da sprint avaliada em `docs/sprints/`;
5. registro da sprint anterior, quando houver dependência ou comparação;
6. ADRs citados;
7. documentação de testes, dependências, qualidade, ambiente e CI;
8. OpenAPI, catálogo de erros, threat model, runbooks e políticas já existentes.

Informe fontes obrigatórias ausentes ou inacessíveis. Não preencha lacunas com memória ou suposição oculta.

## Hierarquia de evidências

Classifique as conclusões nesta ordem:

1. **Comprovado tecnicamente:** código inspecionado ou comando atual com resultado observável.
2. **Comprovado documentalmente:** estado e evidência registrados na sprint ou fonte responsável.
3. **Planejado:** roadmap, critérios futuros ou modelagem proposta.
4. **Inferência:** conclusão derivada das fontes e identificada como tal.
5. **Requer validação técnica:** depende de código, build, testes ou estado ainda não inspecionado.

Roadmap comprova intenção, não implementação. Diagramas ou modelos propostos não comprovam classes, tabelas ou endpoints. Exponha divergências entre fontes, seus impactos e a validação necessária; não escolha silenciosamente uma versão.

## Executar a auditoria

1. Identifique sprint, objetivo, marco, estado declarado e commit ou working tree observado.
2. Separe tarefas e critérios em concluídos, parciais, pendentes, bloqueados e sem evidência.
3. Cruze cada conclusão com critério de aceite e evidência verificável.
4. Calcule progresso somente quando os totais forem inequívocos, deixando claro que itens têm pesos e riscos diferentes.
5. Avalie os atributos pertinentes: determinismo, testabilidade, modularidade, segurança, privacidade, operabilidade, dados e contrato.
6. Compare documento mestre, roadmap, modelagem, sprint, ADRs e implementação em busca de contradições ou estado desatualizado.
7. Identifique riscos concretos, impacto e mitigação proporcional.
8. Determine se objetivo e marco estão atingidos, parcialmente atingidos ou não atingidos.
9. Recomende a menor sequência restante, sem ampliar o escopo.

## Auditoria técnica

Confirme os comandos existentes no repositório antes de executá-los. Priorize o gate documentado:

```bash
./gradlew check --no-daemon --stacktrace
```

No Windows/PowerShell, use o wrapper `.\\gradlew.bat`. Conforme o pedido, inspecione versões resolvidas, módulos Spring Modulith, migrations, Hibernate, profiles, segredos, testes, qualidade, segurança, health, métricas, CI e divergência entre OpenAPI, código e documentação.

Execute apenas comandos não destrutivos. Não inicie, derrube, limpe ou recrie infraestrutura persistente sem autorização explícita. Não atualize dependências e não corrija falhas durante a auditoria.

## Restrições

- Não implementar correções nem editar documentação.
- Não adicionar arquivos ao stage, criar commits, fazer push, abrir PRs ou alterar branches.
- Não marcar task ou sprint como concluída.
- Não tratar quantidade de testes ou cobertura como prova isolada de qualidade.
- Não recomendar microserviços, broker, Redis ou Kubernetes sem requisito e evidência.
- Não repetir fundação já aceita; sprints futuras devem evoluí-la.

## Relatório

Apresente, na profundidade adequada:

1. avaliação geral, modo e limite da auditoria;
2. situação de tarefas e critérios;
3. objetivo, marco e prontidão;
4. pontos fortes comprovados;
5. lacunas e inconsistências;
6. riscos, impactos e mitigações;
7. critérios restantes;
8. próxima sequência mínima recomendada;
9. conclusão e itens que requerem validação técnica.

Use tabelas apenas quando melhorarem comparações exatas. Diferencie fatos, planos e inferências sem ambiguidade.
