# Regras de Negócio

> **Papel deste arquivo:** como o time é montado: favoritos, filtros, score, formação, orçamento, dúvidas e histórico.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---


## Identificação de Times Favoritos

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

## Filtros de Atletas

| Filtro | Regra | Fallback |
|---|---|---|
| `status_id` | `6` (Dúvida) ou `7` (Provável) — apenas `7` com `excluirDuvida=true` | Descartado |
| `preco_num` | `> 0` cartoletas | Descartado |
| Time favorito | Clube em `favoritos_norm` | Descartado |
| Sem odds | `favoritos_norm` vazio | Filtro desativado — usa todos os elegíveis |

## Fórmula do Score

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

## Formação 4-3-3

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

## Seleção de Reservas

- Somente `status == PROVAVEL` (7) — dúvidas não são reservas.
- Preferencialmente mais baratos que o titular mais caro da posição.
- Fallback: qualquer provável da posição se nenhum mais barato existir.
- Sempre da **mesma posição individual** do titular (LAT reserva LAT, ZAG reserva ZAG).
- `TEC` não tem reserva.

## Defesa sem Clube Repetido

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

## Capitão e Reserva de Luxo

- **Capitão:** titular com maior score global, prioridade `ATA > MEI > ZAG > LAT > GOL > TEC` em
  caso de empate. O capitão tem pontuação **dobrada** no Cartola FC.
- **Reserva de Luxo:** atleta de maior score entre todas as **reservas**; como `TEC` não tem
  reserva, técnicos não concorrem a reserva de luxo.

## Limite Máximo por Clube (inclui TEC)

Na escalação titular, o montador respeita **no máximo 4 atletas do mesmo clube**, considerando todas
as posições, inclusive `TEC`. O valor é configurável em runtime via `PATCH /api/config` no campo
`limiteAtletasPorClube` (padrão `4`).

Fallback em três níveis:

1. **Primário** — respeita a regra de defesa (sem clube repetido em GOL/LAT/ZAG), o limite por clube e o budget.
2. **Intermediário** — relaxa a regra de defesa, mas mantém o limite por clube e o budget.
3. **Último recurso** — relaxa também o limite por clube, mas mantém o budget.

## Budget Máximo (C$) e Otimização por Orçamento

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

## Tratamento de Dúvidas

- Titulares com `status_id == 6` são escalados, mas marcados com `⚠️ DÚVIDA`.
- O sistema busca o melhor substituto `PROVAVEL` na **mesma posição individual**.
- O substituto nunca é outro atleta já escalado como titular.
- Alertas retornados em `alertasDuvida` no `TimeResponse`.

**Opt-out via `excluirDuvida`:** o parâmetro opcional `excluirDuvida=true` em `GET /api/time` e
`GET /api/ranking` remove os atletas com `status_id == 6` do pool no `PipelineService`, **após o
cache** e **antes** do `ScoreService` — ver [8.5](api.md#parâmetros-de-get-apitime).

## Normalização de Nomes de Clubes

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

## Histórico de Escalações por Rodada

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

## Histórico das leituras de cota

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
[8.4](api.md#exemplos-de-resposta).

*Por que append-only, por que 92 dias e como o reinício de ciclo é detectado:
[`context.md` › Histórico das leituras de cota](context.md#histórico-das-leituras-de-cota).*

## Endpoint de Ranking (`GET /api/ranking`)

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

## Endpoint de Favoritos (`GET /api/favoritos`)

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
