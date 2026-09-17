# Architecture Decision Records — ADRs

> **Status:** convenção ativa
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Finalidade

ADRs registram decisões arquiteturais relevantes, seu contexto e suas consequências. Eles preservam o motivo de uma escolha mesmo quando pessoas, ferramentas ou restrições mudarem.

O [documento mestre](../documento-mestre-backend.md) mantém apenas o índice resumido. As decisões D-001 a D-008 foram extraídas para os ADRs individuais abaixo; o documento mestre continua sendo referência de contexto e escopo, enquanto cada ADR é a fonte canônica da decisão correspondente.

| Decisão | ADR |
| --- | --- |
| D-001 — monólito modular | [ADR-001](ADR-001-adotar-monolito-modular.md) |
| D-002 — módulos e schemas de Identity e Translation | [ADR-002](ADR-002-separar-identity-e-translation.md) |
| D-003 — Kotlin/JVM e Spring Boot | [ADR-003](ADR-003-usar-kotlin-jvm-e-spring-boot.md), refinado por [ADR-009](ADR-009-fixar-matriz-tecnologica-inicial.md) |
| D-004 — PostgreSQL inicial | [ADR-004](ADR-004-usar-postgresql-como-persistencia-inicial.md) |
| D-005 — JWT curto e refresh rotativo | [ADR-005](ADR-005-usar-jwt-curto-e-refresh-rotativo.md), detalhado para Web por [ADR-011](ADR-011-sessao-web-com-refresh-token-em-cookie.md) |
| D-006 — OpenAPI como fonte da verdade | [ADR-006](ADR-006-usar-openapi-como-fonte-da-verdade.md) |
| D-007 — não compartilhar modelos compilados com KMP | [ADR-007](ADR-007-nao-compartilhar-modelos-compilados-com-kmp.md) |
| D-008 — nome `mykytadu-api` | [ADR-008](ADR-008-nomear-projeto-e-repositorio-mykytadu-api.md) |

ADRs complementares:

- [ADR-009 — Fixar a matriz tecnológica inicial](ADR-009-fixar-matriz-tecnologica-inicial.md), refinamento de D-003;
- [ADR-010 — Cadastro com verificação de e-mail](ADR-010-cadastro-com-verificacao-de-email.md), decisão da B0.1-T1;
- [ADR-011 — Sessão Web com refresh token em cookie protegido](ADR-011-sessao-web-com-refresh-token-em-cookie.md), detalhamento de D-005 e decisão da B0.1-T2;
- [ADR-012 — LibreTranslate inicialmente com adapter substituível](ADR-012-libretranslate-com-adapter-substituivel.md), decisão da B0.1-T3;
- [ADR-013 — Cliente envia descrição para tradução](ADR-013-cliente-envia-descricao-para-traducao.md), decisão da B0.1-T4;
- [ADR-014 — Política de retenção e exclusão de dados](ADR-014-politica-de-retencao-e-exclusao.md), decisão da B0.1-T5;
- [ADR-015 — Render como hospedagem-alvo da API](ADR-015-render-como-hospedagem-alvo-da-api.md), decisão da B0.1-T6.

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
