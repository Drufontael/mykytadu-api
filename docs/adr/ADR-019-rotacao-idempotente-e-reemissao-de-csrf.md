# ADR-019 — Rotação idempotente e reemissão de CSRF

> **Estado:** Aprovado
> **Data:** 2026-09-27
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B2.2 / B2.2-T1 e B2.2-T2
> **Decisões relacionadas:** ADR-005, ADR-011 e ADR-018

## Contexto

A B2.1 cria uma linha em `identity.sessions` com o hash do refresh atual, a
família, o cliente, o hash de CSRF Web e os limites temporais. A B2.2 precisa
rotacionar esse refresh, detectar reutilização, revogar famílias e permitir que
o Web restaure o synchronizer token após reload.

Sobrescrever o único hash da sessão elimina a evidência necessária para
distinguir um predecessor rotacionado de um token desconhecido. Ao mesmo tempo,
preservar o predecessor faz um retry após resposta de rede perdida parecer
reutilização maliciosa. O ADR-011 exige `Idempotency-Key` nesse cenário, mas não
define como repetir a resposta sem persistir refresh token puro ou reversível.

O CSRF Web apresenta restrição semelhante: somente seu hash é persistido, logo
`GET /api/v1/auth/csrf` não pode recuperar o valor emitido anteriormente.

## Drivers da decisão

- refresh e CSRF nunca persistidos em texto puro;
- rotação e detecção de reutilização transacionais;
- retry de rede não pode revogar uma família legítima;
- nenhuma janela genérica aceita predecessor sem a mesma chave idempotente;
- compatibilidade com Web e clientes nativos;
- locks e resultados determinísticos em múltiplas instâncias da aplicação;
- retenção mínima de dados e telemetria sem tokens ou alta cardinalidade.

## Opções consideradas

### Sobrescrever a linha atual

Não selecionada. Simplifica a rotação, mas perde o predecessor e impede detectar
reutilização de forma confiável.

### Aceitar temporariamente o predecessor

Não selecionada. Uma janela de tolerância sem vínculo com a operação original
permite que um token copiado seja usado como retry legítimo.

### Persistir a resposta ou o novo refresh de forma reversível

Não selecionada. Mesmo cifrado, o refresh seria uma representação recuperável
persistida e ampliaria o impacto de comprometimento do banco e das chaves.

### Criar um sucessor e derivar novamente o refresh durante replay limitado

Selecionada. Cada rotação preserva o predecessor, cria um sucessor na mesma
família e associa a transição ao hash da chave de idempotência. O refresh do
sucessor é derivado por HMAC a partir de material externo ao banco e metadados
da transição, permitindo reproduzi-lo somente durante uma janela curta.

## Decisão proposta

### Linhagem da sessão

Cada linha de `identity.sessions` representa uma geração de refresh. A rotação:

1. calcula o hash do refresh apresentado;
2. localiza e bloqueia pessimisticamente a geração correspondente;
3. valida usuário, expiração, revogação, cliente, Origin e CSRF aplicáveis;
4. cria uma nova geração com novo UUIDv7 e a mesma `token_family_id`;
5. marca a predecessora com `revoked_at`, motivo `rotated` e referência para a
   sucessora;
6. persiste na predecessora o hash da `Idempotency-Key` e o limite de replay;
7. conclui criação e revogação na mesma transação.

A migration incremental adicionará somente os campos necessários à linhagem e
ao replay. A migration da B2.1 permanece imutável. Foreign keys, se usadas,
permanecem dentro do schema `identity`.

### Idempotência do refresh

`Idempotency-Key` passa a ser obrigatória em `POST /auth/refresh`, tanto no Web
quanto em clientes nativos. A mudança será registrada no OpenAPI antes da
implementação do endpoint.

O novo refresh será gerado por HMAC-SHA-256 usando:

- uma chave de derivação externa ao banco e identificada por `kid`;
- domínio/versionamento fixo da derivação;
- UUID da geração predecessora;
- UUID da geração sucessora;
- valor integral da `Idempotency-Key`.

O valor retornado será codificado em Base64 URL-safe sem padding e persistido
somente como SHA-256. O banco guardará o hash da chave idempotente, o `kid` da
derivação, a referência da sucessora e `replay_until`, nunca a chave idempotente
nem o refresh em texto puro.

Durante a janela inicial proposta de dois minutos:

- mesmo predecessor e mesma `Idempotency-Key` reproduzem a mesma geração e o
  mesmo refresh, sem nova rotação;
- mesmo predecessor e chave diferente representam reutilização e revogam a
  família;
- após `replay_until`, qualquer uso do predecessor representa reutilização,
  mesmo com a mesma chave.

O access JWT do replay mantém os mesmos `jti`, `iat`, `nbf` e `exp` da primeira
resposta. Esses metadados não são secretos e serão persistidos na transição para
reproduzir o mesmo resultado lógico. Access tokens continuam fora do banco.

A configuração manterá a chave de derivação corrente e as anteriores pelo menos
até expirar a maior janela de replay emitida com cada `kid`. Ausência da chave
necessária falha fechada e produz evento técnico sanitizado.

### Reemissão de CSRF Web

`GET /auth/csrf` localiza a geração atual pelo hash do refresh cookie, bloqueia
a linha, gera um novo token CSRF aleatório, substitui atomicamente o hash e
retorna o valor puro uma única vez. O endpoint nunca torna o hash reversível.

Somente um CSRF permanece válido por geração. O cliente Web coordena chamadas
com Web Locks e compartilha o CSRF novo entre contextos do mesmo site por
BroadcastChannel; refresh tokens nunca passam por esse canal. Em corrida não
coordenada, vence a última reemissão confirmada e a anterior recebe
`csrf_invalid`, podendo obter novo CSRF e repetir uma vez.

Cada rotação de refresh emite também um novo CSRF para a geração sucessora. O
hash do predecessor não é aceito na sucessora.

### Reutilização e revogação

Uso de predecessor fora do replay idempotente revoga todas as gerações ainda
renováveis da família na mesma transação. A resposta externa é sempre
`401 session_invalid`, sem distinguir expiração, revogação, token desconhecido
ou reutilização.

Locks seguem ordem estável: usuário quando necessário, família ordenada por ID
e geração alvo. Logout-all, bloqueio e refresh usam a mesma convenção.

### Privacidade e observabilidade

Logs e métricas não recebem refresh, CSRF, hashes, cookies,
`Idempotency-Key`, `Origin`, `Authorization`, IDs de sessão/família ou dados
pessoais. Eventos usam apenas categorias finitas como `rotated`, `replayed`,
`reuse_detected`, `revoked`, `expired` e `invalid`.

## Consequências

### Positivas

- reutilização permanece detectável sem aceitar predecessor genericamente;
- retry legítimo não cria uma segunda rotação nem revoga a família;
- banco comprometido não contém material suficiente para reconstruir refresh;
- CSRF pode ser restaurado após reload sem persistência reversível;
- o modelo funciona com múltiplas réplicas usando PostgreSQL como coordenador.

### Negativas

- a sessão passa a manter histórico de gerações;
- a derivação exige nova chave operacional e política de rotação por `kid`;
- clientes precisam sempre gerar e preservar `Idempotency-Key` até concluir o
  refresh;
- múltiplas abas Web precisam coordenar reemissão de CSRF;
- limpeza de gerações expiradas precisa respeitar a janela de detecção/replay.

## Plano de adoção

1. aprovar este ADR e atualizar OpenAPI, catálogo, exemplos e modelagem;
2. definir configuração validada para chaves de derivação e janela de replay;
3. criar migration incremental de linhagem/idempotência;
4. evoluir domínio, portas e adapter PostgreSQL com locks explícitos;
5. implementar `/auth/csrf` e `/auth/refresh` para Web e nativos;
6. cobrir replay, reutilização, perda de resposta, concorrência e rotação de
   chave com relógio injetável e PostgreSQL real;
7. definir limpeza segura do histórico antes de ativá-la operacionalmente.

## Critérios de revisão

Reavaliar se o KMP não puder enviar `Idempotency-Key`, se HMAC derivado não
puder ser operado com rotação segura, se a coordenação Web se mostrar
incompatível com os targets ou se requisitos futuros exigirem revogação
imediata de access JWT.
