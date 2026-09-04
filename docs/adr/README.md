# Architecture Decision Records — ADRs

> **Status:** convenção ativa
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Finalidade

ADRs registram decisões arquiteturais relevantes, seu contexto e suas consequências. Eles preservam o motivo de uma escolha mesmo quando pessoas, ferramentas ou restrições mudarem.

O [documento mestre](../documento-mestre-backend.md) contém o registro inicial D-001 a D-008. Esses itens devem ganhar ADRs próprios quando forem revisitados ou antes da implementação afetada exigir detalhamento adicional.

## 2. Quando criar um ADR

Criar ou atualizar um ADR quando a decisão afetar:

- stack, versões centrais ou dependências estruturais;
- fronteiras e comunicação entre módulos;
- contrato HTTP ou compatibilidade com clientes;
- persistência, ownership, retenção ou migrações;
- autenticação, autorização, criptografia ou gestão de segredos;
- integração com provedores externos;
- topologia de implantação, disponibilidade ou observabilidade;
- adoção ou rejeição fundamentada de infraestrutura relevante.

Não criar ADR para detalhes locais facilmente reversíveis sem impacto arquitetural.

## 3. Identificação e nomes

- formato: `ADR-NNN-titulo-curto.md`;
- numeração sequencial e nunca reutilizada;
- título descreve a decisão, não apenas o assunto;
- IDs D-001 a D-008 do documento mestre podem ser preservados como referência no campo `Decisões relacionadas`;
- datas usam `AAAA-MM-DD`.

Exemplo:

```text
ADR-001-adotar-monolito-modular.md
ADR-009-fixar-versao-kotlin.md
```

## 4. Estados

| Estado | Uso |
| --- | --- |
| Proposto | em discussão e ainda não autoriza implementação dependente |
| Aprovado | decisão vigente |
| Rejeitado | proposta avaliada e não adotada |
| Substituído | outro ADR passou a reger a decisão |
| Obsoleto | contexto deixou de existir sem substituição direta |

ADRs aprovados não são reescritos para esconder o histórico. Mudança de decisão cria novo ADR e referencia o anterior como substituído.

## 5. Processo

1. registrar o contexto e as restrições observáveis;
2. descrever opções realmente consideradas;
3. propor uma decisão e suas consequências;
4. revisar impactos em contrato, segurança, dados, operação e custo;
5. aprovar ou rejeitar explicitamente;
6. vincular sprint e tarefas afetadas;
7. atualizar `modelagem.md`, roadmap e contrato quando necessário.

## 6. Modelo

```markdown
# ADR-NNN — Título da decisão

> **Estado:** Proposto
> **Data:** AAAA-MM-DD
> **Responsáveis:** —
> **Sprint/tarefas:** —
> **Decisões relacionadas:** —

## Contexto

Qual problema exige uma decisão? Quais fatos e restrições são conhecidos?

## Drivers da decisão

- critério que influencia a escolha;

## Opções consideradas

### Opção A

Vantagens, limitações e riscos.

### Opção B

Vantagens, limitações e riscos.

## Decisão

Opção escolhida e condições de aplicação.

## Consequências

### Positivas

### Negativas

### Riscos e mitigações

## Evidências

Testes, medições, referências ou experimentos que sustentam a decisão.

## Plano de adoção

Passos, compatibilidade, migração e rollback quando aplicável.

## Critérios de revisão

Condições objetivas que justificam reavaliar a decisão.
```

## 7. Relação com as sprints

O registro da sprint aponta para o ADR e informa seu estado. Uma tarefa bloqueada por decisão arquitetural permanece bloqueada enquanto o ADR necessário estiver `Proposto`, salvo quando a própria tarefa for um experimento destinado a produzir evidência para a decisão.
