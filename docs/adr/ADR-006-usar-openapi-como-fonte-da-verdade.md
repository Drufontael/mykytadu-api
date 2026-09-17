# ADR-006 — Usar OpenAPI como fonte da verdade dos contratos

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação do contrato HTTP
> **Decisões relacionadas:** D-006

## Contexto

O backend JVM e os clientes KMP têm ciclos de build e modelos internos diferentes. O contrato HTTP precisa ser independente de linguagem, versionado e verificável antes da implementação completa dos endpoints.

## Drivers da decisão

- contrato explícito e consumível por múltiplas plataformas;
- exemplos e erros estáveis;
- geração de mocks e testes de compatibilidade;
- redução de divergência entre backend e frontend.

## Opções consideradas

### Contrato informal em documentação narrativa

Não selecionada. É insuficiente para validação automática e tende a divergir da implementação.

### Compartilhar classes compiladas entre backend e cliente

Não selecionada. Acopla tecnologias e ciclos de build incompatíveis.

### OpenAPI 3.1 versionado

Selecionada. A especificação será a fonte normativa do contrato HTTP.

## Decisão

Versionar uma especificação OpenAPI 3.1 no repositório, com schemas, exemplos, autenticação, limites, erros `application/problem+json`, códigos estáveis e compatibilidade da base `/api/v1`.

A implementação e os clientes devem ser validados contra essa especificação. Mocks ou fixtures podem ser gerados a partir dela.

## Consequências

### Positivas

- contrato independente de linguagem;
- validação e testes automatizados;
- frontend pode avançar com mocks;
- mudanças incompatíveis ficam visíveis.

### Negativas

- toda mudança HTTP exige atualização coordenada;
- a especificação precisa de manutenção contínua;
- detalhes de implementação não podem substituir o contrato publicado.

## Plano de adoção

1. criar o arquivo OpenAPI inicial;
2. modelar fluxos de Identity e Translation;
3. incluir exemplos e Problem Details;
4. validar implementação e compatibilidade no CI;
5. documentar depreciações sem alterar `/api/v1` silenciosamente.

## Critérios de revisão

Reavaliar apenas se surgir um padrão de contrato que ofereça equivalência verificável sem perder independência de linguagem e validação automática.
