# Política de evolução e suporte da API

> **Status:** vigente
> **Versão da política:** 0.1
> **Data de referência:** 17 de setembro de 2026

Este documento define como o contrato HTTP evolui, como mudanças são
comunicadas e por quanto tempo uma versão permanece suportada. O
[`openapi.yaml`](../api/openapi.yaml) continua sendo a fonte normativa de
schemas, operações, respostas, exemplos e segurança; este documento define o
processo e as regras de compatibilidade.

## 1. Escopo e fontes

A política se aplica às operações publicadas sob `/api/v1` e aos clientes
Android, Desktop/JVM, iOS, WasmJS e JavaScript do `mykytadu-app`.

Cada mudança de contrato deve atualizar, na mesma unidade de trabalho:

- o OpenAPI e seus exemplos;
- o catálogo de erros, quando códigos ou respostas forem afetados;
- este changelog, com impacto e ação para os clientes;
- a documentação da sprint e o ADR aplicável, quando a mudança for relevante.

O CI executa lint e compatibilidade contra a versão disponível na branch base.
Se a base ainda não possuir OpenAPI, o lint permanece obrigatório e a
comparação é registrada como não aplicável para aquela primeira publicação.

## 2. Versionamento

O caminho da URL representa a versão major da API:

- `/api/v1` permanece estável para clientes publicados;
- uma mudança incompatível exige nova versão major, normalmente `/api/v2`;
- não se cria uma nova porta ou domínio para representar uma versão da API;
  a separação continua sendo feita pelo caminho versionado.

O campo `info.version` do OpenAPI usa SemVer:

| Tipo | Uso | Exemplo |
|---|---|---|
| `PATCH` | correção de documentação, exemplo ou metadado sem alterar o comportamento contratual | `0.1.0` → `0.1.1` |
| `MINOR` | mudança aditiva compatível | `0.1.0` → `0.2.0` |
| `MAJOR` | mudança incompatível; deve vir acompanhada de nova base de URL e plano de migração | `1.4.0` → `2.0.0` |

Enquanto o contrato estiver na série `0.x`, a política de compatibilidade de
`/api/v1` continua sendo a de uma API publicada: o fato de o número estar
abaixo de `1.0.0` não autoriza quebras silenciosas.

## 3. Mudanças compatíveis

São permitidas em `/api/v1`, desde que o CI e a revisão do cliente confirmem
o impacto:

- adicionar uma operação ou recurso novo;
- adicionar uma propriedade opcional em uma requisição;
- adicionar uma propriedade ou header de resposta que clientes possam ignorar;
- ampliar documentação, exemplos ou descrições sem mudar o comportamento;
- adicionar um código de erro que clientes já tratem por fallback de status,
  desde que o catálogo e a ação recomendada sejam atualizados.

Estas mudanças ainda exigem atualização do OpenAPI, revisão do impacto e
incremento de `MINOR` ou `PATCH` conforme a tabela de versionamento.

Não se presume que uma alteração seja compatível apenas por ser aditiva. Novos
valores de enum, novos status possíveis, mudanças em limites e alterações de
serialização exigem revisão específica porque clientes podem tratar esses
conjuntos de forma exaustiva.

## 4. Mudanças incompatíveis

São incompatíveis, entre outras:

- remover ou renomear operação, caminho, parâmetro ou propriedade obrigatória;
- tornar obrigatório um campo antes opcional;
- alterar tipo, formato, semântica ou unidade de um campo existente;
- reduzir limites aceitos ou restringir valores válidos;
- remover resposta, status, header ou código que clientes existentes possam
  receber;
- alterar autenticação, audience, client ID, cookie, CSRF ou requisitos de
  autorização de uma operação existente;
- mudar o significado de uma resposta mantendo a mesma forma;
- adicionar valores a enums de requisição ou resposta sem estratégia de
  compatibilidade explícita.

Uma quebra exige, antes da implementação afetada:

1. ADR com contexto, impacto no `mykytadu-app`, alternativas e plano de
   migração;
2. nova versão major e nova base de URL, salvo exceção de segurança aprovada;
3. atualização do OpenAPI, exemplos, catálogo de erros e changelog;
4. execução do oasdiff no CI e validação das plataformas consumidoras;
5. plano de coexistência e encerramento da versão anterior.

Correções urgentes de segurança podem exigir uma exceção temporária, mas
devem registrar o ADR e a comunicação aos clientes no mesmo ciclo de entrega.

## 5. Depreciação

Uma operação, campo ou comportamento em descontinuação deve:

1. ser marcado como `deprecated: true` no OpenAPI;
2. receber entrada neste changelog com motivo, alternativa e data de início;
3. permanecer documentado durante toda a janela de migração;
4. ser coberto por teste ou verificação que impeça remoção antecipada.

A janela mínima de uma operação estável é de 180 dias ou dois ciclos de
release, prevalecendo o período maior. A remoção só ocorre em nova versão
major, após a janela e com comunicação da data de encerramento.

Depreciação não altera silenciosamente o comportamento: enquanto a operação
estiver suportada, suas respostas e regras permanecem compatíveis.

## 6. Suporte e ciclo de vida

- A versão major em produção recebe correções de segurança e defeitos durante
  todo o período de suporte publicado.
- Quando uma nova major atingir estabilidade, a major anterior permanece
  suportada por no mínimo 12 meses.
- Durante a coexistência, correções compatíveis podem ser aplicadas nas duas
  majors quando necessário; novos recursos prioritariamente entram na major
  atual.
- O fim de suporte deve ser registrado neste changelog com pelo menos 90 dias
  de antecedência, salvo incidente de segurança ou exigência legal.
- Não há data de fim de suporte definida para `/api/v1` neste momento; não
  existe `/api/v2` publicada.

## 7. Fluxo de mudança

1. Descrever a mudança e classificar seu impacto como compatível, deprecação
   ou incompatível.
2. Atualizar o contrato e os artefatos diretamente afetados.
3. Executar o mock, o lint e a compatibilidade contratual.
4. Adaptar e validar o `mykytadu-app` quando a mudança afetar seu consumo.
5. Registrar ADR e plano de migração para mudanças relevantes ou incompatíveis.
6. Publicar a entrada deste changelog junto com a versão do contrato.

O merge é permitido somente com os gates obrigatórios do CI aprovados. Uma
falha de compatibilidade não deve ser ignorada; ela exige correção, nova base
de versão ou exceção formal registrada.

## 8. Registro inicial

| Versão | Data | Estado | Mudança |
|---|---|---|---|
| `0.1.0` | 2026-09-17 | Atual | Contrato inicial de Identity e Translation em `/api/v1`; sem depreciações e sem versão anterior suportada |
