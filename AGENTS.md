# AGENTS.md

## Escopo

Estas instruções valem para todo o repositório `mykytadu-api`. O objetivo é manter as próximas alterações do Codex alinhadas à arquitetura, ao contrato e ao processo de entrega já aprovados.

## Antes de alterar

1. Leia `README.md`, `docs/roadmap.md` e o arquivo da sprint ativa em `docs/sprints/`.
2. Consulte `docs/modelagem.md` para limites arquiteturais e `docs/adr/` para decisões vigentes.
3. Quando a alteração afetar HTTP, dados, segurança, módulos ou operação, consulte também o documento específico da área.
4. Confirme o critério de aceite atendido pela mudança. Não amplie informalmente o escopo da sprint.
5. Preserve alterações existentes que não pertençam à tarefa.

Em caso de divergência, não escolha silenciosamente uma fonte: corrija a inconsistência ou registre o bloqueio. As fontes de verdade são:

- `docs/roadmap.md`: sequência e escopo planejado;
- `docs/sprints/<sprint>.md`: execução, estado e evidências;
- `docs/adr/`: decisões e consequências;
- `docs/modelagem.md`: arquitetura e modelo vigentes;
- `docs/api/openapi.yaml`, quando existir: contrato HTTP.

## Governança do agente e skills

- Este arquivo define as regras do repositório; skills complementam o fluxo, mas não podem relaxar suas restrições.
- Em caso de conflito, preserve a regra mais restritiva e registre o bloqueio em vez de escolher silenciosamente uma interpretação.
- Use `mykytadu-api-audit` para auditoria, revisão de progresso, coerência documental, riscos ou prontidão. Essa skill é somente leitura.
- Use `mykytadu-api-commit-publish` somente quando o usuário invocar a skill ou autorizar explicitamente commit e publicação. A autorização não inclui pull request, merge, rebase ou force push.
- Para implementação sem publicação, não use a skill de publicação; altere somente o escopo autorizado e aguarde nova autorização para commit ou push.
- Antes de usar uma skill, leia seu `SKILL.md` completo e os recursos que ele indicar. O índice das skills fica em `.agents/README.md`.
- Uma skill deve declarar claramente seu escopo, efeitos colaterais, pré-condições e resultado esperado.
- Antes de declarar pronta uma alteração em `AGENTS.md` ou `.agents/`, execute `.agents\scripts\validate-skills.ps1`.

## Arquitetura obrigatória

- Preserve o monólito modular e os módulos lógicos `app`, `api`, `identity`, `translation` e `shared`.
- Mantenha um único módulo Gradle até que uma decisão aprovada determine o contrário.
- Organize cada domínio por `domain`, `application`, `infrastructure` e `web`.
- `domain` não depende de Spring, HTTP, JPA nem SDK externo.
- `application` coordena casos de uso, portas e limites transacionais.
- `infrastructure` implementa portas e isola PostgreSQL, e-mail e fornecedores externos.
- `web` trata validação e representação HTTP; regras de negócio ficam fora de controllers.
- Não acesse tabelas ou detalhes internos de outro módulo. Integre por interfaces públicas mínimas ou eventos quando houver desacoplamento real.
- Mantenha `shared` pequeno e técnico. Não mova regras de Identity ou Translation para ele.
- Não introduza microserviços, broker, Redis, Kubernetes, WebFlux ou abstrações especulativas sem requisito, evidência e ADR aprovado.
- Entidades JPA, modelos de domínio, DTOs HTTP e DTOs de fornecedor não atravessam fronteiras automaticamente; faça mapeamentos explícitos quando as responsabilidades divergirem.

## Stack e dependências

O baseline atual é Kotlin 2.4.10, Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1 e Gradle Wrapper 9.5.0. PostgreSQL 18 é a persistência inicial.

- Não altere versões estruturais sem compatibilidade comprovada e ADR.
- Prefira o BOM do Spring Boot e do Spring Modulith; não fixe versão individual sem necessidade.
- Não use versões dinâmicas, ranges, snapshots ou dependências sem responsabilidade clara.
- Use o Gradle Wrapper versionado. Não dependa de Gradle instalado globalmente.
- Profiles podem mudar composição técnica, nunca regras de negócio.

## Contrato HTTP e segurança

- Preserve a base `/api/v1`, JSON em `camelCase`, UUIDs como strings e datas ISO-8601 em UTC.
- OpenAPI 3.1 será a fonte da verdade do contrato. Atualize contrato, exemplos, testes e implementação na mesma mudança.
- Erros usam `application/problem+json` conforme RFC 9457, com `code` estável e `traceId`. Clientes não devem depender de `detail`.
- A segurança é deny-by-default. Toda superfície pública precisa ser explicitamente aprovada e testada.
- Nunca versione nem registre senhas, segredos, chaves, tokens, hashes de tokens, headers de autorização ou textos integrais enviados para tradução.
- Access tokens não são persistidos. Refresh tokens e tokens de ação são armazenados somente como hash.
- JWT usa assinatura assimétrica e valida issuer, audience, expiração e `kid`.
- Retries são limitados a operações idempotentes e falhas transitórias.
- Logs e métricas não recebem dados pessoais, conteúdo traduzido integral ou labels de alta cardinalidade.

## Persistência e migrações

- Use uma única instância PostgreSQL, com ownership separado nos schemas `identity` e `translation`. Não crie tabelas de negócio em `public`.
- Hibernate valida o schema; Flyway é o único mecanismo de evolução.
- Migrations são incrementais e imutáveis depois de aplicadas. Corrija por uma nova migration.
- Nomeie migrations como `VyyyyMMddHHmmss-descricao.sql`, usando o instante local de `America/Sao_Paulo`, precisão de segundos, descrição em `snake_case` e o separador configurado no projeto.
- Antes de criar uma migration, verifique a maior versão existente.
- Use UUIDv7 para entidades persistidas e `Instant`/`timestamptz` para tempo.
- Não crie foreign keys entre schemas pertencentes a módulos distintos.
- Índices devem responder a consultas previstas e constraints devem proteger invariantes importantes.

## Implementação e testes

- Prefira a menor alteração completa que satisfaça o critério de aceite.
- Evite código genérico prematuro, classes vazias e funcionalidade de negócio fictícia.
- Use JUnit 5 e AssertJ como padrão.
- Use MockK somente em fronteiras que precisem de doubles; evite `relaxed = true`.
- Não faça mock de PostgreSQL, entidades, value objects ou coleções.
- Use Testcontainers para integração real com PostgreSQL e testes do Spring Modulith para fronteiras.
- Cubra caminhos felizes, validação, autorização, falhas, concorrência e tempo na proporção do risco.
- Use relógio injetável; não introduza sleeps frágeis.
- Não persiga cobertura global cega. Priorize domínio, segurança, sessões, adapters e cache.

## Gates obrigatórios

Antes de declarar a alteração pronta, execute na raiz:

```bash
./gradlew check --no-daemon --stacktrace
```

No PowerShell:

```powershell
.\gradlew.bat check --no-daemon --stacktrace
```

O gate deve abranger compilação, testes, ktlint, Detekt, Kover, testes arquiteturais e integrações configuradas. Se não puder ser executado, informe claramente o motivo e quais verificações ficaram pendentes. Não masque falhas preexistentes nem desative gates para obter sucesso.

Para mudanças documentais sem impacto executável, valide links, caminhos, estados e consistência entre as fontes da verdade; não execute uma suíte cara sem benefício verificável.

## Sprints, ADRs e documentação

- Trabalhe incrementalmente segundo a sprint ativa e mantenha commits pequenos e descritivos.
- Atualize o arquivo da sprint quando tarefa, bloqueio, escopo, decisão, risco ou evidência mudar.
- Registre evidências curtas, reproduzíveis e sanitizadas; não cole logs extensos nem artefatos de build.
- Uma tarefa implementada, mas ainda não aceita, permanece `Em validação`.
- Não marque sprint como `Concluída` sem evidência para todos os critérios obrigatórios e aceite registrado.
- Mudanças relevantes de contrato, dados, segurança, limites modulares, dependências estruturais ou implantação exigem ADR antes da implementação afetada.
- Atualize `docs/modelagem.md` quando a arquitetura vigente mudar.
- Não duplique conteúdo canônico: prefira links e mantenha cada decisão em sua fonte responsável.

## Entrega ao usuário

Ao finalizar, informe de forma objetiva:

- o que mudou;
- quais arquivos foram alterados;
- quais critérios da sprint foram atendidos;
- quais comandos/testes foram executados e seus resultados;
- riscos, bloqueios ou validações pendentes;
- qualquer decisão que ainda precise de aceite.

Não crie commits, branches, pull requests, releases ou alterações externas além do que o usuário autorizou explicitamente.
