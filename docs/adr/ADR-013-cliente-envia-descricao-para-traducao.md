# ADR-013 — Receber do cliente a descrição para tradução

> **Estado:** Aprovado
> **Data:** 2026-09-17
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B0.1 / B0.1-T4
> **Decisões relacionadas:** P-004; [ADR-012](ADR-012-libretranslate-com-adapter-substituivel.md)

## Contexto

O `mykytadu-app` já consulta a AniList diretamente para pesquisa e detalhes de anime. A descrição do anime chega ao cliente como parte de `AnimeDetails`. A API precisa traduzir esse conteúdo sem duplicar a integração com a AniList nem transformar-se em cópia ou proxy do catálogo.

A AniList fornece o campo `Media.description`, inclusive com opção de retorno em HTML, mas impõe limites de uso, rate limit e restrições contra backup, armazenamento e coleta massiva de dados.

## Drivers da decisão

- aproveitar a integração AniList já existente no cliente;
- manter a API independente da fonte externa;
- evitar consultas duplicadas e latência adicional;
- reduzir dependência da quota e dos termos operacionais da AniList;
- permitir futuras fontes de conteúdo sem alterar Translation;
- manter o texto sob limites, normalização, cache e observabilidade da API.

## Opções consideradas

### Cliente envia o texto da descrição

Selecionada. O cliente envia o conteúdo que já possui, com idioma, alvo e tipo de conteúdo. A API traduz o texto recebido e pode aceitar metadados opcionais de proveniência.

### API consulta a AniList pelo ID

Não selecionada para o MVP. Duplicaria a integração do cliente, adicionaria latência e faria o backend depender diretamente da disponibilidade, quota e termos da AniList.

### Modelo híbrido

Não selecionado para o MVP. Permitiria receber texto ou resolver IDs, mas aumentaria o contrato e as regras de consistência sem benefício necessário nesta fase.

## Decisão

No MVP, o cliente enviará à API a descrição a ser traduzida. A API não consultará a AniList para resolver o conteúdo por ID.

O contrato aprovado para o MVP será:

```json
{
  "text": "Original anime description",
  "sourceLanguage": "en",
  "targetLanguage": "pt-BR",
  "contentType": "ANIME_DESCRIPTION",
  "source": {
    "system": "ANILIST",
    "externalId": "15125"
  }
}
```

`source` é metadado opcional de proveniência. Não é prova de autenticidade, não substitui o texto na chave de cache e não autoriza a API a buscar o conteúdo externo. A tradução será determinada pelo texto normalizado, idiomas, tipo de conteúdo, fornecedor e versão do modelo.

Regras aprovadas do payload:

- `text` é obrigatório e aceita de 1 a 10.000 caracteres;
- `sourceLanguage` aceita `en`, `ja` ou `auto`;
- `targetLanguage` é `pt-BR` no MVP;
- `contentType` é `ANIME_DESCRIPTION` no MVP;
- `source` é opcional e não confiável;
- o texto é normalizado em Unicode, espaços e quebras de linha antes do cache;
- HTML só será aceito conforme regra explícita do contrato HTTP;
- o limite de requisições será aplicado por usuário/principal;
- o texto integral não será registrado em logs, métricas ou traces;
- `source` não será enviado ao provedor de tradução;
- em caso de falha, o cliente continuará exibindo a descrição original;
- armazenamento, TTL e exclusão são regidos pelo [ADR-014](ADR-014-politica-de-retencao-e-exclusao.md), decidido no B0.1-T5.

O mesmo modelo vale para Android, iOS, Desktop/JVM e Web. O cliente nunca chama o provedor de tradução diretamente.

## Regras de conteúdo e privacidade

- o endpoint aceitará tipos de conteúdo explicitamente aprovados, inicialmente `ANIME_DESCRIPTION`;
- haverá limite de tamanho antes da chamada ao fornecedor;
- `sourceLanguage` poderá ser explícito ou `auto` conforme o contrato futuro;
- `targetLanguage` inicial será `pt-BR`;
- HTML e quebras de linha terão tratamento explícito no contrato;
- o texto integral não será registrado em logs, métricas ou traces;
- o cliente poderá enviar texto alterado, portanto `externalId` não será usado como garantia de origem;
- retenção, armazenamento e exclusão são regidos pelo [ADR-014](ADR-014-politica-de-retencao-e-exclusao.md);
- a API não fará coleta nem cópia do catálogo AniList.

## Consequências

### Positivas

- contrato de Translation não depende da AniList;
- menor latência e menos chamadas externas no backend;
- funciona com conteúdo vindo de outras fontes;
- cliente e servidor podem evoluir a integração de catálogo separadamente;
- a API recebe exatamente o conteúdo que será normalizado, cacheado e traduzido.

### Negativas

- a API não consegue confirmar que o texto corresponde ao `externalId`;
- clientes podem enviar conteúdo diferente da descrição original;
- o texto recebido pode conter dados indevidos, exigindo limites, minimização e política de retenção;
- alterações da descrição na AniList não serão detectadas automaticamente pelo backend.

## Plano de adoção

1. formalizar o payload no OpenAPI;
2. validar tamanho, idioma, tipo de conteúdo e tratamento de HTML;
3. normalizar o texto antes de calcular a chave de cache;
4. manter a proveniência opcional e não confiável;
5. definir retenção e exclusão no B0.1-T5;
6. testar falha de tradução exibindo o original no cliente.

## Critérios de revisão

Reavaliar se o cliente deixar de consultar a fonte externa, se a consistência centralizada se tornar requisito ou se a política da AniList exigir outro fluxo de integração.

## Referências

- [AniList — campo `Media.description`](https://docs.anilist.co/reference/object/media);
- [AniList — termos de uso da API](https://docs.anilist.co/guide/terms-of-use);
- [AniList — rate limiting](https://docs.anilist.co/guide/rate-limiting).
