# ADR-010 — Cadastro com verificação de e-mail antes do login

> **Estado:** Aprovado
> **Data:** 2026-09-15
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B0.1 / B0.1-T1
> **Decisões relacionadas:** P-001, P-002

## Contexto

O MVP precisa definir a forma de cadastro e o momento em que uma conta pode iniciar uma sessão. A decisão altera o ciclo de vida do usuário, o contrato de autenticação, a experiência do cliente e os controles contra contas não verificadas.

## Drivers da decisão

- manter o MVP restrito à autenticação própria por e-mail e senha;
- evitar implementar login social antes de existir requisito aprovado;
- impedir autenticação de contas que ainda não comprovaram controle do e-mail;
- manter estados de conta explícitos e compatíveis com o modelo de Identity.

## Opções consideradas

### Opção A — exigir verificação antes do login

O cadastro cria uma conta `pending`. A conta só pode iniciar sessão depois da confirmação do e-mail e da transição para `active`.

### Opção B — permitir login limitado antes da verificação

A conta poderia iniciar sessão em estado `pending`, com autorização parcial até confirmar o e-mail. Esta opção aumenta a complexidade de autorização e de contrato sem requisito atual.

### Opção C — incluir login social no MVP

O cadastro e o login dependeriam também de provedores externos. Esta opção amplia o escopo, os riscos de integração e as decisões de OAuth/OIDC.

## Decisão

Foi aprovada a Opção A:

- o MVP usará somente e-mail e senha;
- login social permanece fora do escopo atual;
- o cadastro cria o usuário em `pending`;
- a confirmação válida do e-mail ativa o usuário, levando-o para `active`;
- somente usuários `active` poderão autenticar;
- usuários `blocked` e `deleted` não poderão autenticar;
- o contrato de erros, tokens de verificação e limites operacionais serão detalhados nas sprints correspondentes, sem antecipar implementação nesta sprint.

## Consequências

### Positivas

- ciclo de vida de conta definido antes da implementação de Identity;
- evita sessão autenticada para e-mail não verificado;
- reduz o escopo inicial e mantém o contrato focado em autenticação própria;
- permite que autorização e persistência usem estados explícitos.

### Negativas

- o primeiro login depende da entrega e confirmação do e-mail;
- será necessário definir reenvio, expiração e tratamento de tokens de verificação antes da implementação de B2.1;
- clientes precisam tratar a conta pendente sem depender de mensagens textuais instáveis.

### Riscos e mitigações

- **Entrega de e-mail indisponível:** definir reenvio e operação antes de B2.1, conforme P-009.
- **Enumeração de contas:** revisar respostas e códigos do cadastro/login no contrato B0.2 e na implementação de Identity.
- **Estado bloqueado ou excluído voltar a autenticar:** cobrir transições e autorização com testes de integração em B2.1.

## Evidências

- decisão confirmada pelo responsável do projeto em 2026-09-15;
- critérios B0.1-T1 registrados em [B0.1.md](../sprints/B0.1.md);
- estados e ciclo de vida previstos em [modelagem.md](../modelagem.md).

## Plano de adoção

1. manter esta decisão como referência para o contrato de Identity;
2. detalhar schemas, token de verificação e respostas HTTP em B0.2/B2.1;
3. implementar a transição `pending` → `active` com token de uso único e expiração;
4. testar autenticação negada para `pending`, `blocked` e `deleted`.

## Critérios de revisão

Reavaliar quando login social se tornar requisito aprovado, quando a política de verificação mudar ou quando evidência operacional demonstrar que o fluxo impede o uso esperado do produto.
