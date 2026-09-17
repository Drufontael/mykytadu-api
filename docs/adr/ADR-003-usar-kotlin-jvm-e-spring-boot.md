# ADR-003 — Usar Kotlin/JVM e Spring Boot

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação tecnológica
> **Decisões relacionadas:** D-003

## Contexto

O backend precisa de uma base JVM madura para HTTP, segurança, persistência, validação e testes, mantendo alinhamento conceitual com o cliente Kotlin Multiplatform sem compartilhar modelos compilados entre os projetos.

## Drivers da decisão

- produtividade e familiaridade com Kotlin;
- ecossistema estável para aplicações HTTP bloqueantes e JPA;
- integração com segurança, observabilidade e testes;
- compatibilidade com o modelo arquitetural de monólito modular.

## Opções consideradas

### Kotlin/JVM com Spring Boot

Selecionada. Atende o backend modular e oferece integração madura com MVC, Security, JPA, Actuator e testes.

### Outra stack JVM ou serviço não JVM

Não selecionada para a fundação. Exigiria reavaliar produtividade, bibliotecas, operação e integração sem benefício concreto identificado.

## Decisão

Usar Kotlin/JVM com Spring Boot. A aplicação usará Spring MVC, Spring Security, Spring Data JPA/Hibernate, Flyway, Spring Modulith e Actuator conforme a necessidade de cada módulo.

As versões exatas do baseline tecnológico estão detalhadas e validadas em [ADR-009](ADR-009-fixar-matriz-tecnologica-inicial.md).

## Consequências

### Positivas

- base única para web, segurança, persistência e operação;
- bom suporte a testes de integração e arquitetura;
- alinhamento da linguagem do backend com o ecossistema do produto.

### Negativas

- dependência do ciclo de releases do ecossistema Spring;
- aplicações JVM e cliente KMP continuam com modelos e ciclos de build distintos;
- acesso bloqueante permanece a escolha inicial e deve ser reavaliado apenas com evidência de carga.

## Plano de adoção

1. manter o Gradle Wrapper versionado;
2. gerenciar versões Spring pelo BOM quando aplicável;
3. usar Spring MVC e JPA no baseline;
4. validar compatibilidade por compilação, testes e gates de qualidade.

## Critérios de revisão

Reavaliar diante de limitação concreta de carga, segurança, compatibilidade ou manutenção que não possa ser resolvida na stack adotada.
