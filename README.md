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
| `DELETE` | `/api/cache` | Invalida todos os caches imediatamente |
| `DELETE` | `/api/cache/{nome}` | Invalida um cache específico pelo nome |
| `GET` | `/api/config` | Retorna a configuração atual (odd limite, pesos, formação e regras) |
| `PATCH` | `/api/config` | Atualiza um ou mais parâmetros em runtime (sem restart) |
| `POST` | `/api/config/reset` | Restaura todos os parâmetros para os valores padrão |
| `GET` | `/api/historico` | Lista todas as rodadas com escalação registrada e resumo de score sugerido vs. real |
| `GET` | `/api/historico/{rodadaId}` | Detalhe da escalação de uma rodada específica |
| `POST` | `/api/historico/{rodadaId}/atualizar-pontuacao` | Busca a pontuação real da rodada via `/atletas/pontuados` e persiste |
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
    { "rank": 1, "apelido": "Hulk", "formatado": "Hulk (ATM)", "score": 8.54, "preco": 22.0, "desvioPadrao": 1.25, "rodadasConsideradas": 5, "emDuvida": false },
    { "rank": 2, "apelido": "Cano",  "formatado": "Cano (FLU)",  "score": 7.90, "preco": 18.3, "desvioPadrao": 2.10, "rodadasConsideradas": 5, "emDuvida": false },
    { "rank": 3, "apelido": "Pedro", "formatado": "Pedro (FLA) ⚠️ DÚVIDA", "score": 7.70, "preco": 17.0, "desvioPadrao": 0.0, "rodadasConsideradas": 0, "emDuvida": true }
  ]
}
```

> Os campos `desvioPadrao` e `rodadasConsideradas` expõem o desvio padrão populacional das pontuações e a quantidade de rodadas usadas no cálculo do desempenho recente. Disponíveis também no `GET /api/time`. Valem `0.0` e `0` quando o atleta não tem histórico recente (menos de 2 rodadas ou ausente do histórico), caso em que nenhuma penalidade por volatilidade é aplicada.

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

### Histórico de escalações

`GET /api/time` persiste automaticamente a escalação sugerida da rodada (titulares e reservas), **inclusive com `orcamento` informado** — o time respeita um teto real de cartoletas e continua sendo a escalação que será usada. A operação é **idempotente** (uma rodada já registrada não é sobrescrita) e **não bloqueante** — se a persistência falhar, o time é retornado normalmente e o erro é logado.

> **Exceção — `excluirDuvida=true` não persiste.** É uma consulta *comparativa*: o uso natural do parâmetro é confrontar `GET /api/time` com `GET /api/time?excluirDuvida=true` na mesma rodada. Como a persistência é idempotente por rodada, gravá-la faria o histórico registrar *a primeira variante consultada* em vez da sugestão da rodada. Mesmo critério já adotado pelo `GET /api/time/comparar`.

Após o fechamento da rodada, `POST /api/historico/{rodadaId}/atualizar-pontuacao` consulta `/atletas/pontuados` e preenche a `pontuacaoReal` de cada atleta. Enquanto não for atualizada, `pontuacaoReal` permanece `null`. No total da rodada, a pontuação do capitão é contada em dobro.

> **Janela de uso:** o `/atletas/pontuados` do Cartola expõe apenas a rodada corrente. Por isso `atualizar-pontuacao` só aceita a rodada atual — chame logo após o fechamento da rodada. Rodadas diferentes da corrente retornam `400 Bad Request`.

#### Exemplo — `GET /api/historico`

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

#### Exemplo — `GET /api/historico/14`

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

Uma rodada sem escalação registrada retorna `404 Not Found` em `GET /api/historico/{rodadaId}` e `POST /api/historico/{rodadaId}/atualizar-pontuacao`.

### Orçamento máximo

O parâmetro **opcional** `orcamento` em `GET /api/time` limita o total de cartoletas gastas na montagem:

- **Sem `orcamento`** → comportamento padrão: estratégia `SCORE_MAXIMO`, candidatos ordenados por score, sem restrição de custo (a não ser o `budgetMaximo` da configuração, se definido).
- **Com `orcamento`** → estratégia `SCORE_MAXIMO` **sujeita ao teto**: o montador resolve um *multiple-choice knapsack* por posição via **branch-and-bound**, escolhendo a combinação de **maior soma de score** que cabe no orçamento (e não os mais baratos). Em empate de score, vence a de **menor custo** (custo-benefício `score / preço` apenas como desempate). As regras de formação, limite por clube e defesa sem clube repetido continuam respeitadas. Com orçamento folgado, o resultado coincide com o time de maior score absoluto.

A resposta passa a expor `orcamentoInformado`, `custoTotal`, `saldoRestante`, `estrategia`, `formacaoCompleta` e — quando o orçamento não basta para completar os 12 titulares — `avisoOrcamento` (nesse caso o time é o melhor *best-effort* dentro do teto). Valores em cartoletas são arredondados para 2 casas decimais.

#### Exemplo — `GET /api/time?orcamento=120.0`

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

Quando o orçamento é baixo demais para os 12 titulares, a formação é retornada incompleta e `avisoOrcamento` é preenchido (`saldoRestante` nunca fica negativo):

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

#### Exemplo — `GET /api/time` (sem orçamento)

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

### Excluir jogadores em dúvida

O parâmetro **opcional** `excluirDuvida` (padrão `false`) em `GET /api/time` restringe o pool de montagem aos atletas **prováveis** (status 7):

- **`excluirDuvida=false`** (padrão) → comportamento atual: prováveis e dúvidas concorrem às vagas e cada titular em dúvida recebe um substituto provável sugerido em `alertasDuvida`/`substitutoProvavel`.
- **`excluirDuvida=true`** → jogadores em dúvida (status 6) são removidos **antes do cálculo de score**, de modo que nenhum deles apareça entre titulares ou reservas. Como não há dúvidas escaladas, `alertasDuvida` volta vazio.

O filtro é aplicado **após o cache** — as respostas cacheadas das APIs externas são compartilhadas com o fluxo padrão e não são invalidadas.

É combinável com `orcamento` (ex.: `GET /api/time?orcamento=120&excluirDuvida=true`). Se não sobrarem prováveis suficientes para alguma posição, a resposta é retornada normalmente com `formacaoCompleta: false` — o mesmo comportamento já adotado hoje para formações incompletas.

```bash
# Melhor time escalável sem nenhum jogador em dúvida
curl "http://localhost:8080/api/time?excluirDuvida=true"

# Combinando com orçamento
curl "http://localhost:8080/api/time?orcamento=120&excluirDuvida=true"
```

> O mesmo parâmetro já existe em `GET /api/ranking`, com a mesma semântica.
>
> Por ser uma consulta comparativa, `excluirDuvida=true` **não registra** a escalação no histórico — ver [Histórico de escalações](#histórico-de-escalações). O `orcamento`, sozinho, continua registrando normalmente.

### Comparação de formações

`GET /api/time/comparar` monta o melhor time para **cada formação informada** usando o mesmo pool de atletas da rodada e retorna um comparativo ordenado por `scoreTotal`. É uma **consulta pontual**: a formação configurada no banco **não é alterada**.

- Parâmetro **obrigatório** `formacoes`: lista separada por vírgula no formato `def-mei-ata` (ex: `4-3-3,3-4-3,4-4-2`), onde o primeiro número é o total de **defensores** (laterais + zagueiros), como na notação do Cartola FC.
- A soma das posições de linha de cada formação (`def + mei + ata`) deve ser **10**. As posições fixas `GOL=1` e `TEC=1` vêm da configuração; a defesa é derivada com **`LAT` fixo em 2** e **`ZAG = def − LAT`** (ex: `4-3-3` → `ZAG=2`, `LAT=2`), alinhada à composição do `GET /api/time`. Cada time tem exatamente **11 em campo + 1 técnico** (12 titulares).
- Mínimo de **2** e máximo de **5** formações **distintas**; duplicatas são ignoradas silenciosamente.
- Parâmetro **opcional** `orcamento`: aplica o limite de cartoletas a cada formação (custo-benefício), igual ao `GET /api/time`.
- `scoreTotal` soma **apenas os titulares**, para uma comparação justa entre formações com número diferente de atletas por posição.
- As mesmas regras de montagem valem para cada formação: limite por clube, defesa sem clube repetido e dúvidas com substituto.

| Situação | Resposta |
|---|---|
| Menos de 2 formações distintas | `400 Bad Request` |
| Mais de 5 formações distintas | `400 Bad Request` |
| Formação com soma de linhas `!= 10` (ex: `4-3-2`) | `400 Bad Request` com mensagem explicativa |
| Formação com posição zerada ou formato inválido (ex: `10-0-0`, `4-3-3-`) | `400 Bad Request` |
| Parâmetro `formacoes` ausente | `400 Bad Request` |
| `orcamento <= 0` | `400 Bad Request` |
| Nenhum atleta disponível na rodada | `422 Unprocessable Entity` |

#### Exemplo — `GET /api/time/comparar?formacoes=4-3-3,3-4-3,4-4-2`

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
| `400` | Parâmetro inválido (ex: posição inexistente, `oddLimite <= 1.0`, `orcamento <= 0`) |
| `400` | Valor que não converte para o tipo esperado (ex: `?orcamento=abc`, `?excluirDuvida=abc`) |
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
| Status | Somente `Provável` (7) ou `Dúvida` (6) — apenas `Provável` (7) com `excluirDuvida=true` |
| Preço | Deve ser `> 0` cartoletas |
| Time | Clube deve estar no conjunto de times favoritos |

*Sem odds disponíveis: filtro por time desativado — usa todos os elegíveis.*

### Fórmula do score

O score é calculado com fórmulas distintas por posição, priorizando os indicadores mais relevantes para cada função. Os bônus `fatorCasa` e `timeFavorito` valem `10.0` quando verdadeiros, `0.0` caso contrário, e são configuráveis via `PATCH /api/config`.

**Posições sem regra específica (LAT, ZAG, MEI, TEC) — fallback configurável:**

```
score = (mediaPontos × 0.40) + (valorização × 0.20) + (desempenho × 0.20)
      + (fatorCasa × 0.10)  + (timeFavorito × 0.10)
      − (desvioPadrão × pesoDesvio)
```

**Goleiro (GOL) — scouts defensivos com maior peso:**

```
score = (desempenho × 0.35) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (defesasDifíceis × 0.05) + (pênaltisDefendidos × 0.05) − (golsSofridos × 0.02)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      − (desvioPadrão × pesoDesvio)
```

**Atacante (ATA) — participação ofensiva com maior peso:**

```
score = (desempenho × 0.25) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (gols × 0.08) + (assistências × 0.05)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      − (desvioPadrão × pesoDesvio)
```

Os scouts (DD, GS, DP, G, A) são totais acumulados da temporada extraídos de `/atletas/mercado`. Quando não disponíveis na resposta da API, são tratados como 0 sem impacto no cálculo.
Os pesos base de GOL e ATA são constantes no `ScoreService`; para essas posições, apenas `pesoFatorCasa`, `pesoTimeFavorito` e `pesoDesvio` continuam configuráveis via `PATCH /api/config`.

**Desempenho:** usa a média real das últimas 5 rodadas via `/atletas/pontuados`.
Fallback automático para `mediaPontos` da temporada quando o histórico não estiver disponível.

**Penalização por volatilidade:** o `DesempenhoService` calcula o desvio padrão populacional das últimas rodadas. A penalidade `desvioPadrão × pesoDesvio` é subtraída do score em todas as posições, priorizando atletas consistentes em situações de empate técnico. `pesoDesvio` é configurável via `PATCH /api/config` (default `0.05`); o desvio padrão é `0.0` quando há menos de 2 rodadas disponíveis, anulando a penalidade. Atletas sem histórico recente caem para o proxy `mediaPontos` e não são penalizados.

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
    │   │   ├── repository/      (ConfiguracaoRepository, EscalacaoRepository)
    │   │   ├── service/         (OddsService, CartolaDataService, ScoreService,
    │   │   │                     DesempenhoService, MontadorTimeService, PipelineService,
    │   │   │                     RankingService, ConfiguracaoService, EscalacaoService)
    │   │   ├── controller/api/  (TimeApi, RankingApi, FavoritosApi, CacheApi,
    │   │   │                     ConfiguracaoApi, HistoricoApi — Swagger docs)
    │   │   ├── controller/      (TimeController, RankingController, FavoritosController,
    │   │   │                     CacheController, ConfiguracaoController, HistoricoController,
    │   │   │                     GlobalExceptionHandler)
    │   │   ├── exception/       (RecursoNaoEncontradoException)
    │   │   ├── model/           (Atleta, Time, Configuracao, EscalacaoRodada, enums/,
    │   │   │                     request/ConfiguracaoRequest, response/)
    │   │   └── util/            (NormalizadorUtil)
    │   └── resources/
    │       ├── application.properties        # Lê variáveis de ambiente com fallback
    │       └── db/migration/
    │           ├── V1__create_configuracao.sql  # Cria tabela e insere valores padrão
    │           ├── V2__alter_configuracao_numeric_to_double.sql  # Converte colunas para DOUBLE PRECISION
    │           ├── V3__add_evitar_mesmo_clube_defesa.sql         # Regra configurável de defesa
    │           ├── V4__add_limite_atletas_por_clube.sql          # Limite configurável por clube
    │           ├── V5__add_budget_maximo.sql                     # Budget máximo em C$
    │           ├── V6__add_peso_desvio.sql                       # Peso da penalidade por desvio padrão
    │           └── V7__create_escalacao_rodada.sql               # Histórico de escalações por rodada
    └── test/
        ├── java/                            # 23 classes de teste — 424 cenários
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
| `ScoreServiceTest` | 31 — pesos, bônus, desempenho real vs proxy, fallback, score por posição (GOL/ATA), penalidade por desvio, exposição de desvioPadrao/rodadasConsideradas |
| `MontadorTimeServiceTest` | 54 — formação, regra de defesa, limite por clube, fallback intermediário, capitão, reserva de luxo, dúvidas, reservas sem técnico, otimização por orçamento (score máximo, redução por clube em posição multi-vaga, empate por menor custo, best-effort, incompletude por clube sem aviso de orçamento), formação incompleta, override de formação |
| `DesempenhoServiceTest` | 8 — média rodadas, fallback null, atleta parcial |
| `PipelineServiceTest` | 18 — inclui etapa DesempenhoService, propagação de orçamento, filtro `excluirDuvida` (pool filtrado, dúvida com score maior, todos em dúvida, combinação com orçamento) e comparação de formações |
| `CacheConfigTest` | 2 — Caffeine registrado com 7 caches |
| `CacheControllerTest` | 9 — DELETE todos / DELETE por nome / 400 nome inválido |
| `ConfiguracaoControllerTest` | 10 — GET config, PATCH (válido/inválido/regra), POST reset |
| `ConfiguracaoServiceTest` | 2 — atualização/reset da regra de defesa |
| `EscalacaoServiceTest` | 10 — salvar (idempotência), atualizar pontuação real, rodada não corrente, resumo do histórico, 404 |
| `HistoricoControllerTest` | 8 — GET histórico vazio/preenchido, detalhe, 404, atualizar pontuação, path variable de tipo inválido → 400 |
| `RankingServiceTest` | 15 — ordenação, limite, filtro posição |
| `RankingControllerTest` | 12 — HTTP completo |
| `TimeControllerTest` | 33 — HTTP completo, persistência (com orçamento sim, com `excluirDuvida` não), comportamento não bloqueante, orçamento, `excluirDuvida`, aviso, validação (tipo inválido → 400, truncamento de valor longo) e comparação de formações |
| `FormacaoParserTest` | 16 — parsing e validação de formação única e lista (soma, mínimo/máximo, duplicatas) |
| `AtletaTest` | 7 — domínio e imutabilidade |
| `EnumsTest` | 8 — Posicao e StatusAtleta |
| `NormalizadorUtilTest` | 42 — normalização e aliases de clubes |
| `ActuatorEndpointsTest` | 10 — health, metrics, prometheus e bloqueio de endpoints sensíveis |
| `CartolaOddsApplicationTests` | 1 — contexto Spring |

---

*Para documentação técnica detalhada, ver [docs/documentacao.md](docs/documentacao.md).*

---

## Licença

Distribuído sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.
