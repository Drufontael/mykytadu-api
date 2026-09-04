# MykytaDu API — Estratégia de testes

> **Status:** vigente
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Convenções principais

- JUnit 5 organiza e executa os testes;
- AssertJ é a única API principal de assertions;
- MockK cria mocks, stubs, spies e verificações quando um double é necessário;
- Testcontainers fornece dependências de infraestrutura reais e descartáveis;
- testes do Spring Modulith verificam fronteiras arquiteturais.

Não adicionar Kotest Assertions, Hamcrest ou outra biblioteca geral de assertions sem uma necessidade demonstrada e uma revisão desta decisão. Assertions especializadas fornecidas por frameworks podem ser usadas quando expressarem melhor um contrato específico.

## 2. Uso de doubles

Preferir objetos reais simples e fakes determinísticos. Usar MockK nas fronteiras que seriam lentas, não determinísticas ou externas ao comportamento testado, como relógio, gerador de identificador ou provider remoto.

Regras:

- não mockar value objects, entidades ou coleções;
- não mockar o PostgreSQL: testes de persistência usam Testcontainers;
- verificar apenas interações que façam parte do comportamento observável;
- evitar `relaxed = true`, pois respostas implícitas podem ocultar chamadas inesperadas;
- não mockar métodos privados nem detalhes internos da classe testada;
- limpar estado global caso mocking estático seja excepcionalmente necessário.

## 3. Organização

| Categoria | Convenção atual | Dependências externas |
| --- | --- | --- |
| Unitário | classe com sufixo `Test` junto ao pacote da unidade | nenhuma; doubles controlados quando necessários |
| Arquitetural | pacote `architecture` | classes compiladas e metadados dos módulos |
| Smoke de contexto | `@SpringBootTest` com profile `test` | nenhuma infraestrutura manual |
| Integração | pacote `integration`, sufixo `IntegrationTests` e profile `integration-test` | containers efêmeros gerenciados pelo teste |

Executar toda a suíte:

```powershell
.\gradlew.bat test
```

Executar uma categoria ou classe pelo filtro do Gradle:

```powershell
.\gradlew.bat test --tests "br.com.mykytadu.architecture.*"
.\gradlew.bat test --tests "br.com.mykytadu.integration.*"
```

## 4. Versionamento

- AssertJ acompanha o BOM do Spring Boot;
- MockK possui versão explícita porque não é gerenciado pelo BOM;
- upgrades precisam compilar e executar a suíte no Java 25;
- não atualizar bibliotecas de teste apenas por novidade: considerar compatibilidade, correções e manutenção.
