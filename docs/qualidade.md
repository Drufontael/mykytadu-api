# MykytaDu API — Qualidade estática e cobertura

> **Status:** vigente
> **Versão:** 0.1
> **Data de referência:** 4 de setembro de 2026

## 1. Gates

| Ferramenta | Responsabilidade | Comando principal | Saída |
| --- | --- | --- | --- |
| ktlint | estilo e formatação Kotlin/Kotlin DSL | `.\gradlew.bat ktlintCheck` | `build/reports/ktlint/` |
| Detekt | smells, complexidade e problemas estruturais | `.\gradlew.bat detekt` | `build/reports/detekt/` |
| Kover | cobertura dos testes JVM | `.\gradlew.bat koverXmlReport` | `build/reports/kover/report.xml` |

O lifecycle `.\gradlew.bat check` executa os testes, ktlint, Detekt e gera o XML do Kover. Como a suíte inclui Testcontainers, esse comando requer Docker disponível.

O pipeline executa o lifecycle equivalente em Linux, `./gradlew check --no-daemon --stacktrace`, conforme a [documentação de integração contínua](ci.md). Não existe uma sequência alternativa ou um gate reduzido exclusivo do CI.

## 2. Política

- violações de ktlint e Detekt falham o build;
- não manter baseline enquanto o volume de código permitir corrigir todos os achados;
- supressões devem ser locais, específicas e justificadas em comentário;
- `.editorconfig` é a fonte de formatação compartilhada entre Gradle e IDE;
- o arquivo `config/detekt/detekt.yml` contém apenas decisões diferentes do padrão;
- relatórios em `build/` são artefatos locais/CI e não são versionados.

## 3. Cobertura orientada a risco

Não existe percentual mínimo global nesta fase. Uma meta única incentivaria testes triviais em configuração e não demonstraria proteção das regras importantes.

À medida que os módulos evoluírem, critérios de cobertura poderão ser definidos por pacote ou classe para:

- regras de domínio;
- autenticação, autorização e rotação de tokens;
- cache, limites e normalização de tradução;
- adapters externos e tratamento de falhas;
- correções de regressões relevantes.

Toda regra do Kover deverá indicar qual risco protege. Quedas de cobertura são analisadas no diff e não aprovadas apenas porque a média global permaneceu alta.

## 4. Manutenção

Formatar automaticamente antes de revisar mudanças:

```powershell
.\gradlew.bat ktlintFormat
```

O formatador pode alterar vários arquivos; revisar o diff antes do commit. Detekt não deve ser executado com autocorreção sem revisão equivalente.
