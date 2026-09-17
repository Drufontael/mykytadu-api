# ADR-004 — Usar PostgreSQL como persistência inicial

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação de persistência
> **Decisões relacionadas:** D-004

## Contexto

Identity precisa de consistência para contas, credenciais, sessões e tokens de ação. Translation precisa de cache e controle técnico de consumo. O sistema deve começar com operação simples e migrações reproduzíveis.

## Drivers da decisão

- consistência transacional;
- suporte maduro a constraints, índices e consultas relacionais;
- integração com Spring Data JPA e Testcontainers;
- operação local reproduzível e evolução por Flyway.

## Opções consideradas

### PostgreSQL como única instância inicial

Selecionada. Oferece persistência relacional suficiente para os dois domínios, com ownership separado por schema.

### Banco diferente ou múltiplos bancos desde o início

Não selecionada. Acrescentaria operação e sincronização sem evidência de necessidade.

### Armazenamento não relacional para o MVP

Não selecionado. Não atende melhor às invariantes relacionais e às transações necessárias de Identity.

## Decisão

Usar uma única instância PostgreSQL inicial. O schema `identity` pertence a Identity; `translation` pertence a Translation; `public` permanece vazio ou contém apenas extensões controladas.

Flyway é o único mecanismo de evolução. Migrations são incrementais e imutáveis após aplicação, usando UUIDv7 para entidades persistidas e `Instant`/`timestamptz` para tempo.

## Consequências

### Positivas

- operação inicial simples;
- transações e constraints protegem invariantes;
- integração direta com JPA e Testcontainers;
- ownership lógico preservado.

### Negativas

- os módulos compartilham capacidade física do banco;
- evolução exige disciplina de migrations;
- escala independente por módulo não é obtida automaticamente.

## Plano de adoção

1. provisionar PostgreSQL para local, teste, staging e produção;
2. criar schemas por ownership;
3. validar o schema com Hibernate e evoluí-lo somente com Flyway;
4. criar índices a partir de consultas previstas;
5. testar integrações com PostgreSQL real.

## Critérios de revisão

Reavaliar quando escala, disponibilidade, isolamento ou custo justificarem bancos separados.
