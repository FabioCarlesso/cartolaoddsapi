# Banco de Dados

> **Papel deste arquivo:** migrations Flyway, a tabela de parâmetros de negócio e as demais tabelas do domínio.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Parâmetros de Negócio via Banco de Dados

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
| `V7__create_escalacao_rodada.sql` | Tabela `escalacao_rodada` — histórico de escalações por rodada (ver [`regras-de-negocio.md` › Histórico de Escalações por Rodada](regras-de-negocio.md#histórico-de-escalações-por-rodada)) |
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

**Parâmetros e seus padrões** — é o corpo de `GET /api/config` e o conjunto de campos aceitos pelo
`PATCH`, todos opcionais no patch:

| Campo | Padrão | O que controla |
|---|---|---|
| `oddLimite` | `3.0` | Odd máxima para um time entrar como favorito (ver [`regras-de-negocio.md` › Identificação de Times Favoritos](regras-de-negocio.md#identificação-de-times-favoritos)) |
| `pesoMediaPontos` | `0.40` | Peso da média da temporada no score de fallback |
| `pesoValorizacao` | `0.20` | Peso da valorização da última rodada |
| `pesoDesempenho` | `0.20` | Peso da média das últimas 5 rodadas |
| `pesoFatorCasa` | `0.10` | Bônus de mandante — vale em **todas** as posições |
| `pesoTimeFavorito` | `0.10` | Bônus de time favorito — vale em **todas** as posições |
| `pesoDesvio` | `0.05` | Peso da penalidade por volatilidade — vale em **todas** as posições |
| `formacaoGol` · `formacaoLat` · `formacaoZag` · `formacaoMei` · `formacaoAta` · `formacaoTec` | `1` · `2` · `2` · `3` · `3` · `1` | Vagas por posição (padrão 4-3-3) |
| `evitarMesmoClubeDefesa` | `true` | Não repetir clubes entre GOL, LAT e ZAG (ver [`regras-de-negocio.md` › Defesa sem Clube Repetido](regras-de-negocio.md#defesa-sem-clube-repetido)) |
| `limiteAtletasPorClube` | `4` | Teto de titulares do mesmo clube, incluindo TEC (ver [`regras-de-negocio.md` › Limite Máximo por Clube (inclui TEC)](regras-de-negocio.md#limite-máximo-por-clube-inclui-tec)) |
| `budgetMaximo` | `0.0` | Teto de cartoletas; `0` desliga a restrição (ver [`regras-de-negocio.md` › Budget Máximo (C$) e Otimização por Orçamento](regras-de-negocio.md#budget-máximo-c-e-otimização-por-orçamento)) |

A resposta inclui ainda `updatedAt`, somente leitura.

**Validações do `PATCH /api/config`:**
- `oddLimite` deve ser `> 1.0`
- Pesos devem ser `>= 0.0` e `<= 1.0`
- Quando todos os pesos são enviados, a soma deve ser `1.0` (tolerância `±0.01`)
- Formações devem ser `>= 1`
- `evitarMesmoClubeDefesa` ativa/desativa a regra de não repetir clubes entre GOL, LAT e ZAG
- `limiteAtletasPorClube` deve ser `>= 1` e controla o teto de atletas titulares do mesmo clube (inclui TEC)

**Cache:** a configuração é cacheada no Caffeine (`configuracao` cache). `PATCH` e `POST /reset` invalidam o cache automaticamente via `@CacheEvict`.

---

## Ver também

- [`regras-de-negocio.md`](regras-de-negocio.md) — o que cada parâmetro muda na montagem do time
- [`api.md`](api.md) — os endpoints de `/api/config`
- [`arquitetura.md`](arquitetura.md) — onde as entidades e os repositórios vivem
- [`context.md`](context.md) — por que os parâmetros de negócio ficam no banco
