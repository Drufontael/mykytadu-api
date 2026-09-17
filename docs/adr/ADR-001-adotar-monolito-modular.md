# ADR-001 — Adotar monólito modular

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação arquitetural
> **Decisões relacionadas:** D-001

## Contexto

O backend precisa reunir API, Identity e Translation sem transformar diferenças de domínio em serviços distribuídos antes de haver necessidade verificável. O produto ainda está em fase inicial e precisa de baixo custo operacional, fronteiras claras e evolução incremental.

## Drivers da decisão

- reduzir complexidade de build, deploy e operação;
- manter fronteiras de negócio verificáveis dentro do código;
- permitir transações e diagnóstico simples no início;
- preservar a possibilidade de extrair um módulo no futuro.

## Opções consideradas

### Serviços independentes desde o início

Não selecionada. Introduziria rede, deploys, observabilidade e consistência distribuída sem requisito concreto.

### Monólito sem fronteiras explícitas

Não selecionada. Simplifica o início, mas permite acoplamento acidental entre Identity, Translation e detalhes de persistência.

### Monólito modular

Selecionada. Mantém um único processo e deploy, com módulos lógicos e regras explícitas de dependência.

## Decisão

Começar com um monólito modular e um único módulo Gradle. Os módulos lógicos são `app`, `api`, `identity`, `translation` e `shared`; Spring Modulith documenta e testa suas fronteiras.

Identity e Translation não devem acessar tabelas ou detalhes internos uns dos outros. A comunicação ocorre por interfaces públicas mínimas ou eventos quando houver desacoplamento real. A extração de um módulo só será considerada mediante escala, disponibilidade, segurança, equipe, deploy ou fornecedor que justifique o custo.

## Consequências

### Positivas

- um único build e deploy;
- menor custo inicial;
- transações e operação mais simples;
- fronteiras internas verificáveis;
- extração futura possível sem começar distribuído.

### Negativas

- módulos compartilham o processo e o ciclo de deploy;
- isolamento de compilação ainda não é garantido pelo Gradle;
- exige disciplina para evitar dependências indevidas.

## Plano de adoção

1. manter o único módulo Gradle;
2. organizar os módulos por capacidade e camadas;
3. validar dependências com Spring Modulith e testes arquiteturais;
4. reavaliar extração somente diante de evidência operacional.

## Critérios de revisão

Reavaliar quando um módulo tiver escala, disponibilidade, segurança, equipe, fornecedor ou ciclo de deploy significativamente diferentes do restante do sistema.
