# Operação

> **Papel deste arquivo:** cache, guardrail de cota da The Odds API, Actuator, métricas, dashboard e alertas.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Cota da The Odds API: guardrail e sondagem

O plano free da The Odds API dá **500 requisições/mês**, e é o único componente pago da stack.
A The Odds API devolve o saldo restante em todo response, nos headers `x-requests-remaining` e
`x-requests-used` — inclusive nas respostas de **erro**, que é onde o saldo aparece quando a cota
estoura. O `OddsClient` lê esses headers nos dois caminhos e expõe o último valor conhecido.

*As razões de cada uma dessas escolhas estão em [`context.md` › Guardrail de cota da The Odds
API](context.md#guardrail-de-cota-da-the-odds-api).*

**Como o mecanismo se comporta:**

| Peça | Propriedade | Padrão | Comportamento |
|---|---|---|---|
| Guardrail | `odds.api.min-requests-remaining` | `50` | Abaixo desse saldo o `OddsClient` para de chamar o provedor e serve a **última resposta conhecida**, persistida na tabela `odds_snapshot` |
| Sondagem | `odds.api.sonda-intervalo-horas` | `24` | Com o guardrail ativo, uma chamada por intervalo é liberada para reavaliar o saldo. O intervalo conta a partir da **tentativa**, não da leitura bem-sucedida |
| TTL normal | `odds.api.cache-ttl-minutos` | `60` | Vale para resposta **com jogos** |
| TTL degradado | `odds.api.cache-ttl-degradado-minutos` | `10` | Vale para resposta **sem nenhum jogo** |

- **Estado persistido** na tabela `odds_cota` (linha única) e recuperado no boot.
- **Histórico append-only** na tabela `odds_cota_historico`: uma linha por leitura de header,
  gravada ao lado do estado corrente. Sem retenção — no plano free, no máximo ~500 linhas por mês.
- **Snapshot:** uma resposta **sem nenhum jogo** nunca sobrescreve o snapshot, e fica no cache só
  por `cache-ttl-degradado-minutos`. Uma resposta servida pelo snapshot é cacheada pelo **tempo que
  resta** do TTL, contado de quando o provedor produziu aquelas odds.
- **Atalho de boot:** o snapshot substitui uma chamada **apenas na primeira busca após o boot**
  (cache Caffeine frio depois de um redeploy). Depois disso, todo miss de cache — TTL vencido ou
  `DELETE /api/cache` — chega ao provedor, sujeito ao guardrail.
- **Origem visível:** quando uma resposta usa o snapshot em vez de uma consulta ao vivo — por
  guardrail ativo, falha no provedor, ou atalho de boot —, isso fica explícito no campo
  `oddsDeSnapshot` de `GET /api/favoritos`.
- **Log:** `WARN` quando o saldo cruza o **dobro do mínimo** e o próprio mínimo configurado (com o
  padrão de `50`, os limiares são 100 e 50); `ERROR` quando o guardrail entra em ação ou quando o
  provedor falha sem snapshot disponível.

**Onde o saldo aparece:**

| Superfície | Acesso | Conteúdo |
|---|---|---|
| `GET /api/odds/cota` | `ADMIN` | Estado atual da cota, nos sete campos abaixo |
| `GET /api/odds/cota/historico` | `ADMIN` | Série das leituras na janela (`?dias=30`, de 1 a 92), em ordem cronológica, com `reinicioDeCota` marcando a primeira leitura de um ciclo novo |
| `/actuator/prometheus` | `ADMIN` | `odds_api_requests_total`, `odds_api_requests_remaining` e `odds_api_errors_total` — ver [Observabilidade](operacao.md#observabilidade) |

**Campos de `GET /api/odds/cota`.** Listados aqui, e não deixados para o Swagger, porque este é o
endpoint que se consulta **em produção** quando o guardrail arma — e em `prod` o springdoc responde
`404`:

| Campo | Conteúdo |
|---|---|
| `saldoRestante` | Último `x-requests-remaining` lido; `null` enquanto não houve leitura |
| `consumoMes` | Último `x-requests-used` lido |
| `ultimaLeitura` | Instante da leitura que produziu os dois acima |
| `minRequestsRemaining` | O mínimo configurado — o mesmo `odds.api.min-requests-remaining` |
| `guardrailAtivo` | `true` quando o saldo está abaixo do mínimo e o cliente parou de chamar o provedor |
| `ultimaSondagem` | Instante da última chamada liberada como sondagem; `null` se nenhuma ocorreu |
| `proximaSondagem` | Quando a próxima sondagem é liberada — **quando o guardrail se destrava sozinho** |

> **Configuração recusada no boot:** `ODDS_API_CACHE_TTL_DEGRADADO_MINUTOS` é um *piso* dentro de
> `ODDS_API_CACHE_TTL_MINUTOS`, então precisa caber nele; e as quatro variáveis do guardrail têm
> mínimo `1` (com `ODDS_API_SONDA_INTERVALO_HORAS=0` toda requisição viraria sondagem e o guardrail
> deixaria de existir na prática). A aplicação recusa a subir com esses valores inválidos, nomeando
> a propriedade.

---

---

## Cache (Caffeine)

O projeto usa **Caffeine** (cache em memória JVM) via `spring-boot-starter-cache`. Não requer
infraestrutura externa — o cache sobe junto com a aplicação e é reiniciado com ela.

### Caches registrados

| Cache | Dado cacheado | TTL | Justificativa |
|---|---|---|---|
| `odds` | Odds da The Odds API | `odds.api.cache-ttl-minutos`, padrão **60 min** (10 min para resposta sem jogos) | Odds de Brasileirão não mudam a cada poucos minutos, e um TTL curto multiplicava o consumo de cota |
| `atletas` | `/atletas/mercado` | 10 min | Mercado abre/fecha poucas vezes |
| `clubes` | `/clubes` | 10 min | Dados estáticos durante a temporada |
| `partidas` | `/partidas` | 10 min | Partidas da rodada são fixas |
| `pontuados` | `/atletas/pontuados` (chave: rodada) | 10 min | Histórico muda somente após cada rodada |
| `statusMercado` | `/mercado/status` | 10 min | Consultado com frequência |
| `configuracao` | Config do banco | — | Invalidado via `PATCH /api/config` ou `POST /api/config/reset` (`@CacheEvict`) |

```java
// CacheConfig.java — constantes de nome dos caches
CACHE_ODDS           = "odds"
CACHE_ATLETAS        = "atletas"
CACHE_CLUBES         = "clubes"
CACHE_PARTIDAS       = "partidas"
CACHE_PONTUADOS      = "pontuados"
CACHE_STATUS_MERCADO = "statusMercado"
CACHE_CONFIGURACAO   = "configuracao"
```

### Anotações

```java
// CartolaClient — @Cacheable em cada método
@Cacheable(CacheConfig.CACHE_ATLETAS)
public AtletaResponse buscarAtletas() { ... }

// Cache de pontuados com chave por rodada
@Cacheable(value = CacheConfig.CACHE_PONTUADOS, key = "#rodada")
public PontuadosResponse buscarPontuados(int rodada) { ... }
```

### Observações

- **Sem Redis:** cache em memória JVM — reiniciado com o container. O cache `odds` é o único com um
  fallback que sobrevive a isso: o snapshot persistido do [guardrail de cota](operacao.md#cota-da-the-odds-api-guardrail-e-sondagem).
- **Tamanho máximo:** 500 entradas por cache (`maximumSize(500)`).
- **Stats:** `recordStats()` habilitado.
- **Thread-safe:** Caffeine garante consistência em ambientes multi-thread. O `@Cacheable` de odds
  usa `sync = true`: sem ele, N misses simultâneos virariam N chamadas pagas para produzir o mesmo
  valor.

### Invalidação via API

`DELETE /api/cache` (`ADMIN`) permite forçar a atualização dos dados sem reiniciar a aplicação.

**Nomes de cache válidos:** `odds`, `atletas`, `clubes`, `partidas`, `pontuados`, `statusMercado`.
O cache `configuracao` é interno da camada de configuração e é invalidado automaticamente por
`PATCH /api/config` e `POST /api/config/reset`.

Os dois verbos respondem `200` com `cachesInvalidados`, `mensagem` e `timestamp`. Um nome
inválido responde `400`, e a mensagem traz a lista de nomes aceitos:

**Nome inválido — `400 Bad Request`:**
```json
{
  "status": 400,
  "erro": "Parametro invalido",
  "mensagem": "Cache 'xyz' nao encontrado. Caches validos: [odds, atletas, clubes, partidas, pontuados, statusMercado]",
  "timestamp": "2025-06-01T15:30:00"
}
```

**Caso de uso típico:** após um erro nos dados externos ou necessidade de forçar busca de odds
atualizadas, `DELETE /api/cache` garante que a próxima requisição busque dados frescos — sujeita ao
guardrail de cota.

---

---

## Observabilidade

A aplicação expõe endpoints de monitoramento via **Spring Boot Actuator**, com métricas coletadas
pelo **Micrometer** e exportadas no formato **Prometheus**.

### Endpoints do Actuator

O Actuator responde na **mesma porta da aplicação** (`8080`); quem protege é a
[matriz de acesso](seguranca.md#matriz-de-acesso-por-rota). Expostos via
`management.endpoints.web.exposure.include=health,info,metrics,prometheus`:

| Endpoint | Acesso | Descrição |
|---|---|---|
| `GET /actuator/health` | Público | Status de saúde (`UP` / `DOWN`) |
| `GET /actuator/info` | Público | Informações da build |
| `GET /actuator/metrics` | `ADMIN` | Lista todas as métricas disponíveis |
| `GET /actuator/metrics/{nome}` | `ADMIN` | Detalhe de uma métrica (ex: `http.server.requests`) |
| `GET /actuator/prometheus` | `ADMIN` | Métricas em formato Prometheus para scrape |

Endpoints sensíveis (`env`, `beans`, `heapdump`, etc.) **não** são expostos, nem para `ADMIN`.
A tag `application=cartolaoddsapi` é adicionada a todas as métricas via
`management.metrics.tags.application`.

**`GET /actuator/health`** — sem token, o corpo é só o status agregado
(`management.endpoint.health.show-details=when_authorized` com
`management.endpoint.health.roles=ADMIN`):

```json
{ "status": "UP" }
```

Com um token de `ADMIN`, a mesma rota detalha os componentes (`db`, `diskSpace`, `ping`).

**`GET /actuator/prometheus`** (trecho):

```
# HELP http_server_requests_seconds Duration of HTTP server request handling
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{application="cartolaoddsapi",...} 42
```

*Por que o Actuator saiu da porta 9090: [`context.md` › Observabilidade](context.md#observabilidade-spring-actuator--micrometer).*

### Métricas da cota

O guardrail de cota (#40) impede o desastre — a aplicação para de gastar antes de estourar o plano
—, mas não avisa ninguém de que armou. Enquanto está armado, a aplicação serve o último snapshot
conhecido, que envelhece em silêncio. Esta seção descreve o dashboard e os alertas que tornam esse
estado visível (#58). Nenhuma métrica nova foi adicionada: dashboard e alertas usam as três que o
`OddsClient` já expõe.

| Métrica | Tipo | O que mede |
|---|---|---|
| `odds_api_requests_total` | counter | Tentativas de chamada à The Odds API |
| `odds_api_errors_total` | counter | Tentativas que terminaram em erro |
| `odds_api_requests_remaining` | gauge | Saldo lido do header `x-requests-remaining` |

**Saldo baixo e saldo não lido são estados diferentes.** `odds_api_requests_remaining` exporta
`NaN` — e não o sentinela interno `-1` — enquanto nenhuma leitura aconteceu, justamente para não
fazer todo alerta de saldo baixo disparar a cada deploy.

### Arquivos versionados

Tudo vive em [`docs/observabilidade/`](observabilidade/):

| Arquivo | Para que serve |
|---|---|
| `grafana-cota-odds.json` | Dashboard do Grafana (uid `cota-the-odds-api`): saldo restante, consumo do mês, taxa de erro e estado do guardrail |
| `alertas-cota-odds.yml` | Regras de alerta do Prometheus |
| `alertas-cota-odds.test.yml` | Teste das regras (`promtool test rules`) |
| `prometheus.yml` | Exemplo de configuração de scrape |

São quatro arquivos de texto, e é de propósito: **o projeto não sobe Prometheus nem Grafana.** Não
há serviço de observabilidade no `docker-compose.yml`. Quem já opera um Prometheus e um Grafana
copia o que precisa; quem não opera não herda uma segunda stack para cuidar.

> Para ver a cota não é preciso nada disto. `GET /api/odds/cota` devolve o estado atual nos sete
> campos, incluindo o próprio `minRequestsRemaining`, e `GET /api/odds/cota/historico` devolve a
> série do mês (ver [Cota da The Odds API: guardrail e sondagem](operacao.md#cota-da-the-odds-api-guardrail-e-sondagem)). O que estes arquivos
> acrescentam sobre os dois endpoints é a **avaliação contínua** — algo perguntando pelo saldo sem
> ninguém abrir tela — e a taxa de erro sobre `odds_api_errors_total` / `odds_api_requests_total`,
> que não têm endpoint equivalente.

### Apontar um Prometheus para a aplicação

O alvo é `GET /actuator/prometheus`, que exige um token de `ADMIN`.
`docs/observabilidade/prometheus.yml` traz o `scrape_config` pronto para copiar:

```yaml
scrape_configs:
  - job_name: cartola-odds
    metrics_path: /actuator/prometheus
    authorization:
      type: Bearer
      credentials_file: /etc/prometheus/scrape-token
    static_configs:
      - targets: ['cartola-odds:8080']
```

O token vai num **arquivo**, e não inline: o Prometheus não expande variáveis de ambiente no próprio
config, e um token colado no YAML iria para o repositório. O caminho
`docs/observabilidade/scrape-token` está no `.gitignore` para o caso de você criar o arquivo aqui.

As regras de alerta entram no `rule_files` do mesmo Prometheus. Depois de copiar, recarregue:

```bash
curl -X POST http://prometheus:9090/-/reload
```

> ⚠️ **Limite conhecido.** O access token expira em `JWT_EXPIRATION_MS` (padrão 24 h) e não há
> renovação: a coleta para quando ele vence e volta quando alguém cola um token novo. O sintoma é o
> alvo `cartola-odds` aparecer como `DOWN` com `401` em `/targets`. Uma credencial de conta de
> máquina está na [issue #44](https://github.com/FabioCarlesso/cartolaoddsapi/issues/44) — até lá, a
> coleta contínua não tem caminho sustentável.

### Importar o dashboard

No seu Grafana: **Dashboards → New → Import → Upload JSON file**, apontando para
`docs/observabilidade/grafana-cota-odds.json`. Não é preciso editar o JSON — a fonte de dados é uma
variável no topo do dashboard, e o Grafana pede para escolhê-la na importação.

**Duas variáveis no topo do dashboard** existem porque os valores correspondentes não são exportados
como métrica:

| Variável | Padrão | Espelha |
|---|---|---|
| `minimo` | `50` | `odds.api.min-requests-remaining` (`ODDS_API_MIN_REQUESTS_REMAINING`) |
| `cota_mensal` | `500` | Requisições/mês do plano contratado na The Odds API |

Mudou a configuração da aplicação? Mude aqui e em `alertas-cota-odds.yml` também —
`ArtefatosObservabilidadeTest` quebra quando o mínimo diverge do `application.properties`.

### Painéis

| Painel | Expressão | Leitura |
|---|---|---|
| Saldo restante | `odds_api_requests_remaining` | Último saldo lido do provedor |
| Consumo no mês | `$cota_mensal - odds_api_requests_remaining` | `remaining + used` somam a cota do plano, e `used` não é métrica |
| Taxa de erro | `increase(errors[janela]) / increase(requests[janela])` | Taxa legítima: os dois contadores medem tentativas |
| Guardrail | `clamp(sgn($minimo - saldo), 0, 1)` | `ARMADO` / `desarmado` / `sem leitura ainda` |
| Saldo ao longo do mês | `odds_api_requests_remaining` | Linha tracejada no mínimo do guardrail |
| Chamadas e erros por hora | `increase(...[1h])` | Onde o crédito foi gasto |

O painel de saldo mostra `sem leitura ainda` em vez de zero, e o painel de guardrail preserva o
`NaN` pela aritmética (`clamp(sgn(...))`), em vez de colapsá-lo em "desarmado".

### Alertas e o que fazer quando cada um dispara

As regras estão em `docs/observabilidade/alertas-cota-odds.yml`. Num Prometheus já existente, copie
o arquivo para o diretório de regras e recarregue (`curl -X POST http://prometheus:9090/-/reload`).

| Alerta | Dispara quando | Severidade |
|---|---|---|
| `CotaOddsApiAbaixoDoMinimo` | Saldo abaixo do mínimo por 15 min | `warning` |
| `CotaOddsApiGuardrailArmadoHaUmDia` | O mesmo, por 24 h contínuas | `critical` |
| `CotaOddsApiSaldoSemLeitura` | Saldo parado por mais de 25 h | `warning` |
| `CotaOddsApiTaxaDeErroAlta` | Mais de 50% de erro em 6 h, com pelo menos 3 chamadas | `warning` |

**`CotaOddsApiAbaixoDoMinimo` — o guardrail armou.** Informativo de propósito: a aplicação já se
defendeu sozinha, parou de chamar o provedor e passou a servir o snapshot persistido. Nada quebrou;
o que muda é que as odds começam a envelhecer. Confira saldo e janela de destravamento em
`GET /api/odds/cota` (campo `proximaSondagem`). Perto da virada do mês não há o que fazer: a
sondagem periódica reavalia o saldo sozinha e o guardrail desarma.

**`CotaOddsApiGuardrailArmadoHaUmDia` — o que pede decisão humana.** Um dia inteiro armado significa
que a sondagem já rodou e o saldo continua no chão: a cota não volta sozinha antes da virada do mês.
Decida entre aumentar o plano da The Odds API e aceitar odds de mais de 24 h. Se aceitar, silencie o
alerta **com data para acabar** — não o desligue.

**`CotaOddsApiSaldoSemLeitura` — o número no painel virou lembrança.** O saldo só muda quando uma
chamada acontece; parado por mais de um intervalo de sondagem (`odds.api.sonda-intervalo-horas`,
padrão 24 h), ele descreve o passado. Compare com `ultimaLeitura` em `GET /api/odds/cota`. Se a
aplicação está de fato ociosa, é ruído — aumente o `for:` da regra. Se está recebendo tráfego, o
próximo lugar a olhar é a taxa de erro e os logs do `OddsClient`.

**`CotaOddsApiTaxaDeErroAlta` — está queimando crédito sem produzir odds.** Cada tentativa que falha
consome crédito igual. Os logs do `OddsClient` separam as causas: chave inválida e cota estourada
respondem `4xx` (e o saldo real vem nos headers da própria resposta de erro), provedor fora do ar dá
timeout. Se for cota estourada, o guardrail arma em seguida sozinho.

> As duas primeiras regras disparam juntas quando o problema persiste. Com Alertmanager, configure
> uma inibição de `critical` sobre `warning` no mesmo `componente`.

O envio das notificações (e-mail, Slack) está **fora do escopo**: depende do Alertmanager e da
preferência de quem opera.

### Testar as regras

As regras têm teste próprio, incluindo o comportamento com `NaN` — que é o detalhe que faz um deploy
novo não disparar todo alerta de saldo:

```bash
docker run --rm --entrypoint promtool \
  -v "$PWD/docs/observabilidade:/cfg:ro" prom/prometheus:v2.53.0 \
  test rules /cfg/alertas-cota-odds.test.yml
```

Do lado da aplicação, `ArtefatosObservabilidadeTest` confere que todo nome `odds_api_*` citado no
dashboard e nas regras existe de fato na exposição, e que o mínimo do guardrail nos artefatos é o
mesmo do `application.properties`.

*Por que os artefatos ficam em `docs/` e não no compose, e por que o `for: 25h`:
[`context.md` › Dashboard e alertas da cota](context.md#dashboard-e-alertas-da-cota).*

---

---

## Ver também

- [`configuracao.md`](configuracao.md) — as propriedades do guardrail e do cache
- [`api.md`](api.md) — o contrato de `/api/odds/cota` e de `/api/cache`
- [`seguranca.md`](seguranca.md) — por que `metrics` e `prometheus` exigem `ADMIN`
- [`context.md`](context.md) — por que o guardrail, o snapshot e o `NaN` são assim
