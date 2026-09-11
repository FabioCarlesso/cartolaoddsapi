# Context — Cartola Odds API

> **Papel deste arquivo:** registrar as **decisões de arquitetura e o porquê de cada uma** — o
> raciocínio que não se deduz lendo o código nem a referência.
>
> *O que* o sistema faz e *como* se usa cada endpoint está nos arquivos de referência —
> [`api.md`](api.md), [`regras-de-negocio.md`](regras-de-negocio.md),
> [`seguranca.md`](seguranca.md) e os demais listados em [`docs/README.md`](README.md).
> Como subir o projeto está no [README](../README.md).

---

## Visão Geral

API REST em **Java 21 + Spring Boot 3.4.5** que monta automaticamente um time competitivo para o **Cartola FC** cruzando odds do Brasileirão (via [The Odds API](https://the-odds-api.com)) com métricas dos atletas da plataforma Cartola.

---

## Problema que Resolve

Montar um time no Cartola FC exige combinar dois tipos de dados dispersos:
1. **Quais times têm mais chance de vencer?** — respondido pelas odds de mercado.
2. **Quais jogadores desses times estão em melhor forma e são viáveis pelo preço?** — respondido pelos dados da API do Cartola FC.

Esta API cruza essas duas fontes e entrega diretamente os melhores atletas disponíveis, já escalados em formação configurável (padrão **4-3-3**).

As duas fontes consumidas — The Odds API (paga, com cota) e Cartola FC (pública, sem autenticação)
— estão detalhadas em [`arquitetura.md` › APIs Externas](arquitetura.md#apis-externas). A assimetria entre
elas é o que explica boa parte das decisões abaixo: uma é de graça e ilimitada, a outra tem 500
requisições por mês.

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

O login devolve `expiraEmSegundos`, uma duração, e não um instante absoluto: o container roda em
UTC e um horário sem fuso seria lido como local pelo cliente, que passaria a achar que a sessão dura
horas a mais do que o token realmente vale. É a mesma escolha do `expires_in` do OAuth 2.

O login tem freio de força bruta por e-mail (`LoginThrottle`), e não por IP. Não é falta de IP: com
`server.forward-headers-strategy=native` a aplicação lê o endereço real do cliente atrás de um proxy
confiável, e contar por IP seria viável. Continua não sendo o que se quer contar — o e-mail descreve
o alvo do ataque e o IP descreve só o caminho, e caminho é o que um atacante distribuído troca de
graça, enquanto adivinhar a senha do administrador é martelar sempre o mesmo endereço. Contar por IP
ainda faria o freio punir todos juntos atrás de um NAT.

*Componentes, claims e contrato: [`seguranca.md` › Autenticação (JWT)](seguranca.md#autenticação-jwt).*

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

O CORS é parametrizado e nunca curinga: o token viaja em header, e um `*` deixaria qualquer site
chamar a API com o token da vítima. O preflight `OPTIONS` passa antes da autorização porque ele não
carrega `Authorization` — sem essa exceção o navegador levaria `401` sem chegar a enviar a
requisição real.

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

O `RemoteIpValve` é do Tomcat e o MockMvc não o atravessa, então essa parte precisou de dois testes
de HTTP real — um com a faixa padrão e outro que **inverte** `internal-proxies` para provar que os
headers de um cliente não confiável são ignorados. A inversão é o que torna o caso possível: em
`localhost` toda requisição chega de uma faixa confiável, e um teste escrito com MockMvc passaria
verde sem exercitar nada.

*A matriz rota a rota e os cabeçalhos de segurança:
[`seguranca.md` › Matriz de acesso por rota](seguranca.md#matriz-de-acesso-por-rota).*

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
checagem trava as linhas dos administradores ativos (`travarAtivosPorPerfil`, com
`@Lock(PESSIMISTIC_WRITE)` — um `SELECT ... FOR UPDATE`) em vez de contá-las: uma contagem seria
check-then-act, e duas requisições simultâneas removeriam um administrador cada.

A conferência da senha atual na troca de senha reusa o freio do login, com o mesmo contador. Um
token roubado tem validade limitada; sem freio, ele daria tentativas ilimitadas para adivinhar a
senha e, acertando, tomar a conta em definitivo — a troca derruba os tokens do dono legítimo.
Contadores separados para login e troca dariam ao atacante duas janelas para o mesmo segredo.

As respostas usam `UsuarioResponse` e não a entidade `Usuario`, para que um campo novo na entidade
não vaze o hash BCrypt por descuido. O e-mail é gravado sempre em minúsculas porque a `UNIQUE` do
Postgres é sensível a caixa e o login não é: sem normalizar, `Fabio@x.com` e `fabio@x.com`
coexistiriam na tabela e o login ficaria ambíguo.

A paginação usa envelope próprio (`PaginaResponse<T>`) em vez do `Page` do Spring Data, cujo JSON é
detalhe interno do framework e muda entre versões — o próprio Spring avisa disso no log. E a
ordenação é restrita a uma lista fechada de campos: fora dela, o Spring Data respondia 500 com o
nome da entidade interna, e `sort=senha` era aceito — ordenar pelo hash não o revela, mas nada na
API deveria alcançá-lo. A mensagem de erro não ecoa o campo recebido: a lista de aceitos basta para
corrigir a chamada, e evita devolver ao cliente um texto que ele mesmo escolheu.

Payload inválido vira `400` e verbo errado vira `405`, nunca `500`: os dois são erro do cliente, e
tratá-los como falha de servidor enchia o log de stacktrace de "erro inesperado" a cada chamada. A
mensagem original fica só no log — ela nomeia a classe Java e chega a listar os valores aceitos de
um enum.

*Endpoints, contrato e proteções: [`seguranca.md` › Gestão de Usuários](seguranca.md#gestão-de-usuários).*

### Pipeline de Montagem do Time

O `PipelineService` orquestra a montagem em etapas encadeadas — status do mercado, odds e favoritos,
atletas filtrados, desempenho recente, score, montagem —, cada uma isolada num serviço próprio. A
sequência importa porque cada etapa restringe o conjunto da seguinte: filtrar por time favorito
antes de calcular score evita pagar o cálculo por atletas que não entrarão no pool, e o desempenho
precisa estar disponível antes do score porque é um dos seus termos.

*As etapas em detalhe: [`arquitetura.md` › Fluxo de Execução](arquitetura.md#fluxo-de-execução).*

### Fórmula de Score

O `ScoreService` aplica fórmulas distintas conforme a posição do atleta. O fallback configurável
(LAT, ZAG, MEI, TEC) pondera média, valorização e desempenho recente; goleiro e atacante têm
fórmulas próprias porque os scouts que descrevem uma boa atuação são outros — defesas difíceis e
pênaltis defendidos de um lado, gols e assistências do outro. Usar uma fórmula única obrigaria a
escolher entre ignorar esses scouts e distribuí-los a posições onde eles não significam nada.

Os pesos específicos de GOL e ATA são constantes centralizadas no `ScoreService` (prefixos `GOL_` e
`ATA_`), e não parâmetros de banco: são poucos, mudam raramente, e expô-los multiplicaria a
superfície de validação do `PATCH /api/config` sem necessidade. Ficam fáceis de ajustar sem impactar
a fórmula das demais posições. Os bônus situacionais (`fatorCasa` e `timeFavorito`) continuam
configuráveis e valem para todas as posições.

**Penalização por volatilidade.** O `DesempenhoService` calcula o desvio padrão populacional das
últimas rodadas e o `ScoreService` subtrai `desvioPadrao × pesoDesvio` do score em todas as
posições. A ideia é desempatar por consistência: entre dois atletas de média parecida, o que oscila
menos é a aposta mais segura. Atletas com menos de 2 rodadas têm `desvioPadrao = 0.0`, anulando a
penalidade sem quebrar por dados insuficientes, e atletas sem histórico recente caem para o proxy
`mediaPontos` da temporada e não são penalizados — penalizar por um desvio que não foi medido seria
inventar um número.

O `desvioPadrao` e o `rodadasConsideradas` são propagados até os DTOs de resposta em vez de ficarem
internos: é o que permite ao frontend exibir um indicador de consistência sem recalcular nada.

*As fórmulas: [`regras-de-negocio.md` › Fórmula do Score](regras-de-negocio.md#fórmula-do-score).*

### Configuração via Banco de Dados

Parâmetros de negócio (odd limite, pesos do score, formação e regras) ficam numa tabela de linha
única no PostgreSQL, e não em `application.properties`: são o que se quer ajustar *enquanto se usa*
a aplicação, comparando resultados, e um restart por ajuste tornaria esse ciclo inviável. O
`PATCH /api/config` atualiza em runtime e o `POST /api/config/reset` volta aos defaults, que nascem
da própria migration.

O Flyway aplica as migrations automaticamente na inicialização, e `ddl-auto=validate` garante que o
esquema esperado pelo Hibernate e o esquema real não divirjam em silêncio.

*Tabela, migrations e validações: [`banco-de-dados.md` › Parâmetros de Negócio via Banco de Dados](banco-de-dados.md#parâmetros-de-negócio-via-banco-de-dados).*

### Cache Caffeine (in-memory)

Cache em memória JVM, sem Redis: o ganho aqui é evitar chamadas repetidas às APIs externas dentro de
uma janela de minutos, e isso não justifica uma dependência de infraestrutura a mais.

O TTL de `odds` é de **60 minutos**, e não dos 10 minutos das demais entradas: odds de Brasileirão
não mudam a cada poucos minutos, e um TTL curto multiplicava o consumo de cota sem ganho real. O
cache é reiniciado junto com a aplicação — não há persistência entre restarts. O cache `odds` é o
único com um fallback que sobrevive a isso: ver [Guardrail de cota](#guardrail-de-cota-da-the-odds-api),
abaixo.

*Caches registrados, TTLs e invalidação: [`operacao.md` › Cache (Caffeine)](operacao.md#cache-caffeine).*

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
`odds_api_errors_total`. Os contadores medem **tentativas**, não sucessos: o provedor cobra pela
chamada, e a recusa por cota estourada só existe no caminho de erro — contá-la fora do total
deixaria justo esse evento de fora do contador de consumo, e `errors/requests` deixaria de ser
uma taxa. Os nomes são declarados na convenção pontuada do Micrometer (`odds.api.requests`), com
a tradução para o formato do Prometheus fixada por teste, porque é por `odds_api_requests_total`
que dashboard e alerta perguntam. Abaixo do guardrail `odds.api.min-requests-remaining` (padrão 50), o
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
provedor fora do ar não transforme cada requisição numa sondagem nova. `GET /api/odds/cota`
devolve essa janela em `proximaSondagem`: com o guardrail armado, é a única pergunta que sobra
para quem está olhando — quando isso volta sozinho —, e antes disso a resposta só existia no log.

As propriedades do guardrail são recusadas no boot quando não descrevem configuração válida:
mínimo `1` em todas (`sonda-intervalo-horas=0` faria de toda requisição uma sondagem, gastando
exatamente a cota que o mecanismo preserva) e `cache-ttl-degradado-minutos` menor ou igual a
`cache-ttl-minutos`, já que o degradado é um piso dentro do cheio. Invertidos, o cálculo de
validade do cache receberia um intervalo de cabeça para baixo — e esse cálculo roda dentro da
escrita no cache, sob `@Cacheable`: o erro viraria `500` em `/api/favoritos`, `/api/time` e
`/api/ranking`, a cada requisição e depois de o crédito já ter sido gasto. Recusar no boot custa
um restart e diz qual propriedade está errada.

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

*Propriedades, tabelas e contrato dos endpoints:
[`operacao.md` › Cota da The Odds API: guardrail e sondagem](operacao.md#cota-da-the-odds-api-guardrail-e-sondagem).*

### Histórico das leituras de cota

A `odds_cota` guarda o estado corrente numa linha só, sobrescrita a cada leitura — e é isso que o
guardrail precisa no boot. Mas uma linha sobrescrita não tem passado, então "quanto se gastou ao
longo deste mês" não tinha resposta dentro da aplicação. Foi por não ter que a pergunta quase
virou um Prometheus ao lado (#58): guardar amostras era o serviço que aquela stack prestava, e
era o único que ela prestava de fato. Guardar a série aqui custa uma tabela.

A `odds_cota_historico` (#61) é append-only e complementa a `odds_cota`, não a substitui: as duas
respondem perguntas diferentes e mudam pelo mesmo evento. O append fica em `registrarCota()`, e
não em `persistirCota()`, porque o outro chamador de `persistirCota` é a liberação de sondagem em
`guardrailBloqueia()` — ali nenhum header foi lido e o saldo não mudou. Gravar também naquele
ponto encheria a série de degraus onde nada aconteceu. Falha ao gravar não interrompe a busca,
pela mesma regra do `persistirCota`: é registro para gráfico, não pode custar um `500` depois de
o crédito já ter sido gasto.

Não há retenção, e é decisão em vez de esquecimento: uma linha só nasce de uma chamada ao
provedor, e as chamadas são limitadas pela própria cota que a tabela mede — no plano free, no
máximo ~500 linhas por mês. Uma política de expurgo custaria mais atenção do que o espaço que
economiza.

A virada de ciclo é detectada no servidor, e não em cada consumidor: `reinicioDeCota` marca a
leitura em que o consumo caiu em relação à anterior. A renovação da The Odds API não está
confirmada como sendo por mês calendário ou por aniversário da assinatura, e detectar pela série
funciona nos dois casos — enquanto chutar o dia 1º cortaria o gráfico no lugar errado. A primeira
leitura da janela nunca é marcada: sem uma anterior para comparar, afirmar que houve renovação
seria chute. Sem essa marca, a queda do consumo pareceria falha de coleta.

A janela do endpoint é limitada a 92 dias, e o limite é sobre a resposta, não sobre a tabela. A
série não é agregada — cada leitura vira um item —, e medindo com um ano de dados a resposta deu
6.000 itens e 603 KB: meio megabyte para alimentar um gráfico de algumas centenas de pixels. Um
trimestre cobre o mês corrente e os dois anteriores, que é o que se compara na prática. Um ano
inteiro, se um dia fizer falta, pede agregação por dia — não um teto maior.

Os instantes são truncados a microssegundos na origem, no `OddsClient`, e não só na resposta. A
precisão do `timestamp` do PostgreSQL é essa, e sem truncar o mesmo instante aparecia com nove
casas em `ultimaLeitura` — que vem da memória — e seis no `instante` da mesma leitura no
histórico, que volta do banco; arredondado, ainda por cima, deixando o ponto do gráfico depois do
estado que o originou. Depois de um restart o campo trocava de formato, porque passava a vir do
banco. Truncando na origem, memória e tabela guardam o mesmo valor.

A série é ordenada pelo **instante da leitura**, com o `id` como desempate — e não pela ordem de
inserção. Hoje isso só evita que duas leituras do mesmo microssegundo saiam em ordem escolhida
pelo banco, o que é praticamente inalcançável. Importa mesmo se a aplicação um dia rodar em mais
de uma instância: quem chegou ao banco primeiro deixa de importar, vale quando a cota foi lida.
Nesse cenário sobra um risco pequeno — relógios dessincronizados entre instâncias podem inverter
duas leituras vizinhas e fazer o saldo parecer que subiu, marcando um `reinicioDeCota` falso. Com
uma instância só, que é o caso, não acontece.

Os campos de data são `LocalDateTime`, sem offset, como em toda a API. É a convenção herdada, e
foi mantida de propósito para não criar um contrato diferente só neste endpoint; o custo é que o
consumidor precisa converter usando o fuso em que a aplicação roda, e não o do navegador. Está
dito na descrição OpenAPI do endpoint em vez de ficar implícito, porque o frontend é consumidor
novo e um deslocamento de fuso num gráfico não se denuncia sozinho — o gráfico só fica errado.

O matcher de `/api/odds/cota` no `SecurityConfig` é de path exato, então o subcaminho precisou
entrar explicitamente. Sem isso, `/api/odds/cota/historico` cairia no
`anyRequest().authenticated()` e a série ficaria aberta a qualquer token — a rota que existe
justamente para descrever o consumo do componente pago.

*Tabela e contrato: [`regras-de-negocio.md` › Histórico das leituras de cota](regras-de-negocio.md#histórico-das-leituras-de-cota).*

### Dashboard e alertas da cota

O guardrail evita o desastre, mas não avisa que armou — e armado ele serve snapshot antigo em
silêncio. O dashboard do Grafana e as regras do Prometheus vivem versionados em
`docs/observabilidade/`, sobre as três métricas que já existiam: a issue (#58) deixou explícito
que nenhuma métrica nova era necessária, e o que faltava era o outro lado da instrumentação.

Dois números da aplicação não são métricas e por isso ficam repetidos nos artefatos: o mínimo do
guardrail (`odds.api.min-requests-remaining`) e a cota do plano contratado, sem a qual o consumo
do mês não é derivável (`remaining + used` somam a cota, e `used` não é exportado). Repetido, um
número diverge — então `ArtefatosObservabilidadeTest` amarra o mínimo dos artefatos ao
`application.properties` e confere que todo nome `odds_api_*` citado neles existe na exposição.
Um alerta que descreve um corte que a aplicação não aplica é pior do que nenhum alerta, e uma
renomeação de métrica deixaria painel e alerta mudos sem erro em lugar nenhum.

O `NaN` de "sem leitura" atravessa os artefatos de ponta a ponta. As regras de saldo não disparam
porque comparação com `NaN` é falsa; o painel de guardrail usa `clamp(sgn($minimo - saldo), 0, 1)`
justamente porque a aritmética preserva `NaN`, enquanto `< bool` o colapsaria em `0` e diria
"desarmado" antes da primeira chamada.

O alerta de saldo parado mede o tempo no `for:`, e não numa janela de 25 h dentro do `changes()`:
"sem mudança nos últimos 25 h" também é verdade quando só existem dez minutos de dado, e um
Prometheus recém-subido dispararia na primeira meia hora. Com janela curta e `for: 25h`, as 25
horas precisam ter acontecido de fato.

Os artefatos são arquivos, não serviços: o projeto não sobe Prometheus nem Grafana. A primeira
versão desta issue trazia um perfil `observabilidade` no compose, e ele foi cortado antes do
merge — o estado *atual* da cota, que é o que se olha em 90% das vezes, já sai inteiro de
`GET /api/odds/cota`, inclusive o `minRequestsRemaining` que o dashboard precisa duplicar. Uma
segunda stack para cuidar não se paga por gráfico. O que sobra de exclusivo dela é a avaliação
contínua — alguém perguntando pelo saldo sem ninguém abrir tela — e a taxa de erro sobre os
contadores; o histórico do mês deixou de estar nessa lista quando a série passou a ser guardada
na própria aplicação (#61, acima). Somado a isso, o scrape depende de um token de `ADMIN` que
expira em 24 h (#44), então a stack não teria como rodar continuamente mesmo se estivesse no
compose.

*Painéis, alertas e o que fazer quando cada um dispara:
[`operacao.md` › Observabilidade](operacao.md#observabilidade).*

### Observabilidade (Spring Actuator + Micrometer)

O projeto inclui **Spring Boot Actuator** com **Micrometer** e o registry **Prometheus** para coleta de métricas.

O Actuator responde na **mesma porta da aplicação**. Antes ele vivia em `management.server.port=9090` com bind em `127.0.0.1`, e era o bind — não uma regra — que o protegia; uma plataforma que publica uma porta só não sustenta esse arranjo, e a proteção sumiria junto com ele. Na porta única quem protege é a matriz de acesso do `SecurityConfig`.

`health` e `info` são públicos porque o healthcheck da plataforma precisa consultá-los antes de qualquer token existir. Com `management.endpoint.health.show-details=when_authorized` e `management.endpoint.health.roles=ADMIN`, o corpo anônimo é só `{"status":"UP"}` — o estado de banco, disco e dependências só aparece para `ADMIN`. Métricas e `prometheus` ficam restritos a `ADMIN` porque descrevem o interior da aplicação: uso de memória, latência por rota, contagem de erros.

Endpoints sensíveis (`env`, `beans`, `heapdump`, etc.) **não** são expostos, nem para `ADMIN`.

Para scrape com Prometheus, aponte o job para `GET /actuator/prometheus` com um access token de `ADMIN` no header `Authorization`. Esse token expira em 24 h e não há renovação — a coleta contínua depende de um credencial de conta de máquina, tratado na issue #44.

*Endpoints e exemplos: [`operacao.md` › Endpoints do Actuator](operacao.md#endpoints-do-actuator).*

### Filtro de Atletas

O pool considera apenas atletas **prováveis** e em **dúvida**, com preço acima de zero: os demais
status não são escaláveis, e preço zero indica atleta sem participação no mercado da rodada.

O `excluirDuvida=true` filtra o pool **pós-cache**, e não antes: as respostas cacheadas das APIs
externas são compartilhadas com o fluxo padrão, e filtrar antes obrigaria a manter duas entradas de
cache — ou a invalidá-las a cada troca de parâmetro, gastando cota por uma variação de consulta.

Antes de identificar favoritos, o `OddsService` cruza os jogos retornados pela The Odds API com os
confrontos da rodada atual vindos de `/partidas`: odds de jogos fora da rodada atual descreveriam um
confronto que não vai acontecer agora. Quando odds não estão disponíveis, o filtro por time favorito
é desativado e todos os elegíveis entram no pool — uma lista completa é mais útil que uma resposta
vazia.

*Regras e tabela de filtros: [`regras-de-negocio.md` › Filtros de Atletas](regras-de-negocio.md#filtros-de-atletas).*

### Normalização de Clubes

O `NormalizadorUtil` existe porque as duas fontes escrevem o mesmo clube de formas diferentes, e o
cruzamento é por nome. Um dicionário central de aliases, em vez de tratamentos espalhados, é o que
mantém uma divergência nova sendo resolvida em um lugar só.

O nome do lado do Cartola sai do `slug` do clube, não de `nome`/`nome_fantasia` — o `/partidas`
passou a devolver a sigla nesses campos (`"MIR"`), e `apelido` é o apelido de torcida.
`CartolaDataService.nomeClubeParaChave` isola essa escolha; `nomeClube` segue servindo à exibição.
Separar os dois papéis é o que impede que uma mudança na API do Cartola quebre o cruzamento e a
exibição ao mesmo tempo.

*Regra, exemplos e aliases: [`regras-de-negocio.md` › Normalização de Nomes de Clubes](regras-de-negocio.md#normalização-de-nomes-de-clubes).*

### Regra de Defesa e Limite por Clube

Não repetir clubes na defesa (`GOL`, `LAT`, `ZAG`) e limitar atletas do mesmo clube no time titular
são regras de **diversificação de risco**: um time inteiro do mesmo clube transforma uma atuação
ruim em rodada perdida. As duas são configuráveis porque a aposta contrária — concentrar no clube
favorito da rodada — é uma estratégia legítima.

O montador aplica um fallback em três níveis (respeitar tudo → relaxar a defesa → relaxar o limite
por clube), sempre mantendo o budget. A prioridade é explícita: **uma formação completa vale mais
que a regra de diversificação**, porque um time incompleto não é escalável.

*Os três níveis em detalhe: [`regras-de-negocio.md` › Limite Máximo por Clube (inclui TEC)](regras-de-negocio.md#limite-máximo-por-clube-inclui-tec).*

### Budget Máximo (C$) e Otimização por Orçamento

Com um teto de cartoletas, escolher os mais baratos é a resposta errada: o objetivo não é economizar,
é **maximizar o score dentro do teto**. Por isso a seleção sob orçamento é um *multiple-choice
knapsack* por posição resolvido com branch-and-bound (`OtimizadorTitulares`), com custo-benefício
(`score/preço`) apenas como critério de desempate entre soluções de score igual.

Sem teto (`budgetMaximo = 0` e sem `orcamento`), a constraint é desativada e vale a seleção gulosa
por score — que já é ótima nesse caso, e não vale pagar uma busca por ela.

A poda por clube é onde a otimalidade poderia ser perdida por descuido: descartar um atleta porque
existe outro do mesmo clube que o domina só é seguro quando existem **ao menos
`min(vagas da posição, limiteAtletasPorClube)`** dominadores, porque numa posição de várias vagas
dois atletas do mesmo clube podem ser escalados juntos. Uma poda mais agressiva devolveria um time
subótimo sem sinalizar nada.

Quando a formação não fecha, a causa importa: falta de orçamento produz `avisoOrcamento` e um time
*best-effort*; incompletude vinda das **restrições de clube/defesa** não é problema de orçamento, e
o montador recorre à seleção gulosa — que relaxa essas regras em último recurso — para não atribuir
ao orçamento uma culpa que não é dele. E há uma guarda de iterações no branch-and-bound: estourado o
teto, cai na seleção gulosa por orçamento, garantindo resposta sempre válida em vez de uma busca que
não termina.

*O algoritmo e o contrato da resposta:
[`regras-de-negocio.md` › Budget Máximo (C$) e Otimização por Orçamento](regras-de-negocio.md#budget-máximo-c-e-otimização-por-orçamento).*

### Reserva de Luxo

A **reserva de luxo** é a reserva com maior score — e não o segundo melhor titular, como numa versão
anterior. A reserva de luxo só entra em campo se um titular não jogar, então o que interessa é o
melhor *entre quem está no banco*. `TEC` não tem reserva e, portanto, não concorre.

## Pendências Conhecidas

### Dados e Algoritmos
- [x] **Score específico por posição** (goleiros: defesas difíceis; atacantes: gols + assistências)
- [x] **Dicionário de aliases** para nomes de clubes divergentes entre as APIs
- [ ] Ponderar a odd como **variável contínua** em vez de bônus binário

### Infraestrutura
- [ ] **Retry** com backoff exponencial via Spring Retry
- [x] **Métricas** com Spring Actuator + Micrometer, com dashboard e alertas da cota (ver [`operacao.md` › Observabilidade](operacao.md#observabilidade))
- [ ] **Credencial de conta de máquina** para o scrape do Prometheus ([#44](https://github.com/FabioCarlesso/cartolaoddsapi/issues/44))
- [ ] **`TRUSTED_PROXIES`** fixado na faixa real da borda ([#39](https://github.com/FabioCarlesso/cartolaoddsapi/issues/39))
- [ ] **Cobertura de testes** com JaCoCo + relatório HTML

### Regras de Negócio
- [x] **Constraint de budget** máximo (C$) — resolvida com branch-and-bound próprio (ver [`regras-de-negocio.md` › Budget Máximo (C$) e Otimização por Orçamento](regras-de-negocio.md#budget-máximo-c-e-otimização-por-orçamento))
- [x] **Formações alternativas** configuráveis, com comparação via `GET /api/time/comparar`
- [ ] **Simulação** de diferentes `ODD_LIMITE` para comparar times resultantes

### Qualidade
- [ ] **Testes de integração** com WireMock simulando as APIs externas

### Acesso
- [ ] **Convite por e-mail e recuperação de senha** — fora do escopo da [#37](https://github.com/FabioCarlesso/cartolaoddsapi/issues/37)

---

---

## Contexto de Uso

- **Temporada:** Brasileirão Série A
- **Rodada:** determinada dinamicamente via status do mercado Cartola
- **Mercado fechado:** todos os endpoints retornam campo `avisoMercado` informando o estado atual
- **Plano free da Odds API:** 500 requisições/mês — o cache reduz o consumo significativamente
- **Sem API Key:** filtro por time favorito desativado; usa todos os atletas elegíveis por status e preço

---

## Onde está o resto

| Assunto | Arquivo |
|---|---|
| Endpoints, parâmetros e códigos de resposta | [`api.md`](api.md) |
| Regras de montagem do time | [`regras-de-negocio.md`](regras-de-negocio.md) |
| Autenticação, matriz de acesso e usuários | [`seguranca.md`](seguranca.md) |
| Variáveis de ambiente e propriedades | [`configuracao.md`](configuracao.md) |
| Migrations e parâmetros de negócio | [`banco-de-dados.md`](banco-de-dados.md) |
| Cache, cota, Actuator e alertas | [`operacao.md`](operacao.md) |
| Estrutura de pacotes e camadas | [`arquitetura.md`](arquitetura.md) |
| Cobertura de testes por classe | [`desenvolvimento.md`](desenvolvimento.md) |
| Subir o projeto | [README](../README.md) |
