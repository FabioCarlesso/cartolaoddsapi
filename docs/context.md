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

O login tem freio de força bruta por e-mail (`LoginThrottle`), e não por IP. Não é falta de IP: com
`server.forward-headers-strategy=native` a aplicação lê o endereço real do cliente atrás de um proxy
confiável, e contar por IP seria viável. Continua não sendo o que se quer contar — o e-mail descreve
o alvo do ataque e o IP descreve só o caminho, e caminho é o que um atacante distribuído troca de
graça, enquanto adivinhar a senha do administrador é martelar sempre o mesmo endereço.

### Política de acesso por rota e hardening do perfil `prod`

Autenticar diz *quem* está chamando; a matriz de acesso do `SecurityConfig` diz *o que cada um pode
fazer*. O critério da distinção entre `USER` e `ADMIN` é um só — **escreve na instância inteira ou
gasta cota externa** —, e quatro rotas o satisfazem: `PATCH /api/config` e `POST /api/config/reset`
mudam pesos do score, formação e `odd_limite` da instância inteira; `DELETE /api/cache` força
chamadas novas à The Odds API, cuja cota mensal é paga; e
`POST /api/historico/{rodadaId}/atualizar-pontuacao` regrava a `pontuacaoReal` de todos os atletas
da rodada — a tabela de escalação é da instância, não de quem chamou — depois de consultar a API do
Cartola. Deixar essas quatro ao alcance de qualquer usuário logado é entregar o botão de gastar
dinheiro e o de reconfigurar a aplicação a quem só deveria consultar.

As demais rotas de `/api/historico` só leem e continuam abertas a qualquer autenticado: o matcher
cita o verbo `POST`, então o `GET` da mesma rota cai na regra final.

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
`server.forward-headers-strategy=native`, que põe o `RemoteIpValve` na frente da cadeia para
normalizar esquema, host e porta a partir dos `X-Forwarded-*`.

A escolha de `native` em vez de `framework` é o ponto. O `ForwardedHeaderFilter` do framework
normaliza igual, mas sem nenhuma noção de quem está do outro lado: qualquer cliente reescreve
esquema e host da própria requisição, e um `X-Forwarded-Host: evil.example` sai no `Location` de uma
resposta `201`. O `RemoteIpValve` só aplica os headers quando a conexão vem de um endereço listado
em `server.tomcat.remoteip.internal-proxies` (padrão: as faixas privadas; `TRUSTED_PROXIES`
sobrescreve). A dúvida de sempre — quantos saltos confiar — deixa de ser aceita e passa a ser
configurada: a lista de saltos confiáveis existe e tem nome. De quebra o `getRemoteAddr()` passa a
valer atrás do proxy, embora o `LoginThrottle` siga contando por e-mail por outro motivo. A condição
de TLS evita o outro extremo: mandar HSTS em `http://localhost` trava o navegador do desenvolvedor
em HTTPS para todo o host por um ano.

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
- `V7__create_escalacao_rodada.sql` — cria a tabela de histórico de escalações por rodada
- `V8__create_usuario.sql` — cria a tabela de usuários (perfil de acesso e `tokenVersion`)
- `V9__create_odds_snapshot.sql` — cria a tabela de snapshot da última resposta de odds, para o guardrail de cota
- `V10__create_odds_cota.sql` — cria a tabela do último estado conhecido da cota (saldo, consumo, leitura e sondagem)

### Cache Caffeine (in-memory)

Máximo de 500 entradas por cache. TTL de **10 minutos** para a maioria, exceto `odds`
(configurável via `odds.api.cache-ttl-minutos`, padrão **60 minutos** — odds de Brasileirão não
mudam a cada poucos minutos, e um TTL curto multiplicava o consumo de cota sem ganho real).
O cache é reiniciado junto com a aplicação — não há persistência entre restarts. O cache `odds`
é o único com um fallback que sobrevive a isso: ver "Guardrail de cota da The Odds API" abaixo.

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

### Guardrail de cota da The Odds API

A autenticação por JWT limita *quem* chama a API, não *quanto* se gasta com ela — e a The Odds
API é o único componente pago da stack (plano free: 500 requisições/mês). Antes desta issue
(#40), estourar a cota degradava a escalação silenciosamente: o `OddsClient` cacheava por 10
minutos e, em qualquer falha, devolvia lista vazia com um `log.error`, sem filtro de favoritos e
sem ninguém perceber pela API. Pior em produção na Railway, onde o cache é em memória e todo
redeploy o zera — cada deploy virava pelo menos uma chamada nova.

A The Odds API devolve o saldo em cada resposta, nos headers `x-requests-remaining` e
`x-requests-used`. O `OddsClient` lê esses headers — inclusive **na resposta de erro**, que é
onde o saldo real aparece quando a cota estoura: lendo só no caminho de sucesso, o saldo ficaria
congelado no último valor saudável e o guardrail nunca armaria justamente no caso em que ele
existe para agir. O último valor conhecido é exposto por `GET /api/odds/cota` (restrito a
`ADMIN`) e pelas métricas Micrometer `odds_api_requests_total`, `odds_api_requests_remaining` e
`odds_api_errors_total`. Abaixo do guardrail `odds.api.min-requests-remaining` (padrão 50), o
cliente para de chamar o provedor — sem essa parada, a aplicação continuaria gastando cota até
o provedor recusar as chamadas.

Esse estado é persistido na tabela `odds_cota` (linha única) e recuperado no boot. O snapshot de
odds já sobrevivia ao redeploy, mas o saldo que decide *se vale a pena chamar* vivia só em
memória: cada deploy voltava para "sem leitura" e desarmava o guardrail — no ambiente que
motivou a issue, onde o cache é zerado a cada subida. A leitura só é marcada quando algum header
foi de fato lido; um `200` sem os headers não reinicia o relógio da sondagem, senão o guardrail
seguiria barrando por mais um intervalo inteiro com um saldo que ninguém conferiu.

O fallback é a última resposta bem-sucedida, persistida na tabela `odds_snapshot` (linha única,
sobrescrita a cada resposta **com jogos**) em vez de só o cache Caffeine: o cache é apagado a
cada restart e redeploy, e sem persistência o guardrail ficaria sem nada para servir logo depois
de subir — justamente o cenário mais comum em produção. Uma resposta vazia não sobrescreve nada:
a The Odds API responde `200 []` fora de temporada, e gravar isso por cima apagaria o único
fallback que o guardrail tem para o momento em que ele acionar.

Pelo mesmo motivo, na **primeira busca após o boot** o `OddsClient` confere se o snapshot
persistido ainda está dentro do TTL do cache e, se estiver, dispensa a chamada — um restart com
cache Caffeine vazio não precisa gastar crédito para redescobrir a mesma resposta. O atalho é
consumido uma única vez por instância, e essa é a parte que importa: depois dele, todo miss
significa TTL vencido ou cache limpo à mão, e servir o snapshot de novo transformaria
`DELETE /api/cache` num comando sem efeito, quando ele é justamente o gatilho manual de gasto
(agora sujeito ao mesmo guardrail).

O guardrail tem uma válvula: `odds.api.sonda-intervalo-horas` (padrão 24) libera uma chamada por
intervalo mesmo com o saldo abaixo do mínimo. Sem ela o mecanismo se auto-alimentaria — o saldo
só é reavaliado quando uma chamada acontece, então barrar todas as chamadas congelaria o último
saldo conhecido para sempre, e a virada de mês que renova a cota nunca seria percebida sem um
restart. O intervalo conta a partir da tentativa, não da leitura bem-sucedida, para que um
provedor fora do ar não transforme cada requisição numa sondagem nova.

O TTL do cache de odds também é decidido por resultado: resposta com jogos vale **o que resta**
do TTL cheio, contado do instante em que o provedor produziu aquelas odds (`obtidoEm`, carregado
no próprio `OddsComOrigem`) — sem isso, um snapshot de 50 minutos guardado por mais um TTL
inteiro serviria odds de quase duas horas. Resposta vazia vale
`odds.api.cache-ttl-degradado-minutos` (padrão 10), que também é o piso quando o restante seria
negativo. Guardar uma lista vazia
pelos 60 minutos do TTL normal desligaria o filtro de favoritos por uma hora por causa de uma
falha momentânea; não guardar nada faria cada requisição repetir a chamada, e uma resposta
legitimamente vazia custa crédito igual. O `@Cacheable` usa `sync = true` pelo mesmo motivo de
custo: sem ele, N misses simultâneos viram N chamadas pagas para produzir o mesmo valor.

A origem (ao vivo ou snapshot) viaja no próprio valor retornado, `OddsComOrigem`, e não num
campo do cliente: o resultado é cacheado, e num acerto de cache o método nem chega a executar —
uma flag de instância descreveria a última *execução* em vez do que aquele chamador recebeu.

Quando uma resposta usa o snapshot — por guardrail ativo, falha do provedor, ou snapshot ainda
válido logo após um restart —, isso fica explícito no campo `oddsDeSnapshot` de
`GET /api/favoritos`, e não só no log: a degradação silenciosa era exatamente o problema que
motivou a issue. Os alertas em log avisam ao cruzar o **dobro do mínimo configurado** e o
próprio mínimo — derivados da configuração, e não fixos em 100/50, senão um
`min-requests-remaining` maior acionaria o guardrail sem nenhum aviso prévio; com o padrão de
50, os limiares continuam sendo 100 e 50. `ERROR` fica para quando o guardrail efetivamente
entra em ação ou o provedor falha sem snapshot disponível para cobrir a falta.

A métrica `odds_api_requests_remaining` exporta `NaN` enquanto não houve nenhuma leitura, e não
o sentinela interno: um `-1` exportado faria todo alerta de "saldo abaixo do mínimo" disparar a
cada deploy, antes da primeira chamada. Comparação com `NaN` é falsa no PromQL, então a série
fica silenciosa até existir dado de verdade.

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

O nome do lado do Cartola sai do `slug` do clube, não de `nome`/`nome_fantasia` — o `/partidas` passou a devolver a sigla nesses campos (`"MIR"`), e `apelido` é o apelido de torcida. `CartolaDataService.nomeClubeParaChave` isola essa escolha; `nomeClube` segue servindo à exibição.

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
| `GET` | `/api/odds/cota` | Saldo, consumo do mês e estado do guardrail de cota da The Odds API (`ADMIN`) |
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
| `ODDS_API_MIN_REQUESTS_REMAINING` | `50` | Guardrail de cota: abaixo deste saldo restante, para de chamar o provedor e serve o último snapshot |
| `ODDS_API_CACHE_TTL_MINUTOS` | `60` | TTL do cache `odds`, em minutos |
| `ODDS_API_CACHE_TTL_DEGRADADO_MINUTOS` | `10` | TTL de uma resposta de odds sem nenhum jogo |
| `ODDS_API_SONDA_INTERVALO_HORAS` | `24` | Intervalo mínimo entre sondagens de saldo com o guardrail ativo |
| `APP_PORT` | `8080` | Porta exposta no host |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cartola_odds` | URL do banco |
| `SPRING_DATASOURCE_USERNAME` | `cartola` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `cartola` | Senha do banco |
| `SPRING_PROFILES_ACTIVE` | `default` | Profile do Spring Boot; `prod` desliga Swagger/`api-docs` e baixa o log para `INFO` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens do frontend liberadas, separadas por vírgula — nunca `*` |

Parâmetros de negócio (odd limite, pesos, formação) são gerenciados via banco de dados — não precisam de variáveis de ambiente.

---

## Testes

726 cenários distribuídos em 40 classes de teste cobrindo serviços, controllers, segurança, domínio, utilitários e endpoints de observabilidade.
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
