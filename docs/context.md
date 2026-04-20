# Context — Cartola Odds API

## Visão Geral

API REST em **Java 21 + Spring Boot 3.4.5** que monta automaticamente um time competitivo para o **Cartola FC** cruzando odds do Brasileirão (via [The Odds API](https://the-odds-api.com)) com métricas dos atletas da plataforma Cartola.

---

## Problema que Resolve

Montar um time no Cartola FC exige combinar dois tipos de dados dispersos:
1. **Quais times têm mais chance de vencer?** — respondido pelas odds de mercado.
2. **Quais jogadores desses times estão em melhor forma e são viáveis pelo preço?** — respondido pelos dados da API do Cartola FC.

Esta API cruza essas duas fontes e entrega diretamente os melhores atletas disponíveis, já escalados em formação **4-3-3**.

---

## APIs Externas Consumidas

| API | Base URL | Uso |
|---|---|---|
| The Odds API | `https://api.the-odds-api.com` | Odds do Brasileirão para identificar times favoritos |
| Cartola FC API | `https://api.cartola.globo.com` | Atletas, clubes, mercado, pontuações por rodada |

---

## Decisões Arquiteturais

### Pipeline de Montagem do Time

O `PipelineService` orquestra a montagem em etapas:
1. Buscar status do mercado (`/mercado/status`)
2. Buscar odds e identificar times favoritos (`OddsService`)
3. Buscar e filtrar atletas do Cartola (`CartolaDataService`)
4. Calcular desempenho das últimas 5 rodadas (`DesempenhoService`)
5. Calcular score de cada atleta (`ScoreService`)
6. Montar o time em formação 4-3-3 (`MontadorTimeService`)

### Fórmula de Score

```
score = (mediaPontos × 0.40) + (valorização × 0.20) + (desempenho × 0.20)
      + (fatorCasa × 0.10)  + (timeFavorito × 0.10)
```

Os pesos são configuráveis via variáveis de ambiente (`CARTOLA_SCORE_PESO_*`).

### Cache Caffeine (in-memory)

| Cache | TTL |
|---|---|
| odds | 10 min |
| atletas / partidas | 15 min |
| clubes | 60 min |
| status do mercado | 2 min |

O cache é reiniciado junto com a aplicação. Não há persistência entre restarts.

### Filtro de Atletas

Apenas atletas com status **Provável (7)** ou **Dúvida (6)** e preço `> 0` são considerados. Quando odds não estão disponíveis, o filtro por time favorito é desativado e todos os elegíveis entram no pool.

---

## Estrutura de Pacotes

```
com.cartola.odds/
├── config/      — configurações (cache, REST client, OpenAPI, properties)
├── client/      — integrações com APIs externas (OddsClient, CartolaClient)
├── service/     — lógica de negócio (pipeline, score, desempenho, ranking, montador)
├── controller/  — endpoints REST + tratamento global de erros
│   └── api/     — interfaces com anotações Swagger (separadas dos controllers)
├── model/       — entidades de domínio, enums e DTOs de resposta
└── util/        — utilitários (NormalizadorUtil)
```

---

## Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/time` | Monta o time completo para a rodada atual |
| `GET` | `/api/ranking` | Top atletas por score (filtrável por posição e limite) |
| `GET` | `/api/favoritos` | Times favoritos com odds detalhadas |
| `GET` | `/swagger-ui.html` | Documentação interativa |

---

## Configuração Principal

| Variável | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | — | Chave da The Odds API (obrigatória para filtro por odds) |
| `CARTOLA_ODD_LIMITE` | `3.0` | Odd máxima para considerar um time favorito |
| `APP_PORT` | `8080` | Porta exposta no host |

---

## Testes

~110 cenários distribuídos em 15 classes de teste cobrindo serviços, controllers, domínio e utilitários. Execute com:

```bash
mvn test
```

---

## Contexto de Uso

- **Temporada:** Brasileirão Série A
- **Rodada:** determinada dinamicamente via status do mercado Cartola
- **Mercado fechado:** todos os endpoints retornam campo `avisoMercado` informando o estado atual
- **Plano free da Odds API:** 500 requisições/mês — o cache reduz o consumo significativamente
