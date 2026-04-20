# ⚽ Cartola FC — Odds API

> **Stack:** Java 21 · Spring Boot 3.4.5 · Maven · JAR  
> **Versão:** 1.0.0

API REST que monta automaticamente um time competitivo para o Cartola FC cruzando odds do Brasileirão com métricas dos atletas da plataforma.

---

## Índice

1. [Stack e Dependências](#1-stack-e-dependências)
2. [APIs Externas](#2-apis-externas)
3. [Configuração](#3-configuração)
4. [Regras de Negócio](#4-regras-de-negócio)
5. [Fluxo de Execução](#5-fluxo-de-execução)
6. [Estrutura do Projeto](#6-estrutura-do-projeto)
7. [Referência de Funções](#7-referência-de-funções)
8. [Referência de Dados](#8-referência-de-dados)
9. [Testes](#9-testes)
10. [Swagger / OpenAPI](#10-swagger--openapi)
11. [Docker](#11-docker)
12. [Como Executar](#12-como-executar)
12. [Melhorias Futuras](#12-melhorias-futuras)

---

## 1. Stack e Dependências

| Tecnologia | Versão | Uso |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework web, IoC, configuração |
| Maven | 3.9+ | Build e gerenciamento de dependências |
| Lombok | latest | `@Builder`, `@Getter`, `@With` — reduz boilerplate |
| springdoc OpenAPI | 2.8.8 | Swagger UI e spec OpenAPI 3 automática |
| JUnit 5 | via starter-test | Testes unitários e parametrizados |
| Mockito | via starter-test | Mocking de dependências |
| AssertJ | via starter-test | Assertions fluentes |
| MockMvc | via starter-test | Testes de camada web |

**Instalação:**
```bash
mvn clean package -DskipTests
```

---

## 2. APIs Externas

### 2.1 The Odds API

| Atributo | Valor |
|---|---|
| URL base | `https://api.the-odds-api.com/v4` |
| Endpoint | `GET /sports/soccer_brazil_campeonato/odds` |
| Autenticação | Query param `apiKey` |
| Plano gratuito | 500 requisições/mês |
| Header útil | `X-Requests-Remaining` — quota restante |

**Exemplo de chamada:**
```java
restClient.get()
    .uri(b -> b.path("/sports/soccer_brazil_campeonato/odds")
               .queryParam("regions", "us")
               .queryParam("markets", "h2h")
               .queryParam("apiKey", key)
               .build())
    .retrieve()
    .body(new ParameterizedTypeReference<List<OddsResponse>>() {});
```

**Estrutura do retorno:**
```json
{
  "home_team": "Flamengo",
  "away_team": "Palmeiras",
  "bookmakers": [{
    "markets": [{
      "key": "h2h",
      "outcomes": [
        { "name": "Flamengo",  "price": 2.10 },
        { "name": "Palmeiras", "price": 3.40 },
        { "name": "Draw",      "price": 3.20 }
      ]
    }]
  }]
}
```

### 2.2 Cartola FC API (pública, sem autenticação)

**URL base:** `https://api.cartola.globo.com`

| Endpoint | Finalidade | Campos principais |
|---|---|---|
| `/mercado/status` | Status do mercado | `status_mercado` (1=aberto), `rodada_atual` |
| `/atletas/mercado` | Atletas disponíveis | `apelido`, `posicao_id`, `clube_id`, `status_id`, `media_num`, `variacao_num`, `preco_num` |
| `/clubes` | Mapa id → nome/sigla | `nome`, `abreviacao` |
| `/partidas` | Partidas da rodada | `clube_casa_id`, `clube_visitante_id` |
| `/atletas/pontuados` | Pontuação pós-rodada | `pontuacao`, `scout` *(melhoria futura)* |

---

## 3. Configuração

Arquivo: `src/main/resources/application.properties`

```properties
# ── Obrigatório ───────────────────────────────────────────────────────
odds.api.key=SUA_API_KEY_AQUI

# ── The Odds API ──────────────────────────────────────────────────────
odds.api.base-url=https://api.the-odds-api.com/v4
odds.api.sport=soccer_brazil_campeonato
odds.api.regions=us
odds.api.markets=h2h
odds.api.timeout=10000

# ── Cartola FC API ────────────────────────────────────────────────────
cartola.api.base-url=https://api.cartola.globo.com
cartola.api.timeout=15000

# ── Regras de negócio ─────────────────────────────────────────────────
cartola.odd-limite=3.0          # odd máxima para considerar time favorito

cartola.formacao.GOL=1
cartola.formacao.LAT=2
cartola.formacao.ZAG=2
cartola.formacao.MEI=3
cartola.formacao.ATA=3
cartola.formacao.TEC=1

cartola.score.peso.media-pontos=0.40
cartola.score.peso.valorizacao=0.20
cartola.score.peso.desempenho=0.20
cartola.score.peso.fator-casa=0.10
cartola.score.peso.time-favorito=0.10

# ── Swagger ───────────────────────────────────────────────────────────
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.try-it-out-enabled=true
springdoc.api-docs.path=/v3/api-docs

# ── Servidor ──────────────────────────────────────────────────────────
server.port=8080
```

> ⚠️ **Atenção — `@Qualifier` com Lombok:** `@Qualifier` em campos `final` com `@RequiredArgsConstructor` **não funciona** — o Lombok ignora a anotação. `OddsClient` e `CartolaClient` usam construtores explícitos com `@Qualifier` no parâmetro do construtor.

---


## 4. Cache (Caffeine)

### 4.1 Configuração

O projeto usa **Caffeine** (cache em memória JVM) via `spring-boot-starter-cache`.
Não requer infraestrutura externa — o cache sobe junto com a aplicação.

```java
// CacheConfig.java — constantes de nome dos caches
CACHE_ODDS           = "odds"           // TTL: 10 min
CACHE_ATLETAS        = "atletas"        // TTL: 10 min
CACHE_CLUBES         = "clubes"         // TTL: 10 min
CACHE_PARTIDAS       = "partidas"       // TTL: 10 min
CACHE_PONTUADOS      = "pontuados"      // TTL: 10 min (por rodada)
CACHE_STATUS_MERCADO = "statusMercado"  // TTL: 10 min
```

### 4.2 Estratégia por endpoint

| Cache | Endpoint cacheado | Justificativa |
|---|---|---|
| `odds` | `GET /sports/.../odds` | Odds mudam pouco durante o dia |
| `atletas` | `GET /atletas/mercado` | Mercado abre/fecha poucas vezes |
| `clubes` | `GET /clubes` | Dados estáticos durante a temporada |
| `partidas` | `GET /partidas` | Partidas da rodada são fixas |
| `pontuados` | `GET /atletas/pontuados` | Histórico muda somente após cada rodada |
| `statusMercado` | `GET /mercado/status` | Consultado com frequência |

### 4.3 Anotações

```java
// CartolaClient — @Cacheable em cada método
@Cacheable(CacheConfig.CACHE_ATLETAS)
public AtletaResponse buscarAtletas() { ... }

// Cache de pontuados com chave por rodada
@Cacheable(value = CacheConfig.CACHE_PONTUADOS, key = "#rodada")
public PontuadosResponse buscarPontuados(int rodada) { ... }
```

### 4.4 Observações

- **Sem Redis:** cache em memória JVM — reiniciado com o container.
- **Tamanho máximo:** 500 entradas por cache (`maximumSize(500)`).
- **Stats:** `recordStats()` habilitado — métricas acessíveis via Actuator futuramente.
- **Thread-safe:** Caffeine garante consistência em ambientes multi-thread.

## 5. Regras de Negócio

### 4.1 Identificação de Times Favoritos

1. Para cada jogo, seleciona o time com **menor odd** (maior probabilidade de vitória).
2. Aplica `ODD_LIMITE` (padrão `3.0`):
   - `odd ≤ ODD_LIMITE` → time entra no conjunto `favoritos_norm`
   - `odd > ODD_LIMITE` → jogo descartado (equilibrado ou sem favorito claro)
3. Nomes normalizados antes do cruzamento com dados do Cartola.

```
Flamengo x Palmeiras → odds: FLA 2.10 / PAL 3.40
Favorito: Flamengo (2.10 ≤ 3.0) ✅

Fortaleza x Bahia → odds: FOR 3.30 / BAH 3.40
Favorito: Fortaleza (3.30 > 3.0) ⛔ descartado
```

### 4.2 Filtros de Atletas

| Filtro | Regra | Fallback |
|---|---|---|
| `status_id` | `6` (Dúvida) ou `7` (Provável) | Descartado |
| `preco_num` | `> 0` | Descartado |
| Time favorito | Clube em `favoritos_norm` | Descartado |
| Sem odds | `favoritos_norm` vazio | Filtro desativado — usa todos os elegíveis |

### 4.3 Fórmula do Score

```
score = (mediaPontos  × 0.40)
      + (valorização  × 0.20)
      + (desempenho   × 0.20)   ← proxy: mediaPontos
      + (fatorCasa    × 0.10)   ← 10.0 se mandante, 0 caso contrário
      + (timeFavorito × 0.10)   ← 10.0 se favorito pelas odds, 0 caso contrário
```

### 4.4 Formação 4-3-3

| Slot | `posicao_id` | Qtd | Pool elegível |
|---|---|---|---|
| GOL | 1 | 1 | Somente `posicao == GOL` |
| LAT | 2 | 2 | Somente `posicao == LAT` |
| ZAG | 3 | 2 | Somente `posicao == ZAG` |
| MEI | 4 | 3 | Somente `posicao == MEI` |
| ATA | 5 | 3 | Somente `posicao == ATA` |
| TEC | 6 | 1 | Somente `posicao == TEC` |

Cada slot seleciona **exclusivamente** dentro da sua posição.

### 4.5 Seleção de Reservas

- Somente `status == PROVAVEL` (7) — dúvidas não são reservas.
- Preferencialmente mais baratos que o titular mais caro da posição.
- Fallback: qualquer provável da posição se nenhum mais barato existir.
- Sempre da **mesma posição individual** do titular (LAT reserva LAT, ZAG reserva ZAG).

### 4.6 Capitão e Reserva de Luxo

- **Capitão:** maior score, prioridade `ATA > MEI > ZAG > LAT > GOL > TEC`
- **Reserva de Luxo:** segundo maior score global (qualquer posição)
- O capitão tem pontuação **dobrada** no Cartola FC.

### 4.7 Tratamento de Dúvidas

- Titulares com `status_id == 6` são escalados, mas marcados com `⚠️ DÚVIDA`.
- Sistema busca o melhor substituto `PROVAVEL` na **mesma posição individual**.
- Substituto nunca é outro atleta já escalado como titular.
- Alertas retornados em `alertasDuvida` no `TimeResponse`.


### 4.9 Endpoint de Ranking (`GET /api/ranking`)

Retorna os melhores atletas disponíveis ordenados por score decrescente.  
Aplica os **mesmos filtros do `/api/time`** (status, preço e time favorito).

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| `posicao` | string | *(todos)* | Filtra por posição: `GOL`, `LAT`, `ZAG`, `MEI`, `ATA`, `TEC` |
| `limite` | int | `25` | Número de resultados (1–100) |

**Comportamentos:**
- `limite <= 0` → tratado como `1`
- `limite > 100` → clampado para `100`
- `posicao` inválida → HTTP 400 com mensagem detalhada
- Atletas em dúvida aparecem com `emDuvida: true` no response


### 4.10 Endpoint de Favoritos (`GET /api/favoritos`)

Lista todos os jogos da rodada classificados em **favoritos** e **descartados**.

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| `oddLimite` | double | *valor do properties* | Odd máxima para considerar um time favorito. Deve ser `> 1.0`. |

**Lógica por jogo:**
- Seleciona o time com menor odd (excluindo empate) como candidato a favorito.
- Se a menor odd ≤ `oddLimite` → jogo entra em **favoritos** com todos os detalhes.
- Se a menor odd > `oddLimite` → jogo entra em **descartados** com motivo legível.

**Campos retornados para cada favorito:**
- `timeFavorito`, `oddFavorito` — quem é e qual a odd
- `timeAdversario`, `oddAdversario` — adversário e sua odd
- `oddEmpate` — odd do empate quando disponível
- `favoritoEmCasa` — `true` se o favorito é o time mandante

**Validação:** `oddLimite <= 1.0` retorna HTTP 400 (odd de 1.0 ou menos é matematicamente impossível em apostas reais).

### 4.8 Normalização de Nomes

```java
// Remove acentos, converte para lowercase, elimina especiais
NormalizadorUtil.normalizar("Atlético-MG")  // → "atletico mg"
NormalizadorUtil.normalizar("São Paulo FC") // → "sao paulo fc"
NormalizadorUtil.normalizar("Grêmio")       // → "gremio"
```

> ⚠️ **Ponto fraco:** grafias muito divergentes entre APIs podem falhar no cruzamento. Melhoria futura: dicionário de aliases.

---

## 6. Fluxo de Execução

```
GET /api/time
      │
      ▼
1. buscarStatusMercado()        → rodada_atual, mercado aberto/fechado
      │
      ▼
2. buscarFavoritos()            → favoritos_norm (nomes normalizados)
      │
      ▼
3. buscarAtletasFiltrados()     → pool limpo (status + preço + time favorito)
      │                           lança IllegalStateException se vazio
      ▼
4. buscarTimesCasa()            → Set<Integer> IDs dos mandantes
      │
      ▼
5. calcularScores()             → pool com score ponderado por atleta
      │
      ▼
6. selecionar titulares         → top-N por score em cada posição
      │
      ▼
7. selecionar reservas          → provável, mesma posição, mais barato
      │
      ▼
8. mapear substitutos           → provável da mesma posição para cada dúvida
      │
      ▼
9. eleger capitão/reserva luxo → maior e segundo maior score
      │
      ▼
10. TimeResponse.from(time)    → HTTP 200 com JSON
```

---

## 7. Estrutura do Projeto

```
cartola/
├── Dockerfile               # Multi-stage build (JDK 21 build + JRE 21 runtime)
├── docker-compose.yml       # Orquestração com healthcheck e resource limits
├── .env.example             # Template de variáveis de ambiente
├── .dockerignore
├── pom.xml
├── README.md
├── docs/
│   ├── documentacao.md
│   └── documentacao.docx
└── src/
    ├── main/java/com/cartola/odds/
│   ├── CartolaOddsApplication.java      # @SpringBootApplication + @ConfigurationPropertiesScan
│   ├── config/
│   │   ├── CacheConfig.java             # Caffeine: 6 caches, TTL 10 min, maxSize 500
│   │   ├── AppProperties.java           # odd-limite, formacao, pesos (bound do .properties)
│   │   ├── OddsProperties.java          # key, baseUrl, sport, regions, markets, timeout
│   │   ├── CartolaProperties.java       # baseUrl, timeout
│   │   ├── RestClientConfig.java        # beans oddsRestClient e cartolaRestClient
│   │   └── OpenApiConfig.java           # metadados Swagger UI
│   ├── client/
│   │   ├── OddsClient.java              # GET /odds — retorna lista vazia em falha
│   │   └── CartolaClient.java           # GET /mercado/status, /atletas/mercado, /clubes, /partidas
│   ├── service/
│   │   ├── OddsService.java             # extrai favoritos com filtro ODD_LIMITE
│   │   ├── DesempenhoService.java       # média das últimas 5 rodadas por atletaId
│   │   ├── RankingService.java          # top-N atletas por score com filtros opcionais
│   │   └── OddsService.java             # buscarFavoritos() e buscarFavoritosDetalhado()             # extrai favoritos com filtro ODD_LIMITE
│   │   ├── CartolaDataService.java      # filtra atletas: status + preço + time favorito
│   │   ├── ScoreService.java            # calcula score ponderado (5 componentes)
│   │   ├── MontadorTimeService.java     # monta titulares, reservas, capitão, substitutos
│   │   └── PipelineService.java         # orquestra todas as etapas
│   ├── controller/api/
│   │   ├── TimeApi.java                 # Swagger + contrato REST do /api/time
│   │   ├── RankingApi.java              # Swagger + contrato REST do /api/ranking
│   │   └── FavoritosApi.java            # Swagger + contrato REST do /api/favoritos
│   ├── controller/
│   │   ├── TimeController.java          # GET /api/time com anotações Swagger
│   │   ├── RankingController.java       # GET /api/ranking com filtros posicao e limite
│   │   └── FavoritosController.java     # GET /api/favoritos com oddLimite customizavel          # GET /api/time com anotações Swagger
│   │   └── GlobalExceptionHandler.java  # 422, 502, 500
│   ├── model/
│   │   ├── Atleta.java                  # @Builder + @With — imutável com score e substituto
│   │   ├── Time.java                    # resultado final da montagem
│   │   ├── enums/
│   │   │   ├── Posicao.java             # GOL(1) LAT(2) ZAG(3) MEI(4) ATA(5) TEC(6)
│   │   │   └── StatusAtleta.java        # PROVAVEL(7) DUVIDA(6) + não escaláveis
│   │   └── response/
│   │       ├── TimeResponse.java        # DTO de saída com @Schema para Swagger
│   │       ├── ErrorResponse.java       # status, erro, mensagem, timestamp
│   │       ├── OddsResponse.java        # desserializa resposta da Odds API
│   │       ├── AtletaResponse.java      # desserializa /atletas/mercado
│   │       ├── ClubeResponse.java       # desserializa /clubes
│   │       ├── MercadoStatusResponse.java
│   │       └── PartidaResponse.java
│   ├── model/response/
│   │   ├── RankingResponse.java        # DTO de saída do endpoint de ranking
│   │   └── FavoritosResponse.java      # DTO com favoritos, descartados e metadados
│   └── util/
│       └── NormalizadorUtil.java        # normalização de nomes (Unicode NFD)
└── test/java/com/cartola/odds/
    ├── CartolaOddsApplicationTests.java  # sobe contexto Spring completo
    ├── controller/
    │   └── TimeControllerTest.java       # MockMvc — HTTP status + corpo JSON
    ├── model/
    │   ├── AtletaTest.java               # imutabilidade, formatado(), isDuvida()
    │   └── EnumsTest.java                # fromId, fromSigla, isEscalavel
    ├── service/
    │   ├── OddsServiceTest.java          # filtro ODD_LIMITE, normalização, múltiplos jogos
    │   ├── CartolaDataServiceTest.java   # filtros de status/preço/favorito, mapeamento
    │   ├── ScoreServiceTest.java         # pesos, bônus casa/favorito, imutabilidade
    │   ├── MontadorTimeServiceTest.java  # formação, capitão, reservas, dúvidas
    │   └── PipelineServiceTest.java      # orquestração, falhas, propagação de parâmetros
    └── util/
        └── NormalizadorUtilTest.java     # nomes com acento, hifen, nulo, idempotência
```

---

## 8. Referência de Funções

### `OddsService.buscarFavoritos() → Set<String>`
Busca odds da API e retorna nomes normalizados dos times com `odd ≤ ODD_LIMITE`.  
Retorna `Set.of()` se API indisponível ou chave não configurada.

### `CartolaDataService.buscarAtletasFiltrados(Set<String> favoritos) → List<Atleta>`
Busca atletas, clubes e partidas. Aplica filtros de status, preço e time favorito.  
Quando `favoritos` está vazio, ignora o filtro por time.

### `CartolaDataService.buscarTimesCasa() → Set<Integer>`
Retorna IDs dos times mandantes da rodada atual.

### `ScoreService.calcularScores(atletas, timesCasa, favoritos) → List<Atleta>`
Retorna nova lista imutável com campo `score` preenchido para cada atleta.

### `MontadorTimeService.montar(pool, rodada) → Time`
Seleciona titulares, reservas, capitão, reserva de luxo e substitutos.  
Retorna `Time` completo com alertas de dúvida.


### `DesempenhoService.calcularMediaUltimasRodadas(rodadaAtual) → Map<Integer, Double>`
Busca até 5 rodadas anteriores via `/atletas/pontuados` (cacheadas por rodada).
Retorna `{atletaId → médiaUltimasRodadas}`. Atletas sem histórico não aparecem no mapa (o `ScoreService` usa `mediaPontos` como fallback).

### `OddsService.buscarFavoritosDetalhado(oddLimite) → FavoritosResponse`
Processa todos os jogos da Odds API e classifica cada um em favorito ou descartado.
Retorna odds detalhadas de cada time, quem é favorito, se joga em casa e motivo do descarte.

### `RankingService.buscarRanking(posicao, limite) → RankingResponse`
Retorna os melhores atletas por score. Reutiliza o mesmo pipeline de filtros do `/api/time`.  
`posicao = null` retorna todas as posições. `limite` é clampado entre 1 e 100.

### `PipelineService.executar() → Time`
Orquestra todas as etapas. Lança `IllegalStateException` se pool vazio após filtragem.

### `MercadoStatusResponse.getAvisoMercado() → String|null`
Retorna o texto de aviso quando o mercado não está aberto; `null` quando aberto.
Propagado para todos os responses via `Time.avisoMercado` e `RankingResponse.avisoMercado`.

### `NormalizadorUtil.normalizar(String) → String`
Remove acentos (Unicode NFD), converte para lowercase, remove caracteres especiais.

---

## 9. Referência de Dados

### Colunas do domínio `Atleta`

| Campo | Tipo | Origem | Descrição |
|---|---|---|---|
| `apelido` | String | `/atletas/mercado` | Nome popular |
| `posicao` | Posicao | `POS_MAP[posicao_id]` | Enum: GOL/LAT/ZAG/MEI/ATA/TEC |
| `_nome_clube_norm` | String | `normalizar(nomeClube)` | Para cruzamento com odds |
| `_sigla_clube` | String | `/clubes[abreviacao]` | Exibida no formato `Nome (SIG)` |
| `status` | StatusAtleta | `/atletas/mercado` | PROVAVEL/DUVIDA/CONTUNDIDO/... |
| `mediaPontos` | double | `media_num` | Média da temporada |
| `valorizacao` | double | `variacao_num` | Variação última rodada |
| `preco` | double | `preco_num` | Preço em cartoletas (C$) |
| `score` | double | `ScoreService` | Score ponderado calculado |
| `substitutoProvavel` | Atleta | `MontadorTimeService` | Preenchido somente para dúvidas |


### Status do Mercado (`status_mercado`) — referência completa

| Código | Enum | Label | Exibe Aviso | Aviso retornado |
|---|---|---|---|---|
| 1 | `ABERTO` | Aberto | Não | — |
| 2 | `FECHADO` | Fechado | **Sim** | "Mercado fechado. Rodada em andamento." |
| 3 | `MANUTENCAO` | Manutencao | **Sim** | "Mercado em manutencao ou pre-temporada." |
| 4 | `PARCIAL` | Parcial | **Sim** | "Mercado parcialmente aberto. Alguns jogos ja ocorreram." |
| 6 | `FINALIZANDO` | Finalizando | **Sim** | "Processamento pos-rodada em andamento." |
| outros | `DESCONHECIDO` | Desconhecido | **Sim** | "Status desconhecido. Dados podem estar desatualizados." |

> **`avisoMercado` nos responses:** quando o mercado não está aberto, todos os endpoints
> (`/api/time`, `/api/ranking`) retornam o campo `avisoMercado` preenchido com o texto da tabela acima.
> Quando aberto, o campo é `null` (omitido no JSON).

### `status_id` — referência

| ID | Enum | Escalável |
|---|---|---|
| 2 | `CONTUNDIDO` | ❌ |
| 3 | `SUSPENSO` | ❌ |
| 5 | `NULO` | ❌ |
| 6 | `DUVIDA` | ✅ (com alerta) |
| 7 | `PROVAVEL` | ✅ |

### `posicao_id` — referência

| ID | Enum | Posição |
|---|---|---|
| 1 | `GOL` | Goleiro |
| 2 | `LAT` | Lateral |
| 3 | `ZAG` | Zagueiro |
| 4 | `MEI` | Meia |
| 5 | `ATA` | Atacante |
| 6 | `TEC` | Técnico |

---

## 10. Testes

### Cobertura por camada

| Arquivo | Tipo | Cenários cobertos |
|---|---|---|
| `CartolaOddsApplicationTests` | Integração | Contexto Spring sobe sem erros |
| `TimeControllerTest` | Web (MockMvc) | HTTP 200/422/502/500, corpo JSON, campos obrigatórios, timestamp no erro |
| `AtletaTest` | Unitário | `formatado()`, `isDuvida()`, `isProvavel()`, imutabilidade `@With` |
| `EnumsTest` | Unitário | `fromId()`, `fromSigla()`, `isEscalavel()`, `idsEscalaveis()` para todos os valores |
| `OddsServiceTest` | Unitário (Mockito) | Filtro ODD_LIMITE, normalização, múltiplos jogos, jogo sem bookmaker, set imutável |
| `CartolaDataServiceTest` | Unitário (Mockito) | Filtros status/preço/favorito, mapeamento de posição, fallback de sigla, times da casa |
| `ScoreServiceTest` | Unitário | Pesos ponderados, bônus casa, bônus favorito, acúmulo de bônus, imutabilidade, lista vazia |
| `MontadorTimeServiceTest` | Unitário | Formação 4-3-3, capitão, reserva de luxo, reservas por posição, dúvidas com substituto |
| `PipelineServiceTest` | Unitário (Mockito) | Pipeline completo, cada etapa chamada 1x, pool vazio lança exceção, propagação de parâmetros |
| `NormalizadorUtilTest` | Unitário | Acentos, hifens, maiúsculas, nulo, branco, idempotência |

### Executar

```bash
# Todos os testes
mvn test

# Apenas uma classe
mvn test -Dtest=ScoreServiceTest

# Com relatório de cobertura (adicionar JaCoCo ao pom.xml)
mvn test jacoco:report
```

### Exemplo de saída esperada

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- OddsServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- CartolaDataServiceTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- ScoreServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- MontadorTimeServiceTest
[INFO] Tests run:  8, Failures: 0, Errors: 0, Skipped: 0 -- PipelineServiceTest
[INFO] Tests run:  7, Failures: 0, Errors: 0, Skipped: 0 -- TimeControllerTest
[INFO] Tests run:  5, Failures: 0, Errors: 0, Skipped: 0 -- AtletaTest
[INFO] Tests run:  8, Failures: 0, Errors: 0, Skipped: 0 -- EnumsTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- NormalizadorUtilTest
[INFO] BUILD SUCCESS
```

---

## 11. Swagger / OpenAPI

| URL | Descrição |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interface gráfica — Try it out habilitado |
| `http://localhost:8080/v3/api-docs` | JSON OpenAPI 3 (importar no Postman/Insomnia) |

**Endpoints disponíveis:**

| Endpoint | Descrição |
|---|---|
| `GET /api/time` | Monta o time completo da rodada |
| `GET /api/ranking` | Top atletas por score com filtros opcionais |
| `GET /api/ranking?posicao=ATA` | Top atacantes |
| `GET /api/ranking?posicao=MEI&limite=10` | Top 10 meias |
| `GET /api/favoritos` | Times favoritos com oddLimite do properties |
| `GET /api/favoritos?oddLimite=2.5` | Favoritos com limite customizado |

**Respostas documentadas em `GET /api/time`:**

| Código | Cenário |
|---|---|
| `200` | Time montado com sucesso |
| `422` | Pool vazio — ODD_LIMITE muito restritivo ou sem API Key |
| `502` | Falha de comunicação com API externa |
| `500` | Erro interno inesperado |

---


## 12. Docker

### 11.1 Arquivos

| Arquivo | Descrição |
|---|---|
| `Dockerfile` | Build multi-stage: stage `build` (JDK 21 Alpine) + stage `runtime` (JRE 21 Alpine) |
| `docker-compose.yml` | Orquestração com variáveis de ambiente, healthcheck e resource limits |
| `.env.example` | Template de variáveis — copiar para `.env` antes de usar |
| `.dockerignore` | Exclui `target/`, `src/test/`, `docs/` e arquivos de IDE do contexto |
| `application.properties` | Lê variáveis de ambiente com fallback para valores padrão |

### 11.2 Dockerfile — Multi-stage Build

```
Stage 1 — build (eclipse-temurin:21-jdk-alpine)
  └── mvn clean package -DskipTests
        └── gera target/cartola-odds-1.0.0.jar

Stage 2 — runtime (eclipse-temurin:21-jre-alpine)
  └── COPY --from=build .../app.jar
  └── USER cartola (não-root)
  └── EXPOSE 8080
  └── ENTRYPOINT java -XX:+UseContainerSupport ...
```

**Decisões de design:**
- **Alpine** — imagem base mínima (~60 MB vs ~300 MB do Debian)
- **JRE no runtime** — não carrega o compilador na imagem final
- **Usuário não-root** — boa prática de segurança para containers em produção
- **`-XX:+UseContainerSupport`** — JVM respeita os limites de CPU/memória do container
- **`-XX:MaxRAMPercentage=75.0`** — usa até 75% da RAM disponível para o heap Java

### 11.3 Variáveis de Ambiente

O `application.properties` usa sintaxe `${VAR:default}` para ler variáveis do ambiente com fallback:

```properties
odds.api.key=${ODDS_API_KEY:SUA_API_KEY_AQUI}
cartola.odd-limite=${CARTOLA_ODD_LIMITE:3.0}
```

| Variável | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | `SUA_API_KEY_AQUI` | **Obrigatório** — chave da The Odds API |
| `APP_PORT` | `8080` | Porta exposta no host (somente docker-compose) |
| `CARTOLA_ODD_LIMITE` | `3.0` | Odd máxima para considerar um time favorito |
| `CARTOLA_SCORE_PESO_MEDIA_PONTOS` | `0.40` | Peso da média de pontos |
| `CARTOLA_SCORE_PESO_VALORIZACAO` | `0.20` | Peso da valorização |
| `CARTOLA_SCORE_PESO_DESEMPENHO` | `0.20` | Peso do desempenho |
| `CARTOLA_SCORE_PESO_FATOR_CASA` | `0.10` | Peso do bônus mandante |
| `CARTOLA_SCORE_PESO_TIME_FAVORITO` | `0.10` | Peso do bônus favorito |
| `SPRING_PROFILES_ACTIVE` | `default` | Profile do Spring Boot |

### 11.4 Comandos

```bash
# Início rápido
cp .env.example .env           # 1. copiar template
# editar .env com ODDS_API_KEY  # 2. inserir API Key
docker compose up -d           # 3. subir container

# Rebuild após mudança de código
docker compose up -d --build

# Ver logs
docker compose logs -f cartola-odds

# Status e healthcheck
docker compose ps

# Parar
docker compose down

# Build manual
docker build -t cartola-odds:1.0.0 .

# Executar sem Compose (passando variáveis diretamente)
docker run -p 8080:8080 \
  -e ODDS_API_KEY=sua_chave \
  -e CARTOLA_ODD_LIMITE=2.5 \
  cartola-odds:1.0.0
```

### 11.5 Resource Limits (docker-compose.yml)

```yaml
deploy:
  resources:
    limits:
      memory: 512m
      cpus: "1.0"
    reservations:
      memory: 256m
      cpus: "0.25"
```

Ajuste conforme o ambiente de destino. Para produção com carga alta, considere `memory: 768m`.

### 11.6 Healthcheck

O container verifica automaticamente se a aplicação está respondendo a cada 30 segundos:

```
GET http://localhost:8080/v3/api-docs → 200 OK = healthy
```

`start_period: 45s` — aguarda a JVM inicializar antes de começar as verificações.

## 13. Como Executar

```bash
# 1. Configure a API Key
# src/main/resources/application.properties
# odds.api.key=sua_chave_aqui

# 2. Build
mvn clean package -DskipTests

# 3. Run
java -jar target/cartola-odds-1.0.0.jar

# 4. Ou direto pelo Maven
mvn spring-boot:run
```

**Testar sem API Key:** se a chave não estiver configurada, o filtro por time favorito é desativado automaticamente e o sistema usa todos os atletas elegíveis por status e preço.

---

## 13. Melhorias Futuras

### Dados e Algoritmos
- [ ] Substituir proxy de desempenho pela **média real das últimas 5 rodadas** via `/atletas/pontuados`
- [ ] **Score específico por posição** (goleiros: defesas difíceis; atacantes: gols + assistências)
- [ ] **Dicionário de aliases** para nomes de clubes divergentes entre as APIs
- [ ] Ponderar a odd como **variável contínua** em vez de bônus binário

### Infraestrutura
- [ ] **Cache** das respostas com Spring Cache + Caffeine (TTL 1h)
- [ ] **Retry** com backoff exponencial via Spring Retry
- [ ] **Endpoint** `GET /api/atletas?posicao=ATA` para exploração por posição
- [ ] **Cobertura de testes** com JaCoCo + relatório HTML

### Regras de Negócio
- [ ] **Constraint de budget** máximo (C$) com OptaPlanner ou Timefold
- [ ] **Formações alternativas** configuráveis: 4-4-2, 3-5-2, 4-5-1
- [ ] **Simulação** de diferentes `ODD_LIMITE` para comparar times resultantes

### Qualidade
- [ ] **Testes de integração** com WireMock simulando as APIs externas
- [ ] **Métricas** com Spring Actuator + Micrometer

---

## Referências

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

*Projeto de automação pessoal para Cartola FC com dados públicos.*
