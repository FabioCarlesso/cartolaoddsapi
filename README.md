# ⚽ Cartola FC — Odds API

API REST em **Java 21 + Spring Boot 3.4.5** que monta automaticamente um time competitivo para o Cartola FC cruzando odds do Brasileirão com métricas dos atletas da plataforma.

---

## Funcionalidades

| # | Funcionalidade | Descrição |
|---|---|---|
| 1 | **Cache Caffeine** | Respostas das APIs externas cacheadas em memória (10–60 min) |
| 2 | **Invalidação de Cache** | Endpoint `DELETE /api/cache` para forçar atualização imediata dos dados |
| 3 | **Configuração via Banco** | Parâmetros de negócio (odd limite, pesos, formação e regras) gerenciados via banco de dados |
| 4 | **Config em Runtime** | `PATCH /api/config` atualiza parâmetros sem restart; `POST /api/config/reset` restaura defaults |
| 5 | **Desempenho Real** | Score usa média das últimas 5 rodadas via `/atletas/pontuados` |
| 6 | **Interfaces de API** | Swagger docs nas interfaces (`controller/api/`), controllers limpas |
| 7 | **5 Grupos de Endpoints REST** | `/api/time`, `/api/ranking`, `/api/favoritos`, `/api/cache`, `/api/config` |
| 8 | **Formação Configurável** | Padrão 4-3-3, alterável via `PATCH /api/config` |
| 9 | **Dúvidas** | Titulares em dúvida recebem substituto da mesma posição |
| 10 | **Defesa sem Clube Repetido** | Regra configurável evita repetir clubes entre GOL, LAT e ZAG |
| 11 | **Limite por Clube** | Time titular respeita no máximo 4 atletas do mesmo clube (incluindo TEC) |
| 12 | **Reserva de Luxo por Reserva** | Reserva de luxo é sempre a reserva com maior score |
| 13 | **Normalização de Clubes** | Nomes de clubes são normalizados com acentos, hífens, espaços e aliases tratados |
| 14 | **Aviso de Mercado** | Todos os endpoints informam quando o mercado está fechado ou em manutenção |
| 15 | **Observabilidade** | Spring Boot Actuator + Micrometer: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` |


---

## Índice

- [Stack](#stack)
- [Pré-requisitos](#pré-requisitos)
- [Início Rápido com Docker](#início-rápido-com-docker)
- [Início Rápido sem Docker](#início-rápido-sem-docker)
- [Configuração](#configuração)
- [Endpoints](#endpoints)
- [Regras de Negócio](#regras-de-negócio)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Observabilidade](#observabilidade)
- [Testes](#testes)

---

## Stack

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Maven | 3.9+ |
| Docker | 20.10+ |
| Docker Compose | 2.x |
| Caffeine Cache | 3.x |
| PostgreSQL | 16 |
| Flyway | 10.x |
| Micrometer | 1.14.x |
| Prometheus Client | 1.3.x |

---

## Pré-requisitos

- Chave gratuita da [The Odds API](https://the-odds-api.com) *(500 req/mês no plano free)*
- **Com Docker:** Docker Desktop ou Docker Engine + Compose *(PostgreSQL sobe automaticamente)*
- **Sem Docker:** JDK 21+, Maven 3.9+ e PostgreSQL 16+ em execução local

---

## Início Rápido com Docker

```bash
# 1. Configure as variáveis de ambiente
cp .env.example .env

# 2. Edite o .env e insira sua API Key
#    ODDS_API_KEY=sua_chave_aqui

# 3. Suba o container
docker compose up -d

# 4. Acesse
#    Swagger UI: http://localhost:8080/swagger-ui.html
#    Time:       http://localhost:8080/api/time
#    Favoritos:  http://localhost:8080/api/favoritos
#    Ranking:    http://localhost:8080/api/ranking
```

### Comandos Docker úteis

```bash
# Ver logs em tempo real
docker compose logs -f cartola-odds

# Verificar status e health
docker compose ps

# Rebuild após alterações no código
docker compose up -d --build

# Parar e remover container
docker compose down

# Build manual da imagem
docker build -t cartola-odds:1.0.0 .

# Executar imagem diretamente
docker run -p 8080:8080 \
  -e ODDS_API_KEY=sua_chave \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/cartola_odds \
  -e SPRING_DATASOURCE_USERNAME=cartola \
  -e SPRING_DATASOURCE_PASSWORD=cartola \
  cartola-odds:1.0.0
```

---

## Início Rápido sem Docker

```bash
# 1. Configure a API Key no application.properties
#    odds.api.key=sua_chave_aqui

# 2. Build
mvn clean package -DskipTests

# 3. Execute
java -jar target/cartola-odds-1.0.0.jar

# Ou com Maven diretamente
mvn spring-boot:run

# Passando variáveis de ambiente
ODDS_API_KEY=sua_chave mvn spring-boot:run
```

---

## Configuração

### Via arquivo (desenvolvimento)

Edite `src/main/resources/application.properties`:

```properties
odds.api.key=SUA_API_KEY_AQUI
```

> **Parâmetros de negócio** (odd limite, pesos, formação e regras) são gerenciados via banco de dados.
> Use `PATCH /api/config` para ajustá-los em runtime após subir a aplicação.

### Via variáveis de ambiente (produção / Docker)

| Variável | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | `SUA_API_KEY_AQUI` | **Obrigatório** — chave da The Odds API |
| `APP_PORT` | `8080` | Porta exposta no host (docker-compose) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cartola_odds` | URL do banco PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `cartola` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `cartola` | Senha do banco |
| `POSTGRES_USER` | `cartola` | Usuário criado no container PostgreSQL |
| `POSTGRES_PASSWORD` | `cartola` | Senha do container PostgreSQL |
| `SPRING_PROFILES_ACTIVE` | `default` | Profile do Spring Boot |
| `MANAGEMENT_SERVER_PORT` | `9090` | Porta dos endpoints Actuator |
| `MANAGEMENT_SERVER_ADDRESS` | `127.0.0.1` | Interface onde o Actuator escuta |

> **Parâmetros de negócio (odd limite, pesos, formação e regras):** gerenciados via banco de dados.
> Na primeira execução, o Flyway cria a tabela `configuracao` com os valores padrão.
> Use `PATCH /api/config` para atualizar em runtime ou `POST /api/config/reset` para restaurar os defaults.

> **Cache (Caffeine):** As respostas das APIs são cacheadas automaticamente em memória.
> Para forçar atualização imediata sem reiniciar, use `DELETE /api/cache`.

> **Sem API Key configurada:** a aplicação sobe normalmente, o filtro por time favorito é desativado e todos os atletas elegíveis por status/preço são considerados.

---

## Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/time` | Monta o time completo para a rodada atual |
| `GET` | `/api/favoritos` | Lista times favoritos com odds detalhadas |
| `GET` | `/api/favoritos?oddLimite=2.5` | Favoritos com limite customizado |
| `GET` | `/api/ranking` | Top 25 atletas por score |
| `GET` | `/api/ranking?posicao=ATA` | Top 25 atacantes |
| `GET` | `/api/ranking?posicao=MEI&limite=10` | Top 10 meias |
| `DELETE` | `/api/cache` | Invalida todos os caches imediatamente |
| `DELETE` | `/api/cache/{nome}` | Invalida um cache específico pelo nome |
| `GET` | `/api/config` | Retorna a configuração atual (odd limite, pesos, formação e regras) |
| `PATCH` | `/api/config` | Atualiza um ou mais parâmetros em runtime (sem restart) |
| `POST` | `/api/config/reset` | Restaura todos os parâmetros para os valores padrão |
| `GET` | `/swagger-ui.html` | Documentação interativa Swagger UI |
| `GET` | `/v3/api-docs` | Spec OpenAPI 3 em JSON |
| `GET` | `:9090/actuator/health` | Saúde da aplicação |
| `GET` | `:9090/actuator/metrics` | Lista de métricas disponíveis |
| `GET` | `:9090/actuator/metrics/{nome}` | Detalhe de uma métrica específica |
| `GET` | `:9090/actuator/prometheus` | Métricas no formato Prometheus (scrape) |

### Exemplo — `GET /api/favoritos`

```json
{
  "oddLimite": 3.0,
  "totalJogos": 10,
  "totalFavoritos": 4,
  "totalDescartados": 6,
  "favoritos": [
    {
      "timeFavorito": "Flamengo",
      "oddFavorito": 1.95,
      "timeAdversario": "Vasco",
      "oddAdversario": 4.20,
      "oddEmpate": 3.40,
      "favoritoEmCasa": true
    }
  ],
  "descartados": [
    {
      "timeCasa": "Fortaleza",
      "oddCasa": 3.20,
      "timeVisitante": "Bahia",
      "oddVisitante": 3.40,
      "oddEmpate": 3.10,
      "motivo": "Menor odd (3.20) acima do limite (3.0)"
    }
  ]
}
```

### Exemplo — `GET /api/ranking?posicao=ATA&limite=3`

```json
{
  "rodada": 15,
  "posicao": "ATA",
  "limite": 3,
  "totalDisponivel": 18,
  "atletas": [
    { "rank": 1, "apelido": "Hulk", "formatado": "Hulk (ATM)", "score": 8.54, "preco": 22.0, "emDuvida": false },
    { "rank": 2, "apelido": "Cano",  "formatado": "Cano (FLU)",  "score": 7.90, "preco": 18.3, "emDuvida": false },
    { "rank": 3, "apelido": "Pedro", "formatado": "Pedro (FLA) ⚠️ DÚVIDA", "score": 7.70, "preco": 17.0, "emDuvida": true }
  ]
}
```

### Exemplo — `DELETE /api/cache`

```json
{
  "cachesInvalidados": ["odds", "atletas", "clubes", "partidas", "pontuados", "statusMercado"],
  "mensagem": "Todos os caches invalidados com sucesso.",
  "timestamp": "2025-06-01T15:30:00"
}
```

### Exemplo — `DELETE /api/cache/atletas`

```json
{
  "cachesInvalidados": ["atletas"],
  "mensagem": "Cache 'atletas' invalidado com sucesso.",
  "timestamp": "2025-06-01T15:30:00"
}
```

**Nomes de cache válidos:** `odds`, `atletas`, `clubes`, `partidas`, `pontuados`, `statusMercado`

Passar um nome inválido retorna `400 Bad Request` com a lista de nomes aceitos.

### Aviso de mercado

Quando o mercado não está aberto, todos os endpoints retornam o campo `avisoMercado` preenchido:

| Código | Status | Aviso retornado |
|---|---|---|
| 1 | Aberto | `null` (campo omitido) |
| 2 | **Fechado** | `"Mercado fechado. Rodada em andamento."` |
| 3 | Manutenção | `"Mercado em manutencao ou pre-temporada."` |
| 4 | Parcial | `"Mercado parcialmente aberto. Alguns jogos ja ocorreram."` |
| 6 | Finalizando | `"Processamento pos-rodada em andamento."` |

### Códigos de resposta

| Código | Situação |
|---|---|
| `200` | Sucesso |
| `400` | Parâmetro inválido (ex: posição inexistente, `oddLimite <= 1.0`) |
| `400` | Erro de validação no corpo do `PATCH /api/config` |
| `422` | Nenhum atleta disponível após filtragem (ODD_LIMITE muito restritivo) |
| `502` | Falha de comunicação com API externa |
| `500` | Erro interno inesperado |

---

## Regras de Negócio

### Identificação de favoritos

Para cada jogo da Odds API que corresponda a um confronto da rodada atual do Cartola:
- O time com **menor odd** é candidato a favorito.
- Se `odd ≤ ODD_LIMITE` → entra como favorito; jogadores desse time são incluídos no pool.
- Se `odd > ODD_LIMITE` → jogo descartado; nenhum time desse jogo entra no pool.

Odds de confrontos fora da rodada atual são ignoradas. Se não for possível obter os confrontos atuais via `/partidas`, a API mantém o comportamento resiliente anterior e processa todas as odds disponíveis.

### Filtros de atletas

| Filtro | Regra |
|---|---|
| Status | Somente `Provável` (7) ou `Dúvida` (6) |
| Preço | Deve ser `> 0` cartoletas |
| Time | Clube deve estar no conjunto de times favoritos |

*Sem odds disponíveis: filtro por time desativado — usa todos os elegíveis.*

### Fórmula do score

O score é calculado com fórmulas distintas por posição, priorizando os indicadores mais relevantes para cada função. Os bônus `fatorCasa` e `timeFavorito` valem `10.0` quando verdadeiros, `0.0` caso contrário, e são configuráveis via `PATCH /api/config`.

**Posições sem regra específica (LAT, ZAG, MEI, TEC) — fallback configurável:**

```
score = (mediaPontos × 0.40) + (valorização × 0.20) + (desempenho × 0.20)
      + (fatorCasa × 0.10)  + (timeFavorito × 0.10)
```

**Goleiro (GOL) — scouts defensivos com maior peso:**

```
score = (desempenho × 0.35) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (defesasDifíceis × 0.05) + (pênaltisDefendidos × 0.05) − (golsSofridos × 0.02)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
```

**Atacante (ATA) — participação ofensiva com maior peso:**

```
score = (desempenho × 0.25) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (gols × 0.08) + (assistências × 0.05)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
```

Os scouts (DD, GS, DP, G, A) são totais acumulados da temporada extraídos de `/atletas/mercado`. Quando não disponíveis na resposta da API, são tratados como 0 sem impacto no cálculo.
Os pesos base de GOL e ATA são constantes no `ScoreService`; para essas posições, apenas `pesoFatorCasa` e `pesoTimeFavorito` continuam configuráveis via `PATCH /api/config`.

**Desempenho:** usa a média real das últimas 5 rodadas via `/atletas/pontuados`.
Fallback automático para `mediaPontos` da temporada quando o histórico não estiver disponível.

### Formação 4-3-3

`1 GOL · 2 LAT · 2 ZAG · 3 MEI · 3 ATA · 1 TEC`

Cada slot seleciona exclusivamente dentro da sua posição. Reservas são sempre prováveis, da mesma posição e preferencialmente mais baratos. `TEC` não tem reserva.

### Defesa sem clube repetido

Quando `evitarMesmoClubeDefesa=true` (padrão), o montador não repete clubes entre os titulares de `GOL`, `LAT` e `ZAG`. A regra pode ser desligada em runtime com `PATCH /api/config`:

```json
{ "evitarMesmoClubeDefesa": false }
```

Quando não há candidatos suficientes sem repetição (ex: poucos clubes disponíveis na rodada), o montador completa a posição com os melhores atletas restantes, garantindo que a formação nunca fique incompleta.

### Limite máximo por clube (incluindo treinador)

Na escalação titular, o montador respeita **no máximo 4 atletas do mesmo clube**, considerando todas as posições, inclusive `TEC`.
Esse valor é configurável em runtime via `PATCH /api/config` com o campo `limiteAtletasPorClube` (default `4`).
Quando o limite impedir a escalação completa em uma posição, o montador tenta completar com atletas de outros clubes; em último caso, relaxa a regra para manter a formação completa.

### Capitão e Reserva de Luxo

- **Capitão:** titular com maior score global.
- **Reserva de Luxo:** atleta de maior score entre todas as **reservas**; como `TEC` não tem reserva, técnicos não concorrem a reserva de luxo.

### Normalização de nomes de clubes

Antes de cruzar Odds API e Cartola FC, nomes são convertidos para lowercase, sem acentos, com hífens transformados em espaços, espaços duplicados colapsados e aliases aplicados. Exemplos: `Atlético-MG` → `atletico mg`, `Atlético Mineiro MG` → `atletico mg`, `Athletico Paranaense` → `athletico pr`, `São Paulo FC` → `sao paulo`, `Inter` → `internacional`, `Fluminense FC` → `fluminense`, `Vasco da Gama` → `vasco`.

O dicionário central fica em `NormalizadorUtil`. Para adicionar um alias, normalize mentalmente a grafia de entrada (sem acento, minúscula, hífen como espaço) e inclua uma entrada no mapa `ALIASES` apontando para o nome canônico já usado no cruzamento.

---

## Estrutura do Projeto

```
cartola/
├── Dockerfile               # Multi-stage: build (JDK 21) + runtime (JRE 21 Alpine)
├── docker-compose.yml       # app + postgres:16, healthcheck, resource limits
├── .env.example             # Template de variáveis de ambiente
├── .dockerignore            # Exclui target/, testes, docs do contexto Docker
├── pom.xml
├── README.md
├── docs/
│   ├── documentacao.md      # Documentação técnica completa
│   └── documentacao.docx    # Versão Word formatada
└── src/
    ├── main/
    │   ├── java/com/cartola/odds/
    │   │   ├── config/          (OddsProperties, CartolaProperties,
    │   │   │                     CacheConfig, RestClientConfig, OpenApiConfig)
    │   │   ├── client/          (OddsClient, CartolaClient)
    │   │   ├── repository/      (ConfiguracaoRepository)
    │   │   ├── service/         (OddsService, CartolaDataService, ScoreService,
    │   │   │                     DesempenhoService, MontadorTimeService, PipelineService,
    │   │   │                     RankingService, ConfiguracaoService)
    │   │   ├── controller/api/  (TimeApi, RankingApi, FavoritosApi, CacheApi,
    │   │   │                     ConfiguracaoApi — Swagger docs)
    │   │   ├── controller/      (TimeController, RankingController, FavoritosController,
    │   │   │                     CacheController, ConfiguracaoController,
    │   │   │                     GlobalExceptionHandler)
    │   │   ├── model/           (Atleta, Time, Configuracao, enums/,
    │   │   │                     request/ConfiguracaoRequest, response/)
    │   │   └── util/            (NormalizadorUtil)
    │   └── resources/
    │       ├── application.properties        # Lê variáveis de ambiente com fallback
    │       └── db/migration/
    │           ├── V1__create_configuracao.sql  # Cria tabela e insere valores padrão
    │           ├── V2__alter_configuracao_numeric_to_double.sql  # Converte colunas para DOUBLE PRECISION
    │           ├── V3__add_evitar_mesmo_clube_defesa.sql         # Regra configurável de defesa
    │           └── V4__add_limite_atletas_por_clube.sql          # Limite configurável por clube
    └── test/
        ├── java/                            # 21 classes de teste — 311 cenários
        └── resources/
            ├── application.properties       # H2 in-memory (MODE=PostgreSQL) para testes
            └── db/migration/h2/             # Migrations equivalentes ajustadas à sintaxe H2
```

---

## Observabilidade

A aplicação expõe endpoints de monitoramento via **Spring Boot Actuator** com métricas coletadas pelo **Micrometer** e exportadas no formato **Prometheus**.

Por padrão, o Actuator roda em uma porta separada (`MANAGEMENT_SERVER_PORT`, padrão `9090`) e ligado ao endereço local (`MANAGEMENT_SERVER_ADDRESS`, padrão `127.0.0.1`). Isso mantém métricas fora da porta pública da API (`8080`) em execuções locais. No Docker Compose, a porta de gerenciamento fica disponível apenas na rede interna do compose para permitir scrape por Prometheus sem publicá-la no host.

### Endpoints expostos

| Endpoint | Descrição |
|---|---|
| `GET :9090/actuator/health` | Status de saúde (`UP` / `DOWN`) |
| `GET :9090/actuator/metrics` | Lista todas as métricas disponíveis |
| `GET :9090/actuator/metrics/{nome}` | Detalhe de uma métrica (ex: `http.server.requests`) |
| `GET :9090/actuator/prometheus` | Métricas em formato Prometheus para scrape |

### Exemplo — `GET :9090/actuator/health`

```json
{ "status": "UP" }
```

### Exemplo — `GET :9090/actuator/prometheus` (trecho)

```
# HELP http_server_requests_seconds Duration of HTTP server request handling
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{application="cartolaoddsapi",...} 42
```

### Integração com Prometheus/Grafana

Para integrar com Prometheus, adicione o job no `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'cartolaoddsapi'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:9090']
```

> Endpoints sensíveis (`env`, `beans`, `heapdump`, etc.) não são expostos. Apenas `health`, `info`, `metrics` e `prometheus` ficam disponíveis.

---

## Testes

```bash
# Executar todos os testes
mvn test

# Classe específica
mvn test -Dtest=OddsServiceTest

# Com relatório (requer JaCoCo no pom.xml)
mvn test jacoco:report
```

| Classe | Cenários |
|---|---|
| `OddsServiceTest` | 22 — buscarFavoritos + buscarFavoritosDetalhado + filtro por rodada atual |
| `FavoritosControllerTest` | 13 — HTTP 200/400/502, campos, validação oddLimite |
| `CartolaDataServiceTest` | 14 — filtros de status/preço/favorito, mandantes e confrontos da rodada |
| `ScoreServiceTest` | 27 — pesos, bônus, desempenho real vs proxy, fallback, score por posição (GOL/ATA) |
| `MontadorTimeServiceTest` | 25 — formação, regra de defesa, limite por clube, fallback intermediário, capitão, reserva de luxo, dúvidas, reservas sem técnico |
| `DesempenhoServiceTest` | 8 — média rodadas, fallback null, atleta parcial |
| `PipelineServiceTest` | 8 — inclui etapa DesempenhoService |
| `CacheConfigTest` | 2 — Caffeine registrado com 7 caches |
| `CacheControllerTest` | 9 — DELETE todos / DELETE por nome / 400 nome inválido |
| `ConfiguracaoControllerTest` | 10 — GET config, PATCH (válido/inválido/regra), POST reset |
| `ConfiguracaoServiceTest` | 2 — atualização/reset da regra de defesa |
| `RankingServiceTest` | 15 — ordenação, limite, filtro posição |
| `RankingControllerTest` | 12 — HTTP completo |
| `TimeControllerTest` | 7 — HTTP completo |
| `AtletaTest` | 5 — domínio e imutabilidade |
| `EnumsTest` | 8 — Posicao e StatusAtleta |
| `NormalizadorUtilTest` | 42 — normalização e aliases de clubes |
| `ActuatorEndpointsTest` | 10 — health, metrics, prometheus e bloqueio de endpoints sensíveis |
| `CartolaOddsApplicationTests` | 1 — contexto Spring |

---

*Para documentação técnica detalhada, ver [docs/documentacao.md](docs/documentacao.md).*
