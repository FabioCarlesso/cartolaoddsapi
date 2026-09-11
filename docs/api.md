# API REST

> **Papel deste arquivo:** mapa de rotas, códigos de resposta, parâmetros e os contratos que o OpenAPI não expressa.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Endpoints

### Tabela completa

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

### Códigos de resposta

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

### Aviso de mercado

Quando o mercado não está aberto, todos os endpoints retornam o campo `avisoMercado` preenchido:

| Código | Enum | Label | Exibe aviso | Aviso retornado |
|---|---|---|---|---|
| 1 | `ABERTO` | Aberto | Não | `null` (campo omitido no JSON) |
| 2 | `FECHADO` | Fechado | **Sim** | `"Mercado fechado. Rodada em andamento."` |
| 3 | `MANUTENCAO` | Manutencao | **Sim** | `"Mercado em manutencao ou pre-temporada."` |
| 4 | `PARCIAL` | Parcial | **Sim** | `"Mercado parcialmente aberto. Alguns jogos ja ocorreram."` |
| 6 | `FINALIZANDO` | Finalizando | **Sim** | `"Processamento pos-rodada em andamento."` |
| outros | `DESCONHECIDO` | Desconhecido | **Sim** | `"Status desconhecido. Dados podem estar desatualizados."` |

### Exemplos de resposta

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

### Parâmetros de `GET /api/time`

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
são arredondados para 2 casas decimais. Sem `orcamento`, `orcamentoInformado` e `saldoRestante`
vêm `null` e `custoTotal` traz o custo real da escalação. Detalhes do algoritmo em
[9.9](regras-de-negocio.md#budget-máximo-c-e-otimização-por-orçamento).

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

> O mesmo parâmetro já existe em `GET /api/ranking`, com a mesma semântica.
>
> Por ser uma consulta comparativa, `excluirDuvida=true` **não registra** a escalação no histórico —
> ver [9.12](regras-de-negocio.md#histórico-de-escalações-por-rodada). O `orcamento`, sozinho, continua registrando
> normalmente.

### `GET /api/time/comparar`

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

Na resposta, `resultados` vem ordenado por `scoreTotal` decrescente e cada item traz `posicao` — o
ranking daquela formação entre as comparadas. `melhorFormacao` aponta para o primeiro da lista.
`formacaoCompleta` e, quando o orçamento não basta, `avisoOrcamento` são **por resultado**, não
globais. Cada `time` traz a estrutura completa do `GET /api/time` (titulares, reservas, capitão).

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

---

## Swagger / OpenAPI

| URL | Descrição |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interface gráfica — *Try it out* habilitado |
| `http://localhost:8080/v3/api-docs` | JSON OpenAPI 3 (importar no Postman/Insomnia) |

> Ambas respondem **`404`** com `SPRING_PROFILES_ACTIVE=prod`: o `application-prod.properties`
> desliga o springdoc. Ver [5.5](seguranca.md#perfil-de-produção).

No Swagger UI, o botão **Authorize** recebe apenas o valor do `accessToken` devolvido por
`POST /api/auth/login`. A lista de endpoints e seus contratos está em [8](api.md#endpoints); falhas de
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

---

## Referência de Dados

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

Ver [8.3](api.md#aviso-de-mercado).

---
