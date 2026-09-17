# ADR-012 — Adotar LibreTranslate inicialmente com adapter substituível

> **Estado:** Aprovado
> **Data:** 2026-09-17
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B0.1 / B0.1-T3
> **Decisões relacionadas:** P-003; D-002

## Contexto

O produto traduzirá descrições de animes, inicialmente para `pt-BR`. O backend precisa controlar limites, cache, observabilidade e tratamento de falhas sem expor credenciais de fornecedor ao cliente.

LibreTranslate fornece uma API HTTP self-hosted e utiliza Argos Translate como motor. Argos também pode ser executado como biblioteca ou CLI offline. A solução atende à necessidade de controle de dados e evita acoplar o domínio a um SDK comercial.

Ao mesmo tempo, a qualidade, a latência e o custo operacional podem justificar uma troca futura para Google Cloud Translation, Azure, AWS, DeepL ou outro motor. A escolha do fornecedor não deve atravessar as fronteiras de Translation.

## Drivers da decisão

- preservar a possibilidade de trocar o fornecedor sem alterar o contrato público;
- manter textos no ambiente controlado quando o provedor for self-hosted;
- evitar cobrança por caractere do fornecedor no início;
- permitir benchmark específico de descrições de anime;
- manter cache, quota, limites e observabilidade sob responsabilidade da API;
- evitar runtime Python embutido no processo Kotlin/JVM.

## Opções consideradas

### LibreTranslate self-hosted com Argos

Selecionada como implementação inicial. Oferece API HTTP, execução controlada e modelos locais. O custo de tradução do fornecedor é substituído por custo de infraestrutura, operação e atualização dos modelos. LibreTranslate é distribuído sob AGPL-3.0, exigindo revisão de licença antes da produção.

### Google Cloud Translation Basic

Não selecionada como padrão inicial. Tem operação gerenciada, cobertura ampla e SLA público, mas transfere o processamento para um fornecedor comercial e introduz cobrança por caracteres. Permanece candidato a adapter futuro.

### AWS, Azure ou DeepL

Não selecionados nesta etapa. Podem ser incorporados posteriormente se qualidade, disponibilidade, residência de dados, custo total ou requisitos de negócio justificarem a troca.

### Argos embutido diretamente no backend

Não selecionada. O runtime Python e o ciclo de atualização dos modelos aumentariam o acoplamento e a complexidade do processo Kotlin/JVM. Argos será usado atrás da API LibreTranslate.

### Bergamot no cliente Web

Não selecionada como provedor central. Bergamot é uma biblioteca C++/WASM voltada à tradução client-side no navegador. Poderá ser avaliada no futuro como fallback Web/offline, mas não substitui o fluxo central de cache, quota e observabilidade da API.

## Decisão

O provedor inicial da Translation API será **LibreTranslate self-hosted com Argos Translate**, acessado pelo backend por HTTP.

O backend dependerá somente da porta interna `TranslationProvider`. A implementação concreta ficará em `translation.infrastructure`, com um adapter equivalente a `LibreTranslateProviderAdapter`. O contrato público da API não conterá nomes, DTOs, códigos de erro ou detalhes de configuração do LibreTranslate.

O desenho obrigatório será:

```text
TranslationUseCase
        |
        v
TranslationProvider
        |
        +--> LibreTranslateProviderAdapter
        +--> GoogleCloudProviderAdapter (futuro)
        +--> AzureProviderAdapter (futuro)
        +--> FakeTranslationProvider (testes)
```

Regras de adoção:

- o cliente nunca chama LibreTranslate diretamente;
- a chave ou credencial do fornecedor nunca sai do backend;
- a URL do fornecedor é configuração do ambiente, não entrada do usuário;
- limites de tamanho e idiomas são aplicados antes da chamada externa;
- retries ficam restritos a falhas transitórias e operações idempotentes;
- o adapter converte respostas e falhas externas para tipos internos;
- a chave do cache inclui fornecedor e versão do modelo;
- não haverá fallback automático para outro fornecedor nesta etapa;
- o ambiente local continuará podendo usar `FakeTranslationProvider`;
- staging deverá validar o adapter real com limites baixos;
- produção depende do benchmark, da revisão da licença e da decisão de hospedagem.

## Matriz resumida de decisão

| Critério | LibreTranslate/Argos | Google Cloud | Bergamot |
| --- | --- | --- | --- |
| Papel | API central self-hosted | API central gerenciada | tradução local Web |
| Custo direto | sem cobrança por caractere; há custo de infraestrutura | cobrança por uso | sem fornecedor central; custo no dispositivo |
| Controle de dados | alto quando executado localmente | depende da política/região do fornecedor | processamento no cliente, conforme integração |
| SLA | responsabilidade do MykytaDu | SLA do fornecedor | responsabilidade do cliente Web |
| Integração com Translation API | direta por HTTP | direta por HTTP | inadequada como provider central |
| Decisão | **inicial** | adapter futuro | fallback futuro possível |

## Consequências

### Positivas

- troca futura de fornecedor sem alteração do domínio ou contrato público;
- controle local do conteúdo enviado ao motor;
- custo inicial previsível pela infraestrutura própria;
- testes podem usar fake ou LibreTranslate real;
- fornecedor e modelo ficam registrados no resultado/cache.

### Negativas

- MykytaDu passa a operar disponibilidade, capacidade, atualizações e modelos;
- qualidade de Argos precisa ser comprovada para `en → pt-BR` e `ja → pt-BR`;
- LibreTranslate/Argos adiciona componentes fora do processo Kotlin/JVM;
- AGPL-3.0 exige revisão jurídica e cuidados se houver modificação ou distribuição;
- troca de fornecedor pode exigir invalidação ou coexistência temporária de cache.

## Gate de validação antes da produção

Antes do uso produtivo, executar benchmark com pelo menos 30 descrições autorizadas ou sintéticas, cobrindo `en → pt-BR`, `ja → pt-BR`, tamanhos variados, nomes próprios e HTML. Comparar com pelo menos um provedor gerenciado.

O resultado deve registrar qualidade humana, preservação de estrutura, latência p50/p95, throughput, memória, inicialização, erros e custo total estimado. A decisão poderá ser reaberta se a qualidade ou a operação não atenderem aos limites aprovados.

## Plano de adoção

1. manter a porta `TranslationProvider` independente do fornecedor;
2. criar o adapter HTTP para LibreTranslate;
3. executar o benchmark e registrar os resultados;
4. validar staging com limites baixos e conteúdo permitido;
5. revisar licença AGPL-3.0 e custos de hospedagem;
6. implementar adapter alternativo somente se houver evidência para a troca.

## Critérios de revisão

Reavaliar se o benchmark mostrar qualidade insuficiente, se o custo operacional superar o orçamento, se a disponibilidade não atender ao objetivo, se houver mudança de licença ou se um fornecedor alternativo apresentar benefício comprovado.

## Referências

- [LibreTranslate — documentação](https://docs.libretranslate.com/);
- [Argos Translate — repositório oficial](https://github.com/argosopentech/argos-translate);
- [Bergamot Translator — repositório oficial](https://github.com/browsermt/bergamot-translator).
