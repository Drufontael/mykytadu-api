# ADR-016 — Construir imagem OCI com Cloud Native Buildpacks

> **Estado:** Aprovado
> **Data:** 2026-09-17
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B1.1 / B1.1-T4
> **Decisões relacionadas:** [ADR-003](ADR-003-usar-kotlin-jvm-e-spring-boot.md), [ADR-009](ADR-009-fixar-matriz-tecnologica-inicial.md), [ADR-015](ADR-015-render-como-hospedagem-alvo-da-api.md)

## Contexto

A API precisa produzir um artefato OCI compatível com a hospedagem-alvo do
Render, usando Java 25, sem colocar credenciais na imagem e sem introduzir uma
segunda estratégia de empacotamento antes de existir necessidade operacional.
O projeto também precisa manter uma construção local reproduzível enquanto a
publicação em registry e o deploy remoto permanecem fora do escopo.

## Drivers da decisão

- compatibilidade com Spring Boot e Java 25;
- imagem enxuta com camadas e SBOM fornecidos pelo processo de build;
- configuração de ambiente e secrets somente no runtime;
- reprodutibilidade por referência imutável do builder;
- baixo custo de adoção e compatibilidade com Docker/Render.

## Opções consideradas

### `bootBuildImage` com Cloud Native Buildpacks

Integra-se ao plugin Gradle do Spring Boot, produz a imagem sem um Dockerfile
manual e permite configurar a versão da JVM e o builder. Exige Docker e deixa a
atualização de buildpacks condicionada à atualização deliberada do builder.

### Dockerfile mantido pelo projeto

Oferece controle direto sobre a imagem base e o comando de execução, mas exige
manutenção adicional de base, JVM, camadas, usuário, SBOM e correções de
segurança. Não há evidência nesta sprint que justifique esse custo.

## Decisão

Usar `bootBuildImage` com Cloud Native Buildpacks e o builder
`paketobuildpacks/builder-noble-java-tiny` fixado pelo digest registrado em
`build.gradle.kts`. Configurar `BP_JVM_VERSION=25` e gerar a imagem local
`mykytadu-api:<project.version>`.

O task depende de `check`. A aplicação escuta na porta 8080 dentro do
container; URL do banco, credenciais e demais valores sensíveis são fornecidos
por variáveis do ambiente de execução. A imagem não é publicada nem implantada
automaticamente nesta decisão.

## Consequências

### Positivas

- mantém o empacotamento alinhado ao Spring Boot;
- evita duplicar lógica de montagem em Dockerfile;
- produz camadas reutilizáveis, processo `web` e metadados OCI/SBOM;
- permite validar localmente o mesmo tipo de artefato esperado pelo Render;
- impede que uma mudança silenciosa de `latest` altere o builder usado.

### Negativas

- a construção depende do Docker e de downloads iniciais;
- a atualização do builder exige revisão, novo digest e nova validação;
- o projeto delega parte da composição da imagem aos buildpacks.

### Riscos e mitigações

- **Builder desatualizado:** revisar o digest quando houver atualização de
  Java, Spring Boot ou correções relevantes e registrar nova evidência;
- **segredo embutido:** manter configuração obrigatória no runtime e inspecionar
  a imagem antes de publicar;
- **diferença entre local e remoto:** executar os probes com configuração
  externa antes de habilitar o pipeline de imagem e o deploy.

## Evidências

- `bootBuildImage --console=plain` executou `check` e produziu
  `mykytadu-api:0.0.1-SNAPSHOT`;
- o build registrou BellSoft Liberica JRE 25.0.4;
- a imagem iniciou conectada ao PostgreSQL 18.6 com variáveis externas;
- liveness e readiness responderam HTTP 200 com `{"status":"UP"}`;
- a inspeção da imagem não encontrou variáveis de banco ou credenciais na
  configuração OCI.

## Plano de adoção

1. construir localmente com `bootBuildImage`;
2. validar os probes com configuração externa;
3. adicionar um job identificável de imagem no CI em B1.1-T5;
4. definir publicação e deploy somente após registry, orçamento e secrets do
   Render estarem aprovados.

## Critérios de revisão

Reavaliar esta decisão se o Render exigir um formato diferente, se o builder
deixar de suportar Java 25, se os tempos/tamanhos de build forem inadequados
ou se a equipe precisar de controle de imagem que os buildpacks não ofereçam.
