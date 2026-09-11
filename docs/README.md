# Documentação — Cartola FC Odds API

| Arquivo | O que contém |
|---|---|
| [`arquitetura.md`](arquitetura.md) | Stack, APIs externas consumidas, camadas, estrutura do projeto, fluxo de execução e os pontos de entrada de cada serviço |
| [`api.md`](api.md) | Mapa de rotas, códigos de resposta, parâmetros, Swagger e a referência de dados do domínio |
| [`regras-de-negocio.md`](regras-de-negocio.md) | Favoritos, filtros, score, formação, orçamento, dúvidas e histórico de escalações |
| [`seguranca.md`](seguranca.md) | Autenticação JWT, matriz de acesso por rota, hardening do perfil `prod` e gestão de usuários |
| [`configuracao.md`](configuracao.md) | `application.properties` e todas as variáveis de ambiente |
| [`banco-de-dados.md`](banco-de-dados.md) | Migrations Flyway e os parâmetros de negócio da tabela `configuracao` |
| [`operacao.md`](operacao.md) | Cache, guardrail de cota da The Odds API, Actuator, métricas, dashboard e alertas |
| [`deploy.md`](deploy.md) | Docker, Compose, healthcheck e limites de recurso |
| [`desenvolvimento.md`](desenvolvimento.md) | Como rodar os testes e o que cada classe cobre |
| [`context.md`](context.md) | Decisões de arquitetura, o porquê de cada uma e as pendências conhecidas |
| [`observabilidade/`](observabilidade/) | Dashboard do Grafana e regras de alerta da cota, prontos para importar num Prometheus/Grafana existente |

Para instalar e rodar o projeto, veja o [README](../README.md). O contrato REST completo (schemas de
request e response) é gerado pelo springdoc e fica no
[Swagger UI](http://localhost:8080/swagger-ui.html) com a aplicação de pé — **exceto no perfil
`prod`**, onde o springdoc é desligado e essas rotas respondem `404`
([perfil de produção](seguranca.md#perfil-de-produção)). Nesse cenário, [`api.md`](api.md) é a
referência.

## Onde documentar cada mudança

O `README.md` já teve 1.259 linhas porque toda feature nova acrescentava um bloco a ele, e o mesmo
assunto acabava descrito em três arquivos ao mesmo tempo ([#59](https://github.com/FabioCarlesso/cartolaoddsapi/issues/59)).
Para não repetir isso, cada tipo de informação tem um dono:

| Se você mudou… | Documente em |
|---|---|
| Uma rota (nova, removida, com parâmetro ou contrato diferente) | [`api.md`](api.md) |
| Uma regra de negócio: score, formação, filtros, orçamento, histórico | [`regras-de-negocio.md`](regras-de-negocio.md) |
| Quem pode chamar o quê, ou o hardening do perfil `prod` | [`seguranca.md`](seguranca.md) |
| Uma variável de ambiente ou propriedade | [`configuracao.md`](configuracao.md) |
| Um parâmetro de negócio do banco (`PATCH /api/config`) ou uma migration Flyway | [`banco-de-dados.md`](banco-de-dados.md) |
| O guardrail de cota, a sondagem, o snapshot de odds ou um cache | [`operacao.md`](operacao.md) |
| Actuator, métrica, dashboard ou regra de alerta | [`operacao.md`](operacao.md) e `observabilidade/` |
| Uma camada, pacote, o fluxo do pipeline ou o tratamento de erros | [`arquitetura.md`](arquitetura.md) |
| Uma classe de teste ou a contagem de cenários | [`desenvolvimento.md`](desenvolvimento.md) |
| Docker, Compose, healthcheck ou resource limits | [`deploy.md`](deploy.md) |
| **Uma decisão técnica e o porquê dela** | [`context.md`](context.md) |
| **Como instalar ou rodar o projeto** | [`README.md`](../README.md) — e só nesse caso |

Regra prática: se a informação não ajuda alguém a colocar o projeto no ar nos primeiros cinco
minutos, ela não pertence ao `README.md` da raiz.

Um tema tem **um** dono. Quando ele aparece em dois lugares, o segundo vira um link para o primeiro
— nunca uma segunda cópia do texto. É assim que a referência e o `context.md` se dividem: a regra
fica no arquivo de referência, o motivo dela fica no context, e cada um linka para o outro.

Arquivo novo em `docs/` só quando um assunto não couber em nenhum dos existentes — e, quando
nascer, ele entra nas duas tabelas acima no mesmo commit.

## O que não se documenta à mão

Estas quatro coisas nascem desatualizadas e foram removidas de propósito. O repositório, o OpenAPI
ou o próprio build já as respondem, e com a vantagem de nunca discordarem do código:

| Não mantenha aqui | Onde a resposta está |
|---|---|
| Árvore de arquivos comentada classe a classe | `find src/main/java -name '*.java'`, ou a árvore do GitHub. A referência mantém o **critério** de cada camada ([`arquitetura.md` › Estrutura do Projeto](arquitetura.md#estrutura-do-projeto)), não a lista |
| Corpo de request/response por rota | Swagger UI / `/v3/api-docs` (fora de `prod`) |
| Catálogo de exemplos `curl` por endpoint | A tabela de rotas em [`api.md` › Tabela completa](api.md#tabela-completa) e o Swagger UI |
| Listagem de todos os campos de um DTO | Os `@Schema` do springdoc |

**As exceções, e o critério delas.** Um exemplo entra quando mostra **comportamento que o schema não
expressa** — um campo que só aparece em certo estado, um valor que muda de significado, uma sequência
em que a ordem importa. Sai quando só repete o schema. Pelo mesmo critério, o que ficou:

| O que ficou | Por quê |
|---|---|
| `GET /api/odds/cota/historico` ([`api.md` › Exemplos de resposta](api.md#exemplos-de-resposta)) | Mostra o `reinicioDeCota` virando `true` numa leitura específica — o schema diz que o campo é booleano, não quando ele muda |
| `GET /api/time` com orçamento insuficiente ([`api.md` › Parâmetros de `GET /api/time`](api.md#parâmetros-de-get-apitime)) | É o caso degradado: `formacaoCompleta: false` com `avisoOrcamento` preenchido |
| `ErrorResponse` de `403` ([`seguranca.md` › Erros de autorização (401 e 403)](seguranca.md#erros-de-autorização-401-e-403)) | `401` e `403` nascem no filter chain, antes do MVC — o springdoc não os documenta por rota |
| Envelope `PaginaResponse` ([`seguranca.md` › Formato da paginação](seguranca.md#formato-da-paginação)) | É o contrato que os **próximos** endpoints paginados devem reusar |
| Fluxo de gestão de usuários ([`seguranca.md` › Fluxo típico](seguranca.md#fluxo-típico)) | Runbook: a sequência autenticar → criar → listar → desativar é a resposta a "como libero acesso a alguém" |
| Retorno da The Odds API ([`arquitetura.md` › The Odds API](arquitetura.md#the-odds-api)) | API de terceiro — não está no nosso OpenAPI |
| `{"status":"UP"}` do Actuator ([`operacao.md` › Endpoints do Actuator](operacao.md#endpoints-do-actuator)) | O ponto é a **diferença** entre o corpo anônimo e o de `ADMIN` |
| Os 7 campos de `GET /api/odds/cota` ([`operacao.md` › Cota da The Odds API: guardrail e sondagem](operacao.md#cota-da-the-odds-api-guardrail-e-sondagem)) | É o endpoint que se lê **em produção** quando o guardrail arma — e em `prod` o springdoc responde `404` |

## Tamanho dos arquivos

Nenhum arquivo aqui passa de ~370 linhas. O `documentacao.md` que existiu entre os dois primeiros
commits desta série chegou a 2.086 linhas e 108 KB — reproduzia, em escala menor, o problema que a
[#59](https://github.com/FabioCarlesso/cartolaoddsapi/issues/59) atacou: grande demais para ler
inteiro, e uma mudança de rota disputando o mesmo arquivo com uma de deploy. Quando um destes
arquivos começar a crescer sem parar, o sinal é o mesmo e a saída também: fatiar por assunto e
atualizar as duas tabelas acima.

A exceção é o [`context.md`](context.md), hoje o maior arquivo de `docs/`. Ele não é fatiado de
propósito: é um registro **narrativo** de decisões, em que uma escolha explica a seguinte — o
guardrail de cota explica o histórico de leituras, que explica o dashboard. Cortá-lo por tema
espalharia esse encadeamento por vários arquivos e destruiria justamente o que a
[#59](https://github.com/FabioCarlesso/cartolaoddsapi/issues/59) apontou como o material mais
valioso e o menos duplicável do repositório. Ele se lê por seção, pelo índice do GitHub, não de
ponta a ponta.

## Formato Word

O repositório mantinha um `docs/documentacao.docx`, gerado a partir de uma versão antiga da
referência. Ele foi removido: era um binário que não aparece no diff de um PR, tinha duas
alterações contra dezenas dos arquivos `.md` e, na prática, descrevia uma versão do projeto
anterior à autenticação. Um documento que ninguém consegue revisar e que discorda da fonte é pior
do que documento nenhum.

Quem precisar de uma versão Word gera na hora, a partir dos arquivos atuais:

```bash
pandoc docs/arquitetura.md docs/api.md docs/regras-de-negocio.md docs/seguranca.md \
       docs/configuracao.md docs/banco-de-dados.md docs/operacao.md docs/deploy.md \
       docs/desenvolvimento.md -o documentacao.docx
```

## Referências externas

| Recurso | Link |
|---|---|
| The Odds API | https://the-odds-api.com/liveapi/guides/v4/ |
| Cartola FC API (não-oficial) | https://github.com/henriquemiranda/cartola-api |
| Spring Boot 3.4.x | https://docs.spring.io/spring-boot/docs/3.4.x/reference/html/ |
| springdoc OpenAPI | https://springdoc.org/ |
| Lombok | https://projectlombok.org/features/ |
| AssertJ | https://assertj.github.io/doc/ |
| Mockito | https://javadoc.io/doc/org.mockito/mockito-core/latest/ |

---
