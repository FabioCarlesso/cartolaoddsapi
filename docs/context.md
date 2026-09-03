# Context — Cartola Odds API

## Visão Geral

API REST em **Java 21 + Spring Boot 3.4.5** que monta automaticamente um time competitivo para o **Cartola FC** cruzando odds do Brasileirão (via [The Odds API](https://the-odds-api.com)) com métricas dos atletas da plataforma Cartola.

---

## Problema que Resolve

Montar um time no Cartola FC exige combinar dois tipos de dados dispersos:
1. **Quais times têm mais chance de vencer?** — respondido pelas odds de mercado.
2. **Quais jogadores desses times estão em melhor forma e são viáveis pelo preço?** — respondido pelos dados da API do Cartola FC.

Esta API cruza essas duas fontes e entrega diretamente os melhores atletas disponíveis, já escalados em formação configurável (padrão **4-3-3**).

---

## APIs Externas Consumidas

| API | Base URL | Uso |
|---|---|---|
| The Odds API | `https://api.the-odds-api.com` | Odds do Brasileirão para identificar times favoritos |
| Cartola FC API | `https://api.cartola.globo.com` | Atletas, clubes, mercado, partidas, pontuações por rodada |

Endpoints Cartola utilizados: `/mercado/status`, `/atletas/mercado`, `/clubes`, `/partidas`, `/atletas/pontuados`.

---

## Decisões Arquiteturais

### Autenticação por JWT com `tokenVersion`

A API é fechada por JWT porque cada consulta fora do cache gasta cota paga da The Odds API —
publicá-la aberta seria entregar essa cota a quem descobrisse o endereço.

A escolha por token stateless (em vez de sessão no servidor) traz o problema de revogação: um
token válido continua valendo até expirar. A resposta é o campo `token_version` no usuário,
copiado como claim na emissão e comparado com o banco a cada requisição. Trocar a senha,
desativar o usuário ou rebaixar seu perfil incrementa o contador e derruba todos os tokens
anteriores, sem estado de sessão — ao custo de uma consulta ao usuário por requisição, aceitável no volume deste projeto.

O administrador inicial nasce de variáveis de ambiente no primeiro boot, nunca de senha em
migration. Sem `APP_ADMIN_INICIAL_SENHA` e sem nenhum ADMIN ativo no banco, a aplicação recusa
subir em qualquer perfil — antes mesmo de abrir a porta. A alternativa, avisar em log e subir
assim mesmo, produzia uma API no ar que ninguém conseguia autenticar, com o aviso passando
despercebido. A exigência cai quando já existe um ADMIN ativo, para que produção não precise
manter para sempre a senha do primeiro acesso depois de trocada.

O login tem freio de força bruta por e-mail (`LoginThrottle`), e não por IP: atrás do nginx e da
borda da plataforma, `getRemoteAddr()` devolve o endereço do proxy, igual para todo mundo, e ler
`X-Forwarded-For` com segurança depende de configuração de ambiente que só chega com o deploy. O
e-mail descreve exatamente o alvo — adivinhar a senha do administrador é martelar sempre o mesmo
endereço.

### Política de acesso por rota e hardening do perfil `prod`

Autenticar diz *quem* está chamando; a matriz de acesso do `SecurityConfig` diz *o que cada um pode
fazer*. Três rotas justificam a distinção entre `USER` e `ADMIN`: `PATCH /api/config` e
`POST /api/config/reset` mudam pesos do score, formação e `odd_limite` da instância inteira, e
`DELETE /api/cache` força chamadas novas à The Odds API, cuja cota mensal é paga. Deixar essas
três ao alcance de qualquer usuário logado é entregar o botão de gastar dinheiro e o de reconfigurar
a aplicação a quem só deveria consultar.

Quem decide é o primeiro matcher que casa, então a ordem importa: `/api/usuarios/me` antes de
`/api/usuarios/**`, e as regras de `ADMIN` antes do `anyRequest().authenticated()` final. Um matcher
por método cobre só aquele método — `HEAD` não herda a autorização de `GET` —, e por isso as regras
de `ADMIN` citam apenas os verbos que escrevem: leitura e `HEAD` caem na regra final, que é fechada.
A regra final ser `authenticated()` é o que faz uma rota nova nascer fechada em vez de aberta.

O Actuator saiu da porta separada e foi para a porta única da aplicação. O `bind` em `127.0.0.1`
protegia por acidente de topologia, não por regra, e some numa plataforma que publica uma porta só;
`health` e `info` ficam públicos para o healthcheck da plataforma consultar sem token, e `metrics` e
`prometheus` passam a exigir `ADMIN`. O `show-details=when_authorized` é o que mantém o `health`
público sem vazar estado de banco e dependências.

No perfil `prod` o springdoc é desligado por completo, e a diferença entre desligar e proteger é
proposital: com ele desligado as rotas não existem e respondem `404`. Um `401` confirmaria que a
documentação está lá, atrás de uma senha; o `404` não confirma nada. O contrato completo da API é o
mapa que um atacante levaria tempo montando na mão, e o *Try it out* do Swagger UI deixa disparar as
chamadas dali mesmo.

O HSTS sai condicionado à requisição ter chegado por TLS. Atrás da borda da plataforma o TLS termina
no proxy e o Tomcat veria HTTP puro, então o cabeçalho nunca apareceria em produção; quem resolve é
`server.forward-headers-strategy=framework`, que põe o `ForwardedHeaderFilter` na frente da cadeia
para normalizar esquema, host e porta a partir dos `X-Forwarded-*`. Ler o `X-Forwarded-Proto` na mão
dentro do `SecurityConfig` também funcionaria, mas deixaria a aplicação confiando em header de
cliente em dois lugares com regras diferentes — o `LoginThrottle` recusa o `X-Forwarded-For` por não
saber quantos saltos confiar. Concentrar no filtro do framework deixa uma decisão só. A condição
evita o outro extremo: mandar HSTS em `http://localhost` trava o navegador do desenvolvedor em HTTPS
para todo o host por um ano.

### Gestão de usuários pela API, restrita a administradores

Com a API fechada por JWT, o único usuário de uma instância nova é o administrador do bootstrap.
Liberar acesso a mais alguém exigiria `INSERT` manual no banco de produção com hash BCrypt gerado à
mão — `/api/usuarios` existe para tirar essa operação do banco e colocá-la na API.

Não há auto-cadastro público, e a escolha é a mesma que fechou a API: cada consulta fora do cache
gasta cota paga da The Odds API. Quem entra é decisão de quem administra a instância.

A regra fina das rotas de usuários vive em `@PreAuthorize`, ao lado de cada endpoint: elas misturam
operações de administrador com as do próprio usuário (`/me`), e é ali que a distinção fica legível.
O `SecurityConfig` declara sobre elas apenas um piso — `/api/usuarios/me` para qualquer autenticado,
o resto de `/api/usuarios/**` para `ADMIN` —, que não repete a regra de cada método e cobre o caso
de um endpoint novo nascer sem `@PreAuthorize`. O efeito colateral do `@PreAuthorize` é que a recusa
nasce dentro do MVC: o `GlobalExceptionHandler` precisa tratar `AccessDeniedException`
explicitamente, ou o handler genérico a transformaria em 500 antes de ela chegar ao
`ErroSegurancaHandler`.

A exclusão é lógica (`ativo = false`), nunca física: o registro sustenta o histórico de escalações
que aquele usuário produziu. E o rebaixamento de perfil entrou na lista do que incrementa a
`tokenVersion`, junto da troca de senha e da desativação — sem isso, um administrador recém-
-rebaixado continuaria administrando a aplicação até o token expirar.

Duas operações são recusadas com 409 mesmo vindas de um administrador: mexer na própria conta
(desativar ou rebaixar) e desativar ou rebaixar o último `ADMIN` ativo. A primeira é quase sempre
engano; a segunda deixaria a instância sem nenhum acesso administrativo, recuperável só por acesso
direto ao banco — exatamente o que o bootstrap do admin inicial existe para evitar. Essa segunda
checagem trava as linhas dos administradores ativos (`SELECT ... FOR UPDATE`) em vez de contá-las:
uma contagem seria check-then-act, e duas requisições simultâneas removeriam um administrador cada.

A conferência da senha atual na troca de senha reusa o freio do login, com o mesmo contador. Um
token roubado tem validade limitada; sem freio, ele daria tentativas ilimitadas para adivinhar a
senha e, acertando, tomar a conta em definitivo — a troca derruba os tokens do dono legítimo.
Contadores separados para login e troca dariam ao atacante duas janelas para o mesmo segredo.

A ordenação da listagem é restrita a uma lista fechada de campos. Fora dela, o Spring Data
respondia 500 com o nome da entidade interna, e `sort=senha` era aceito — ordenar pelo hash não o
revela, mas nada na API deveria alcançá-lo.

### Pipeline de Montagem do Time

O `PipelineService` orquestra a montagem em etapas:
1. Buscar status do mercado (`/mercado/status`)
2. Buscar odds e identificar times favoritos (`OddsService`)
3. Buscar e filtrar atletas do Cartola (`CartolaDataService`)
4. Calcular desempenho das últimas 5 rodadas — média e desvio padrão (`DesempenhoService` via `/atletas/pontuados`)
5. Calcular score de cada atleta (`ScoreService`)
6. Montar o time em formação configurável (`MontadorTimeService`)

### Fórmula de Score

O `ScoreService` aplica fórmulas distintas conforme a posição do atleta, priorizando os indicadores mais relevantes para cada função. Os bônus situacionais (`fatorCasa` e `timeFavorito`) são configuráveis via banco de dados e se aplicam a todas as posições.

#### Posições sem regra específica (LAT, ZAG, MEI, TEC) — fallback configurável

```
score = (mediaPontos × 0.40) + (valorização × 0.20) + (desempenho × 0.20)
      + (fatorCasa × 0.10)   + (timeFavorito × 0.10)
      − (desvioPadrao × pesoDesvio)
```

Todos os pesos do fallback são configuráveis em runtime via `PATCH /api/config` sem restart.

#### Goleiro (GOL) — prioridade em scouts defensivos

```
score = (desempenho × 0.35) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (defesasDificeis × 0.05) + (penaltisDefendidos × 0.05) − (golsSofridos × 0.02)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      − (desvioPadrao × pesoDesvio)
```

`defesasDificeis` (DD), `penaltisDefendidos` (DP) e `golsSofridos` (GS) são scouts acumulados da temporada extraídos de `/atletas/mercado`. Quando não disponíveis na resposta da API, os scouts são tratados como 0 sem impacto no cálculo.

#### Atacante (ATA) — prioridade em participação ofensiva

```
score = (desempenho × 0.25) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (gols × 0.08) + (assistencias × 0.05)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      − (desvioPadrao × pesoDesvio)
```

`gols` (G) e `assistencias` (A) são scouts acumulados da temporada. O bônus `timeFavorito` reforça a preferência por atacantes de times com odds favoráveis.

### Penalização por Volatilidade (Desvio Padrão)

O `DesempenhoService` retorna, para cada atleta, um `DesempenhoAtleta` com `mediaPontos`, `desvioPadrao` (populacional, divisão por N) e `rodadasConsideradas` das últimas rodadas. O `ScoreService` subtrai `desvioPadrao × pesoDesvio` do score final em todas as posições, penalizando atletas inconsistentes e priorizando consistência em situações de empate técnico. O `pesoDesvio` é configurável via `PATCH /api/config` (padrão `0.05`). Atletas com menos de 2 rodadas disponíveis têm `desvioPadrao = 0.0`, anulando a penalidade sem quebrar por dados insuficientes. Atletas sem histórico recente (ausentes do mapa) caem para o proxy `mediaPontos` da temporada e não recebem penalidade.

O `ScoreService` propaga `desvioPadrao` (arredondado para 4 casas) e `rodadasConsideradas` para o modelo `Atleta`, e ambos são expostos nos DTOs de resposta `AtletaDto` (`GET /api/time`) e `AtletaRankingDto` (`GET /api/ranking`). Para atletas que usam o proxy (sem histórico recente), os campos valem `0.0` e `0`. Esses valores alimentam o indicador de consistência exibido no frontend.

#### Constantes de peso por posição

Os pesos específicos por posição são constantes centralizadas em `ScoreService` (prefixo `GOL_` e `ATA_`), fáceis de ajustar sem impactar a fórmula das demais posições.

### Configuração via Banco de Dados

Parâmetros de negócio (odd limite, pesos do score, formação e regras) ficam na tabela `configuracao` do PostgreSQL.
Gerenciados via API REST:

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/config` | Retorna configuração atual |
| `PATCH` | `/api/config` | Atualiza campos em runtime |
| `POST` | `/api/config/reset` | Restaura defaults |

O Flyway aplica as migrations automaticamente na inicialização:
- `V1__create_configuracao.sql` — cria tabela e insere valores padrão
- `V2__alter_configuracao_numeric_to_double.sql` — converte colunas para `DOUBLE PRECISION`
- `V3__add_evitar_mesmo_clube_defesa.sql` — adiciona a regra configurável para não repetir clubes entre GOL, LAT e ZAG
- `V4__add_limite_atletas_por_clube.sql` — adiciona limite configurável de atletas titulares por clube
- `V5__add_budget_maximo.sql` — adiciona constraint de budget máximo em C$ para os titulares (padrão `0` = sem limite)
- `V6__add_peso_desvio.sql` — adiciona o peso da penalidade por desvio padrão do desempenho (padrão `0.05`)

### Cache Caffeine (in-memory)

Todos os caches usam TTL de **10 minutos** e máximo de 500 entradas.
O cache é reiniciado junto com a aplicação — não há persistência entre restarts.

| Cache | Dado cacheado |
|---|---|
| `odds` | Odds da The Odds API |
| `atletas` | `/atletas/mercado` |
| `clubes` | `/clubes` |
| `partidas` | `/partidas` |
| `pontuados` | `/atletas/pontuados` (chave: rodada) |
| `statusMercado` | `/mercado/status` |
| `configuracao` | Config do banco (invalidado via PATCH/POST /api/config) |

Invalidação manual via `DELETE /api/cache` (todos) ou `DELETE /api/cache/{nome}` (específico).

### Observabilidade (Spring Actuator + Micrometer)

O projeto inclui **Spring Boot Actuator** com **Micrometer** e o registry **Prometheus** para coleta de métricas.

O Actuator responde na **mesma porta da aplicação**. Antes ele vivia em `management.server.port=9090` com bind em `127.0.0.1`, e era o bind — não uma regra — que o protegia; uma plataforma que publica uma porta só não sustenta esse arranjo, e a proteção sumiria junto com ele. Na porta única quem protege é a matriz de acesso do `SecurityConfig`.

Endpoints expostos via `management.endpoints.web.exposure.include=health,info,metrics,prometheus`:

| Endpoint | Acesso | Descrição |
|---|---|---|
| `/actuator/health` | Público | Saúde da aplicação |
| `/actuator/info` | Público | Informações da build |
| `/actuator/metrics` | `ADMIN` | Lista de métricas coletadas |
| `/actuator/metrics/{nome}` | `ADMIN` | Detalhe de uma métrica específica |
| `/actuator/prometheus` | `ADMIN` | Métricas em formato Prometheus (scrape) |

`health` e `info` são públicos porque o healthcheck da plataforma precisa consultá-los antes de qualquer token existir. Com `management.endpoint.health.show-details=when_authorized` e `management.endpoint.health.roles=ADMIN`, o corpo anônimo é só `{"status":"UP"}` — o estado de banco, disco e dependências só aparece para `ADMIN`.

Endpoints sensíveis (`env`, `beans`, `heapdump`, etc.) **não** são expostos, nem para `ADMIN`.

A tag `application=cartolaoddsapi` é adicionada a todas as métricas via `management.metrics.tags.application`.

Para scrape com Prometheus, aponte o job para `GET /actuator/prometheus` com um access token de `ADMIN` no header `Authorization`. Esse token expira em 24 h e não há renovação — a coleta contínua depende de um credencial de conta de máquina, tratado na issue #44.

### Filtro de Atletas

Apenas atletas com status **Provável (7)** ou **Dúvida (6)** e preço `> 0` são considerados.
O parâmetro `excluirDuvida=true` (disponível em `GET /api/time` e `GET /api/ranking`) restringe o pool aos **Prováveis (7)**. O filtro é aplicado **pós-cache**, antes do cálculo de score, para não invalidar entradas de cache compartilhadas com o fluxo padrão.
Antes de identificar favoritos, o `OddsService` cruza os jogos retornados pela The Odds API com os confrontos da rodada atual vindos de `/partidas`; odds de jogos fora da rodada atual são ignoradas.
Quando odds não estão disponíveis, o filtro por time favorito é desativado e todos os elegíveis entram no pool.

### Normalização de Clubes

O `NormalizadorUtil` remove acentos, converte para lowercase, troca hífens por espaços, colapsa espaços duplicados e aplica um dicionário central de aliases para alinhar nomes vindos da The Odds API com os nomes do Cartola FC. Isso cobre variações como `Atlético-MG`, `Atlético Mineiro`, `Atlético Mineiro MG`, `Athletico Paranaense`, `Atlético Paranaense`, `São Paulo FC`, `Inter`, `Fluminense FC` e `Vasco da Gama`.

### Regra de Defesa

Quando `evitarMesmoClubeDefesa=true` (padrão), o `MontadorTimeService` evita repetir clubes entre titulares das posições **GOL**, **LAT** e **ZAG**. A regra é configurável via `PATCH /api/config` e pode ser desativada em runtime. Quando não há candidatos suficientes sem repetição, o montador completa a posição com os melhores atletas restantes (respeitando ainda o limite por clube), garantindo que a formação nunca fique incompleta.

### Limite por Clube no Time Titular

O time titular respeita o limite de **no máximo 4 atletas do mesmo clube**, incluindo o **treinador (TEC)**. O limite é configurável em runtime via `PATCH /api/config` no campo `limiteAtletasPorClube` (padrão `4`). O montador aplica um fallback em três níveis:
1. **Primário** — respeita regra de defesa (sem clube repetido em GOL/LAT/ZAG), limite por clube e budget.
2. **Intermediário** — relaxa a regra de defesa, mas mantém o limite máximo por clube e budget.
3. **Último recurso** — relaxa também o limite por clube, mas mantém o budget.

### Budget Máximo (C$) e Otimização por Orçamento

O `MontadorTimeService` respeita um **teto de gasto em Cartoletas (C$)** para o time titular. O teto efetivo é o `orcamento` informado em `GET /api/time` (tem prioridade) ou, na ausência dele, o `budgetMaximo` configurável em runtime via `PATCH /api/config`. Quando não há teto (`budgetMaximo = 0` e sem `orcamento`), a constraint é desativada: o montador usa a **seleção gulosa por score**, que já é ótima nesse caso.

Quando há um teto finito, a seleção dos titulares passa pelo `OtimizadorTitulares`, que resolve um **multiple-choice knapsack por posição** via **branch-and-bound**. O objetivo é **maximizar a soma de score** com `Σ preço ≤ orçamento` (estratégia sempre `SCORE_MAXIMO`), e não pegar os mais baratos. Características:

- **Desempate:** entre soluções de score igual (dentro de um epsilon), vence a de **menor custo** (equivalente a maior `score/preço`).
- **Restrições preservadas:** formação, `limiteAtletasPorClube` e a regra de defesa sem clube repetido (GOL/LAT/ZAG) são respeitadas dentro da busca.
- **Podas:** viabilidade de orçamento (custo mínimo para completar as vagas restantes), limite superior admissível de score e redução de candidatos **por clube**. A redução só descarta um atleta quando existem ao menos `min(vagas da posição, limiteAtletasPorClube)` outros do mesmo clube que o dominam (score ≥ e preço ≤), preservando a otimalidade mesmo em posições com várias vagas (onde dois atletas do mesmo clube podem ser escalados juntos). Na defesa com a regra ativa, o limite é 1 por clube.
- **Orçamento insuficiente vs. restrições:** quando não é possível completar a formação dentro do teto por falta de orçamento, retorna o melhor time *best-effort* (mais vagas preenchidas, depois maior score), com `formacaoCompleta = false` e `avisoOrcamento` preenchido. Se a incompletude vier das **restrições de clube/defesa** (e não do orçamento), o montador recorre à seleção gulosa — que relaxa essas regras em último recurso, como no fluxo sem orçamento — e fica com a montagem que preenche mais vagas, evitando atribuir ao orçamento uma incompletude que é de clube.
- **Guarda de iterações:** ao estourar o teto de iterações do branch-and-bound, o montador recai na seleção gulosa por orçamento como fallback, garantindo resposta sempre válida.

### Reserva de Luxo

A **reserva de luxo** é sempre a reserva com maior score entre as posições que possuem reserva (não é mais o segundo melhor titular). `TEC` não tem reserva e, portanto, não concorre a reserva de luxo.

### Validação de Entrada

Falhas de Bean Validation no corpo de requisições, principalmente em `PATCH /api/config`, são tratadas no `GlobalExceptionHandler` e retornam HTTP 400 com todas as mensagens de campos inválidos concatenadas com `"; "`.

---

## Estrutura de Pacotes

```
com.cartola.odds/
├── config/      — configurações (cache, REST client, OpenAPI, properties)
├── client/      — integrações com APIs externas (OddsClient, CartolaClient)
├── repository/  — persistência (ConfiguracaoRepository)
├── service/     — lógica de negócio (pipeline, score, desempenho, ranking, montador, configuração)
├── controller/  — endpoints REST + tratamento global de erros
│   └── api/     — interfaces com anotações Swagger (separadas dos controllers)
├── model/       — entidades de domínio, enums, DTOs de request e response
└── util/        — utilitários (NormalizadorUtil)
```

---

## Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/time` | Monta o time completo para a rodada atual (opcionais: `orcamento`, `excluirDuvida`) |
| `GET` | `/api/ranking` | Top atletas por score (filtrável por posição, limite e `excluirDuvida`) |
| `GET` | `/api/favoritos` | Times favoritos com odds detalhadas |
| `DELETE` | `/api/cache` | Invalida todos os caches |
| `DELETE` | `/api/cache/{nome}` | Invalida um cache específico |
| `GET` | `/api/config` | Retorna configuração atual |
| `PATCH` | `/api/config` | Atualiza parâmetros em runtime |
| `POST` | `/api/config/reset` | Restaura defaults |
| `POST` | `/api/usuarios` | Cria usuário (`ADMIN`) |
| `GET` | `/api/usuarios` | Lista usuários, paginada (`ADMIN`) |
| `GET` | `/api/usuarios/{id}` | Detalhe do usuário (`ADMIN`) |
| `PATCH` | `/api/usuarios/{id}` | Atualiza nome, e-mail, perfil e situação (`ADMIN`) |
| `DELETE` | `/api/usuarios/{id}` | Desativação lógica (`ADMIN`) |
| `GET` | `/api/usuarios/me` | Dados da própria conta (autenticado) |
| `PATCH` | `/api/usuarios/me/senha` | Troca a própria senha (autenticado) |
| `GET` | `/swagger-ui.html` | Documentação interativa (público fora de produção, `404` no perfil `prod`) |
| `GET` | `/actuator/health` | Status de saúde da aplicação (público) |
| `GET` | `/actuator/info` | Informações da build (público) |
| `GET` | `/actuator/metrics` | Lista de métricas disponíveis (`ADMIN`) |
| `GET` | `/actuator/metrics/{nome}` | Detalhe de uma métrica específica (`ADMIN`) |
| `GET` | `/actuator/prometheus` | Métricas no formato Prometheus (`ADMIN`) |

---

## Configuração Principal

| Variável de Ambiente | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | — | Chave da The Odds API (obrigatória para filtro por odds) |
| `APP_PORT` | `8080` | Porta exposta no host |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cartola_odds` | URL do banco |
| `SPRING_DATASOURCE_USERNAME` | `cartola` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `cartola` | Senha do banco |
| `SPRING_PROFILES_ACTIVE` | `default` | Profile do Spring Boot; `prod` desliga Swagger/`api-docs` e baixa o log para `INFO` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens do frontend liberadas, separadas por vírgula — nunca `*` |

Parâmetros de negócio (odd limite, pesos, formação) são gerenciados via banco de dados — não precisam de variáveis de ambiente.

---

## Testes

544 cenários distribuídos em 32 classes de teste cobrindo serviços, controllers, segurança, domínio, utilitários e endpoints de observabilidade.
Os testes usam migrations Flyway próprias em `src/test/resources/db/migration/h2`, equivalentes às de produção e ajustadas para a sintaxe do H2. Execute com:

```bash
mvn test
```

---

## Contexto de Uso

- **Temporada:** Brasileirão Série A
- **Rodada:** determinada dinamicamente via status do mercado Cartola
- **Mercado fechado:** todos os endpoints retornam campo `avisoMercado` informando o estado atual
- **Plano free da Odds API:** 500 requisições/mês — o cache reduz o consumo significativamente
- **Sem API Key:** filtro por time favorito desativado; usa todos os atletas elegíveis por status e preço
