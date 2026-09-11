# Arquitetura

> **Papel deste arquivo:** camadas, estrutura do projeto, stack, APIs externas consumidas, fluxo de execução e os pontos de entrada de cada serviço.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Stack e Dependências

| Tecnologia | Versão | Uso |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework web, IoC, configuração |
| Maven | 3.9+ | Build e gerenciamento de dependências |
| PostgreSQL | 16 | Banco de dados relacional (configuração, produção) |
| H2 | runtime | Banco in-memory (MODE=PostgreSQL) para testes |
| Flyway | 10.x | Migrations de banco de dados |
| Spring Data JPA | via starter-data-jpa | Persistência com Hibernate |
| Spring Security | via starter-security | Cadeia stateless, JWT, `@PreAuthorize` |
| Caffeine Cache | 3.x | Cache em memória JVM |
| Micrometer | 1.14.x | Coleta de métricas |
| Prometheus Client | 1.3.x | Exposição das métricas em `/actuator/prometheus` |
| Lombok | latest | `@Builder`, `@Getter`, `@With` — reduz boilerplate |
| springdoc OpenAPI | 2.8.8 | Swagger UI e spec OpenAPI 3 automática |
| Docker | 20.10+ | Empacotamento e execução |
| Docker Compose | 2.x | Orquestração de app + PostgreSQL |
| JUnit 5 | via starter-test | Testes unitários e parametrizados |
| Mockito | via starter-test | Mocking de dependências |
| AssertJ | via starter-test | Assertions fluentes |
| MockMvc | via starter-test | Testes de camada web |

**Instalação:**
```bash
mvn clean package -DskipTests
```

---

---

## APIs Externas

### The Odds API

| Atributo | Valor |
|---|---|
| URL base | `https://api.the-odds-api.com/v4` |
| Endpoint | `GET /sports/soccer_brazil_campeonato/odds` |
| Autenticação | Query param `apiKey` |
| Plano gratuito | 500 requisições/mês |
| Headers de cota | `x-requests-remaining` e `x-requests-used` — lidos também nas respostas de erro (ver [`operacao.md` › Cota da The Odds API: guardrail e sondagem](operacao.md#cota-da-the-odds-api-guardrail-e-sondagem)) |

**Custo por chamada:** a The Odds API cobra por requisição **por região e por mercado**. Com
`odds.api.regions=us` e `odds.api.markets=h2h` (um valor em cada), cada chamada custa 1 crédito —
acrescentar uma região ou mercado multiplica o custo por chamada.

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

### Cartola FC API (pública, sem autenticação)

**URL base:** `https://api.cartola.globo.com`

| Endpoint | Finalidade | Campos principais |
|---|---|---|
| `/mercado/status` | Status do mercado | `status_mercado` (1=aberto), `rodada_atual` |
| `/atletas/mercado` | Atletas disponíveis | `apelido`, `posicao_id`, `clube_id`, `status_id`, `media_num`, `variacao_num`, `preco_num` |
| `/clubes` | Mapa id → nome/sigla | `nome`, `abreviacao`, `slug` |
| `/partidas` | Partidas da rodada | `clube_casa_id`, `clube_visitante_id` |
| `/atletas/pontuados` | Pontuação pós-rodada | `pontuacao`, `scout` |

---

---

## Estrutura do Projeto

Orientação de repositório — o que existe na raiz e onde cada coisa mora:

```
cartolaoddsapi/
├── Dockerfile               # Multi-stage: build (JDK 21) + runtime (JRE 21 Alpine)
├── docker-compose.yml       # app + postgres:16, healthcheck, resource limits
├── .env.example             # Template de variáveis de ambiente
├── .dockerignore            # Exclui target/, testes, docs do contexto Docker
├── pom.xml
├── README.md                # Porta de entrada: o que é e como subir
├── docs/                    # Ver docs/README.md
└── src/
    ├── main/java/com/cartola/odds/   # Camadas abaixo
    ├── main/resources/
    │   ├── application.properties        # Variáveis de ambiente com fallback
    │   ├── application-prod.properties   # Perfil prod: springdoc desligado, log em INFO
    │   └── db/migration/                 # V1 … V11 — ver banco-de-dados.md
    └── test/
        ├── java/                         # 44 classes — 761 cenários
        └── resources/
            ├── application.properties    # H2 in-memory (MODE=PostgreSQL) para testes
            └── db/migration/h2/          # Migrations equivalentes ajustadas à sintaxe H2
```

**Camadas** — o que cada pacote é responsável por conter:

```
com.cartola.odds/
├── config/      — configurações (cache, REST client, OpenAPI, security, properties)
├── client/      — integrações com APIs externas (OddsClient, CartolaClient)
├── security/    — filtro JWT e escrita de 401/403
├── repository/  — persistência (Spring Data JPA)
├── service/     — lógica de negócio (pipeline, score, desempenho, ranking, montador, usuários)
├── controller/  — endpoints REST + tratamento global de erros
│   └── api/     — interfaces com anotações Swagger (separadas dos controllers)
├── exception/   — exceções de domínio mapeadas a status HTTP
├── model/       — entidades de domínio, enums, DTOs de request e response
└── util/        — utilitários (NormalizadorUtil, FormacaoParser)
```

> **A lista de classes de cada pacote não é mantida aqui.** Ela nasce desatualizada a cada classe
> nova, e o repositório já a responde (`find src/main/java -name '*.java'`, ou a árvore do próprio
> GitHub). O que este documento mantém é o **critério**: o que pertence a cada camada. Os pontos de
> entrada citados por nome vivem em [Referência de Funções](arquitetura.md#referência-de-funções), onde o nome é parte da
> explicação. Ver [`docs/README.md`](README.md#o-que-não-se-documenta-à-mão).

---

## Fluxo de Execução

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
5. calcularDesempenho()         → média e desvio padrão das últimas 5 rodadas
      │
      ▼
6. calcularScores()             → pool com score ponderado por atleta
      │
      ▼
7. selecionar titulares         → top-N por score em cada posição (ou branch-and-bound com orçamento)
      │
      ▼
8. selecionar reservas          → provável, mesma posição, mais barato, exceto TEC
      │
      ▼
9. mapear substitutos           → provável da mesma posição para cada dúvida
      │
      ▼
10. eleger capitão/reserva luxo → maior titular e melhor reserva
      │
      ▼
11. TimeResponse.from(time)     → HTTP 200 com JSON
```

---

---

## Referência de Funções

### `ConfiguracaoService.buscarConfig() → Configuracao`
Retorna a configuração atual do banco (resultado cacheado em `configuracao`).
Usado por `OddsService`, `ScoreService`, `MontadorTimeService` e `FavoritosController`.

### `ConfiguracaoService.atualizar(ConfiguracaoRequest) → ConfiguracaoResponse`
Atualiza os campos não-nulos da configuração, valida a soma dos pesos e invalida o cache.

### `ConfiguracaoService.resetar() → ConfiguracaoResponse`
Restaura todos os campos para os valores padrão e invalida o cache.

### `OddsService.buscarFavoritos() → Set<String>`
Busca odds da API, filtra os confrontos da rodada atual e retorna nomes normalizados dos times com `odd ≤ ODD_LIMITE` (lido do banco).
Retorna `Set.of()` se API indisponível ou chave não configurada.

### `OddsService.buscarFavoritosDetalhado(oddLimite) → FavoritosResponse`
Processa todos os jogos da Odds API e classifica cada um em favorito ou descartado.
Retorna odds detalhadas de cada time, quem é favorito, se joga em casa e motivo do descarte.

### `OddsCotaService` — estado e histórico da cota
Monta a resposta de `GET /api/odds/cota` a partir da `odds_cota` e a série de
`GET /api/odds/cota/historico` a partir da `odds_cota_historico`, aplicando a detecção de
`reinicioDeCota` e recusando janelas fora de 1–92 dias.

### `CartolaDataService.buscarAtletasFiltrados(Set<String> favoritos) → List<Atleta>`
Busca atletas, clubes, partidas e scouts acumulados da temporada. Aplica filtros de status, preço e time favorito.
Quando `favoritos` está vazio, ignora o filtro por time.

### `CartolaDataService.buscarTimesCasa() → Set<Integer>`
Retorna IDs dos times mandantes da rodada atual.

### `CartolaDataService.buscarConfrontosRodadaAtual() → Set<String>`
Retorna chaves normalizadas dos confrontos da rodada atual para limitar as odds processadas.

### `ScoreService.calcularScores(atletas, timesCasa, favoritos) → List<Atleta>`
Retorna nova lista imutável com campo `score` preenchido para cada atleta, usando fórmulas específicas para GOL/ATA e fallback configurável para as demais posições.

### `DesempenhoService.calcularDesempenhoUltimasRodadas(rodadaAtual) → Map<Integer, DesempenhoAtleta>`
Busca até 5 rodadas anteriores via `/atletas/pontuados` (cacheadas por rodada).
Retorna `{atletaId → DesempenhoAtleta(mediaPontos, desvioPadrao, rodadasConsideradas)}`. Atletas sem histórico não aparecem no mapa (o `ScoreService` usa `mediaPontos` como fallback e não aplica penalidade por desvio).

### `MontadorTimeService.montar(pool, rodada, avisoMercado) → Time`
Seleciona titulares, aplica a regra configurável de defesa sem clube repetido, reservas (exceto TEC), capitão, reserva de luxo e substitutos.
Retorna `Time` completo com alertas de dúvida.

### `OtimizadorTitulares` — seleção sob orçamento
Resolve o *multiple-choice knapsack* por posição via branch-and-bound quando há teto de cartoletas.
Ver [`regras-de-negocio.md` › Budget Máximo (C$) e Otimização por Orçamento](regras-de-negocio.md#budget-máximo-c-e-otimização-por-orçamento).

### `RankingService.buscarRanking(posicao, limite) → RankingResponse`
Retorna os melhores atletas por score. Reutiliza o mesmo pipeline de filtros do `/api/time`.
`posicao = null` retorna todas as posições. `limite` é clampado entre 1 e 100.

### `EscalacaoService.salvarEscalacao(Time, rodadaId)` / `atualizarPontuacaoReal(rodadaId)`
Persiste a escalação da rodada de forma idempotente e, depois do fechamento, preenche a
`pontuacao_real` a partir de `/atletas/pontuados`. Ver [`regras-de-negocio.md` › Histórico de Escalações por Rodada](regras-de-negocio.md#histórico-de-escalações-por-rodada).

### `PipelineService.executar() → Time`
Orquestra todas as etapas. Lança `IllegalStateException` se pool vazio após filtragem.

### `MercadoStatusResponse.getAvisoMercado() → String|null`
Retorna o texto de aviso quando o mercado não está aberto; `null` quando aberto.
Propagado para todos os responses via `Time.avisoMercado` e `RankingResponse.avisoMercado`.

### `NormalizadorUtil.normalizar(String) → String`
Remove acentos (Unicode NFD), converte para lowercase, transforma hífen em espaço, remove caracteres especiais, colapsa espaços duplicados e aplica aliases de clubes definidos no mapa central `ALIASES`.

### `FormacaoParser.parse(String)` / `parseLista(String)`
Converte `def-mei-ata` em `FormacaoConfig`, validando soma 10, posições não zeradas e, na lista, o
mínimo de 2 e o máximo de 5 formações distintas.

### `GlobalExceptionHandler.handleValidation(MethodArgumentNotValidException) → ErrorResponse`
Converte falhas de Bean Validation — principalmente no corpo do `PATCH /api/config` — em HTTP 400 com `erro="Parametro invalido"` e todas as mensagens de campos inválidos concatenadas com `"; "`.

### `GlobalExceptionHandler.handleTypeMismatch(MethodArgumentTypeMismatchException) → ErrorResponse`
Converte valores de query param/path variable que não convertem para o tipo esperado (ex.: `?orcamento=abc`, `?excluirDuvida=abc`) em HTTP 400, informando nome do parâmetro, valor recebido e tipo esperado. Sem este handler a exceção cairia no `handleGeneric(Exception)` e seria reportada como `500`, tratando erro de cliente como falha de servidor.

---

---

## Ver também

- [`api.md`](api.md) — contrato REST das rotas expostas por cada controller
- [`regras-de-negocio.md`](regras-de-negocio.md) — o que cada serviço do pipeline decide
- [`banco-de-dados.md`](banco-de-dados.md) — migrations e o esquema que o `ddl-auto=validate` confere
- [`context.md`](context.md) — por que as camadas e o pipeline são assim
