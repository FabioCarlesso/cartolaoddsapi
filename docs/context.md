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

### Pipeline de Montagem do Time

O `PipelineService` orquestra a montagem em etapas:
1. Buscar status do mercado (`/mercado/status`)
2. Buscar odds e identificar times favoritos (`OddsService`)
3. Buscar e filtrar atletas do Cartola (`CartolaDataService`)
4. Calcular desempenho das últimas 5 rodadas (`DesempenhoService` via `/atletas/pontuados`)
5. Calcular score de cada atleta (`ScoreService`)
6. Montar o time em formação configurável (`MontadorTimeService`)

### Fórmula de Score

O `ScoreService` aplica fórmulas distintas conforme a posição do atleta, priorizando os indicadores mais relevantes para cada função. Os bônus situacionais (`fatorCasa` e `timeFavorito`) são configuráveis via banco de dados e se aplicam a todas as posições.

#### Posições sem regra específica (LAT, ZAG, MEI, TEC) — fallback configurável

```
score = (mediaPontos × 0.40) + (valorização × 0.20) + (desempenho × 0.20)
      + (fatorCasa × 0.10)   + (timeFavorito × 0.10)
```

Todos os pesos do fallback são configuráveis em runtime via `PATCH /api/config` sem restart.

#### Goleiro (GOL) — prioridade em scouts defensivos

```
score = (desempenho × 0.35) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (defesasDificeis × 0.05) + (penaltisDefendidos × 0.05) − (golsSofridos × 0.02)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
```

`defesasDificeis` (DD), `penaltisDefendidos` (DP) e `golsSofridos` (GS) são scouts acumulados da temporada extraídos de `/atletas/mercado`. Quando não disponíveis na resposta da API, os scouts são tratados como 0 sem impacto no cálculo.

#### Atacante (ATA) — prioridade em participação ofensiva

```
score = (desempenho × 0.25) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (gols × 0.08) + (assistencias × 0.05)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
```

`gols` (G) e `assistencias` (A) são scouts acumulados da temporada. O bônus `timeFavorito` reforça a preferência por atacantes de times com odds favoráveis.

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

Endpoints expostos via `management.endpoints.web.exposure.include=health,info,metrics,prometheus`:

| Endpoint | Descrição |
|---|---|
| `/actuator/health` | Saúde da aplicação |
| `/actuator/metrics` | Lista de métricas coletadas |
| `/actuator/prometheus` | Métricas em formato Prometheus (scrape) |

Endpoints sensíveis (`env`, `beans`, `heapdump`, etc.) **não** são expostos.

A tag `application=cartolaoddsapi` é adicionada a todas as métricas via `management.metrics.tags.application`.

Para scrape com Prometheus, aponte o job para `GET /actuator/prometheus`.

### Filtro de Atletas

Apenas atletas com status **Provável (7)** ou **Dúvida (6)** e preço `> 0` são considerados.
Quando odds não estão disponíveis, o filtro por time favorito é desativado e todos os elegíveis entram no pool.

### Normalização de Clubes

O `NormalizadorUtil` remove acentos, converte para lowercase, troca hífens por espaços, colapsa espaços duplicados e aplica um dicionário central de aliases para alinhar nomes vindos da The Odds API com os nomes do Cartola FC. Isso cobre variações como `Atlético-MG`, `Atlético Mineiro`, `Atlético Mineiro MG`, `Athletico Paranaense`, `Atlético Paranaense`, `São Paulo FC`, `Inter`, `Fluminense FC` e `Vasco da Gama`.

### Regra de Defesa

Quando `evitarMesmoClubeDefesa=true` (padrão), o `MontadorTimeService` evita repetir clubes entre titulares das posições **GOL**, **LAT** e **ZAG**. A regra é configurável via `PATCH /api/config` e pode ser desativada em runtime. Quando não há candidatos suficientes sem repetição, o montador completa a posição com os melhores atletas restantes (respeitando ainda o limite por clube), garantindo que a formação nunca fique incompleta.

### Limite por Clube no Time Titular

O time titular respeita o limite de **no máximo 4 atletas do mesmo clube**, incluindo o **treinador (TEC)**. O limite é configurável em runtime via `PATCH /api/config` no campo `limiteAtletasPorClube` (padrão `4`). O montador aplica um fallback em três níveis:
1. **Primário** — respeita regra de defesa (sem clube repetido em GOL/LAT/ZAG) e limite por clube.
2. **Intermediário** — relaxa a regra de defesa, mas mantém o limite máximo por clube.
3. **Último recurso** — relaxa também o limite por clube para preservar a formação completa.

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
| `GET` | `/api/time` | Monta o time completo para a rodada atual |
| `GET` | `/api/ranking` | Top atletas por score (filtrável por posição e limite) |
| `GET` | `/api/favoritos` | Times favoritos com odds detalhadas |
| `DELETE` | `/api/cache` | Invalida todos os caches |
| `DELETE` | `/api/cache/{nome}` | Invalida um cache específico |
| `GET` | `/api/config` | Retorna configuração atual |
| `PATCH` | `/api/config` | Atualiza parâmetros em runtime |
| `POST` | `/api/config/reset` | Restaura defaults |
| `GET` | `/swagger-ui.html` | Documentação interativa |
| `GET` | `/actuator/health` | Status de saúde da aplicação |
| `GET` | `/actuator/metrics` | Lista de métricas disponíveis |
| `GET` | `/actuator/prometheus` | Métricas no formato Prometheus |

---

## Configuração Principal

| Variável de Ambiente | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | — | Chave da The Odds API (obrigatória para filtro por odds) |
| `APP_PORT` | `8080` | Porta exposta no host |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cartola_odds` | URL do banco |
| `SPRING_DATASOURCE_USERNAME` | `cartola` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `cartola` | Senha do banco |

Parâmetros de negócio (odd limite, pesos, formação) são gerenciados via banco de dados — não precisam de variáveis de ambiente.

---

## Testes

301 cenários distribuídos em 21 classes de teste cobrindo serviços, controllers, domínio, utilitários e endpoints de observabilidade.
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
