# ADR-015 — Render como hospedagem-alvo da API

> **Estado:** Aprovado
> **Data:** 2026-09-17
> **Responsáveis:** equipe MykytaDu API
> **Sprint/tarefas:** B0.1 / B0.1-T6
> **Decisões relacionadas:** P-008, [ADR-004](ADR-004-usar-postgresql-como-persistencia-inicial.md), [ADR-012](ADR-012-libretranslate-com-adapter-substituivel.md), [ADR-014](ADR-014-politica-de-retencao-e-exclusao.md)

## Contexto

O backend é um monólito modular Kotlin/JVM com Spring Boot, PostgreSQL, sessões duráveis e um adapter HTTP para LibreTranslate. A hospedagem precisa executar uma imagem Docker, manter a API stateless, oferecer TLS, rede privada e separar staging de produção sem introduzir Kubernetes ou outros componentes prematuros.

Neste momento não há orçamento para contratar infraestrutura remota. A decisão deve definir o alvo futuro sem iniciar recursos pagos; desenvolvimento e testes continuam locais.

## Drivers da decisão

- compatibilidade com Docker e aplicações JVM de longa duração;
- PostgreSQL gerenciado na mesma região da API;
- serviços privados para o provedor de tradução;
- isolamento de staging e produção;
- configuração de secrets sem versioná-los;
- baixo esforço operacional inicial e migração futura reversível;
- custo previsível e ativação somente quando houver orçamento.

## Opções consideradas

### Render

Selecionada como hospedagem-alvo da API. Oferece serviços Docker, PostgreSQL, serviços privados, rede privada e projetos/ambientes separados. A API, o banco e o LibreTranslate poderão ficar na mesma região e se comunicar por rede interna.

### Railway

Mantida como alternativa futura. Tem ambientes isolados e rede privada, mas a opção padrão de PostgreSQL exige mais responsabilidade operacional para backup e manutenção neste estágio.

### VPS ou Kubernetes

Não selecionadas para o MVP. Uma VPS transfere segurança, atualização, backup e observabilidade para a equipe; Kubernetes adicionaria complexidade sem evidência de escala ou disponibilidade que a justifique.

## Decisão

O Render será a hospedagem-alvo da API, com a seguinte topologia quando o orçamento for aprovado:

- API como serviço Web baseado na imagem Docker do backend;
- PostgreSQL gerenciado separado, na mesma região da API;
- LibreTranslate/Argos como serviço privado, sem exposição pública;
- staging e produção em ambientes separados, com banco, secrets e rede isolados;
- TLS e domínio customizado para a API;
- Web e API continuarão no mesmo site por domínio-base, mas não precisam estar no mesmo provedor;
- origem Web e origem da API serão definidas por ambiente antes da configuração CORS remota.

O alvo de produção permanece compatível com a política do [ADR-014](ADR-014-politica-de-retencao-e-exclusao.md): backups criptografados com ciclo de vida máximo de 30 dias. O PITR nativo disponível no Render não substitui automaticamente esse requisito; se a janela nativa for menor, será necessário backup lógico externo em armazenamento de objetos com expiração controlada.

### Configuração e secrets

No primeiro deploy, variáveis protegidas, grupos de ambiente e secret files do Render serão usados para injetar configuração por ambiente. Nenhum valor secreto será colocado no repositório, no `render.yaml`, na imagem Docker ou nos logs. Um secret manager externo será reavaliado antes de produção caso a rotação, auditoria ou segregação exigidas ultrapassem os recursos do Render.

### Estado de ativação

Nenhum serviço pago será provisionado agora. Até a liberação do orçamento:

- `local` usa Docker Compose, PostgreSQL local e provider fake/sandbox;
- `test` usa Testcontainers e adapters controlados;
- staging e produção permanecem planejados, não ativos;
- a criação dos recursos dependerá de orçamento, domínio, credenciais e teste de restauração aprovados.

## Consequências

### Positivas

- preserva a arquitetura Kotlin/Spring Boot existente;
- reduz o trabalho de operação inicial;
- mantém o LibreTranslate fora da internet pública;
- permite iniciar com uma instância da API e evoluir posteriormente;
- não cria custo enquanto o projeto permanecer local.

### Negativas

- a região disponível pode ficar distante do usuário brasileiro;
- produção dependerá de serviço pago e de armazenamento externo para cumprir a retenção de backups;
- Web e API em provedores distintos podem exigir configuração adicional de DNS, CORS e observabilidade;
- alguns recursos de isolamento e ambientes dependem do plano contratado.

### Riscos e mitigações

- **Custo exceder o orçamento:** configurar alertas e limites antes do primeiro deploy; não habilitar autoscaling sem medição.
- **Backup insuficiente:** validar a janela contratada e executar restore em ambiente isolado antes da produção.
- **Staging acessar produção:** usar ambientes, bancos, grupos de secrets e credenciais distintos; bloquear tráfego entre ambientes quando disponível.
- **Dependência do fornecedor:** manter Docker, PostgreSQL e configuração externa portáveis; não depender de APIs proprietárias no domínio.

## Evidências

- O Render documenta suporte a [Docker para serviços](https://render.com/docs/docker), [serviços privados e rede interna](https://render.com/docs/private-services) e [PostgreSQL na mesma região](https://render.com/docs/postgresql-creating-connecting).
- [Projetos e ambientes do Render](https://render.com/docs/projects) permitem separar staging e produção, inclusive com variáveis, secrets e controles de rede por ambiente.
- [Variáveis e secrets do Render](https://render.com/docs/configure-environment-variables) permitem configuração externa ao código, sem inserir credenciais no repositório.
- A política de retenção de backups está definida no [ADR-014](ADR-014-politica-de-retencao-e-exclusao.md).

## Plano de adoção

1. continuar o desenvolvimento local sem provisionar recursos pagos;
2. produzir a imagem Docker e validar health checks no CI;
3. definir domínio, região e orçamento antes de abrir o ambiente remoto;
4. criar ambientes separados e PostgreSQL gerenciado;
5. configurar secrets, CORS, TLS, migrations e logs sem dados sensíveis;
6. configurar o LibreTranslate como serviço privado;
7. configurar backup lógico externo e executar restore de validação;
8. publicar staging antes de qualquer exposição produtiva.

## Critérios de revisão

Reavaliar o fornecedor se a região, o custo, a residência de dados, a janela de backup, o suporte a Docker/JVM ou os requisitos de disponibilidade deixarem de atender ao projeto. A ausência temporária de orçamento não é motivo para trocar a decisão; apenas impede sua ativação.
