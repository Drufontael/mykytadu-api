# ADR-017 — Enviar e-mail após commit com reemissão segura

> **Estado:** Aprovado
> **Data:** 2026-09-18
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B2.1 / B2.1-T2 e B2.1-T3
> **Decisões relacionadas:** P-009, [ADR-010](ADR-010-cadastro-com-verificacao-de-email.md), [ADR-014](ADR-014-politica-de-retencao-e-exclusao.md)

## Contexto

O cadastro precisa persistir usuário, credencial, papel e token de verificação
atomicamente, mas o serviço de e-mail é uma dependência externa que não
participa da transação PostgreSQL. Enviar antes do commit pode entregar um
token sem estado correspondente; enviar depois do commit pode falhar e deixar
uma conta `pending` sem mensagem entregue.

Tokens de ação permanecem opacos e só podem ser persistidos como hash. Uma
outbox contendo o token puro ou o corpo integral do e-mail violaria essa regra.
O MVP precisa ser recuperável sem introduzir broker ou persistência reversível
do token.

## Drivers da decisão

- preservar a atomicidade dos dados de Identity;
- nunca persistir token de ação puro, nem mesmo em outbox;
- informar a falha da primeira entrega sem desfazer uma conta já confirmada no
  banco;
- permitir recuperação segura e resistente à enumeração;
- manter a menor solução operável para uma única instância no MVP;
- limitar chamadas externas com timeout e sem retry cego após resultado
  ambíguo.

## Opções consideradas

### Envio antes do commit

Não selecionada. O provedor pode aceitar a mensagem e a transação falhar,
produzindo um link que nunca poderá ser validado.

### Envio síncrono após commit com reemissão

Selecionada. O token puro permanece apenas em memória durante a tentativa. Se
o envio falhar, a conta continua `pending`, a resposta informa indisponibilidade
e uma operação pública, uniforme e limitada permite emitir outro token.

### Outbox local com a mensagem pronta

Não selecionada no MVP. Persistir a mensagem ou o link armazenaria o token
puro de forma recuperável. Uma outbox que gere tokens apenas no despacho é
possível, mas amplia concorrência, duplicidade de mensagens, limpeza e operação
sem evidência atual que justifique esse custo.

## Decisão

- a transação de cadastro persiste usuário `pending`, credencial, papel `USER`
  e somente o hash do token de verificação;
- o token de verificação vale por 24 horas a partir da emissão; reemissão cria
  nova validade e invalida tokens de verificação anteriores da mesma conta;
- após o commit, o adapter de e-mail recebe o token puro ainda em memória e
  realiza uma única tentativa com timeout explícito;
- resultado ambíguo não recebe retry automático, evitando duplicação
  descontrolada no provedor;
- se a entrega inicial falhar, a conta permanece `pending` e o cadastro retorna
  `503 email_delivery_unavailable` com `Retry-After` quando houver estimativa;
- `POST /api/v1/auth/verify-email/resend` aceita apenas o e-mail e sempre
  retorna `202` para uma entrada sintaticamente válida, exista ou não conta
  elegível;
- para conta `pending`, a reemissão invalida tokens de verificação ainda não
  consumidos, cria e persiste atomicamente um novo hash e tenta o envio após o
  commit;
- falha interna ou do provedor durante a reemissão não altera a resposta
  uniforme; é registrada por evento e métrica de baixa cardinalidade para não
  permitir enumeração;
- cadastro e reemissão possuem rate limits independentes; nenhum log, métrica
  ou evento contém e-mail, token, hash de token ou corpo integral da mensagem;
- ambientes local e de teste usam adapter controlado, sem envio remoto real.

No MVP, entrega confiável significa estado consistente e caminho explícito de
recuperação, não garantia exactly-once do provedor externo.

## Consequências

### Positivas

- nenhum token puro é persistido;
- falha externa não corrompe nem reverte a conta criada;
- a pessoa pode recuperar uma conta `pending` sem suporte manual;
- a resposta uniforme de reemissão reduz enumeração de contas;
- broker, outbox e worker não entram sem necessidade comprovada.

### Negativas

- uma falha entre commit e envio exige reemissão pelo cliente;
- a primeira tentativa pode retornar falha embora a conta já exista;
- a reemissão substitui links anteriores ainda não consumidos;
- a entrega automática em background fica fora do MVP.

### Riscos e mitigações

- **enumeração por resposta ou tempo:** resposta `202` uniforme, trabalho
  equivalente quando viável e rate limit por origem técnica;
- **conta pendente sem entrega:** cliente oferece reemissão e a operação é
  observável por código seguro;
- **mensagem duplicada após resultado ambíguo:** não repetir automaticamente; a
  próxima reemissão cria outro token e invalida o anterior;
- **abuso do reenvio:** cooldown, limite por endereço normalizado e limite
  técnico por origem, sem registrar o e-mail em telemetria;
- **indisponibilidade prolongada:** alerta operacional e futura reavaliação de
  outbox local com geração segura no despacho.

## Evidências

- o [ADR-010](ADR-010-cadastro-com-verificacao-de-email.md) exige reenvio e
  tratamento da indisponibilidade antes da B2.1;
- o [ADR-014](ADR-014-politica-de-retencao-e-exclusao.md) limita tokens de ação
  a hash e define sua retenção;
- a modelagem já posiciona o envio depois do commit e mantém o serviço de
  e-mail atrás de adapter substituível.

## Plano de adoção

1. atualizar OpenAPI, catálogo de erros e mock com reemissão e falha de entrega;
2. criar a migration incremental de `identity.action_tokens`;
3. implementar porta de e-mail e adapters controlados por ambiente;
4. persistir cadastro e token em uma única transação;
5. disparar o adapter somente após commit;
6. implementar reemissão uniforme, cooldown e rate limit;
7. testar falha antes/depois do commit, timeout, repetição e ausência de dados
   sensíveis em logs e métricas.

## Critérios de revisão

Reavaliar se o volume de contas pendentes por falha de entrega se tornar
relevante, se o provedor oferecer idempotência verificável ou se o produto
exigir entrega automática sem nova ação do cliente. Uma outbox futura não pode
persistir token puro nem relaxar a minimização aprovada.
