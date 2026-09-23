# ADR-018 — Criar a sessão inicial no login da B2.1

> **Estado:** Aprovado
> **Data:** 2026-09-18
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B2.1-T4, B2.1-T5 e B2.2-T1
> **Decisões relacionadas:** [ADR-005](ADR-005-usar-jwt-curto-e-refresh-rotativo.md), [ADR-011](ADR-011-sessao-web-com-refresh-token-em-cookie.md)

## Contexto

O roadmap atribuía o login e a emissão de JWT à B2.1, mas deixava toda a
persistência de sessões para B2.2. O OpenAPI e o ADR-011 já definem que
`POST /api/v1/auth/login` cria uma sessão, devolve refresh token aos clientes
nativos e usa cookie protegido no Web. Implementar apenas o access token
produziria uma resposta parcial e incompatível com o contrato aprovado.

O request de login também não identificava o cliente, embora a forma de
entrega do refresh e a operação Web dependam da plataforma. Tornar um novo
campo obrigatório agora quebraria `/api/v1`, portanto a adoção precisa ser
aditiva.

## Drivers da decisão

- entregar o login completo prometido pelo OpenAPI;
- evitar uma resposta temporária que o cliente precisaria desaprender;
- manter access token curto e refresh opaco persistido somente como hash;
- distinguir Web de clientes nativos sem depender de `User-Agent`;
- preservar compatibilidade contratual de `/api/v1`;
- deixar rotação, reutilização e logout concentrados na B2.2.

## Opções consideradas

### Emitir somente access token na B2.1

Não selecionada. Contradiz o `SessionResponse`, não restaura sessão Web e cria
um fluxo descartável.

### Adiar todo o endpoint de login para B2.2

Não selecionada. Impede a saída demonstrável da B2.1 e separa artificialmente
validação de credenciais da criação da primeira sessão.

### Criar a sessão inicial na B2.1

Selecionada. B2.1 entrega login completo; B2.2 evolui essa sessão para rotação,
detecção de reutilização, logout e revogação concorrente.

## Decisão

- B2.1 persiste a sessão inicial e a família, gera refresh opaco, armazena
  somente seu hash e emite o access JWT de 10 minutos;
- a sessão registra o `clientId` quando informado e os metadados mínimos
  necessários para expiração e futura rotação;
- para `mykytadu-web`, o login exige `Origin` permitida, envia refresh em cookie
  protegido e retorna um synchronizer token cujo hash fica associado à sessão;
- para Android, iOS e Desktop/JVM, o refresh é devolvido no JSON e o CSRF não é
  usado;
- `LoginRequest.clientId` é adicionado de forma opcional em `/api/v1` para não
  quebrar consumidores existentes; sua ausência é tratada como cliente nativo
  legado, sem claim de client ID e sem cookie;
- novos clientes devem enviar um dos IDs aprovados: `mykytadu-web`,
  `mykytadu-android`, `mykytadu-ios` ou `mykytadu-desktop`;
- `aud` permanece `mykytadu-api`; quando conhecido, o client ID entra em claim
  separado e não substitui audience;
- B2.2 implementa rotação atômica, detecção de reuse, endpoint de refresh,
  obtenção/validação de CSRF, logout, logout-all e revogação por estado da conta.

## Consequências

### Positivas

- login e OpenAPI passam a descrever a mesma operação;
- o cliente recebe uma sessão utilizável desde B2.1;
- B2.2 começa com uma sessão real e concentra seu ciclo de vida;
- a evolução de `clientId` permanece compatível nesta versão.

### Negativas

- B2.1 passa a incluir migration e modelo mínimo de sessão;
- o modo legado sem `clientId` precisa permanecer testado durante a transição;
- Web exige cookie, Origin e hash de CSRF já no login;
- B2.2-T1 precisa ser reescrita para evoluir, não criar, a sessão.

### Riscos e mitigações

- **cliente forja `clientId`:** o campo seleciona protocolo, não concede papel;
  Web também exige Origin permitida e controles do ADR-011;
- **refresh exposto ao JavaScript:** `mykytadu-web` nunca recebe refresh no
  corpo; cookie é `HttpOnly`, `Secure`, `SameSite=Lax` e host-only;
- **modo legado indefinido:** ausência sempre produz fluxo nativo, sem inferência
  por `User-Agent`;
- **escopo crescer para toda B2.2:** limitar B2.1 à criação inicial; nenhuma
  rotação, reuse ou logout entra antecipadamente.

## Evidências

- o [ADR-005](ADR-005-usar-jwt-curto-e-refresh-rotativo.md) define o par access
  curto e refresh opaco;
- o [ADR-011](ADR-011-sessao-web-com-refresh-token-em-cookie.md) exige que o
  login crie sessão e diferencia Web de clientes nativos;
- o OpenAPI 0.2.0 já possui `SessionResponse`, cookie e exemplos Web/nativo.

## Plano de adoção

1. atualizar roadmap, sprint, modelagem e OpenAPI;
2. adicionar `clientId` opcional e exemplos por plataforma;
3. criar migration e modelo mínimo de sessão em B2.1-T5;
4. implementar criação transacional da sessão após credenciais válidas;
5. testar modo legado, cada client ID, cookie Web e ausência de refresh no corpo;
6. iniciar B2.2 a partir da sessão persistida, sem duplicar sua criação.

## Critérios de revisão

Remover o modo legado e tornar `clientId` obrigatório somente em versão major
ou após migração compatível formalmente aprovada. Reavaliar a fronteira se o
frontend abandonar sessão Web persistente ou se o login deixar de emitir
refresh.
