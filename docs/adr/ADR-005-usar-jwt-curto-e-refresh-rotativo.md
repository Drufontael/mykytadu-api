# ADR-005 — Usar JWT curto e refresh opaco rotativo

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação de autenticação
> **Decisões relacionadas:** D-005

## Contexto

Os clientes precisam acessar recursos autenticados sem que a API persista access tokens. A sessão longa precisa ser revogável e detectar reutilização de um refresh token comprometido.

## Drivers da decisão

- access token curto e verificável pela API;
- refresh token não utilizável como credencial de negócio;
- rotação e detecção de reutilização;
- armazenamento seguro de segredos e hashes;
- compatibilidade com clientes Web e nativos.

## Opções consideradas

### Sessão opaca única no servidor

Não selecionada como modelo principal. Centraliza toda autorização no estado do servidor e não oferece as vantagens operacionais de validação local do access token.

### JWT longo e não rotativo

Não selecionada. Amplia o período de uso de uma credencial comprometida e dificulta revogação.

### JWT curto com refresh opaco rotativo

Selecionada. O access token é curto e o refresh é armazenado somente como hash, rotacionado a cada uso e associado a uma família.

## Decisão

Usar JWT de curta duração, assinado assimetricamente e validado por assinatura, `iss`, `aud`, `exp`, `nbf` e `kid`. Access tokens não são persistidos.

Usar refresh token opaco, persistido somente como hash, com rotação a cada uso. Reutilização de um token revoga toda a família. Logout revoga a sessão correspondente. Os detalhes específicos do fluxo Web, incluindo cookie, CSRF, TTLs e client IDs, estão em [ADR-011](ADR-011-sessao-web-com-refresh-token-em-cookie.md).

## Consequências

### Positivas

- access tokens comprometidos expiram rapidamente;
- refresh tokens não ficam em texto puro no banco;
- reutilização sinaliza possível comprometimento;
- clientes podem renovar a sessão sem novo login a cada requisição.

### Negativas

- rotação exige atualização atômica e coordenação no cliente;
- revogação de família pode encerrar sessões legítimas em caso de concorrência mal controlada;
- chaves assimétricas e seus `kid` precisam de operação segura.

## Plano de adoção

1. definir claims mínimos e validação do resource server;
2. persistir hashes, família, expiração e revogação;
3. implementar rotação atômica e detecção de reuse;
4. cobrir concorrência, expiração, logout e falhas de rede;
5. aplicar o perfil Web aprovado no ADR-011.

## Critérios de revisão

Reavaliar se houver mudança de clientes, requisito de OAuth/OIDC completo ou evidência de que o modelo não atende a revogação e operação esperadas.
