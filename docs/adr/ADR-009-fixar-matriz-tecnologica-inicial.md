# ADR-009 — Fixar a matriz tecnológica inicial

> **Estado:** Aprovado
> **Data:** 2026-09-04
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B-1 / B-1-T01
> **Decisões relacionadas:** D-003

## Contexto

O documento mestre definiu Kotlin 2.4.10, Java 25 LTS e Spring Boot 4.1.1, mas o esqueleto do projeto usava Kotlin 2.3.21 e Gradle Wrapper 9.7.1.

Kotlin 2.4.10 é a versão estável da linha 2.4 na data da decisão. Sua matriz oficial declara suporte completo ao Gradle de 7.6.3 a 9.5.0. Embora versões mais recentes do Gradle possam funcionar, elas podem produzir avisos de depreciação ou apresentar recursos ainda não suportados pelo Kotlin Gradle Plugin.

Spring Boot 4.1.1 aceita Gradle 8.14 ou superior na linha 8 e qualquer Gradle 9.x, além de Java entre 17 e 26. Gradle 9.5.0 pode executar sobre Java 25. Spring Modulith 2.1.1 é a linha estável selecionada e será validado pelo build e pelos testes de modularidade da B-1.

## Drivers da decisão

- usar somente releases estáveis;
- permanecer dentro das faixas oficialmente suportadas;
- alinhar build e documentação;
- executar e produzir bytecode com Java 25;
- evitar risco desnecessário antes das funcionalidades de domínio;
- manter builds reproduzíveis e verificar a distribuição do wrapper.

## Opções consideradas

### Manter Kotlin 2.3.21 e Gradle 9.7.1

Não selecionada. O Kotlin 2.3.21 tem suporte completo somente até Gradle 9.3.0, portanto a combinação atual já estava fora da faixa recomendada e divergia do documento mestre.

### Usar Kotlin 2.4.10 e manter Gradle 9.7.1

Não selecionada. Pode funcionar, mas Gradle 9.7.1 excede o máximo 9.5.0 oficialmente suportado pelo Kotlin 2.4.10.

### Usar Kotlin 2.4.10 e Gradle 9.5.0

Selecionada. É a versão mais alta dentro da faixa integralmente suportada pelo Kotlin 2.4.10 e permanece compatível com Java 25 e Spring Boot 4.1.1.

## Decisão

Fixar a matriz inicial em:

| Componente | Versão |
| --- | --- |
| Kotlin e plugins Kotlin | 2.4.10 |
| Java/toolchain | 25 |
| Spring Boot | 4.1.1 |
| Spring Modulith | 2.1.1 |
| Gradle Wrapper | 9.5.0 |

O wrapper deve usar a distribuição binária oficial e validar seu SHA-256. Dependências pertencentes ao ecossistema Spring continuam preferencialmente gerenciadas por seus BOMs.

## Consequências

### Positivas

- build e documentação passam a expressar a mesma matriz;
- Kotlin e Gradle permanecem dentro da faixa integralmente suportada;
- a distribuição Gradle fica protegida por checksum;
- Java 25 permanece como baseline LTS do projeto.

### Negativas

- o wrapper recua de 9.7.1 para 9.5.0;
- recursos exclusivos de versões posteriores do Gradle não estarão disponíveis;
- atualizações devem considerar conjuntamente Kotlin, Gradle e Spring Boot.

### Riscos e mitigações

- plugins adicionais podem ainda apresentar incompatibilidade: validar por resolução, compilação e testes;
- compatibilidade documental não prova comportamento: manter gates automatizados;
- uma correção importante do Gradle pode exigir versão posterior: reavaliar por novo ADR ou substituição deste.

## Evidências

- documentação oficial do Kotlin: Kotlin 2.4.10 suporta integralmente Gradle 7.6.3–9.5.0;
- documentação oficial do Spring Boot 4.1.1: Gradle 8.14+ ou 9.x e Java até 26;
- matriz oficial do Gradle: Java 25 é suportado para execução desde Gradle 9.1;
- checksum oficial do `gradle-9.5.0-bin.zip`: `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`;
- wrapper regenerado com JAR cujo SHA-256 é `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`;
- `compileKotlin` e `compileTestKotlin` concluíram com sucesso e produziram bytecode Java 25 (`major version 69`);
- plugins Kotlin 2.4.10, Spring Boot 4.1.1 e Spring Modulith 2.1.1 foram resolvidos pelo Gradle;
- o teste de contexto existente falhou por ausência de datasource, lacuna já atribuída a B-1-T06/T07 e não à matriz tecnológica;
- resultados detalhados estão registrados em `docs/sprints/B-1.md`.

## Plano de adoção

1. atualizar todos os plugins Kotlin para 2.4.10;
2. atualizar o wrapper e seu checksum para Gradle 9.5.0;
3. confirmar Java 25 no launcher, daemon e toolchain;
4. resolver dependências sem conflitos fatais;
5. compilar e executar testes;
6. registrar resultados na Sprint B-1.

## Critérios de revisão

Reavaliar quando:

- Kotlin ampliar oficialmente sua faixa de suporte a Gradle;
- uma correção de segurança exigir atualização de componente;
- Spring Boot ou Spring Modulith mudar sua matriz suportada;
- o projeto adotar uma nova linha de linguagem ou de JVM;
- uma limitação concreta do Gradle 9.5.0 bloquear o desenvolvimento.
