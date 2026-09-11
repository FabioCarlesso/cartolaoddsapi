# Documentação — Cartola FC Odds API

| Arquivo | O que contém |
|---|---|
| [`documentacao.md`](documentacao.md) | Referência completa: funcionalidades, configuração, endpoints, regras de negócio, estrutura, testes, Docker e observabilidade |
| [`context.md`](context.md) | Decisões de arquitetura e o porquê de cada uma |
| [`observabilidade/`](observabilidade/) | Dashboard do Grafana e regras de alerta da cota, prontos para importar num Prometheus/Grafana existente |

Para instalar e rodar o projeto, veja o [README](../README.md). O contrato REST completo (schemas de
request e response) é gerado pelo springdoc e fica no
[Swagger UI](http://localhost:8080/swagger-ui.html) com a aplicação de pé — **exceto no perfil
`prod`**, onde o springdoc é desligado e essas rotas respondem `404`
([5.5](documentacao.md#55-perfil-de-produção)). Nesse cenário, `documentacao.md` é a referência.

## Onde documentar cada mudança

O `README.md` já teve 1.259 linhas porque toda feature nova acrescentava um bloco a ele, e o mesmo
assunto acabava descrito em três arquivos ao mesmo tempo ([#59](https://github.com/FabioCarlesso/cartolaoddsapi/issues/59)).
Para não repetir isso, cada tipo de informação tem um dono:

| Se você mudou… | Documente em |
|---|---|
| Uma rota (nova, removida, com parâmetro ou contrato diferente) | [`documentacao.md` § 8](documentacao.md#8-endpoints) |
| Uma regra de negócio: score, formação, filtros, orçamento, histórico | [`documentacao.md` § 9](documentacao.md#9-regras-de-negócio) |
| Quem pode chamar o quê, ou o hardening do perfil `prod` | [`documentacao.md` § 5](documentacao.md#5-autenticação-e-política-de-acesso) |
| Uma variável de ambiente ou propriedade | [`documentacao.md` § 4.1 e 4.2](documentacao.md#4-configuração) |
| Um parâmetro de negócio do banco (`PATCH /api/config`) | [`documentacao.md` § 4.3](documentacao.md#43-parâmetros-de-negócio-via-banco-de-dados) |
| Uma migration Flyway | [`documentacao.md` § 4.3](documentacao.md#43-parâmetros-de-negócio-via-banco-de-dados) |
| O guardrail de cota, a sondagem ou o snapshot de odds | [`documentacao.md` § 4.4](documentacao.md#44-cota-da-the-odds-api-guardrail-e-sondagem) |
| Um cache, TTL ou invalidação | [`documentacao.md` § 7](documentacao.md#7-cache-caffeine) |
| Uma camada, pacote ou o tratamento de erros | [`documentacao.md` § 11](documentacao.md#11-estrutura-do-projeto) |
| Uma classe de teste ou a contagem de cenários | [`documentacao.md` § 14](documentacao.md#14-testes) |
| Docker, Compose, healthcheck ou resource limits | [`documentacao.md` § 16](documentacao.md#16-docker) |
| Actuator, métrica, dashboard ou regra de alerta | [`documentacao.md` § 17](documentacao.md#17-observabilidade) e `observabilidade/` |
| **Uma decisão técnica e o porquê dela** | [`context.md`](context.md) |
| **Como instalar ou rodar o projeto** | [`README.md`](../README.md) — e só nesse caso |

Regra prática: se a informação não ajuda alguém a colocar o projeto no ar nos primeiros cinco
minutos, ela não pertence ao `README.md` da raiz.

Um tema tem **um** dono. Quando ele aparece em dois lugares, o segundo vira um link para o primeiro
— nunca uma segunda cópia do texto. É como `documentacao.md` e `context.md` se dividem hoje: a
regra fica na referência, o motivo dela fica no context, e cada um linka para o outro.

## O que não se documenta à mão

Estas quatro coisas nascem desatualizadas e foram removidas de propósito. O repositório, o OpenAPI
ou o próprio build já as respondem, e com a vantagem de nunca discordarem do código:

| Não mantenha aqui | Onde a resposta está |
|---|---|
| Árvore de arquivos comentada classe a classe | `find src/main/java -name '*.java'`, ou a árvore do GitHub. A referência mantém o **critério** de cada camada ([§ 11](documentacao.md#11-estrutura-do-projeto)), não a lista |
| Corpo de request/response por rota | Swagger UI / `/v3/api-docs` (fora de `prod`) |
| Catálogo de exemplos `curl` por endpoint | A tabela de rotas em [§ 8.1](documentacao.md#81-tabela-completa) e o Swagger UI |
| Listagem de todos os campos de um DTO | Os `@Schema` do springdoc |

**As exceções, e o critério delas.** Um exemplo entra quando mostra **comportamento que o schema não
expressa** — um campo que só aparece em certo estado, um valor que muda de significado, uma sequência
em que a ordem importa. Sai quando só repete o schema. Pelo mesmo critério, o que ficou:

| O que ficou | Por quê |
|---|---|
| `GET /api/odds/cota/historico` ([8.4](documentacao.md#84-exemplos-de-resposta)) | Mostra o `reinicioDeCota` virando `true` numa leitura específica — o schema diz que o campo é booleano, não quando ele muda |
| `GET /api/time` com orçamento insuficiente ([8.5](documentacao.md#85-parâmetros-de-get-apitime)) | É o caso degradado: `formacaoCompleta: false` com `avisoOrcamento` preenchido |
| `ErrorResponse` de `403` ([5.3](documentacao.md#53-erros-de-autorização-401-e-403)) | `401` e `403` nascem no filter chain, antes do MVC — o springdoc não os documenta por rota |
| Envelope `PaginaResponse` ([6.3](documentacao.md#63-formato-da-paginação)) | É o contrato que os **próximos** endpoints paginados devem reusar |
| Fluxo de gestão de usuários ([6.1](documentacao.md#61-fluxo-típico)) | Runbook: a sequência autenticar → criar → listar → desativar é a resposta a "como libero acesso a alguém" |
| Retorno da The Odds API ([3.1](documentacao.md#31-the-odds-api)) | API de terceiro — não está no nosso OpenAPI |
| `{"status":"UP"}` do Actuator ([17.1](documentacao.md#171-endpoints-do-actuator)) | O ponto é a **diferença** entre o corpo anônimo e o de `ADMIN` |
| Os 7 campos de `GET /api/odds/cota` ([4.4](documentacao.md#44-cota-da-the-odds-api-guardrail-e-sondagem)) | É o endpoint que se lê **em produção** quando o guardrail arma — e em `prod` o springdoc responde `404` |

## Próximo passo conhecido

`documentacao.md` é um arquivo único de ~19 seções. Quando ele voltar a ficar grande demais para
ler, o caminho é fatiá-lo por tema — `api.md`, `regras-de-negocio.md`, `deploy.md`, `operacao.md`,
`banco-de-dados.md` — mantendo este arquivo como índice e a tabela de roteamento acima apontando
para os novos arquivos em vez de para seções.
