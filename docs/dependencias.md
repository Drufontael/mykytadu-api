# MykytaDu API — Política e inventário de dependências

> **Status:** baseline revisado na B-1-T02
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Objetivo

Este documento registra por que cada grupo de dependências existe, quais capacidades foram adiadas e como novas bibliotecas devem entrar no MykytaDu API. Ele complementa o [documento mestre](documento-mestre-backend.md) e não substitui os relatórios produzidos pelo Gradle.

## 2. Regras

- Preferir recursos da plataforma e do Spring Boot antes de adicionar bibliotecas externas.
- Usar o BOM do Spring Boot para seu ecossistema e o BOM oficial do Spring Modulith.
- Não declarar versão individual para dependência gerenciada, salvo incompatibilidade comprovada e registrada.
- Não importar um BOM que sobrescreva silenciosamente versões centrais gerenciadas pelo Boot.
- Adicionar uma dependência somente quando houver consumidor ou tarefa imediata claramente identificada.
- Manter escopo mínimo: `testImplementation`, `runtimeOnly` e `developmentOnly` não devem virar `implementation` por conveniência.
- Reavaliar dependências transitivas antes de adicionar diretamente o mesmo artefato.
- Versões dinâmicas, snapshots e ranges são proibidos no baseline.
- Vulnerabilidades e licenças serão avaliadas por gate próprio na evolução do CI.

## 3. Dependências de produção mantidas

| Dependência/grupo | Escopo | Responsabilidade | Gestão de versão |
| --- | --- | --- | --- |
| `spring-boot-starter-webmvc` | implementação | API HTTP bloqueante, Spring MVC e servidor embarcado | BOM do Spring Boot |
| `spring-boot-starter-security` | implementação | filtros, autenticação e autorização | BOM do Spring Boot |
| `spring-boot-starter-security-oauth2-resource-server` | implementação | validação futura de JWT Bearer | BOM do Spring Boot |
| `spring-boot-starter-data-jpa` | implementação | persistência relacional e Hibernate | BOM do Spring Boot |
| `spring-boot-starter-flyway` | implementação | integração do ciclo de migrations | BOM do Spring Boot |
| `flyway-database-postgresql` | implementação | suporte Flyway específico ao PostgreSQL | BOM do Spring Boot |
| `postgresql` | runtime | driver JDBC do PostgreSQL | BOM do Spring Boot |
| `spring-boot-starter-actuator` | implementação | health, métricas e infraestrutura de observabilidade | BOM do Spring Boot |
| `spring-modulith-starter-core` | implementação | modelo, verificação e runtime fundamentais dos módulos | BOM do Spring Modulith 2.1.1 |
| `springdoc-openapi-starter-webmvc-ui` | implementação | exposição e inspeção local/staging do contrato OpenAPI | versão explícita 3.1.0 |
| `jackson-module-kotlin` | implementação | serialização e desserialização adequada de tipos Kotlin | BOM do Spring Boot/Jackson |
| `kotlin-reflect` | implementação | reflexão requerida pela integração Kotlin/Spring | Kotlin Gradle Plugin |
| `spring-boot-docker-compose` | desenvolvimento | descoberta e lifecycle dos serviços locais do Compose | BOM do Spring Boot |

O `springdoc` permanece porque o contrato OpenAPI é uma entrega imediata da B0.2. Sua exposição por ambiente será restringida nas tarefas de configuração e segurança.

## 4. Dependências de teste mantidas

| Dependência/grupo | Responsabilidade |
| --- | --- |
| starters de teste Actuator, JPA, Flyway, Web MVC e Resource Server | suporte focado às capacidades de produção selecionadas |
| `spring-security-test` | autenticação e autorização nos testes |
| `kotlin-test-junit5` | integração das asserções Kotlin com JUnit 5 no baseline atual |
| `spring-modulith-starter-test` | verificação, documentação e testes isolados de módulos |
| `spring-boot-testcontainers` | cria conexões de serviço do Spring a partir dos containers de teste |
| `testcontainers-junit-jupiter` | controla o ciclo de vida dos containers com JUnit 5 |
| `testcontainers-postgresql` | fornece PostgreSQL real e efêmero para testes de integração |
| `junit-platform-launcher` | execução dos testes na plataforma JUnit |

A escolha final de assertions e mocks pertence à B-1-T08. Até lá, não se adiciona MockK, Kotest ou uma segunda convenção.

## 5. Removidas na B-1-T02

| Dependência | Motivo |
| --- | --- |
| `datasource-micrometer-spring-boot` e seu BOM | adicionavam instrumentação externa antes de existir uma necessidade além das métricas de pool `jdbc.connections` e Hikari já fornecidas pelo Actuator; podem ser reavaliados mediante lacuna observável |
| `spring-modulith-observability-api` | só é necessária em compile scope para customizar as métricas de publicações de eventos, caso inexistente no momento |
| `spring-modulith-starter-jpa` | inclui o starter core e adiciona registro persistente de eventos JPA antes de haver eventos publicados |
| `spring-modulith-actuator` | exposição da estrutura modular ainda não foi aprovada; avaliar na B-1-T12 |
| `spring-modulith-observability-core` | tracing entre módulos será avaliado na B-1-T12, quando existirem módulos e interação observável |
| `spring-modulith-runtime` explícito | não há inicializadores nem verificação modular em startup; o suporte necessário agora fica no starter core e nos testes, e o runtime será reavaliado quando existir consumidor |

## 6. Dependências planejadas, ainda não adicionadas

| Capacidade | Candidatas | Tarefa de decisão/adoção |
| --- | --- | --- |
| assertions e mocks | AssertJ/MockK ou alternativa aprovada | B-1-T08 |
| análise e cobertura | Detekt, ktlint e Kover | B-1-T09 |
| provedor HTTP simulado | WireMock ou MockWebServer | B3.1 |
| observabilidade modular | `spring-modulith-starter-insight` ou artefatos individuais | B-1-T12, conforme necessidade demonstrada |
| persistência de eventos | starter Modulith JDBC/JPA, se houver publicação persistente | sprint do primeiro caso de evento confiável |

## 7. Verificações mínimas

Após alterar dependências:

```powershell
.\gradlew.bat buildEnvironment --console=plain
.\gradlew.bat dependencies --configuration runtimeClasspath --console=plain
.\gradlew.bat dependencies --configuration testRuntimeClasspath --console=plain
.\gradlew.bat compileKotlin compileTestKotlin --console=plain
```

O relatório deve confirmar:

- Kotlin stdlib e reflect em 2.4.10;
- Spring Boot e starters em 4.1.1;
- Spring Modulith em 2.1.1;
- ausência de snapshots, ranges e dependências não resolvidas;
- nenhuma substituição externa da linha Micrometer gerenciada pelo Boot;
- compilação principal e de testes sem warnings.
