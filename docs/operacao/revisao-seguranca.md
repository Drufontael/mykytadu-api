# MykytaDu API — Revisão Final de Segurança, Dependências e Imagem OCI

> **Status:** Vigente  
> **Versão:** 1.0.0  
> **Data de referência:** 31 de outubro de 2026  
> **Escopo:** Auditoria de vulnerabilidades, superfície de ataque, empacotamento OCI e dependências de produção

Este documento consolida a revisão final de segurança para o Release Candidate da API MykytaDu, em cumprimento à tarefa **B4.2-T3**. A revisão assegura que a imagem de produção e suas dependências cumpram os requisitos de segurança, minimização de privilégios e ausência de vulnerabilidades críticas.

---

## 1. Inventário de Dependências e Baseline Tecnológico

A matriz tecnológica respeita o baseline fixado no [ADR-003](../adr/ADR-003-usar-kotlin-jvm-e-spring-boot.md) e [ADR-009](../adr/ADR-009-fixar-matriz-tecnologica-inicial.md):

| Componente | Versão Fixada | Função / Responsabilidade | Origem de Resolução |
|---|---|---|---|
| **Java Runtime** | 25 LTS | Ambiente de execução JVM | Toolchain Gradle / Paketo CNB |
| **Kotlin** | 2.4.10 | Linguagem principal da API | Kotlin Gradle Plugin |
| **Spring Boot** | 4.1.1 | Framework web, segurança, dados e actuator | Spring Boot BOM |
| **Spring Modulith** | 2.1.1 | Governança arquitetural e isolamento modular | Spring Modulith BOM |
| **PostgreSQL Driver** | 42.7.13 | Conectividade JDBC com PostgreSQL 18 | BOM Spring Boot |
| **Flyway** | 12.4.0 | Mecanismo único de migração de banco | Spring Boot Starter Flyway |
| **Bouncy Castle** | 1.86 (`bcprov-jdk18on`) | Criptografia para chaves assimétricas e HMAC | Pinned explícito |
| **Redocly CLI** | 2.45.0 | Linting rigoroso da especificação OpenAPI 3.1 | Pinagem no `build.gradle.kts` |
| **Tomcat Embed** | 11.0.24 | Servidor servlet embutido | BOM Spring Boot |
| **Jackson Tools** | 3.1.5 | Serialização e desserialização JSON | Spring Boot BOM |

### 1.1 Política de Dependências
- **Zero Versões Dinâmicas:** Nenhuma dependência utiliza ranges, snapshots ou versões dinâmicas (`+`, `latest`).
- **Resolução via BOM:** As dependências do Spring ecosystem e Jackson são resolvidas exclusivamente pelo BOM do Spring Boot 4.1.1 e Spring Modulith 2.1.1, evitando conflitos transitivos de classpath.
- **Isolamento de SDKs Externos:** Nenhum SDK proprietário de terceiros é importado no módulo `domain` (ADR-001 e ADR-002).

---

## 2. Empacotamento OCI e Construção com Buildpacks

A geração da imagem candidata utiliza Cloud Native Buildpacks (CNB) gerenciados pelo plugin Spring Boot ([ADR-016](../adr/ADR-016-construir-imagem-oci-com-buildpacks.md)):

```kotlin
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("mykytadu-api:${project.version}")
    builder.set("paketobuildpacks/builder-noble-java-tiny@sha256:7688d7b91bd173fbad6124797e5e5c73c848ac3143f9c0f5046ec9e5f4127673")
    environment.set(mapOf("BP_JVM_VERSION" to "25"))
    dependsOn("check")
}
```

### 2.1 Atributos de Segurança da Imagem
1. **Builder Fixado por Digest SHA-256:** Impede injeção silenciosa de camadas ou mutabilidade de tags `latest`.
2. **Base Distroless/Tiny (Ubuntu 24.04 Noble Tiny):** Contém unicamente as bibliotecas mínimas de runtime necessárias para a JVM, eliminando shells desnecessários, gerenciadores de pacotes (`apt`) e ferramentas de compilação na imagem final.
3. **Execução sem Privilégios (Non-Root):** A aplicação executa sob o usuário sem privilégios padrão dos buildpacks (`cnb`, UID/GID 1000).
4. **Dependência Obrigatória dos Gates:** A tarefa `bootBuildImage` possui `dependsOn("check")`, assegurando que nenhuma imagem seja gerada caso qualquer teste, linter ou verificação arquitetural falhe.

---

## 3. Auditoria de Segredos e Superfície Exposta

### 3.1 Gestão de Segredos e Variáveis de Ambiente
- **Ausência de Hardcoded Secrets:** Os arquivos `application.yaml` e `application-staging.yaml` utilizam exclusivamente interpolação de variáveis de ambiente (`${MYKYTADU_*}`).
- **Chaves Criptográficas:**
  - Chave privada RSA (JWT): Injetada via `MYKYTADU_JWT_PRIVATE_KEY_PEM` pelo secret manager do ambiente.
  - Chave secreta HMAC (Refresh): Injetada via `MYKYTADU_REFRESH_ACTIVE_KEY_BASE64` (mínimo 256 bits).
- **Datasource:** `MYKYTADU_DATABASE_PASSWORD` é injetado diretamente pelo provedor de hospedagem (Render). A URL de conexão JDBC não embute credenciais de acesso.

### 3.2 Superfície HTTP e Política Deny-by-Default
1. **Endpoints Públicos Aprovados:**
   - Probes de saúde: `GET /actuator/health/liveness`, `GET /actuator/health/readiness`
   - Obtenção de CSRF token (Web): `GET /api/v1/auth/csrf`
   - Fluxos de autenticação pública: `POST /api/v1/auth/login`, `/register`, `/verify-email`, `/verify-email/resend`, `/refresh`, `/password/forgot`, `/password/reset`
2. **Endpoints Restritos:**
   - Todos os demais caminhos (incluindo `/api/v1/translations`, `/api/v1/auth/me`, `/actuator/metrics`) exigem credencial válida (`Bearer <jwt>`) ou rejeitam com HTTP 401 Unauthorized (`code: authentication_required`).
3. **Proteção contra Information Disclosure:**
   - `management.endpoint.health.show-details: never` configurado em produção.
   - Respostas de erro formatadas estritamente sob RFC 9457 Problem Details (`application/problem+json`), sem rastros de stack trace, classes Java ou detalhes internos de banco de dados.

---

## 4. Gates de Qualidade e Conformidade Estática

O pipeline de verificação contínua (`check`) executa compilação, testes unitários, testes de integração de fatias, testes de conformidade contratual e análise estática:

- **Redocly CLI:** Validação sintática e semântica do contrato OpenAPI 3.1 (`docs/api/openapi.yaml`).
- **Ktlint (1.8.0):** Fiscalização de estilo e boas práticas de código Kotlin.
- **Detekt (2.0.0-alpha.6):** Análise estática de complexidade, code smells e conformidade de arquitetura.
- **Spring Modulith Test:** Garantia de que nenhuma violação de fronteira modular (`domain` isolado, sem acesso cruzado a tabelas de outro módulo) exista no projeto.
