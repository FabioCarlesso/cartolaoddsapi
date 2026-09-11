# ⚽ Cartola FC — Odds API · Referência técnica

> **Papel deste arquivo:** referência completa da API — funcionalidades, configuração, endpoints,
> contratos de resposta, regras de negócio, estrutura e testes. É aqui que se procura *o que* o
> sistema faz e *como* se usa cada parte.
>
> Para subir o projeto em cinco minutos, veja o [README](../README.md).
> Para entender *por que* cada decisão foi tomada, veja [`context.md`](context.md).

> **Stack:** Java 21 · Spring Boot 3.4.5 · Maven · JAR
> **Versão:** 1.0.0

---

## Índice

1. [Funcionalidades](#1-funcionalidades)
2. [Stack e Dependências](#2-stack-e-dependências)
3. [APIs Externas](#3-apis-externas)
4. [Configuração](#4-configuração)
5. [Autenticação e Política de Acesso](#5-autenticação-e-política-de-acesso)
6. [Gestão de Usuários](#6-gestão-de-usuários)
7. [Cache (Caffeine)](#7-cache-caffeine)
8. [Endpoints](#8-endpoints)
9. [Regras de Negócio](#9-regras-de-negócio)
10. [Fluxo de Execução](#10-fluxo-de-execução)
11. [Estrutura do Projeto](#11-estrutura-do-projeto)
12. [Referência de Funções](#12-referência-de-funções)
13. [Referência de Dados](#13-referência-de-dados)
14. [Testes](#14-testes)
15. [Swagger / OpenAPI](#15-swagger--openapi)
16. [Docker](#16-docker)
17. [Observabilidade](#17-observabilidade)
18. [Melhorias Futuras](#18-melhorias-futuras)
19. [Referências](#19-referências)

---

## 1. Funcionalidades

| # | Funcionalidade | Descrição |
|---|---|---|
| 1 | **Cache Caffeine** | Respostas das APIs externas cacheadas em memória (10–60 min) |
| 2 | **Invalidação de Cache** | Endpoint `DELETE /api/cache` para forçar atualização imediata dos dados |
| 3 | **Configuração via Banco** | Parâmetros de negócio (odd limite, pesos, formação e regras) gerenciados via banco de dados |
| 4 | **Config em Runtime** | `PATCH /api/config` atualiza parâmetros sem restart; `POST /api/config/reset` restaura defaults |
| 5 | **Desempenho Real** | Score usa média das últimas 5 rodadas via `/atletas/pontuados` |
| 6 | **Interfaces de API** | Swagger docs nas interfaces (`controller/api/`), controllers limpas |
| 7 | **6 Grupos de Endpoints REST** | `/api/time`, `/api/ranking`, `/api/favoritos`, `/api/cache`, `/api/config`, `/api/historico` |
| 8 | **Formação Configurável** | Padrão 4-3-3, alterável via `PATCH /api/config` |
| 9 | **Dúvidas** | Titulares em dúvida recebem substituto da mesma posição |
| 10 | **Defesa sem Clube Repetido** | Regra configurável evita repetir clubes entre GOL, LAT e ZAG |
| 11 | **Limite por Clube** | Time titular respeita no máximo 4 atletas do mesmo clube (incluindo TEC) |
| 12 | **Reserva de Luxo por Reserva** | Reserva de luxo é sempre a reserva com maior score |
| 13 | **Normalização de Clubes** | Nomes de clubes são normalizados com acentos, hífens, espaços e aliases tratados |
| 14 | **Aviso de Mercado** | Todos os endpoints informam quando o mercado está fechado ou em manutenção |
| 15 | **Observabilidade** | Spring Boot Actuator + Micrometer: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` |
| 16 | **Histórico de Escalações** | `GET /api/time` persiste a escalação da rodada (idempotente; exceto `excluirDuvida=true`, que é comparativo); `/api/historico` permite comparar score sugerido vs. pontuação real |
| 17 | **Orçamento Máximo** | `GET /api/time?orcamento=120.0` monta o time de **maior score** que cabe no limite de cartoletas (otimização branch-and-bound; custo-benefício só como desempate) |
| 18 | **Excluir Dúvidas do Ranking** | `GET /api/ranking?excluirDuvida=true` remove jogadores em dúvida (status 6), retornando apenas prováveis. Padrão `false` |
| 19 | **Comparar Formações** | `GET /api/time/comparar?formacoes=4-3-3,3-4-3` monta o melhor time para cada formação com o mesmo pool e retorna um comparativo por `scoreTotal` (consulta pontual, não altera a configuração) |
| 20 | **Excluir Dúvidas do Time** | `GET /api/time?excluirDuvida=true` monta o time só com prováveis — nenhum jogador em dúvida entre titulares e reservas. Padrão `false`, combinável com `orcamento` |
| 21 | **Autenticação JWT** | A API é fechada: `POST /api/auth/login` emite o access token e todo o resto exige `Authorization: Bearer`. Admin inicial criado no primeiro boot |
| 22 | **Gestão de Usuários** | `/api/usuarios` — administrador cria, lista, edita e desativa contas pela própria API; qualquer autenticado vê os próprios dados e troca a própria senha |
| 23 | **Guardrail de Cota** | Abaixo do saldo mínimo, o cliente para de chamar a The Odds API e serve o último snapshot persistido; `GET /api/odds/cota` e `/api/odds/cota/historico` expõem saldo, consumo e a série das leituras |

---

## 2. Stack e Dependências

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

## 3. APIs Externas

### 3.1 The Odds API

| Atributo | Valor |
|---|---|
| URL base | `https://api.the-odds-api.com/v4` |
| Endpoint | `GET /sports/soccer_brazil_campeonato/odds` |
| Autenticação | Query param `apiKey` |
| Plano gratuito | 500 requisições/mês |
| Headers de cota | `x-requests-remaining` e `x-requests-used` — lidos também nas respostas de erro (ver [4.4](#44-cota-da-the-odds-api-guardrail-e-sondagem)) |

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

### 3.2 Cartola FC API (pública, sem autenticação)

**URL base:** `https://api.cartola.globo.com`

| Endpoint | Finalidade | Campos principais |
|---|---|---|
| `/mercado/status` | Status do mercado | `status_mercado` (1=aberto), `rodada_atual` |
| `/atletas/mercado` | Atletas disponíveis | `apelido`, `posicao_id`, `clube_id`, `status_id`, `media_num`, `variacao_num`, `preco_num` |
| `/clubes` | Mapa id → nome/sigla | `nome`, `abreviacao`, `slug` |
| `/partidas` | Partidas da rodada | `clube_casa_id`, `clube_visitante_id` |
| `/atletas/pontuados` | Pontuação pós-rodada | `pontuacao`, `scout` |

---

## 4. Configuração

### 4.1 Arquivo de Propriedades

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
odds.api.min-requests-remaining=50
odds.api.cache-ttl-minutos=60
odds.api.cache-ttl-degradado-minutos=10
odds.api.sonda-intervalo-horas=24

# ── Cartola FC API ────────────────────────────────────────────────────
cartola.api.base-url=https://api.cartola.globo.com
cartola.api.timeout=15000

# ── Banco de Dados ────────────────────────────────────────────────────
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/cartola_odds}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:cartola}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:cartola}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# ── Swagger ───────────────────────────────────────────────────────────
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.try-it-out-enabled=true
springdoc.api-docs.path=/v3/api-docs

# ── Actuator ──────────────────────────────────────────────────────────
# Mesma porta da aplicação; quem separa acesso é a matriz do SecurityConfig
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when_authorized
management.endpoint.health.roles=ADMIN

# ── CORS ──────────────────────────────────────────────────────────────
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}

# ── Servidor ──────────────────────────────────────────────────────────
server.port=8080
server.forward-headers-strategy=native
server.tomcat.remoteip.internal-proxies=${TRUSTED_PROXIES:<faixas privadas>}
```

Arquivo: `src/main/resources/application-prod.properties` — só o que muda em produção
(`SPRING_PROFILES_ACTIVE=prod`):

```properties
# Swagger e OpenAPI desligados: as rotas passam a responder 404 (ver 5.5)
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false

# DEBUG imprime o e-mail do dono de um token recusado e a URI de cada 401/403.
# As linhas de operação já identificam o usuário por id — ver 5.5.
logging.level.com.cartola=INFO
```

### 4.2 Variáveis de Ambiente

Tabela única de todas as variáveis lidas pela aplicação. O `application.properties` usa a sintaxe
`${VAR:default}` para ler o ambiente com fallback:

```properties
odds.api.key=${ODDS_API_KEY:SUA_API_KEY_AQUI}
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/cartola_odds}
```

| Variável | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | `SUA_API_KEY_AQUI` | **Obrigatório** — chave da The Odds API |
| `APP_PORT` | `8080` | Porta exposta no host (somente docker-compose) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cartola_odds` (`postgres:5432` no compose) | URL do banco PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `cartola` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `cartola` | Senha do banco |
| `POSTGRES_USER` | `cartola` | Usuário criado no container PostgreSQL |
| `POSTGRES_PASSWORD` | `cartola` | Senha do container PostgreSQL |
| `SPRING_PROFILES_ACTIVE` | `default` | Profile do Spring Boot. Use `prod` em produção: desliga Swagger e `/v3/api-docs` (passam a responder `404`) e baixa o log de `com.cartola` para `INFO` |
| `JWT_SECRET` | — | Segredo HMAC de assinatura dos tokens (mínimo 32 caracteres). **Obrigatório em produção** |
| `JWT_EXPIRATION_MS` | `86400000` | Validade do access token em milissegundos (24 h) |
| `APP_ADMIN_INICIAL_EMAIL` | `admin@cartolaodds.local` | E-mail do administrador criado no primeiro boot |
| `APP_ADMIN_INICIAL_SENHA` | — | Senha do administrador inicial (mínimo 8 caracteres). **Obrigatória enquanto não houver nenhum administrador ativo no banco** |
| `APP_LOGIN_MAX_TENTATIVAS` | `5` | Falhas de login toleradas por e-mail dentro da janela |
| `APP_LOGIN_JANELA_MINUTOS` | `5` | Janela do freio de login, em minutos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens do frontend liberadas para CORS, separadas por vírgula — nunca `*` |
| `TRUSTED_PROXIES` | faixas privadas | Regex dos endereços de proxy confiáveis (ver [5.4](#54-cabeçalhos-de-segurança-e-proxy-confiável)) |
| `ODDS_API_MIN_REQUESTS_REMAINING` | `50` | Guardrail de cota: abaixo deste saldo restante, o cliente para de chamar a The Odds API e serve o último snapshot conhecido |
| `ODDS_API_CACHE_TTL_MINUTOS` | `60` | TTL do cache `odds` em minutos |
| `ODDS_API_CACHE_TTL_DEGRADADO_MINUTOS` | `10` | TTL de uma resposta de odds **sem nenhum jogo** (provedor fora do ar, fora de temporada) |
| `ODDS_API_SONDA_INTERVALO_HORAS` | `24` | Com o guardrail ativo, intervalo mínimo entre chamadas de sondagem que reavaliam o saldo |

> **Parâmetros de negócio** (odd limite, pesos, formação e regras) **não** são variáveis de
> ambiente: ficam no banco e são gerenciados por `PATCH /api/config` — ver [4.3](#43-parâmetros-de-negócio-via-banco-de-dados).

> **Sem API Key configurada:** a aplicação sobe normalmente, o filtro por time favorito é
> desativado e todos os atletas elegíveis por status/preço são considerados.

### 4.3 Parâmetros de Negócio via Banco de Dados

Os parâmetros de negócio (odd limite, pesos do score, formação e regras) são armazenados na tabela `configuracao` do PostgreSQL e gerenciados via API REST — sem necessidade de restart.

**Migrations Flyway** (aplicadas automaticamente na inicialização):

| Migration | O que faz |
|---|---|
| `V1__create_configuracao.sql` | Cria a tabela com valores padrão (colunas `NUMERIC`) |
| `V2__alter_configuracao_numeric_to_double.sql` | Converte as colunas de pesos/odds para `DOUBLE PRECISION` (necessário para o mapeamento Hibernate de `double`) |
| `V3__add_evitar_mesmo_clube_defesa.sql` | Regra configurável para evitar clubes repetidos entre GOL, LAT e ZAG |
| `V4__add_limite_atletas_por_clube.sql` | Limite configurável de atletas titulares por clube |
| `V5__add_budget_maximo.sql` | Constraint de budget máximo em C$ para os titulares (padrão `0` = sem limite) |
| `V6__add_peso_desvio.sql` | Peso da penalidade por desvio padrão do desempenho (padrão `0.05`) |
| `V7__create_escalacao_rodada.sql` | Tabela `escalacao_rodada` — histórico de escalações por rodada (ver [9.12](#912-histórico-de-escalações-por-rodada)) |
| `V8__create_usuario.sql` | Tabela de usuários (perfil de acesso e `tokenVersion`) |
| `V9__create_odds_snapshot.sql` | Última resposta de odds, para o fallback do guardrail de cota |
| `V10__create_odds_cota.sql` | Estado corrente da cota (saldo, consumo, leitura e sondagem), para o guardrail sobreviver ao deploy |
| `V11__create_odds_cota_historico.sql` | Série append-only das leituras de cota |

```sql
-- V1: estrutura inicial
CREATE TABLE configuracao (
    id               BIGINT PRIMARY KEY,
    odd_limite       DOUBLE PRECISION NOT NULL DEFAULT 3.00,
    peso_media_pontos DOUBLE PRECISION NOT NULL DEFAULT 0.400,
    peso_valorizacao DOUBLE PRECISION NOT NULL DEFAULT 0.200,
    peso_desempenho  DOUBLE PRECISION NOT NULL DEFAULT 0.200,
    peso_fator_casa  DOUBLE PRECISION NOT NULL DEFAULT 0.100,
    peso_time_favorito DOUBLE PRECISION NOT NULL DEFAULT 0.100,
    formacao_gol     INT  NOT NULL DEFAULT 1,
    formacao_lat     INT  NOT NULL DEFAULT 2,
    formacao_zag     INT  NOT NULL DEFAULT 2,
    formacao_mei     INT  NOT NULL DEFAULT 3,
    formacao_ata     INT  NOT NULL DEFAULT 3,
    formacao_tec     INT  NOT NULL DEFAULT 1,
    evitar_mesmo_clube_defesa BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_single_row CHECK (id = 1)
);
```

**Endpoints de configuração:**

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/api/config` | Autenticado | Retorna a configuração atual |
| `PATCH` | `/api/config` | `ADMIN` | Atualiza um ou mais campos em runtime |
| `POST` | `/api/config/reset` | `ADMIN` | Restaura todos os defaults |

**Exemplo — `GET /api/config`:**
```json
{
  "oddLimite": 3.0,
  "pesoMediaPontos": 0.40,
  "pesoValorizacao": 0.20,
  "pesoDesempenho": 0.20,
  "pesoFatorCasa": 0.10,
  "pesoTimeFavorito": 0.10,
  "pesoDesvio": 0.05,
  "formacaoGol": 1,
  "formacaoLat": 2,
  "formacaoZag": 2,
  "formacaoMei": 3,
  "formacaoAta": 3,
  "formacaoTec": 1,
  "evitarMesmoClubeDefesa": true,
  "limiteAtletasPorClube": 4,
  "budgetMaximo": 0.0,
  "updatedAt": "2025-06-01T15:30:00"
}
```

**Validações do `PATCH /api/config`:**
- `oddLimite` deve ser `> 1.0`
- Pesos devem ser `>= 0.0` e `<= 1.0`
- Quando todos os pesos são enviados, a soma deve ser `1.0` (tolerância `±0.01`)
- Formações devem ser `>= 1`
- `evitarMesmoClubeDefesa` ativa/desativa a regra de não repetir clubes entre GOL, LAT e ZAG
- `limiteAtletasPorClube` deve ser `>= 1` e controla o teto de atletas titulares do mesmo clube (inclui TEC)

**Cache:** a configuração é cacheada no Caffeine (`configuracao` cache). `PATCH` e `POST /reset` invalidam o cache automaticamente via `@CacheEvict`.

### 4.4 Cota da The Odds API: guardrail e sondagem

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
| `GET /api/odds/cota` | `ADMIN` | Saldo restante, consumo do mês, instante da última leitura, se o guardrail está ativo e quando a próxima sondagem o destrava (`proximaSondagem`) — ver [8.4](#84-exemplos-de-resposta) |
| `GET /api/odds/cota/historico` | `ADMIN` | Série das leituras na janela (`?dias=30`, de 1 a 92), em ordem cronológica, com `reinicioDeCota` marcando a primeira leitura de um ciclo novo |
| `/actuator/prometheus` | `ADMIN` | `odds_api_requests_total`, `odds_api_requests_remaining` e `odds_api_errors_total` — ver [17](#17-observabilidade) |

> **Configuração recusada no boot:** `ODDS_API_CACHE_TTL_DEGRADADO_MINUTOS` é um *piso* dentro de
> `ODDS_API_CACHE_TTL_MINUTOS`, então precisa caber nele; e as quatro variáveis do guardrail têm
> mínimo `1` (com `ODDS_API_SONDA_INTERVALO_HORAS=0` toda requisição viraria sondagem e o guardrail
> deixaria de existir na prática). A aplicação recusa a subir com esses valores inválidos, nomeando
> a propriedade.

---

## 5. Autenticação e Política de Acesso

### 5.1 Autenticação (JWT)

Todos os endpoints exigem `Authorization: Bearer <accessToken>`, exceto `POST /api/auth/login`,
o healthcheck (`/actuator/health`, `/actuator/info`) e a documentação OpenAPI — esta última só fora
de produção. A API consome cota paga da The Odds API a cada chamada que não vem do cache.

Autenticar diz *quem* está chamando; a [matriz de acesso](#52-matriz-de-acesso-por-rota) diz
*o que cada um pode fazer*. *As razões da escolha por JWT stateless com `tokenVersion` estão em
[`context.md` › Autenticação por JWT](context.md#autenticação-por-jwt-com-tokenversion).*

**Componentes:**

| Classe | Papel |
|---|---|
| `SecurityConfig` | Cadeia stateless, CSRF desabilitado, rotas públicas e o filtro JWT |
| `JwtService` | Emite e lê o token (HS256); resolve o segredo no boot |
| `JwtAuthenticationFilter` | Lê o header, valida token, `ativo` e `tokenVersion`, popula o `SecurityContext` |
| `UsuarioDetailsService` | Carrega o `Usuario` pelo e-mail para o Spring Security |
| `AuthService` | Valida credenciais pelo `AuthenticationManager` e emite o token |
| `ErroSegurancaHandler` | Escreve 401 e 403 no contrato `ErrorResponse` |
| `LoginThrottle` | Freio de força bruta por e-mail, com janela configurável |
| `AdminInicialBootstrap` | Cria o administrador inicial no primeiro boot, de forma idempotente |

**O que o token carrega:**

| Claim | Conteúdo |
|---|---|
| `sub` | E-mail do usuário |
| `perfil` | `ADMIN` ou `USER` |
| `usuarioId` | Id do usuário |
| `tokenVersion` | Versão do token no momento da emissão |

A `tokenVersion` é comparada com a do banco a cada requisição: incrementá-la — ao trocar a senha,
desativar o usuário ou rebaixar seu perfil — invalida na hora todos os tokens já emitidos, sem
sessão no servidor. Trocar o e-mail não precisa do contador: o e-mail é o `subject` do token, e o
token antigo deixa de resolver um usuário sozinho.

**Fluxo de uso:**

```bash
# 1. Autenticar
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@cartolaodds.local","senha":"sua-senha-aqui"}' | jq -r .accessToken)

# 2. Usar o token nas demais chamadas
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/time
```

No **Swagger UI**, o botão **Authorize** recebe apenas o valor do `accessToken`.

**Duração em vez de instante.** O login devolve `expiraEmSegundos` — o tempo de vida do token a
partir da resposta —, não uma data. O container roda em UTC e um horário sem fuso seria lido como
local pelo cliente, que acharia a sessão mais longa do que o token é. Mesma escolha do `expires_in`
do OAuth 2.

**Administrador inicial.** No primeiro boot, se não existir nenhum administrador ativo, a aplicação
cria um a partir de `APP_ADMIN_INICIAL_EMAIL` e `APP_ADMIN_INICIAL_SENHA`. Nem `JWT_SECRET` nem
`APP_ADMIN_INICIAL_SENHA` têm default versionado:

- `APP_ADMIN_INICIAL_SENHA`: exigida em **qualquer perfil** enquanto não houver ADMIN ativo no banco
  — sem ela a aplicação **falha ao iniciar**, com a variável nomeada na mensagem, antes de o
  servidor web abrir a porta (`SmartInitializingSingleton`, ainda dentro do refresh do contexto).
  Com um ADMIN ativo, deixa de ser exigida. O bootstrap é idempotente: nos boots seguintes ele
  encontra o administrador ativo e não faz nada.
- `JWT_SECRET`: obrigatório em `prod`; fora dele, ausente, vira chave efêmera por boot (os tokens
  emitidos deixam de valer no restart), com aviso em log.

**Freio de força bruta por e-mail.** `LoginThrottle` conta falhas por e-mail normalizado e responde
`429` ao atingir o limite — `APP_LOGIN_MAX_TENTATIVAS` (padrão 5) dentro de
`APP_LOGIN_JANELA_MINUTOS` (padrão 5). Um login bem-sucedido zera a contagem, e o bloqueio de um
e-mail não afeta os demais usuários. O mesmo contador protege a conferência da senha atual em
`PATCH /api/usuarios/me/senha` (ver [6](#6-gestão-de-usuários)).

### 5.2 Matriz de acesso por rota

Declarada no `SecurityConfig`. É a **fonte única** desta informação no repositório.

| Rota | Acesso |
|---|---|
| `POST /api/auth/login` | Público |
| `/actuator/health`, `/actuator/health/**`, `/actuator/info` | Público (healthcheck da plataforma) |
| `/error` | Público |
| `/actuator/**` — na prática `metrics` e `prometheus`, já que `health` e `info` casam antes | `ADMIN` |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | Público fora de produção; **404 no perfil `prod`** |
| Preflight `OPTIONS` das origens em `CORS_ALLOWED_ORIGINS` | Público (não carrega token) |
| `GET /api/time`, `/api/time/comparar` | Autenticado |
| `GET /api/ranking`, `/api/favoritos`, `/api/historico**` | Autenticado |
| `POST /api/historico/{rodadaId}/atualizar-pontuacao` | `ADMIN` |
| `GET /api/config` | Autenticado |
| `PATCH /api/config`, `POST /api/config/reset` | `ADMIN` |
| `DELETE /api/cache`, `DELETE /api/cache/{nome}` | `ADMIN` |
| `GET /api/odds/cota`, `/api/odds/cota/**` | `ADMIN` |
| `/api/usuarios/me`, `/api/usuarios/me/**` | Autenticado (qualquer perfil) |
| Todo o resto de `/api/usuarios**` | `ADMIN` |
| Qualquer outra rota | Autenticado |

**O critério é um só: escreve na instância inteira ou gasta cota externa.** `PATCH /api/config` e
`POST /api/config/reset` mudam pesos do score, formação e `odd_limite` da instância inteira;
`DELETE /api/cache` força chamadas novas à The Odds API, cuja cota mensal é paga; e
`POST /api/historico/{rodadaId}/atualizar-pontuacao` regrava a `pontuacaoReal` de todos os atletas
da rodada — a tabela de escalação é da instância, não de quem chamou — depois de consultar a API do
Cartola. As demais rotas de `/api/historico` só leem, e continuam abertas a qualquer autenticado: o
matcher cita o verbo `POST`, então o `GET` da mesma rota cai na regra final.

**A ordem dos matchers importa.** Vale o primeiro que casa: `/api/usuarios/me` vem antes de
`/api/usuarios/**`, e as regras de `ADMIN` vêm antes do `anyRequest().authenticated()` final. Um
matcher por método cobre só aquele método — **`HEAD` não herda a autorização de `GET`** —, e por
isso as regras de `ADMIN` citam apenas os verbos que escrevem: leitura e `HEAD` caem na regra final,
que já é fechada. Em `/api/usuarios` o matcher de URL é o **piso** e o `@PreAuthorize` ao lado de
cada método é a regra fina (ver [6](#6-gestão-de-usuários)).

**Verificação.** `PoliticaAcessoIntegrationTest` percorre a matriz rota a rota nos três estados
(sem token, `USER`, `ADMIN`) e afirma apenas o veredito da autorização, não o status de negócio do
endpoint — amarrar ao status exato faria a matriz quebrar a cada mudança de validação.
`SwaggerProdIntegrationTest` confirma os `404` com `@ActiveProfiles("prod")`, e
`ActuatorEndpointsTest` cobre o Actuator por HTTP real.

*Por que a separação é essa, e por que o Actuator saiu da porta 9090: [`context.md` › Política de
acesso por rota](context.md#política-de-acesso-por-rota-e-hardening-do-perfil-prod).*

### 5.3 Erros de autorização (401 e 403)

`401` e `403` nascem dentro do filter chain, antes do MVC, e não passam pelo
`GlobalExceptionHandler`. Quem os escreve é o `ErroSegurancaHandler`, apontado pelo
`authenticationEntryPoint` e pelo `accessDeniedHandler` — sem isso o cliente receberia a página de
erro do container em vez de JSON. O contrato é o mesmo `ErrorResponse` dos demais erros da API:

```json
{
  "status": 403,
  "erro": "Acesso negado",
  "mensagem": "Voce nao tem permissao para acessar este recurso.",
  "timestamp": "2026-01-15T10:32:00.123"
}
```

O `403` que nasce de um `@PreAuthorize` é tratado dentro do MVC, pelo
`@ExceptionHandler(AccessDeniedException.class)` do `GlobalExceptionHandler`, com o mesmo texto —
para que o corpo do `403` seja um só, venha de onde vier.

### 5.4 Cabeçalhos de segurança e proxy confiável

Toda resposta sai com:

| Cabeçalho | Valor | Origem | Por quê |
|---|---|---|---|
| `X-Content-Type-Options` | `nosniff` | Padrão do Spring Security | Impede o navegador de adivinhar o tipo do conteúdo e executar como script uma resposta que não é |
| `X-Frame-Options` | `DENY` | Padrão do Spring Security | Bloqueia a API dentro de um iframe de terceiro |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | `SecurityConfig` | Evita que a URL completa vaze como referrer para outro site |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains` | `SecurityConfig` | Só quando a requisição chegou por TLS |

O HSTS usa o matcher padrão do Spring Security, que só o emite quando `request.isSecure()`. Atrás da
borda da plataforma o TLS termina no proxy e o Tomcat veria HTTP puro — quem corrige é o
`RemoteIpValve`, ligado por `server.forward-headers-strategy=native`, que normaliza esquema, host e
porta a partir dos `X-Forwarded-*` antes de a requisição chegar ao filter chain (e mantém o
`Location` das respostas `201` apontando para o host público). E o HSTS **não** sai em
`http://localhost`: mandar HSTS ali travaria o navegador do desenvolvedor em HTTPS para todo o host
por um ano.

**`TRUSTED_PROXIES`.** O padrão de `internal-proxies` cobre as faixas privadas (`10/8`,
`172.16-31/12`, `192.168/16`, `169.254/16`, `127/8`, `::1`), que é onde o proxy da plataforma
normalmente fala com o container — na maioria dos deploys não é preciso configurar nada. Se a borda
chegar de um IP público, liste-o em `TRUSTED_PROXIES`:

```bash
TRUSTED_PROXIES=203\.0\.113\.\d{1,3}
```

O valor é uma **regex de endereços**, não um CIDR. O sintoma de faixa errada é observável: os
`X-Forwarded-*` são descartados, `request.isSecure()` fica falso e o `Strict-Transport-Security`
some das respostas. Se o HSTS não aparecer em produção, é aqui que se olha.

O padrão confia em qualquer origem de faixa privada — correto numa plataforma em que só a borda
alcança o container, mas é premissa sobre a topologia, não garantia da aplicação. Fixar
`TRUSTED_PROXIES` na faixa real da borda é tarefa do deploy
([#39](https://github.com/FabioCarlesso/cartolaoddsapi/issues/39)).

*Por que `native` e não `framework`: [`context.md` › Política de acesso por rota](context.md#política-de-acesso-por-rota-e-hardening-do-perfil-prod).*

O `RemoteIpValve` é do Tomcat, e o MockMvc não o atravessa: um caso de `X-Forwarded-*` escrito com
MockMvc passaria verde sem exercitar nada. Por isso essa parte vive em dois testes de HTTP real —
`ProxyConfiavelIntegrationTest`, com a faixa padrão que confia em `127.0.0.1`, e
`ProxyNaoConfiavelIntegrationTest`, que inverte `internal-proxies` para provar que os headers de um
cliente não confiável são ignorados.

### 5.5 Perfil de produção

Com `SPRING_PROFILES_ACTIVE=prod`, o `application-prod.properties` desliga o springdoc:

- `GET /swagger-ui.html` e `GET /v3/api-docs` respondem **`404`**, não `401`.
- `logging.level.com.cartola` cai para `INFO`: em `DEBUG` o log imprime, a cada requisição, o
  e-mail do dono de um token recusado (`JwtAuthenticationFilter`) e a URI de cada `401`/`403`
  (`ErroSegurancaHandler`).

No perfil default as duas rotas continuam abertas, para não atrapalhar o desenvolvimento.

O que **não** depende desse ajuste: as linhas de operação (login, criação de usuário, desativação,
troca de senha) identificam o usuário por `id`, não por e-mail. A única exceção é
`AdminInicialBootstrap`, que cita o e-mail uma vez por instância ao criar o administrador inicial,
repetindo o valor de `APP_ADMIN_INICIAL_EMAIL`.

### 5.6 CORS

`app.cors.allowed-origins` lê `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`); métodos e
headers são listados um a um, **nunca `*`**. O token viaja em header, e uma origem curinga deixaria
qualquer site chamar a API com o token da vítima. O preflight `OPTIONS` passa antes da autorização —
ele não carrega `Authorization`, e sem isso o navegador levaria `401` sem chegar a enviar a
requisição real. Uma origem não liberada recebe `403` no preflight.

> ⚠️ **Pendência conhecida.** Com `/actuator/prometheus` restrito a `ADMIN`, o scrape passa a
> depender de um access token que expira em 24 h sem renovação. Tratado na
> [issue #44](https://github.com/FabioCarlesso/cartolaoddsapi/issues/44).

> ⚠️ **Atenção — `@Qualifier` com Lombok:** `@Qualifier` em campos `final` com
> `@RequiredArgsConstructor` **não funciona** — o Lombok ignora a anotação. `OddsClient` e
> `CartolaClient` usam construtores explícitos com `@Qualifier` no parâmetro do construtor.

---

## 6. Gestão de Usuários

Cadastro e manutenção de contas pela própria API, para que liberar acesso não dependa de `INSERT`
manual no banco de produção com hash BCrypt gerado à mão. **Não há auto-cadastro público:** toda
conta nasce de um administrador.

*Por que não há auto-cadastro e por que a exclusão é lógica: [`context.md` › Gestão de usuários pela
API](context.md#gestão-de-usuários-pela-api-restrita-a-administradores).*

| Classe | Papel |
|---|---|
| `UsuarioApi` | Contrato REST e documentação Swagger dos sete endpoints |
| `UsuarioController` | Implementação limpa; carrega os `@PreAuthorize` de cada rota |
| `UsuarioService` | Regras de criação, atualização, desativação lógica e troca de senha |
| `PaginaResponse<T>` | Envelope de paginação da API, reusável pelos próximos endpoints paginados |
| `ConflitoException` / `SenhaInvalidaException` | Mapeadas a `409` e `422` no `GlobalExceptionHandler` |
| `LoginThrottle` | Freio de força bruta compartilhado entre o login e a conferência da senha atual |

**Endpoints:**

| Método | Rota | Acesso |
|---|---|---|
| `POST` | `/api/usuarios` | `ADMIN` — `201` com `Location` |
| `GET` | `/api/usuarios` | `ADMIN` — paginado (`page`, `size`, `sort`) |
| `GET` | `/api/usuarios/{id}` | `ADMIN` |
| `PATCH` | `/api/usuarios/{id}` | `ADMIN` — `nome`, `email`, `perfil`, `ativo` |
| `DELETE` | `/api/usuarios/{id}` | `ADMIN` — desativação lógica, `204` |
| `GET` | `/api/usuarios/me` | Qualquer autenticado |
| `PATCH` | `/api/usuarios/me/senha` | Qualquer autenticado, exige a senha atual |

### 6.1 Fluxo típico

```bash
# 1. Autenticar como administrador
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@cartolaodds.local","senha":"sua-senha-aqui"}' | jq -r .accessToken)

# 2. Criar o usuário — 201 com o header Location apontando para o recurso
curl -X POST http://localhost:8080/api/usuarios \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nome":"Amigo da Liga","email":"amigo@exemplo.com","senha":"senha-com-8-ou-mais","perfil":"USER"}'

# 3. Listar (paginado) e detalhar
curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/usuarios?page=0&size=20'
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/usuarios/2

# 4. Desativar — o registro continua no banco
curl -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/usuarios/2
```

Já autenticado, qualquer usuário consulta e altera a própria conta:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/usuarios/me

curl -X PATCH http://localhost:8080/api/usuarios/me/senha \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"senhaAtual":"senha-antiga","novaSenha":"senha-nova-com-8-ou-mais"}'
```

### 6.2 Contrato

- **`perfil` é opcional na criação** — sem ele, o usuário nasce como `USER`.
- **Senha:** mínimo de 8 caracteres, gravada em hash BCrypt. **Nenhuma resposta de `/api/usuarios`
  traz o campo de senha, nem em hash** — as respostas usam `UsuarioResponse`, e não a entidade
  `Usuario`, para que um campo novo na entidade não vaze o hash por descuido. `PATCH
  /api/usuarios/{id}` também não aceita senha: quem troca é o dono da conta, confirmando a atual.
- **E-mail** é normalizado para minúsculas e precisa ser único; repetido, responde `409`. A `UNIQUE`
  do Postgres é sensível a caixa e o login (`findByEmailIgnoreCase`) não é — sem normalizar,
  `Fabio@x.com` e `fabio@x.com` coexistiriam e o login ficaria ambíguo. A checagem de duplicidade
  acontece no service; a `UNIQUE` continua sendo a rede para duas criações simultâneas, e o
  `DataIntegrityViolationException` resultante também vira `409`.
- **Regra fina em `@PreAuthorize`, piso em matcher de URL.** O `@PreAuthorize` ao lado de cada
  endpoint (ligado pelo `@EnableMethodSecurity` do `SecurityConfig`) distingue as operações de
  administrador das que o usuário faz sobre a própria conta; o `SecurityConfig` declara sobre elas
  apenas um piso, que cobre o caso de um endpoint novo nascer sem `@PreAuthorize`.

**Desativação é lógica.** `DELETE /api/usuarios/{id}` marca `ativo = false` e mantém o registro no
banco, para não apagar o histórico de quem o produziu. O usuário deixa de autenticar na hora, e os
tokens já emitidos para ele param de valer na requisição seguinte. Repetir a chamada sobre alguém já
inativo responde `204` sem alterar nada.

**O que derruba os tokens já emitidos** (três operações incrementam a `tokenVersion`):

| Operação | Efeito |
|---|---|
| `PATCH /api/usuarios/me/senha` | Derruba inclusive o token usado na própria troca — é preciso autenticar de novo |
| `DELETE /api/usuarios/{id}` (ou `ativo: false`) | O desativado para de acessar a API na requisição seguinte |
| Rebaixar `ADMIN` → `USER` | Sem isso, o rebaixado seguiria administrando a aplicação até o token expirar |

**Proteções contra ficar sem administrador** (`409`, sem alterar o registro):

| Situação | Resposta |
|---|---|
| Administrador desativa ou rebaixa a **própria** conta | `409` — perderia o acesso no ato, quase sempre por engano |
| Desativar ou rebaixar o **último** `ADMIN` ativo | `409` — a instância só voltaria a ter administrador por acesso ao banco |

A checagem do último administrador usa `travarAtivosPorPerfil`, com `@Lock(PESSIMISTIC_WRITE)`
(`SELECT ... FOR UPDATE`), em vez de apenas contar as linhas. Trocar o **próprio e-mail** não é
bloqueado, mas desloga o administrador na prática — o e-mail é o `subject` do token; o `@Operation`
do endpoint avisa disso.

**Freio de força bruta na troca de senha.** `alterarSenha` chama o mesmo `LoginThrottle` do login,
com o mesmo contador por e-mail — os dois conferem o mesmo segredo, e separá-los daria ao atacante
duas janelas para adivinhar a mesma senha.

### 6.3 Formato da paginação

`GET /api/usuarios` é o primeiro endpoint paginado da API e fixa o envelope que os próximos devem
reusar — `PaginaResponse<T>` em vez do `Page` do Spring Data, cujo JSON é detalhe interno do
framework, muda entre versões e o próprio Spring avisa disso no log:

```json
{
  "conteudo": [ { "id": 1, "nome": "Administrador", "email": "admin@cartolaodds.local",
                  "perfil": "ADMIN", "ativo": true, "criadoEm": "2026-09-02T18:00:00" } ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 1,
  "totalPaginas": 1,
  "ultima": true
}
```

Sem parâmetros, devolve os 20 primeiros ordenados por nome. `page`, `size` e `sort` são os
parâmetros padrão do Spring Data, mas `sort` aceita apenas `id`, `nome`, `email`, `perfil`, `ativo`
e `criadoEm` — qualquer outro campo responde `400`. A mensagem de erro não ecoa o campo recebido: a
lista de aceitos basta para corrigir a chamada.

**Payload inválido é `400`, nunca `500`.** O `GlobalExceptionHandler` trata
`HttpMessageNotReadableException` (JSON mal formado, valor fora do enum `Perfil`) e
`PropertyReferenceException` (ordenação desconhecida). Nos dois casos a mensagem original fica só no
log: ela nomeia a classe Java e chega a listar os valores aceitos do enum.

**Verbo errado é `405`, também não `500`.** O handler trata `HttpRequestMethodNotSupportedException`
e a resposta traz o cabeçalho `Allow` com os verbos aceitos, como a RFC 9110 exige.

### 6.4 Recuperação de senha

Não existe nesta versão: sem envio de e-mail, quem esquece a senha depende do administrador da
instância. Auto-cadastro público, convite por e-mail e redefinição de senha ficaram fora do escopo
da [issue #37](https://github.com/FabioCarlesso/cartolaoddsapi/issues/37).

---

## 7. Cache (Caffeine)

O projeto usa **Caffeine** (cache em memória JVM) via `spring-boot-starter-cache`. Não requer
infraestrutura externa — o cache sobe junto com a aplicação e é reiniciado com ela.

### 7.1 Caches registrados

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

### 7.2 Anotações

```java
// CartolaClient — @Cacheable em cada método
@Cacheable(CacheConfig.CACHE_ATLETAS)
public AtletaResponse buscarAtletas() { ... }

// Cache de pontuados com chave por rodada
@Cacheable(value = CacheConfig.CACHE_PONTUADOS, key = "#rodada")
public PontuadosResponse buscarPontuados(int rodada) { ... }
```

### 7.3 Observações

- **Sem Redis:** cache em memória JVM — reiniciado com o container. O cache `odds` é o único com um
  fallback que sobrevive a isso: o snapshot persistido do [guardrail de cota](#44-cota-da-the-odds-api-guardrail-e-sondagem).
- **Tamanho máximo:** 500 entradas por cache (`maximumSize(500)`).
- **Stats:** `recordStats()` habilitado.
- **Thread-safe:** Caffeine garante consistência em ambientes multi-thread. O `@Cacheable` de odds
  usa `sync = true`: sem ele, N misses simultâneos virariam N chamadas pagas para produzir o mesmo
  valor.

### 7.4 Invalidação via API

`DELETE /api/cache` (`ADMIN`) permite forçar a atualização dos dados sem reiniciar a aplicação.

**Nomes de cache válidos:** `odds`, `atletas`, `clubes`, `partidas`, `pontuados`, `statusMercado`.
O cache `configuracao` é interno da camada de configuração e é invalidado automaticamente por
`PATCH /api/config` e `POST /api/config/reset`.

**`DELETE /api/cache` — `200 OK`:**
```json
{
  "cachesInvalidados": ["odds", "atletas", "clubes", "partidas", "pontuados", "statusMercado"],
  "mensagem": "Todos os caches invalidados com sucesso.",
  "timestamp": "2025-06-01T15:30:00"
}
```

**`DELETE /api/cache/{nome}` — `200 OK`:**
```json
{
  "cachesInvalidados": ["atletas"],
  "mensagem": "Cache 'atletas' invalidado com sucesso.",
  "timestamp": "2025-06-01T15:30:00"
}
```

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

## 8. Endpoints

### 8.1 Tabela completa

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/auth/login` | **Público** — valida e-mail/senha e emite o access token JWT |
| `GET` | `/api/time` | Monta o time completo para a rodada atual |
| `GET` | `/api/time?orcamento=120.0` | Monta o time de maior score que cabe no orçamento em cartoletas |
| `GET` | `/api/time?excluirDuvida=true` | Monta o time apenas com prováveis, sem jogadores em dúvida |
| `GET` | `/api/time?orcamento=120.0&excluirDuvida=true` | Monta o time só com prováveis dentro do orçamento informado |
| `GET` | `/api/time/comparar?formacoes=4-3-3,3-4-3` | Compara o melhor time entre múltiplas formações (2 a 5) e ordena por `scoreTotal` |
| `GET` | `/api/time/comparar?formacoes=4-3-3,3-4-3&orcamento=120.0` | Compara formações montando cada uma dentro do orçamento informado |
| `GET` | `/api/favoritos` | Lista times favoritos com odds detalhadas |
| `GET` | `/api/favoritos?oddLimite=2.5` | Favoritos com limite customizado |
| `GET` | `/api/ranking` | Top 25 atletas por score |
| `GET` | `/api/ranking?posicao=ATA` | Top 25 atacantes |
| `GET` | `/api/ranking?posicao=MEI&limite=10` | Top 10 meias |
| `GET` | `/api/ranking?posicao=MEI&limite=5&excluirDuvida=true` | Top 5 meias, sem jogadores em dúvida |
| `DELETE` | `/api/cache` | **`ADMIN`** — invalida todos os caches imediatamente |
| `DELETE` | `/api/cache/{nome}` | **`ADMIN`** — invalida um cache específico pelo nome |
| `GET` | `/api/config` | Retorna a configuração atual (odd limite, pesos, formação e regras) |
| `PATCH` | `/api/config` | **`ADMIN`** — atualiza um ou mais parâmetros em runtime (sem restart) |
| `POST` | `/api/config/reset` | **`ADMIN`** — restaura todos os parâmetros para os valores padrão |
| `POST` | `/api/usuarios` | **`ADMIN`** — cria um usuário e devolve `201` com `Location` |
| `GET` | `/api/usuarios` | **`ADMIN`** — lista paginada de usuários (`page`, `size`, `sort`) |
| `GET` | `/api/usuarios/{id}` | **`ADMIN`** — detalhe de um usuário |
| `PATCH` | `/api/usuarios/{id}` | **`ADMIN`** — atualiza `nome`, `email`, `perfil` e `ativo` |
| `DELETE` | `/api/usuarios/{id}` | **`ADMIN`** — desativação lógica (`ativo = false`), sem apagar o registro |
| `GET` | `/api/usuarios/me` | Dados da própria conta (qualquer autenticado) |
| `PATCH` | `/api/usuarios/me/senha` | Troca a própria senha, exigindo a senha atual |
| `GET` | `/api/historico` | Lista todas as rodadas com escalação registrada e resumo de score sugerido vs. real |
| `GET` | `/api/historico/{rodadaId}` | Detalhe da escalação de uma rodada específica |
| `POST` | `/api/historico/{rodadaId}/atualizar-pontuacao` | Busca a pontuação real da rodada via `/atletas/pontuados` e persiste — exige `ADMIN` |
| `GET` | `/api/odds/cota` | **`ADMIN`** — saldo restante, consumo do mês, instante da última leitura, se o guardrail de cota está ativo e quando a próxima sondagem o destrava |
| `GET` | `/api/odds/cota/historico` | **`ADMIN`** — série das leituras de cota na janela (`?dias=30`, 1 a 92), em ordem cronológica, com marca de reinício de ciclo |
| `GET` | `/swagger-ui.html` | Documentação interativa Swagger UI — pública fora de produção, `404` no perfil `prod` |
| `GET` | `/v3/api-docs` | Spec OpenAPI 3 em JSON — pública fora de produção, `404` no perfil `prod` |
| `GET` | `/actuator/health` | Público — saúde da aplicação |
| `GET` | `/actuator/info` | Público — informações da build |
| `GET` | `/actuator/metrics` | **`ADMIN`** — lista de métricas disponíveis |
| `GET` | `/actuator/metrics/{nome}` | **`ADMIN`** — detalhe de uma métrica específica |
| `GET` | `/actuator/prometheus` | **`ADMIN`** — métricas no formato Prometheus (scrape) |

### 8.2 Códigos de resposta

| Código | Situação |
|---|---|
| `200` | Sucesso |
| `400` | Parâmetro inválido (ex: posição inexistente, `oddLimite <= 1.0`, `orcamento <= 0`) |
| `400` | Valor que não converte para o tipo esperado (ex: `?orcamento=abc`, `?excluirDuvida=abc`) |
| `400` | Erro de validação no corpo do `PATCH /api/config` |
| `400` | Corpo mal formatado ou valor fora de um enum (ex: `"perfil": "SUPERADMIN"`) |
| `400` | `?sort=` com campo não suportado em endpoint paginado |
| `401` | Credenciais inválidas no login, ou requisição sem token / com token inválido, expirado ou revogado |
| `403` | Autenticado, mas sem permissão para o recurso; ou preflight CORS de origem não liberada |
| `404` | Recurso inexistente (ex: `GET /api/usuarios/{id}` de um id que não existe) |
| `405` | Verbo errado numa rota que existe (ex: `GET /api/config/reset`); a resposta traz `Allow` com os verbos aceitos |
| `409` | E-mail já cadastrado, ou operação que deixaria a instância sem administrador ativo |
| `422` | Nenhum atleta disponível após filtragem (ODD_LIMITE muito restritivo) |
| `422` | Senha atual incorreta em `PATCH /api/usuarios/me/senha` |
| `429` | Excesso de tentativas de login, ou de senha atual errada em `PATCH /api/usuarios/me/senha` |
| `502` | Falha de comunicação com API externa |
| `500` | Erro interno inesperado |

### 8.3 Aviso de mercado

Quando o mercado não está aberto, todos os endpoints retornam o campo `avisoMercado` preenchido:

| Código | Enum | Label | Exibe aviso | Aviso retornado |
|---|---|---|---|---|
| 1 | `ABERTO` | Aberto | Não | `null` (campo omitido no JSON) |
| 2 | `FECHADO` | Fechado | **Sim** | `"Mercado fechado. Rodada em andamento."` |
| 3 | `MANUTENCAO` | Manutencao | **Sim** | `"Mercado em manutencao ou pre-temporada."` |
| 4 | `PARCIAL` | Parcial | **Sim** | `"Mercado parcialmente aberto. Alguns jogos ja ocorreram."` |
| 6 | `FINALIZANDO` | Finalizando | **Sim** | `"Processamento pos-rodada em andamento."` |
| outros | `DESCONHECIDO` | Desconhecido | **Sim** | `"Status desconhecido. Dados podem estar desatualizados."` |

### 8.4 Exemplos de resposta

#### `GET /api/favoritos`

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
  ],
  "oddsDeSnapshot": false
}
```

`oddsDeSnapshot` indica que a resposta veio do snapshot persistido em vez de uma consulta ao vivo —
ver [4.4](#44-cota-da-the-odds-api-guardrail-e-sondagem).

#### `GET /api/odds/cota`

```json
{
  "saldoRestante": 412,
  "consumoMes": 88,
  "ultimaLeitura": "2026-09-05T10:00:00",
  "minRequestsRemaining": 50,
  "guardrailAtivo": false,
  "ultimaSondagem": null,
  "proximaSondagem": "2026-09-06T10:00:00"
}
```

#### `GET /api/odds/cota/historico?dias=7`

```json
{
  "dias": 7,
  "desde": "2026-08-29T10:00:00",
  "total": 3,
  "leituras": [
    { "instante": "2026-08-31T22:00:00", "saldoRestante": 8,   "consumoMes": 492, "reinicioDeCota": false },
    { "instante": "2026-09-01T09:00:00", "saldoRestante": 500, "consumoMes": 0,   "reinicioDeCota": true  },
    { "instante": "2026-09-01T10:00:00", "saldoRestante": 499, "consumoMes": 1,   "reinicioDeCota": false }
  ]
}
```

> `reinicioDeCota` marca a primeira leitura de um ciclo novo: em relação à leitura anterior, o
> consumo caiu **ou** o saldo subiu — os dois sinais que a renovação da cota produz. Nunca vem
> `true` na primeira leitura da janela: sem uma anterior para comparar, afirmar que houve renovação
> seria chute.

> ⏱️ **Fuso horário.** `instante` e `desde` são `LocalDateTime`: data e hora **locais do servidor**,
> sem offset. Um `new Date(instante)` no navegador interpreta como hora local dele — com servidor em
> UTC e navegador em UTC−3, todo ponto do gráfico desloca 3 h. Converta usando o fuso em que a
> aplicação roda. É a convenção de data/hora de toda a API, não só deste endpoint. O `instante` da
> última leitura da série é exatamente o `ultimaLeitura` de `GET /api/odds/cota` — os dois são
> truncados a microssegundos na origem, então comparam direto.

> 📦 **Tamanho da resposta.** A série não é agregada: cada leitura vira um item. A janela padrão de
> 30 dias dá ~500 itens (~50 KB); o teto de 92 dias, ~1.500.

#### `GET /api/ranking?posicao=ATA&limite=3`

```json
{
  "rodada": 15,
  "posicao": "ATA",
  "limite": 3,
  "totalDisponivel": 18,
  "atletas": [
    { "rank": 1, "apelido": "Hulk", "formatado": "Hulk (ATM)", "score": 8.54, "preco": 22.0, "desvioPadrao": 1.25, "rodadasConsideradas": 5, "emDuvida": false },
    { "rank": 2, "apelido": "Cano",  "formatado": "Cano (FLU)",  "score": 7.90, "preco": 18.3, "desvioPadrao": 2.10, "rodadasConsideradas": 5, "emDuvida": false },
    { "rank": 3, "apelido": "Pedro", "formatado": "Pedro (FLA) ⚠️ DÚVIDA", "score": 7.70, "preco": 17.0, "desvioPadrao": 0.0, "rodadasConsideradas": 0, "emDuvida": true }
  ]
}
```

> Os campos `desvioPadrao` e `rodadasConsideradas` expõem o desvio padrão populacional das
> pontuações e a quantidade de rodadas usadas no cálculo do desempenho recente. Disponíveis também
> no `GET /api/time`. Valem `0.0` e `0` quando o atleta não tem histórico recente (menos de 2
> rodadas ou ausente do histórico), caso em que nenhuma penalidade por volatilidade é aplicada.

#### `GET /api/historico`

```json
{
  "totalRodadas": 2,
  "rodadas": [
    {
      "rodadaId": 14,
      "criadoEm": "2025-05-10T10:30:00",
      "totalAtletas": 12,
      "scoreSugeridoTotal": 94.3,
      "pontuacaoRealTotal": 87.5,
      "pontuacaoRealDisponivel": true
    },
    {
      "rodadaId": 15,
      "criadoEm": "2025-05-17T09:15:00",
      "totalAtletas": 12,
      "scoreSugeridoTotal": 101.2,
      "pontuacaoRealTotal": null,
      "pontuacaoRealDisponivel": false
    }
  ]
}
```

#### `GET /api/historico/14`

```json
{
  "rodadaId": 14,
  "atletas": [
    {
      "apelido": "Hulk",
      "posicao": "ATA",
      "clube": "Atletico MG",
      "scoreSugerido": 9.2,
      "pontuacaoReal": 8.5,
      "capitao": true,
      "reservaLuxo": false,
      "emDuvida": false
    }
  ]
}
```

Uma rodada sem escalação registrada retorna `404 Not Found` em `GET /api/historico/{rodadaId}` e
`POST /api/historico/{rodadaId}/atualizar-pontuacao`.

#### `GET /api/time/comparar?formacoes=4-3-3,3-4-3,4-4-2`

```json
{
  "rodada": 15,
  "formacoesComparadas": 3,
  "melhorFormacao": "4-3-3",
  "resultados": [
    { "formacao": "4-3-3", "scoreTotal": 94.3, "custoTotal": 138.5, "capitao": "Hulk (ATM)", "posicao": 1, "formacaoCompleta": true, "time": { } },
    { "formacao": "3-4-3", "scoreTotal": 91.7, "custoTotal": 132.1, "capitao": "Arrascaeta (FLA)", "posicao": 2, "formacaoCompleta": true, "time": { } },
    { "formacao": "4-4-2", "scoreTotal": 89.2, "custoTotal": 129.8, "capitao": "Hulk (ATM)", "posicao": 3, "formacaoCompleta": true, "time": { } }
  ]
}
```

- `resultados` ordenados por `scoreTotal` decrescente; `posicao` indica o ranking entre as formações comparadas.
- `melhorFormacao` aponta para o primeiro da lista (maior `scoreTotal`).
- `formacaoCompleta` sinaliza, por resultado, se a formação pôde ser totalmente preenchida; quando há `orcamento` insuficiente, `avisoOrcamento` também é preenchido naquele resultado.
- Cada `time` traz a estrutura completa do `GET /api/time` (titulares, reservas, capitão, etc.).

### 8.5 Parâmetros de `GET /api/time`

#### `orcamento` (opcional)

Limita o total de cartoletas gastas na montagem:

- **Sem `orcamento`** → estratégia `SCORE_MAXIMO`, candidatos ordenados por score, sem restrição de
  custo (a não ser o `budgetMaximo` da configuração, se definido).
- **Com `orcamento`** → estratégia `SCORE_MAXIMO` **sujeita ao teto**: o montador resolve um
  *multiple-choice knapsack* por posição via **branch-and-bound**, escolhendo a combinação de
  **maior soma de score** que cabe no orçamento (e não os mais baratos). Em empate de score, vence a
  de **menor custo**. As regras de formação, limite por clube e defesa sem clube repetido continuam
  respeitadas. Com orçamento folgado, o resultado coincide com o time de maior score absoluto.

A resposta passa a expor `orcamentoInformado`, `custoTotal`, `saldoRestante`, `estrategia`,
`formacaoCompleta` e — quando o orçamento não basta para completar os 12 titulares —
`avisoOrcamento` (nesse caso o time é o melhor *best-effort* dentro do teto). Valores em cartoletas
são arredondados para 2 casas decimais. Detalhes do algoritmo em
[9.9](#99-budget-máximo-c-e-otimização-por-orçamento).

```json
{
  "rodada": 15,
  "orcamentoInformado": 120.0,
  "custoTotal": 118.3,
  "saldoRestante": 1.7,
  "estrategia": "SCORE_MAXIMO",
  "formacaoCompleta": true,
  "avisoMercado": null,
  "titulares": { },
  "reservas": { }
}
```

Quando o orçamento é baixo demais para os 12 titulares, a formação é retornada incompleta e
`avisoOrcamento` é preenchido (`saldoRestante` nunca fica negativo):

```json
{
  "rodada": 15,
  "orcamentoInformado": 30.0,
  "custoTotal": 24.0,
  "saldoRestante": 6.0,
  "estrategia": "SCORE_MAXIMO",
  "formacaoCompleta": false,
  "avisoOrcamento": "Orcamento de C$30,0 insuficiente para completar a formacao (10/12 titulares escalados). Considere aumentar o orcamento.",
  "titulares": { },
  "reservas": { }
}
```

Sem orçamento, `orcamentoInformado` e `saldoRestante` vêm `null` e `custoTotal` traz o custo real da
escalação:

```json
{
  "rodada": 15,
  "orcamentoInformado": null,
  "custoTotal": 147.8,
  "saldoRestante": null,
  "estrategia": "SCORE_MAXIMO",
  "formacaoCompleta": true,
  "avisoMercado": null,
  "titulares": { },
  "reservas": { }
}
```

> `orcamento` deve ser **maior que 0** — valores `<= 0` retornam `400 Bad Request`.

#### `excluirDuvida` (opcional, padrão `false`)

Restringe o pool de montagem aos atletas **prováveis** (status 7):

- **`false`** (padrão) → prováveis e dúvidas concorrem às vagas e cada titular em dúvida recebe um
  substituto provável sugerido em `alertasDuvida`/`substitutoProvavel`.
- **`true`** → jogadores em dúvida (status 6) são removidos **antes do cálculo de score**, de modo
  que nenhum deles apareça entre titulares ou reservas. Como não há dúvidas escaladas,
  `alertasDuvida` volta vazio.

O filtro é aplicado **após o cache** — as respostas cacheadas das APIs externas são compartilhadas
com o fluxo padrão e não são invalidadas. É combinável com `orcamento`. Se não sobrarem prováveis
suficientes para alguma posição, a resposta é retornada normalmente com `formacaoCompleta: false`.

```bash
# Melhor time escalável sem nenhum jogador em dúvida
curl "http://localhost:8080/api/time?excluirDuvida=true"

# Combinando com orçamento
curl "http://localhost:8080/api/time?orcamento=120&excluirDuvida=true"
```

> O mesmo parâmetro já existe em `GET /api/ranking`, com a mesma semântica.
>
> Por ser uma consulta comparativa, `excluirDuvida=true` **não registra** a escalação no histórico —
> ver [9.12](#912-histórico-de-escalações-por-rodada). O `orcamento`, sozinho, continua registrando
> normalmente.

### 8.6 `GET /api/time/comparar`

Monta o melhor time para **cada formação informada** usando o mesmo pool de atletas da rodada e
retorna um comparativo ordenado por `scoreTotal`. É uma **consulta pontual**: a formação configurada
no banco **não é alterada**.

- Parâmetro **obrigatório** `formacoes`: lista separada por vírgula no formato `def-mei-ata` (ex:
  `4-3-3,3-4-3,4-4-2`), onde o primeiro número é o total de **defensores** (laterais + zagueiros),
  como na notação do Cartola FC.
- A soma das posições de linha de cada formação (`def + mei + ata`) deve ser **10**. As posições
  fixas `GOL=1` e `TEC=1` vêm da configuração; a defesa é derivada com **`LAT` fixo em 2** e
  **`ZAG = def − LAT`** (ex: `4-3-3` → `ZAG=2`, `LAT=2`). Cada time tem exatamente **11 em campo + 1
  técnico** (12 titulares).
- Mínimo de **2** e máximo de **5** formações **distintas**; duplicatas são ignoradas silenciosamente.
- Parâmetro **opcional** `orcamento`: aplica o limite de cartoletas a cada formação, igual ao `GET /api/time`.
- `scoreTotal` soma **apenas os titulares**, para uma comparação justa entre formações com número
  diferente de atletas por posição.
- As mesmas regras de montagem valem para cada formação: limite por clube, defesa sem clube repetido
  e dúvidas com substituto.

| Situação | Resposta |
|---|---|
| Menos de 2 formações distintas | `400 Bad Request` |
| Mais de 5 formações distintas | `400 Bad Request` |
| Formação com soma de linhas `!= 10` (ex: `4-3-2`) | `400 Bad Request` com mensagem explicativa |
| Formação com posição zerada ou formato inválido (ex: `10-0-0`, `4-3-3-`) | `400 Bad Request` |
| Parâmetro `formacoes` ausente | `400 Bad Request` |
| `orcamento <= 0` | `400 Bad Request` |
| Nenhum atleta disponível na rodada | `422 Unprocessable Entity` |

---

## 9. Regras de Negócio

### 9.1 Identificação de Times Favoritos

1. Cruza os jogos da Odds API com os confrontos da rodada atual do Cartola (`/partidas` + `/clubes`).
2. Ignora odds de confrontos que não pertencem à rodada atual.
3. Para cada jogo restante, seleciona o time com **menor odd** (maior probabilidade de vitória).
4. Aplica `ODD_LIMITE` (padrão `3.0`):
   - `odd ≤ ODD_LIMITE` → time entra no conjunto `favoritos_norm`; jogadores desse time são incluídos no pool
   - `odd > ODD_LIMITE` → jogo descartado (equilibrado ou sem favorito claro); **nenhum** time desse jogo entra no pool
5. Nomes normalizados antes do cruzamento com dados do Cartola.

Se os confrontos da rodada atual não estiverem disponíveis, o processamento mantém o fallback resiliente e considera todas as odds retornadas.

```
Flamengo x Palmeiras → odds: FLA 2.10 / PAL 3.40
Favorito: Flamengo (2.10 ≤ 3.0) ✅

Fortaleza x Bahia → odds: FOR 3.30 / BAH 3.40
Favorito: Fortaleza (3.30 > 3.0) ⛔ descartado
```

### 9.2 Filtros de Atletas

| Filtro | Regra | Fallback |
|---|---|---|
| `status_id` | `6` (Dúvida) ou `7` (Provável) — apenas `7` com `excluirDuvida=true` | Descartado |
| `preco_num` | `> 0` cartoletas | Descartado |
| Time favorito | Clube em `favoritos_norm` | Descartado |
| Sem odds | `favoritos_norm` vazio | Filtro desativado — usa todos os elegíveis |

### 9.3 Fórmula do Score

O `ScoreService` usa fórmulas específicas por posição para GOL e ATA, mantendo o fallback
configurável para LAT, ZAG, MEI e TEC. Os bônus `fatorCasa` e `timeFavorito` valem `10.0` quando
verdadeiros e `0.0` caso contrário.

**Fallback configurável (LAT, ZAG, MEI, TEC)** — pesos padrão entre parênteses:

```
score = (mediaPontos × pesoMediaPontos)    (0.40)
      + (valorização × pesoValorizacao)    (0.20)
      + (desempenho × pesoDesempenho)      (0.20)
      + (fatorCasa × pesoFatorCasa)        (0.10)
      + (timeFavorito × pesoTimeFavorito)  (0.10)
      - (desvioPadrao × pesoDesvio)        (0.05)
```

**Goleiro (GOL) — scouts defensivos com maior peso:**

```
score = (desempenho × 0.35) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (defesasDificeis × 0.05) + (penaltisDefendidos × 0.05) - (golsSofridos × 0.02)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      - (desvioPadrao × pesoDesvio)
```

**Atacante (ATA) — participação ofensiva com maior peso:**

```
score = (desempenho × 0.25) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (gols × 0.08) + (assistencias × 0.05)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      - (desvioPadrao × pesoDesvio)
```

Os scouts são acumulados da temporada vindos de `/atletas/mercado`: `DD` → `defesasDificeis`,
`GS` → `golsSofridos`, `DP` → `penaltisDefendidos`, `G` → `gols`, `A` → `assistencias`. Valores
ausentes ou nulos são tratados como `0`, sem impacto no cálculo.

Todos os pesos do fallback e os bônus situacionais são configuráveis em runtime via
`PATCH /api/config`. Os pesos base de GOL e ATA são constantes centralizadas no `ScoreService`
(prefixos `GOL_` e `ATA_`); para essas posições, apenas `pesoFatorCasa`, `pesoTimeFavorito` e
`pesoDesvio` continuam configuráveis.

**Desempenho:** usa a média real das últimas 5 rodadas via `/atletas/pontuados`. Fallback automático
para `mediaPontos` da temporada quando o histórico não estiver disponível.

**Penalização por volatilidade:** o `DesempenhoService` retorna um `DesempenhoAtleta` com
`mediaPontos`, `desvioPadrao` (populacional, divisão por N) e `rodadasConsideradas`. O
`ScoreService` subtrai `desvioPadrao × pesoDesvio` do score final em todas as posições, priorizando
atletas consistentes em situações de empate técnico. `pesoDesvio` é configurável via
`PATCH /api/config` (padrão `0.05`); com menos de 2 rodadas o `desvioPadrao` é `0.0`, anulando a
penalidade. Atletas sem histórico recente caem para o proxy `mediaPontos` e não são penalizados.

**Exposição na API:** o `desvioPadrao` (arredondado para 4 casas decimais) e `rodadasConsideradas`
são propagados para o modelo `Atleta` e retornados nos DTOs `AtletaDto` (`GET /api/time`) e
`AtletaRankingDto` (`GET /api/ranking`), permitindo ao frontend exibir um indicador de consistência.
Atletas que usam o proxy retornam `desvioPadrao = 0.0` e `rodadasConsideradas = 0`. Os campos são
documentados no schema OpenAPI via anotações `@Schema`.

*Por que fórmulas distintas por posição: [`context.md` › Fórmula de Score](context.md#fórmula-de-score).*

### 9.4 Formação 4-3-3

`1 GOL · 2 LAT · 2 ZAG · 3 MEI · 3 ATA · 1 TEC`

| Slot | `posicao_id` | Qtd | Pool elegível |
|---|---|---|---|
| GOL | 1 | 1 | Somente `posicao == GOL` |
| LAT | 2 | 2 | Somente `posicao == LAT` |
| ZAG | 3 | 2 | Somente `posicao == ZAG` |
| MEI | 4 | 3 | Somente `posicao == MEI` |
| ATA | 5 | 3 | Somente `posicao == ATA` |
| TEC | 6 | 1 | Somente `posicao == TEC` |

Cada slot seleciona **exclusivamente** dentro da sua posição. A formação é configurável via
`PATCH /api/config`.

### 9.5 Seleção de Reservas

- Somente `status == PROVAVEL` (7) — dúvidas não são reservas.
- Preferencialmente mais baratos que o titular mais caro da posição.
- Fallback: qualquer provável da posição se nenhum mais barato existir.
- Sempre da **mesma posição individual** do titular (LAT reserva LAT, ZAG reserva ZAG).
- `TEC` não tem reserva.

### 9.6 Defesa sem Clube Repetido

Quando `evitarMesmoClubeDefesa=true` (padrão), a seleção de titulares não repete clubes entre `GOL`,
`LAT` e `ZAG`. O montador percorre os candidatos por score e pula defensores cujo clube já tenha
sido usado nessas posições. A regra não limita `MEI`, `ATA` ou `TEC` e pode ser desligada em runtime
com `PATCH /api/config`:

```json
{ "evitarMesmoClubeDefesa": false }
```

Caso não haja candidatos suficientes sem repetição (ex: poucos clubes disponíveis na rodada), o
montador completa a posição com os melhores atletas restantes — evitando apenas apelidos já
escalados — garantindo que a formação nunca fique incompleta.

### 9.7 Capitão e Reserva de Luxo

- **Capitão:** titular com maior score global, prioridade `ATA > MEI > ZAG > LAT > GOL > TEC` em
  caso de empate. O capitão tem pontuação **dobrada** no Cartola FC.
- **Reserva de Luxo:** atleta de maior score entre todas as **reservas**; como `TEC` não tem
  reserva, técnicos não concorrem a reserva de luxo.

### 9.8 Limite Máximo por Clube (inclui TEC)

Na escalação titular, o montador respeita **no máximo 4 atletas do mesmo clube**, considerando todas
as posições, inclusive `TEC`. O valor é configurável em runtime via `PATCH /api/config` no campo
`limiteAtletasPorClube` (padrão `4`).

Fallback em três níveis:

1. **Primário** — respeita a regra de defesa (sem clube repetido em GOL/LAT/ZAG), o limite por clube e o budget.
2. **Intermediário** — relaxa a regra de defesa, mas mantém o limite por clube e o budget.
3. **Último recurso** — relaxa também o limite por clube, mas mantém o budget.

### 9.9 Budget Máximo (C$) e Otimização por Orçamento

O `MontadorTimeService` respeita um **teto de gasto em Cartoletas (C$)** para o time titular. O teto
efetivo é o `orcamento` informado em `GET /api/time` (tem prioridade) ou, na ausência dele, o
`budgetMaximo` configurável via `PATCH /api/config`. Quando não há teto (`budgetMaximo = 0` e sem
`orcamento`), a constraint é desativada: o montador usa a **seleção gulosa por score**, que já é
ótima nesse caso.

Com um teto finito, a seleção dos titulares passa pelo `OtimizadorTitulares`, que resolve um
**multiple-choice knapsack por posição** via **branch-and-bound**. O objetivo é **maximizar a soma
de score** com `Σ preço ≤ orçamento` (estratégia sempre `SCORE_MAXIMO`), e não pegar os mais
baratos:

- **Desempate:** entre soluções de score igual (dentro de um epsilon), vence a de **menor custo**
  (equivalente a maior `score/preço`).
- **Restrições preservadas:** formação, `limiteAtletasPorClube` e a regra de defesa sem clube
  repetido (GOL/LAT/ZAG) são respeitadas dentro da busca.
- **Podas:** viabilidade de orçamento (custo mínimo para completar as vagas restantes), limite
  superior admissível de score e redução de candidatos **por clube**. A redução só descarta um
  atleta quando existem ao menos `min(vagas da posição, limiteAtletasPorClube)` outros do mesmo
  clube que o dominam (score ≥ e preço ≤), preservando a otimalidade mesmo em posições com várias
  vagas. Na defesa com a regra ativa, o limite é 1 por clube.
- **Orçamento insuficiente vs. restrições:** quando não é possível completar a formação dentro do
  teto por falta de orçamento, retorna o melhor time *best-effort* (mais vagas preenchidas, depois
  maior score), com `formacaoCompleta = false` e `avisoOrcamento` preenchido. Se a incompletude vier
  das **restrições de clube/defesa** (e não do orçamento), o montador recorre à seleção gulosa — que
  relaxa essas regras em último recurso — e fica com a montagem que preenche mais vagas, evitando
  atribuir ao orçamento uma incompletude que é de clube.
- **Guarda de iterações:** ao estourar o teto de iterações do branch-and-bound, o montador recai na
  seleção gulosa por orçamento como fallback, garantindo resposta sempre válida.

### 9.10 Tratamento de Dúvidas

- Titulares com `status_id == 6` são escalados, mas marcados com `⚠️ DÚVIDA`.
- O sistema busca o melhor substituto `PROVAVEL` na **mesma posição individual**.
- O substituto nunca é outro atleta já escalado como titular.
- Alertas retornados em `alertasDuvida` no `TimeResponse`.

**Opt-out via `excluirDuvida`:** o parâmetro opcional `excluirDuvida=true` em `GET /api/time` e
`GET /api/ranking` remove os atletas com `status_id == 6` do pool no `PipelineService`, **após o
cache** e **antes** do `ScoreService` — ver [8.5](#85-parâmetros-de-get-apitime).

### 9.11 Normalização de Nomes de Clubes

Antes de cruzar Odds API e Cartola FC, nomes são convertidos para lowercase, sem acentos (Unicode
NFD), com hífens transformados em espaços, caracteres especiais removidos, espaços duplicados
colapsados e aliases aplicados.

```java
NormalizadorUtil.normalizar("Atlético-MG")          // → "atletico mg"
NormalizadorUtil.normalizar("Atlético Mineiro MG")  // → "atletico mg"
NormalizadorUtil.normalizar("Athletico Paranaense") // → "athletico pr"
NormalizadorUtil.normalizar("São Paulo FC")         // → "sao paulo"
NormalizadorUtil.normalizar("Grêmio")               // → "gremio"
NormalizadorUtil.normalizar("Inter")                // → "internacional"
NormalizadorUtil.normalizar("Fluminense FC")        // → "fluminense"
NormalizadorUtil.normalizar("Vasco da Gama")        // → "vasco"
```

Aliases atuais cobrem divergências recorrentes entre The Odds API e Cartola FC, como
`atletico mineiro`, `atletico mineiro mg`, `red bull bragantino`, `bragantino sp`,
`atletico goianiense`, `america mineiro`, `atletico paranaense`, `athletico paranaense`,
`sao paulo fc`, `inter`, `fluminense fc`, `vasco da gama` e variantes como `botafogo fr`/`botafogo
rj`, `ec bahia`, `cruzeiro ec`, `palmeiras sp`, `sport recife` e `santos fc`. Entradas como
`sportrecife` (sem espaço) existem no mapa para cobrir casos em que uma barra é removida pelo
pipeline antes da consulta ao dicionário (ex.: `Sport/Recife` → `sportrecife` após limpeza de
caracteres especiais).

**Para manter o dicionário**, adicione entradas em `NormalizadorUtil.ALIASES`. A chave deve estar no
formato já normalizado pelo utilitário (sem acentos, lowercase, hífens como espaços e espaços
duplicados colapsados) e o valor deve ser o nome canônico usado no cruzamento entre favoritos e
clubes do Cartola.

**De onde vem cada nome.** Pelo lado da Odds API, do `home_team`/`away_team`. Pelo lado do Cartola,
do **`slug`** do clube (`"atletico-pr"`), e não de `nome`/`nome_fantasia`: desde 2026 o `/partidas`
devolve a sigla nesses dois campos (ambos `"MIR"`), e `apelido` traz o apelido de torcida
(`"Colorado"`, `"Furacão"`). O `slug` é o único campo que ainda carrega o nome por extenso. Quem
exibe o clube continua usando `nome_fantasia`/`nome` — `CartolaDataService` separa os dois papéis em
`nomeClube` (exibição) e `nomeClubeParaChave` (cruzamento).

### 9.12 Histórico de Escalações por Rodada

`GET /api/time` persiste a escalação sugerida da rodada (titulares e reservas) na tabela
`escalacao_rodada`, permitindo análise retroativa da qualidade das sugestões. A persistência é
orquestrada pelo `EscalacaoService`:

- **Idempotência:** `salvarEscalacao(Time, rodadaId)` verifica `existsByRodadaId` antes de gravar;
  uma rodada já registrada não é sobrescrita.
- **Não bloqueante:** a chamada parte do `TimeController` dentro de um `try/catch`; falhas ao
  persistir são logadas e não impedem o retorno do time.
- **Com `orcamento` persiste; com `excluirDuvida=true` não.** O `orcamento` delimita um teto real de
  cartoletas e produz a escalação que de fato será usada. Já `excluirDuvida=true` é uma consulta
  *comparativa*: como a gravação é idempotente por rodada, registrar a variante sem dúvidas faria o
  histórico gravar a **primeira** consulta feita na rodada em vez da sugestão da rodada. Mesmo
  critério do `/api/time/comparar`, que também não persiste.
- **Flags por atleta:** `capitao`, `reserva_luxo` e `em_duvida` são derivadas do `Time` montado. A
  `pontuacao_real` nasce `null`.

Após o fechamento da rodada, `POST /api/historico/{rodadaId}/atualizar-pontuacao` consulta
`/atletas/pontuados` e preenche a `pontuacao_real` dos atletas encontrados (os ausentes permanecem
`null`). No cálculo do total da rodada (`pontuacaoRealTotal`), a pontuação do capitão é contada em
dobro.

> **Restrição da rodada corrente:** o `/atletas/pontuados` do Cartola expõe somente a rodada atual.
> Para não gravar a pontuação de uma rodada em outra, `atualizarPontuacaoReal` valida
> `rodadaId == rodada corrente` (via `/mercado/status`) e lança `IllegalArgumentException` (→ `400`)
> caso contrário. A leitura e a chamada HTTP ocorrem fora de transação de escrita; apenas o
> `saveAll` final abre transação.

**Tabela `escalacao_rodada` (migration `V7`):**

```sql
CREATE TABLE escalacao_rodada (
    id             BIGSERIAL PRIMARY KEY,
    rodada_id      INTEGER NOT NULL,
    atleta_id      INTEGER NOT NULL,
    apelido        VARCHAR(100) NOT NULL,
    posicao        VARCHAR(10)  NOT NULL,
    clube          VARCHAR(100) NOT NULL,
    score_sugerido DOUBLE PRECISION NOT NULL,
    preco          DOUBLE PRECISION NOT NULL,
    capitao        BOOLEAN NOT NULL DEFAULT FALSE,
    reserva_luxo   BOOLEAN NOT NULL DEFAULT FALSE,
    em_duvida      BOOLEAN NOT NULL DEFAULT FALSE,
    pontuacao_real DOUBLE PRECISION,
    criado_em      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_escalacao_rodada_atleta UNIQUE (rodada_id, atleta_id)
);
```

**Endpoints de histórico:**

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/api/historico` | Autenticado | Lista as rodadas registradas com resumo (score sugerido vs. pontuação real) |
| `GET` | `/api/historico/{rodadaId}` | Autenticado | Detalhe da escalação de uma rodada — `404` se não registrada |
| `POST` | `/api/historico/{rodadaId}/atualizar-pontuacao` | `ADMIN` | Preenche a `pontuacao_real` da rodada — `404` se não registrada |

Rodadas sem escalação registrada resultam em `RecursoNaoEncontradoException`, mapeada para
`404 Not Found` pelo `GlobalExceptionHandler`. Um `GET` na rota de atualização — que só aceita `POST`
— responde `405 Method Not Allowed` com `Allow: POST`.

### 9.13 Histórico das leituras de cota

**Tabela `odds_cota_historico` (migration `V11`):**

| Coluna | Tipo | Conteúdo |
|---|---|---|
| `id` | `BIGSERIAL` | Chave |
| `instante` | `TIMESTAMP NOT NULL` | Momento da leitura dos headers de cota |
| `saldo_restante` | `BIGINT` | `x-requests-remaining`; nulo quando o header não veio |
| `consumo_mes` | `BIGINT` | `x-requests-used`; nulo quando o header não veio |

Append-only: uma linha por leitura, nunca atualizada. Complementa a `odds_cota` (`V10`, linha única
com o estado corrente) em vez de substituí-la — é a linha única que o guardrail recupera no boot, e
é a série que responde "quanto se gastou ao longo do mês". Sem retenção, por decisão: uma linha só
nasce de uma chamada ao provedor, e as chamadas são limitadas pela própria cota que a tabela mede
(~500 linhas/mês no plano free).

`GET /api/odds/cota/historico?dias=N` lê essa tabela, com `N` entre 1 e 92 (`400` fora da faixa). A
série é ordenada pelo **instante da leitura**, com o `id` como desempate. Os campos de data são
`LocalDateTime` — hora local do servidor, sem offset — como no resto da API. Contrato de resposta em
[8.4](#84-exemplos-de-resposta).

*Por que append-only, por que 92 dias e como o reinício de ciclo é detectado:
[`context.md` › Histórico das leituras de cota](context.md#histórico-das-leituras-de-cota).*

### 9.14 Endpoint de Ranking (`GET /api/ranking`)

Retorna os melhores atletas disponíveis ordenados por score decrescente. Aplica os **mesmos filtros
do `/api/time`** (status, preço e time favorito).

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| `posicao` | string | *(todos)* | Filtra por posição: `GOL`, `LAT`, `ZAG`, `MEI`, `ATA`, `TEC` |
| `limite` | int | `25` | Número de resultados (1–100) |
| `excluirDuvida` | boolean | `false` | Remove os atletas em dúvida (status 6) do pool |

**Comportamentos:**
- `limite <= 0` → tratado como `1`
- `limite > 100` → clampado para `100`
- `posicao` inválida → HTTP 400 com mensagem detalhada
- Atletas em dúvida aparecem com `emDuvida: true` no response

### 9.15 Endpoint de Favoritos (`GET /api/favoritos`)

Lista os jogos da rodada atual classificados em **favoritos** e **descartados**.

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| `oddLimite` | double | *valor da configuração* | Odd máxima para considerar um time favorito. Deve ser `> 1.0` |

**Lógica por jogo:**
- Ignora odds de confrontos fora da rodada atual do Cartola.
- Seleciona o time com menor odd (excluindo empate) como candidato a favorito.
- Se a menor odd ≤ `oddLimite` → jogo entra em **favoritos** com todos os detalhes.
- Se a menor odd > `oddLimite` → jogo entra em **descartados** com motivo legível.

**Campos retornados para cada favorito:** `timeFavorito`, `oddFavorito`, `timeAdversario`,
`oddAdversario`, `oddEmpate` (quando disponível) e `favoritoEmCasa`.

**Validação:** `oddLimite <= 1.0` retorna HTTP 400 (odd de 1.0 ou menos é matematicamente impossível
em apostas reais).

---

## 10. Fluxo de Execução

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

## 11. Estrutura do Projeto

```
cartolaoddsapi/
├── Dockerfile               # Multi-stage: build (JDK 21) + runtime (JRE 21 Alpine)
├── docker-compose.yml       # app + postgres:16, healthcheck, resource limits
├── .env.example             # Template de variáveis de ambiente
├── .dockerignore            # Exclui target/, testes, docs do contexto Docker
├── pom.xml
├── README.md                # Porta de entrada: o que é e como subir
├── docs/
│   ├── documentacao.md      # Este arquivo — referência completa
│   ├── context.md           # Decisões de arquitetura e seus porquês
│   └── observabilidade/     # Dashboard e alertas da cota (ver 17)
│       ├── grafana-cota-odds.json
│       ├── alertas-cota-odds.yml
│       ├── alertas-cota-odds.test.yml
│       └── prometheus.yml
└── src/
    ├── main/
    │   ├── java/com/cartola/odds/
    │   │   ├── CartolaOddsApplication.java
    │   │   ├── config/          (OddsProperties, CartolaProperties, JwtProperties,
    │   │   │                     LoginProperties, AdminInicialProperties, AdminInicialBootstrap,
    │   │   │                     CacheConfig, RestClientConfig, OpenApiConfig, SecurityConfig)
    │   │   ├── client/          (OddsClient, CartolaClient)
    │   │   ├── security/        (JwtAuthenticationFilter, ErroSegurancaHandler)
    │   │   ├── repository/      (ConfiguracaoRepository, EscalacaoRepository, UsuarioRepository,
    │   │   │                     OddsSnapshotRepository, OddsCotaRepository,
    │   │   │                     OddsCotaHistoricoRepository — todos `JpaRepository`)
    │   │   ├── service/         (OddsService, OddsCotaService, CartolaDataService, ScoreService,
    │   │   │                     DesempenhoService, MontadorTimeService, OtimizadorTitulares,
    │   │   │                     PipelineService, RankingService, ConfiguracaoService,
    │   │   │                     EscalacaoService, AuthService, JwtService, LoginThrottle,
    │   │   │                     UsuarioService, UsuarioDetailsService)
    │   │   ├── controller/api/  (AuthApi, UsuarioApi, TimeApi, RankingApi, FavoritosApi,
    │   │   │                     CacheApi, ConfiguracaoApi, HistoricoApi, OddsCotaApi
    │   │   │                     — interfaces com as anotações Swagger)
    │   │   ├── controller/      (AuthController, UsuarioController, TimeController,
    │   │   │                     RankingController, FavoritosController, CacheController,
    │   │   │                     ConfiguracaoController, HistoricoController, OddsCotaController,
    │   │   │                     GlobalExceptionHandler)
    │   │   ├── exception/       (RecursoNaoEncontradoException, TentativasExcedidasException,
    │   │   │                     ConflitoException, SenhaInvalidaException)
    │   │   ├── model/           (Atleta, Time, Configuracao, EscalacaoRodada, Usuario,
    │   │   │                     FormacaoConfig, ResultadoFormacao, OddsComOrigem, OddsSnapshot,
    │   │   │                     OddsCota, OddsCotaHistorico)
    │   │   │   ├── enums/        (Posicao, StatusAtleta, StatusMercado, Perfil, Estrategia)
    │   │   │   ├── request/      (ConfiguracaoRequest, LoginRequest, UsuarioRequest,
    │   │   │   │                  UsuarioUpdateRequest, AlterarSenhaRequest)
    │   │   │   └── response/     (TimeResponse, RankingResponse, FavoritosResponse,
    │   │   │                      CompararFormacoesResponse, HistoricoResponse,
    │   │   │                      EscalacaoRodadaResponse, ConfiguracaoResponse, CacheResponse,
    │   │   │                      OddsCotaResponse, OddsCotaHistoricoResponse, LoginResponse,
    │   │   │                      UsuarioResponse, PaginaResponse, ErrorResponse, OddsResponse,
    │   │   │                      AtletaResponse, ClubeResponse, PartidaResponse,
    │   │   │                      PontuadosResponse, MercadoStatusResponse)
    │   │   └── util/            (NormalizadorUtil, FormacaoParser)
    │   └── resources/
    │       ├── application.properties        # Lê variáveis de ambiente com fallback
    │       ├── application-prod.properties   # Perfil prod: springdoc desligado, log em INFO
    │       └── db/migration/                 # V1 … V11 — ver 4.3
    └── test/
        ├── java/                            # 44 classes de teste — 761 cenários (ver 14)
        └── resources/
            ├── application.properties       # H2 in-memory (MODE=PostgreSQL) para testes
            └── db/migration/h2/             # Migrations equivalentes ajustadas à sintaxe H2
```

**Camadas:**

```
com.cartola.odds/
├── config/      — configurações (cache, REST client, OpenAPI, security, properties)
├── client/      — integrações com APIs externas (OddsClient, CartolaClient)
├── security/    — filtro JWT e escrita de 401/403
├── repository/  — persistência (JPA)
├── service/     — lógica de negócio (pipeline, score, desempenho, ranking, montador, usuários)
├── controller/  — endpoints REST + tratamento global de erros
│   └── api/     — interfaces com anotações Swagger (separadas dos controllers)
├── exception/   — exceções de domínio mapeadas a status HTTP
├── model/       — entidades de domínio, enums, DTOs de request e response
└── util/        — utilitários (NormalizadorUtil, FormacaoParser)
```

---

## 12. Referência de Funções

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
Ver [9.9](#99-budget-máximo-c-e-otimização-por-orçamento).

### `RankingService.buscarRanking(posicao, limite) → RankingResponse`
Retorna os melhores atletas por score. Reutiliza o mesmo pipeline de filtros do `/api/time`.
`posicao = null` retorna todas as posições. `limite` é clampado entre 1 e 100.

### `EscalacaoService.salvarEscalacao(Time, rodadaId)` / `atualizarPontuacaoReal(rodadaId)`
Persiste a escalação da rodada de forma idempotente e, depois do fechamento, preenche a
`pontuacao_real` a partir de `/atletas/pontuados`. Ver [9.12](#912-histórico-de-escalações-por-rodada).

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

## 13. Referência de Dados

### Colunas do domínio `Atleta`

| Campo | Tipo | Origem | Descrição |
|---|---|---|---|
| `apelido` | String | `/atletas/mercado` | Nome popular |
| `posicao` | Posicao | `POS_MAP[posicao_id]` | Enum: GOL/LAT/ZAG/MEI/ATA/TEC |
| `_nome_clube_norm` | String | `normalizar(slug do clube)` | Para cruzamento com odds |
| `_sigla_clube` | String | `/clubes[abreviacao]` | Exibida no formato `Nome (SIG)` |
| `status` | StatusAtleta | `/atletas/mercado` | PROVAVEL/DUVIDA/CONTUNDIDO/... |
| `mediaPontos` | double | `media_num` | Média da temporada |
| `valorizacao` | double | `variacao_num` | Variação última rodada |
| `preco` | double | `preco_num` | Preço em cartoletas (C$) |
| `defesasDificeis` | int | `scout.DD` | Defesas difíceis acumuladas |
| `golsSofridos` | int | `scout.GS` | Gols sofridos acumulados |
| `penaltisDefendidos` | int | `scout.DP` | Pênaltis defendidos acumulados |
| `gols` | int | `scout.G` | Gols marcados acumulados |
| `assistencias` | int | `scout.A` | Assistências acumuladas |
| `score` | double | `ScoreService` | Score ponderado calculado |
| `desvioPadrao` | double | `DesempenhoService` | Desvio padrão populacional das últimas rodadas |
| `rodadasConsideradas` | int | `DesempenhoService` | Rodadas usadas no cálculo do desempenho |
| `substitutoProvavel` | Atleta | `MontadorTimeService` | Preenchido somente para dúvidas |

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

### `status_mercado` — referência

Ver [8.3](#83-aviso-de-mercado).

---

## 14. Testes

**44 classes de teste — 761 cenários**, cobrindo serviços, controllers, segurança, domínio,
utilitários e endpoints de observabilidade. Os testes usam migrations Flyway próprias em
`src/test/resources/db/migration/h2`, equivalentes às de produção e ajustadas para a sintaxe do H2.

### 14.1 Executar

```bash
# Todos os testes
mvn test

# Apenas uma classe
mvn test -Dtest=ScoreServiceTest

# Com relatório de cobertura (requer JaCoCo no pom.xml)
mvn test jacoco:report
```

### 14.2 Cobertura por classe

| Classe | Tipo | Cenários | O que cobre |
|---|---|---|---|
| `CartolaOddsApplicationTests` | Integração | 1 | Contexto Spring sobe sem erros |
| `OddsServiceTest` | Unitário (Mockito) | 29 | `buscarFavoritos` e `buscarFavoritosDetalhado`: filtro `ODD_LIMITE`, normalização, filtro por rodada atual, fallback sem confrontos, múltiplos jogos, jogo sem bookmaker, set imutável |
| `OddsClientTest` | Unitário (Mockito + MockRestServiceServer) | 35 | Guardrail de cota, sondagem, atalho de boot, fallback por snapshot, leitura dos headers, limiares de aviso, métricas e gravação do histórico de leituras |
| `OddsCotaServiceTest` | Unitário (Mockito) | 11 | Montagem de `GET /api/odds/cota`, janela do histórico, detecção de reinício de ciclo e recusa de janela inválida |
| `OddsCotaControllerTest` | Web (MockMvc) | 6 | HTTP dos dois endpoints de cota, incluindo `?dias` inválido |
| `OddsPropertiesValidacaoTest` | Unitário | 6 | Recusa no boot das propriedades inválidas do guardrail |
| `MetricasOddsPrometheusTest` | Unitário | 2 | Tradução dos nomes Micrometer para o formato Prometheus |
| `ArtefatosObservabilidadeTest` | Unitário | 2 | Nomes `odds_api_*` citados no dashboard e nas regras existem na exposição; o mínimo do guardrail acompanha o `application.properties` |
| `CartolaDataServiceTest` | Unitário (Mockito) | 25 | Filtros de status/preço/favorito, mapeamento de posição, fallback de sigla, mandantes e confrontos da rodada, status do mercado |
| `ScoreServiceTest` | Unitário (Mockito) | 36 | Pesos, bônus casa/favorito, desempenho real vs. proxy, fallback, score por posição (GOL/ATA), penalidade por desvio, exposição de `desvioPadrao`/`rodadasConsideradas`, imutabilidade |
| `DesempenhoServiceTest` | Unitário (Mockito) | 12 | Média das rodadas, desvio padrão, fallback null, atleta parcial |
| `MontadorTimeServiceTest` | Unitário | 54 | Formação, regra de defesa, limite por clube, fallback intermediário, capitão, reserva de luxo, dúvidas, reservas sem técnico, budget máximo, otimização por orçamento e override de formação |
| `PipelineServiceTest` | Unitário (Mockito) | 18 | Pipeline completo com a etapa de desempenho, propagação de orçamento, filtro `excluirDuvida` e comparação de formações |
| `RankingServiceTest` | Unitário (Mockito) | 21 | Ordenação, limite, filtro por posição, `excluirDuvida` e metadados |
| `EscalacaoServiceTest` | Unitário (Mockito) | 11 | Salvar (idempotência), atualizar pontuação real, rodada não corrente, resumo do histórico, 404 |
| `ConfiguracaoServiceTest` | Unitário (Mockito) | 7 | Atualização e reset dos parâmetros, incluindo a regra de defesa |
| `UsuarioServiceTest` | Unitário (Mockito) | 26 | Criação, e-mail duplicado/normalizado, desativação lógica e idempotente, incremento de `tokenVersion`, autodesativação/auto-rebaixamento, último administrador ativo, troca de senha, whitelist de ordenação e freio de força bruta |
| `JwtServiceTest` | Unitário | 8 | Emissão e leitura do token, claims e segredo efêmero |
| `LoginThrottleTest` | Unitário | 6 | Contagem por e-mail, janela e reset após sucesso |
| `AdminInicialBootstrapTest` | Unitário | 6 | Criação idempotente do administrador inicial e falha sem senha configurada |
| `TimeControllerTest` | Web (MockMvc) | 33 | HTTP completo, persistência (com orçamento sim, com `excluirDuvida` não), comportamento não bloqueante, orçamento, `excluirDuvida`, aviso de mercado, validação e comparação de formações |
| `RankingControllerTest` | Web (MockMvc) | 14 | HTTP completo com filtros de posição e limite |
| `FavoritosControllerTest` | Web (MockMvc) | 13 | HTTP 200/400/502, campos favorito/descartado, validação de `oddLimite` |
| `CacheControllerTest` | Web (MockMvc) | 9 | `DELETE` todos / por nome / `400` para nome inválido |
| `ConfiguracaoControllerTest` | Web (MockMvc) | 17 | `GET` config, `PATCH` (válido/inválido/soma/regra), `POST` reset |
| `HistoricoControllerTest` | Web (MockMvc) | 9 | Histórico vazio/preenchido, detalhe, 404, atualizar pontuação, verbo errado → 405 com `Allow`, path variable inválida → 400 |
| `UsuarioControllerTest` | Web (MockMvc) | 23 | HTTP 201 com `Location`, 400 de validação/corpo ilegível, 403 para `USER`, 404, 409, 422 e 429; senha ausente das respostas |
| `AuthControllerTest` | Web (MockMvc) | 6 | Login válido, credenciais inválidas e freio de tentativas |
| `PoliticaAcessoIntegrationTest` | Integração (MockMvc) | 103 | Matriz de acesso rota a rota nos três estados (sem token, `USER`, `ADMIN`); status exato das rotas públicas; contrato `ErrorResponse` em 401/403; cabeçalhos de segurança |
| `SegurancaIntegrationTest` | Integração (MockMvc) | 18 | Fluxo de autenticação ponta a ponta e rejeição de tokens inválidos |
| `GestaoUsuariosIntegrationTest` | Integração (MockMvc) | 21 | Admin cria → novo usuário autentica; 401 sem token; desativação derruba login e token; troca de senha invalida o token anterior; proteções do último administrador; ordenação e payloads inválidos; freio de força bruta na troca de senha |
| `ProxyConfiavelIntegrationTest` | Integração (HTTP real) | 2 | HSTS sai com `X-Forwarded-Proto: https` de proxy confiável, e não sai sem o header |
| `ProxyNaoConfiavelIntegrationTest` | Integração (HTTP real) | 1 | `X-Forwarded-*` de cliente fora de `internal-proxies` são ignorados |
| `CorsConfigTest` | Unitário | 6 | Origens aparadas e entradas vazias descartadas, `HEAD` entre os métodos, headers explícitos, sem curinga |
| `SwaggerProdIntegrationTest` | Integração (`@ActiveProfiles("prod")`) | 4 | Swagger UI e `/v3/api-docs` respondem 404 em produção |
| `ActuatorEndpointsTest` | Integração (HTTP real) | 14 | `health`/`info` públicos, `health` sem detalhes para anônimo e com detalhes para `ADMIN`, `metrics`/`prometheus` exigindo `ADMIN`, endpoints sensíveis não expostos |
| `CacheConfigTest` | Unitário | 7 | Caffeine registrado com os sete caches e seus TTLs |
| `NormalizadorUtilTest` | Unitário | 44 | Acentos, hífens, maiúsculas, nulo, branco, idempotência e aliases de clubes |
| `FormacaoParserTest` | Unitário | 17 | Parsing e validação de formação única e de lista (soma, mínimo/máximo, duplicatas) |
| `AtletaTest` | Unitário | 9 | `formatado()`, `isDuvida()`, `isProvavel()`, imutabilidade `@With` |
| `UsuarioTest` | Unitário | 5 | Domínio do usuário e incremento de `tokenVersion` |
| `EnumsTest` | Unitário | 23 | `Posicao` e `StatusAtleta`: `fromId()`, `fromSigla()`, `isEscalavel()`, `idsEscalaveis()` |
| `StatusMercadoTest` | Unitário | 26 | `fromCodigo()`, `isAberto()`, exibição e texto do aviso |
| `MercadoStatusResponseTest` | Unitário | 15 | `getAvisoMercado()`, `getStatus()`, `isAberto()` e `rodadaAtual` |

### 14.3 Saída esperada

```
[INFO] Results:
[INFO]
[INFO] Tests run: 761, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 15. Swagger / OpenAPI

| URL | Descrição |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interface gráfica — *Try it out* habilitado |
| `http://localhost:8080/v3/api-docs` | JSON OpenAPI 3 (importar no Postman/Insomnia) |

> Ambas respondem **`404`** com `SPRING_PROFILES_ACTIVE=prod`: o `application-prod.properties`
> desliga o springdoc. Ver [5.5](#55-perfil-de-produção).

No Swagger UI, o botão **Authorize** recebe apenas o valor do `accessToken` devolvido por
`POST /api/auth/login`. A lista de endpoints e seus contratos está em [8](#8-endpoints); falhas de
validação de request body em `PATCH /api/config` retornam HTTP 400 com a mensagem do campo inválido.

**Respostas documentadas em `GET /api/time`:**

| Código | Cenário |
|---|---|
| `200` | Time montado com sucesso |
| `400` | `orcamento` inválido (deve ser > 0), ou valor que não converte para o tipo esperado (`?orcamento=abc`, `?excluirDuvida=abc`) |
| `422` | Pool vazio — ODD_LIMITE muito restritivo ou sem API Key |
| `502` | Falha de comunicação com API externa |
| `500` | Erro interno inesperado |

---

## 16. Docker

### 16.1 Arquivos

| Arquivo | Descrição |
|---|---|
| `Dockerfile` | Build multi-stage: stage `build` (JDK 21 Alpine) + stage `runtime` (JRE 21 Alpine) |
| `docker-compose.yml` | Orquestração com variáveis de ambiente, healthcheck e resource limits |
| `.env.example` | Template de variáveis — copiar para `.env` antes de usar |
| `.dockerignore` | Exclui `target/`, `src/test/`, `docs/` e arquivos de IDE do contexto |
| `application.properties` | Lê variáveis de ambiente com fallback para valores padrão (ver [4.2](#42-variáveis-de-ambiente)) |

### 16.2 Dockerfile — Multi-stage Build

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

### 16.3 Comandos

O início rápido está no [README](../README.md#início-rápido-com-docker). Os demais comandos:

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

# Executar sem Compose (passando variáveis diretamente)
docker run -p 8080:8080 \
  -e ODDS_API_KEY=sua_chave \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/cartola_odds \
  -e SPRING_DATASOURCE_USERNAME=cartola \
  -e SPRING_DATASOURCE_PASSWORD=cartola \
  cartola-odds:1.0.0
```

### 16.4 Resource Limits (docker-compose.yml)

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

### 16.5 Healthcheck

O container verifica automaticamente se a aplicação está respondendo a cada 30 segundos:

```
GET http://localhost:8080/v3/api-docs → 200 OK = healthy
```

`start_period: 60s` — aguarda a JVM e o PostgreSQL inicializarem antes de começar as verificações.

---

## 17. Observabilidade

A aplicação expõe endpoints de monitoramento via **Spring Boot Actuator**, com métricas coletadas
pelo **Micrometer** e exportadas no formato **Prometheus**.

### 17.1 Endpoints do Actuator

O Actuator responde na **mesma porta da aplicação** (`8080`); quem protege é a
[matriz de acesso](#52-matriz-de-acesso-por-rota). Expostos via
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

### 17.2 Métricas da cota

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

### 17.3 Arquivos versionados

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
> série do mês (ver [4.4](#44-cota-da-the-odds-api-guardrail-e-sondagem)). O que estes arquivos
> acrescentam sobre os dois endpoints é a **avaliação contínua** — algo perguntando pelo saldo sem
> ninguém abrir tela — e a taxa de erro sobre `odds_api_errors_total` / `odds_api_requests_total`,
> que não têm endpoint equivalente.

### 17.4 Apontar um Prometheus para a aplicação

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

### 17.5 Importar o dashboard

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

### 17.6 Painéis

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

### 17.7 Alertas e o que fazer quando cada um dispara

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

### 17.8 Testar as regras

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

## 18. Melhorias Futuras

### Dados e Algoritmos
- [x] **Score específico por posição** (goleiros: defesas difíceis; atacantes: gols + assistências)
- [x] **Dicionário de aliases** para nomes de clubes divergentes entre as APIs
- [ ] Ponderar a odd como **variável contínua** em vez de bônus binário

### Infraestrutura
- [ ] **Retry** com backoff exponencial via Spring Retry
- [x] **Métricas** com Spring Actuator + Micrometer, com dashboard e alertas da cota (ver [17](#17-observabilidade))
- [ ] **Credencial de conta de máquina** para o scrape do Prometheus ([#44](https://github.com/FabioCarlesso/cartolaoddsapi/issues/44))
- [ ] **`TRUSTED_PROXIES`** fixado na faixa real da borda ([#39](https://github.com/FabioCarlesso/cartolaoddsapi/issues/39))
- [ ] **Cobertura de testes** com JaCoCo + relatório HTML

### Regras de Negócio
- [x] **Constraint de budget** máximo (C$) — resolvida com branch-and-bound próprio (ver [9.9](#99-budget-máximo-c-e-otimização-por-orçamento))
- [x] **Formações alternativas** configuráveis, com comparação via `GET /api/time/comparar`
- [ ] **Simulação** de diferentes `ODD_LIMITE` para comparar times resultantes

### Qualidade
- [ ] **Testes de integração** com WireMock simulando as APIs externas

### Acesso
- [ ] **Convite por e-mail e recuperação de senha** — fora do escopo da [#37](https://github.com/FabioCarlesso/cartolaoddsapi/issues/37)

---

## 19. Referências

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

## Formato Word

O repositório mantinha um `docs/documentacao.docx` gerado a partir deste arquivo. Ele foi removido:
era um binário que não aparece no diff de um PR, tinha duas alterações contra dezenas do `.md` e,
na prática, descrevia uma versão do projeto anterior à autenticação. Um documento que ninguém
consegue revisar e que discorda da fonte é pior do que documento nenhum.

Quem precisar de uma versão Word gera na hora, a partir da fonte atual:

```bash
pandoc docs/documentacao.md -o documentacao.docx
```

---

*Projeto de automação pessoal para Cartola FC com dados públicos.*
