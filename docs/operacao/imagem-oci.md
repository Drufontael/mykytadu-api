# Imagem OCI

> **Status:** vigente
> **Versão:** 0.2
> **Data de referência:** 23 de setembro de 2026

## Objetivo

A imagem OCI do `mykytadu-api` é construída pelo `bootBuildImage`, usando Cloud
Native Buildpacks do Spring Boot e o builder Paketo com digest fixado. O
processo não publica a imagem em registry e não substitui a configuração de
deploy definida para o Render; nesta fase ele prepara e valida o artefato
local.

## Unidade de implantação

A imagem contém a aplicação completa e os módulos lógicos `app`, `api`,
`identity`, `translation` e `shared`. Spring Modulith verifica as fronteiras
internas, mas não transforma esses módulos em processos, imagens ou unidades de
escala independentes. O baseline aprovado no
[ADR-001](../adr/ADR-001-adotar-monolito-modular.md) e no
[ADR-002](../adr/ADR-002-separar-identity-e-translation.md) permanece um único
build, uma única imagem e um único deploy.

Quando houver mais de uma instância, cada réplica executará a aplicação inteira.
Os processos permanecem stateless, enquanto sessões duráveis e demais dados
ficam no PostgreSQL. Antes de escalar horizontalmente, a decisão P-010 sobre
rate limit multi-instância precisa ser resolvida; Redis ou outra tecnologia não
é pressuposta.

Profiles selecionam composição técnica e configuração de ambiente. Eles não
ativam ou desativam módulos de negócio por container. Uma possível extração de
Translation exige evidência de escala, disponibilidade, custo, equipe ou ciclo
de deploy, além de decisão arquitetural anterior à implementação, conforme a
[modelagem evolutiva](../modelagem.md#15-evolução-arquitetural-esperada).

## Construção local

Pré-requisitos: Java 25, Docker em execução e o Node.js/npx necessários ao gate
de contrato. Na raiz do repositório, executar:

```powershell
.\gradlew.bat bootBuildImage --console=plain
```

O task depende de `check`, portanto a imagem só é gerada depois dos gates
obrigatórios. O resultado padrão é:

```text
mykytadu-api:0.0.1-SNAPSHOT
```

O build fixa `BP_JVM_VERSION=25`. O builder é referenciado por digest no
`build.gradle.kts`, evitando que uma mudança silenciosa de `latest` altere a
base de uma construção futura. A atualização desse digest deve ser deliberada,
validada localmente e registrada na documentação da sprint.

## Configuração no runtime

A imagem não recebe credenciais nem configurações específicas de ambiente. Em
ambientes sem um profile local, a aplicação exige estas variáveis:

```text
MYKYTADU_DATABASE_URL
MYKYTADU_DATABASE_USERNAME
MYKYTADU_DATABASE_PASSWORD
```

Exemplo sanitizado de execução:

```powershell
docker run --rm --name mykytadu-api-t4 `
  -p 18081:8080 `
  -e MYKYTADU_DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/mykytadu" `
  -e MYKYTADU_DATABASE_USERNAME="usuario-fornecido-pelo-ambiente" `
  -e MYKYTADU_DATABASE_PASSWORD="segredo-fornecido-pelo-ambiente" `
  mykytadu-api:0.0.1-SNAPSHOT
```

Os valores acima são placeholders. Segredos reais devem ser injetados pelo
ambiente de execução ou secret manager e nunca devem aparecer em Dockerfile,
imagem, repositório ou log.

## Validação

Com o container em execução, os probes públicos podem ser verificados pela
porta publicada:

```powershell
Invoke-RestMethod http://127.0.0.1:18081/actuator/health/liveness
Invoke-RestMethod http://127.0.0.1:18081/actuator/health/readiness
```

O liveness confirma o processo. O readiness também depende do PostgreSQL e
deve ser usado para decidir se a instância pode receber tráfego. A imagem deve
ser removida somente após interromper o container; o build não altera o volume
local do PostgreSQL.

## Limites desta etapa

- não há publicação em registry;
- não há deploy automático;
- o job dedicado à imagem foi adicionado em B1.1-T5, mas não publica em registry;
- os módulos não são publicados nem escalados separadamente;
- a definição operacional do Render permanece no [ADR-015](../adr/ADR-015-render-como-hospedagem-alvo-da-api.md).
