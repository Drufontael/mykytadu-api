# ADR-014 — Política de retenção e exclusão de dados

> **Estado:** Aprovado
> **Data:** 2026-09-17
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B0.1 / B0.1-T5
> **Decisões relacionadas:** P-005, P-006, [ADR-010](ADR-010-cadastro-com-verificacao-de-email.md), [ADR-013](ADR-013-cliente-envia-descricao-para-traducao.md)

## Contexto

O backend receberá descrições de anime enviadas pelo cliente, poderá manter traduções em cache e persistirá dados necessários para identidade, sessões, tokens de ação, auditoria e medição de uso. A retenção indefinida aumenta exposição e custo, enquanto a exclusão de uma conta precisa revogar acessos sem quebrar a capacidade mínima de investigação de eventos de segurança.

A política deve aplicar minimização de dados, separar cache técnico de dados de conta e permitir que a infraestrutura de backup seja definida na B0.1-T6 sem alterar as regras de domínio.

## Drivers da decisão

- manter dados somente pelo tempo necessário à finalidade declarada;
- não persistir o texto original recebido para tradução;
- preservar detecção de reutilização de refresh token durante sua janela útil;
- revogar o acesso da conta imediatamente quando ela for excluída;
- limitar o impacto de backups e permitir limpeza verificável;
- não registrar segredos, conteúdo integral ou identificadores pessoais desnecessários.

## Opções consideradas

### Retenção indefinida

Não selecionada. Simplifica a implementação, mas contradiz a minimização, amplia o impacto de incidentes e mantém traduções possivelmente obsoletas.

### Retenção curta com limpeza automática

Selecionada. Cada categoria recebe uma finalidade e um prazo explícito. A limpeza será executada por processo controlado, idempotente e observável, com índices/consultas adequados para expiração.

### Exclusão apenas lógica

Não selecionada. O estado `DELETED` é necessário para o domínio, mas um booleano isolado não remove credenciais, tokens e dados pessoais.

## Decisão

### Prazos aprovados

Os prazos abaixo são limites operacionais máximos no ambiente de aplicação. A contagem usa `Instant`/UTC e começa no evento indicado.

| Categoria | Dados mantidos | Prazo | Regra de limpeza |
| --- | --- | --- | --- |
| Cache de tradução | `content_hash`, idiomas, tipo, tradução, provider/model e timestamps técnicos | 30 dias a partir de `created_at` | hit não estende o prazo; o texto original não é persistido |
| Tokens de ação | hash, tipo e timestamps de verificação/reset | até 24 horas após expirar ou ser consumido | remover token expirado/consumido e seus dados associados |
| Sessões | hash do refresh, família, expiração, revogação e motivo | até `max(expires_at, revoked_at) + 7 dias` | preservar a janela necessária à detecção de reuse; depois remover |
| Auditoria de segurança | evento, resultado, código de motivo, instante, tipo de cliente e identificador técnico pseudonimizado | 90 dias | não incluir senha, token, hash de token, e-mail, IP, user-agent ou texto integral |
| Métricas de uso | contadores agregados por dia e principal técnico | 90 dias | após exclusão, desvincular o principal; não manter histórico pessoal identificável |
| Logs operacionais | eventos técnicos, `traceId`, status e latência | 30 dias | não incluir dados pessoais, segredos ou conteúdo traduzido integral |
| Backups | cópia criptografada dos dados necessários à recuperação | 30 dias | acesso operacional restrito; backups expirados devem ser eliminados conforme a solução escolhida no T6 |

O prazo do cache é absoluto a partir da criação. A coluna `last_accessed_at`, se não tiver finalidade operacional comprovada, não deve ser usada para prolongar a retenção.

### Exclusão de conta

Não haverá janela de recuperação no MVP. Ao receber uma exclusão autorizada, o sistema deverá, de forma transacional quando estiver no mesmo limite de consistência:

1. marcar a conta como `DELETED` e revogar imediatamente todas as sessões;
2. remover ou anonimizar credenciais, papéis, tokens de ação e atributos pessoais do perfil;
3. desvincular o principal das métricas de uso;
4. remover ou irreversivelmente anonimizar o identificador da conta nos eventos de auditoria que precisarem permanecer até o fim do prazo;
5. permitir que caches de tradução independentes da conta expirem normalmente, pois não armazenam `user_id` nem o texto original;
6. deixar a remoção física dos registros elegíveis para o processo de limpeza, sem permitir autenticação ou recuperação da conta marcada como `DELETED`.

O e-mail e outros atributos pessoais não devem continuar disponíveis para autenticação após a exclusão. Caso seja necessário preservar um tombstone técnico para impedir inconsistências, ele deve conter somente identificador não reutilizável, estado e timestamps, sem dados pessoais, e ter finalidade e prazo documentados.

### Exceções controladas

Uma retenção superior somente pode ocorrer por obrigação legal, ordem válida ou necessidade documentada de exercício de direitos, defesa ou investigação de segurança. A exceção deve ser aprovada, limitada aos dados necessários, registrada sem conteúdo sensível e revisada quanto ao prazo.

## Consequências

### Positivas

- menor exposição de descrições e traduções armazenadas;
- exclusão de conta revoga acesso sem depender da limpeza posterior;
- a tradução em cache não fica acoplada ao ciclo de vida de uma conta;
- a detecção de reuse continua possível durante a janela relevante;
- a infraestrutura do T6 pode mudar sem alterar os prazos de domínio.

### Negativas

- traduções antigas precisarão ser recalculadas após 30 dias;
- jobs de limpeza, métricas e alertas passam a ser necessários;
- a exclusão de dados em backups depende do ciclo de vida da solução de backup;
- auditoria sem e-mail/IP reduz a informação disponível para algumas investigações.

### Riscos e mitigações

- **Job de limpeza falhar:** expor idade máxima e volume pendente em métricas sem dados pessoais; alertar e executar novamente de forma idempotente.
- **Exclusão parcial:** registrar estado técnico da operação, sem conteúdo pessoal, e permitir retry seguro.
- **Backup restaurar dados excluídos:** restringir acesso, reexecutar a exclusão após restauração e limitar a retenção a 30 dias.
- **Necessidade de prazo diferente:** revisar com evidência de uso, incidente, obrigação legal ou requisito operacional; não alterar silenciosamente a política.

## Evidências

- A [LGPD, arts. 15, 16 e 18](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm) relaciona o término do tratamento, a eliminação e os direitos de eliminação, bloqueio e anonimização, ressalvadas hipóteses legais.
- A [Nota Técnica ANPD nº 29/2024](https://www.gov.br/anpd/pt-br/centrais-de-conteudo/documentos-tecnicos-orientativos/nota-tecnica-29_2024.pdf) orienta vincular retenção à finalidade, evitar conservação excessiva e adotar eliminação segura.
- A [ADR-013](ADR-013-cliente-envia-descricao-para-traducao.md) define que o cliente envia o texto e remete armazenamento, TTL e exclusão a esta decisão.

Esta decisão é técnica e não substitui revisão jurídica específica para a operação real.

## Plano de adoção

1. refletir `expires_at` e a ausência de `original_text` no modelo de Translation;
2. implementar políticas de limpeza idempotentes para cache, tokens, sessões, auditoria, métricas e logs;
3. implementar a exclusão de conta com revogação imediata e remoção/anonimização dos dados definidos;
4. adicionar testes determinísticos usando relógio injetável;
5. definir no B0.1-T6 a tecnologia de backup, o controle de acesso e a evidência de expiração;
6. criar o inventário operacional de privacidade antes da primeira exposição remota.

## Critérios de revisão

Reavaliar os prazos se o benchmark/uso real demonstrar necessidade, se houver incidente ou requisito de investigação, se o fornecedor/hosting alterar retenção, ou antes de introduzir novos tipos de conteúdo, histórico de traduções ou recuperação de contas.
