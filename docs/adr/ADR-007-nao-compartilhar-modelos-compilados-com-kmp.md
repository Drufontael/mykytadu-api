# ADR-007 — Não compartilhar modelos compilados entre backend JVM e cliente KMP

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação de integração com o cliente
> **Decisões relacionadas:** D-007

## Contexto

O backend usa Kotlin/JVM e Spring, enquanto o cliente usa Kotlin Multiplatform com Android, Desktop/JVM, iOS e Web. Compartilhar classes compiladas faria o contrato depender de detalhes de linguagem, framework e persistência.

## Drivers da decisão

- preservar independência entre ciclos de release;
- evitar vazamento de entidades JPA e DTOs internos;
- manter o contrato utilizável em todas as plataformas;
- permitir evolução interna sem quebrar o cliente.

## Opções consideradas

### Compartilhar um módulo de modelos Kotlin

Não selecionada. Aumenta acoplamento, mistura responsabilidades e dificulta o suporte multiplataforma.

### Compartilhar somente o contrato OpenAPI

Selecionada. O contrato é independente de linguagem; cada lado possui seus modelos e mapeamentos explícitos.

### Compartilhar apenas tipos técnicos mínimos

Não é a regra para DTOs. Tipos técnicos estritamente estáveis podem ser avaliados separadamente, sem transformar o compartilhamento em modelo de domínio comum.

## Decisão

Não compartilhar modelos compilados entre `mykytadu-api` e `mykytadu-app`. DTOs HTTP, modelos de domínio, entidades JPA e DTOs de fornecedores permanecem em suas fronteiras. O contrato OpenAPI e mapeamentos explícitos conectam backend e cliente.

## Consequências

### Positivas

- fronteiras tecnológicas preservadas;
- entidades e detalhes internos não vazam;
- clientes podem evoluir por plataforma;
- contrato HTTP torna a compatibilidade verificável.

### Negativas

- existem mapeamentos e modelos duplicados;
- mudanças de contrato exigem coordenação entre repositórios;
- geração de clientes, se adotada, ainda precisa de revisão de compatibilidade.

## Plano de adoção

1. manter OpenAPI como artefato compartilhado;
2. criar DTOs HTTP locais em cada projeto;
3. mapear explicitamente entre contrato, domínio e persistência;
4. validar compatibilidade por testes de contrato.

## Critérios de revisão

Reavaliar somente se um compartilhamento limitado demonstrar redução real de risco sem acoplar framework, persistência ou ciclo de release.
