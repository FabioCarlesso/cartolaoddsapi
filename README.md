# ⚽ Cartola FC — Odds API

API REST em **Java 21 + Spring Boot 3.4.5** que monta automaticamente um time competitivo para o
Cartola FC cruzando odds do Brasileirão com métricas dos atletas da plataforma.

> **Papel deste arquivo:** porta de entrada — o que o projeto é, como subir, como autenticar e para
> onde ir depois. Ele não descreve contratos nem decisões: cada assunto tem um único lugar.

---

## Onde está cada coisa

| Documento | Papel |
|---|---|
| **`README.md`** (este) | O que é, como subir, como autenticar, índice do resto |
| [**`docs/README.md`**](docs/README.md) | Índice da documentação e **onde documentar cada mudança** |
| [**`docs/`**](docs/) | Um arquivo por assunto: arquitetura, api, regras de negócio, segurança, configuração, banco de dados, operação, deploy e desenvolvimento |
| [**`docs/context.md`**](docs/context.md) | Decisões de arquitetura e o *porquê* de cada uma |

> Vai abrir um PR que mexe em documentação? A tabela *"onde documentar cada mudança"* de
> [`docs/README.md`](docs/README.md#onde-documentar-cada-mudança) diz qual arquivo é o dono do
> assunto — é o que impede a sobreposição de voltar.

---

## O que a API faz

Cruza as odds do Brasileirão ([The Odds API](https://the-odds-api.com)) com os dados dos atletas
do Cartola FC e escala o time da rodada. Em detalhe:

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

**Stack:** Java 21 · Spring Boot 3.4.5 · PostgreSQL 16 · Flyway · Caffeine · Docker —
[versões e dependências](docs/arquitetura.md#stack-e-dependências).

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

# 2. Edite o .env e preencha as TRÊS variáveis obrigatórias
#    ODDS_API_KEY=sua_chave_aqui
#    APP_ADMIN_INICIAL_SENHA=uma_senha_com_8_ou_mais_caracteres
#    JWT_SECRET=$(openssl rand -base64 48)

# 3. Suba o container
docker compose up -d

# 4. Autentique-se — a API é fechada, todo endpoint exige token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@cartolaodds.local","senha":"a_senha_do_passo_2"}' | jq -r .accessToken)

# 5. Acesse
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/time
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/favoritos
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/ranking
#    Swagger UI: http://localhost:8080/swagger-ui.html (botão Authorize)
```

> **Sem `APP_ADMIN_INICIAL_SENHA` a aplicação não sobe** quando o banco não tem nenhum
> administrador ativo — de propósito. Uma API no ar que ninguém consegue autenticar é pior
> do que uma que falha dizendo qual variável falta.

Os demais comandos (logs, rebuild, build manual, `docker run` sem Compose) estão em
[`deploy.md` › Comandos](docs/deploy.md#comandos).

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

> **Sem API Key configurada:** a aplicação sobe normalmente, o filtro por time favorito é desativado
> e todos os atletas elegíveis por status/preço são considerados.

---

## As três variáveis obrigatórias

| Variável | Para quê |
|---|---|
| `ODDS_API_KEY` | Chave da The Odds API — sem ela o filtro por time favorito é desativado |
| `APP_ADMIN_INICIAL_SENHA` | Senha do administrador criado no primeiro boot (mínimo 8 caracteres). Exigida enquanto não houver nenhum administrador ativo no banco |
| `JWT_SECRET` | Segredo HMAC de assinatura dos tokens (mínimo 32 caracteres). Obrigatório em produção; fora dela, ausente, vira uma chave efêmera por boot |

Todas as outras — banco, porta, CORS, freio de login, TTLs e guardrail de cota — têm padrão e estão
na [tabela completa de variáveis de ambiente](docs/configuracao.md#variáveis-de-ambiente).

> **Parâmetros de negócio** (odd limite, pesos do score, formação e regras) **não** são variáveis de
> ambiente: ficam no banco, com valores padrão criados pelo Flyway no primeiro boot, e são ajustados
> em runtime por `PATCH /api/config` — ver
> [`banco-de-dados.md` › Parâmetros de Negócio via Banco de Dados](docs/banco-de-dados.md#parâmetros-de-negócio-via-banco-de-dados).

---

## Autenticação

Fora `POST /api/auth/login`, do healthcheck (`/actuator/health`, `/actuator/info`) e da
documentação OpenAPI — esta última só fora de produção —, toda requisição precisa do header
`Authorization: Bearer <accessToken>`. O motivo é direto: cada consulta que não vem do cache gasta
cota da The Odds API, que é paga.

Autenticar diz *quem* está chamando; a
[matriz de acesso por rota](docs/seguranca.md#matriz-de-acesso-por-rota) diz *o que cada um
pode fazer*. O critério que separa `USER` de `ADMIN` é um só: **escreve na instância inteira ou
gasta cota externa**.

No primeiro boot, se não existir nenhum administrador ativo, a aplicação cria um a partir de
`APP_ADMIN_INICIAL_EMAIL` (padrão `admin@cartolaodds.local`) e `APP_ADMIN_INICIAL_SENHA`. A partir
daí, contas novas nascem de `POST /api/usuarios` — não há auto-cadastro público.

Detalhes de claims, expiração, freio de força bruta e revogação de token:
[`seguranca.md` › Autenticação e Política de Acesso](docs/seguranca.md#autenticação-e-política-de-acesso).

---

## Testes

```bash
# Executar todos os testes
mvn test

# Classe específica
mvn test -Dtest=OddsServiceTest
```

**44 classes de teste — 761 cenários.** A cobertura por classe está em
[`desenvolvimento.md` › Testes](docs/desenvolvimento.md#testes).

---

## Para onde ir depois

| Quero… | Vá para |
|---|---|
| A lista de endpoints e os parâmetros de cada um | [`docs/api.md`](docs/api.md) |
| As regras de montagem do time (score, formação, orçamento) | [`docs/regras-de-negocio.md`](docs/regras-de-negocio.md) |
| A matriz de acesso por rota e o hardening de `prod` | [`docs/seguranca.md`](docs/seguranca.md) |
| Criar, listar ou desativar usuários | [`docs/seguranca.md`](docs/seguranca.md#gestão-de-usuários) |
| Todas as variáveis de ambiente | [`docs/configuracao.md`](docs/configuracao.md) |
| Ajustar pesos do score, formação ou `oddLimite` | [`docs/banco-de-dados.md`](docs/banco-de-dados.md) |
| Entender o guardrail de cota da The Odds API | [`docs/operacao.md`](docs/operacao.md#cota-da-the-odds-api-guardrail-e-sondagem) |
| Monitorar a aplicação com Prometheus/Grafana | [`docs/operacao.md`](docs/operacao.md#observabilidade) |
| A estrutura de pacotes e camadas | [`docs/arquitetura.md`](docs/arquitetura.md) |
| Rodar ou entender os testes | [`docs/desenvolvimento.md`](docs/desenvolvimento.md) |
| Saber **por que** algo foi feito assim | [`docs/context.md`](docs/context.md) |

---

## Licença

Distribuído sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.
