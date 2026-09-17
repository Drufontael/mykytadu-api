# ADR-002 — Separar Identity e Translation por módulos e schemas

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação arquitetural
> **Decisões relacionadas:** D-002

## Contexto

Identity e Translation têm responsabilidades, dados e riscos diferentes. A separação precisa existir desde o monólito modular para impedir que autenticação, conteúdo traduzido e consumo do fornecedor se misturem.

## Drivers da decisão

- ownership claro dos dados;
- menor acoplamento entre domínios;
- possibilidade de evolução ou extração futura;
- proteção de credenciais e conteúdo por fronteiras técnicas.

## Opções consideradas

### Tabelas compartilhadas em `public`

Não selecionada. Dilui ownership e facilita dependências acidentais.

### Módulos separados com schemas próprios

Selecionada. Identity possui `identity`; Translation possui `translation`; ambos usam a mesma instância PostgreSQL inicial.

### Bancos separados desde o início

Não selecionada. Aumentaria custo operacional e complexidade antes de existir requisito de isolamento físico.

## Decisão

Manter `identity` e `translation` como módulos lógicos independentes, com ownership separado nos schemas `identity` e `translation`. O módulo `api` trata HTTP e `app` compõe a aplicação; `shared` permanece técnico e mínimo.

Não criar foreign keys entre schemas pertencentes a módulos distintos. Integrações entre módulos usam interfaces públicas mínimas ou eventos, sem acesso direto às tabelas alheias.

## Consequências

### Positivas

- responsabilidades e dados têm donos explícitos;
- limites podem ser testados;
- futura extração encontra fronteiras já conhecidas;
- credenciais não precisam atravessar Translation.

### Negativas

- algumas consultas e processos de reconciliação exigirão interfaces ou eventos;
- uma única instância PostgreSQL ainda compartilha recursos físicos;
- regras de consistência entre módulos não serão protegidas por foreign key cross-schema.

## Plano de adoção

1. manter ownership por schema;
2. organizar cada domínio em `domain`, `application`, `infrastructure` e `web`;
3. proibir dependências de infraestrutura entre módulos;
4. validar fronteiras com testes arquiteturais e Spring Modulith.

## Critérios de revisão

Reavaliar se a necessidade de isolamento físico, escala ou disponibilidade justificar bancos ou serviços independentes.
