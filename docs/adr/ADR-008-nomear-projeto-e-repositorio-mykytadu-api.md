# ADR-008 — Nomear o projeto e repositório como `mykytadu-api`

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** fundação do projeto
> **Decisões relacionadas:** D-008

## Contexto

O backend reúne API, Identity e Translation. O nome precisa identificar a interface oferecida ao cliente sem limitar o projeto a um único módulo ou a uma futura topologia de implantação.

## Drivers da decisão

- identificação clara do backend;
- alinhamento entre projeto, repositório e documentação;
- nome estável diante da evolução modular;
- distinção do aplicativo cliente `mykytadu-app`.

## Opções consideradas

### `mykytadu-api`

Selecionada. É direta, representa a interface estável e não impõe uma implementação interna específica.

### `mykytadu-bff`

Não selecionada. Restringiria o significado do backend a BFF, embora Identity e Translation também sejam capacidades próprias.

### Nome baseado em um módulo, como `mykytadu-identity`

Não selecionada. O repositório abriga mais de um domínio e pode receber novas capacidades.

## Decisão

Usar `mykytadu-api` como nome do projeto e repositório. O produto pode ser chamado de MykytaDu API, enquanto os módulos internos permanecem nomeados por capacidade.

## Consequências

### Positivas

- nome consistente em código, documentação e operação;
- separação clara em relação ao cliente `mykytadu-app`;
- não impede monólito modular nem futura extração.

### Negativas

- o nome não comunica cada domínio interno;
- eventual extração de módulo exigirá nomes próprios sem alterar o histórico deste repositório.

## Plano de adoção

1. manter `mykytadu-api` em documentação e automações;
2. usar nomes de módulo para responsabilidades internas;
3. revisar referências antes de qualquer renomeação futura.

## Critérios de revisão

Reavaliar somente se o escopo do repositório deixar de representar uma API backend ou se houver decisão explícita de separar o produto em repositórios independentes.
