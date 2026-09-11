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
| [**`docs/documentacao.md`**](docs/documentacao.md) | Referência completa: endpoints, contratos de resposta, configuração, regras de negócio, estrutura e testes |
| [**`docs/context.md`**](docs/context.md) | Decisões de arquitetura e o *porquê* de cada uma |
| [**`docs/observabilidade/`**](docs/observabilidade/) | Dashboard do Grafana e regras de alerta da cota, prontos para importar |

---

## O que a API faz

- Cruza as odds do Brasileirão ([The Odds API](https://the-odds-api.com)) com os dados dos atletas
  do Cartola FC e escala o time da rodada — formação padrão **4-3-3**, configurável.
- Monta o time de **maior score** dentro de um teto de cartoletas (`GET /api/time?orcamento=120`),
  compara formações lado a lado e permite excluir jogadores em dúvida.
- Persiste a escalação de cada rodada e depois compara **score sugerido vs. pontuação real**
  (`/api/historico`).
- Ajusta pesos do score, formação e regras **em runtime**, sem restart (`PATCH /api/config`).
- É **fechada por JWT**: todo endpoint exige token, e um administrador gerencia as contas pela
  própria API (`/api/usuarios`).
- Protege a cota paga da The Odds API com um **guardrail**: abaixo do saldo mínimo, para de chamar o
  provedor e serve o último snapshot persistido — com o saldo e o histórico de consumo expostos em
  `/api/odds/cota`.
- Expõe saúde e métricas via Actuator + Micrometer, com dashboard e alertas prontos.

A lista completa de funcionalidades está em
[`documentacao.md` § 1](docs/documentacao.md#1-funcionalidades).

**Stack:** Java 21 · Spring Boot 3.4.5 · PostgreSQL 16 · Flyway · Caffeine · Docker —
[versões e dependências](docs/documentacao.md#2-stack-e-dependências).

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
[`documentacao.md` § 16.3](docs/documentacao.md#163-comandos).

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
na [tabela completa de variáveis de ambiente](docs/documentacao.md#42-variáveis-de-ambiente).

> **Parâmetros de negócio** (odd limite, pesos do score, formação e regras) **não** são variáveis de
> ambiente: ficam no banco, com valores padrão criados pelo Flyway no primeiro boot, e são ajustados
> em runtime por `PATCH /api/config` — ver
> [`documentacao.md` § 4.3](docs/documentacao.md#43-parâmetros-de-negócio-via-banco-de-dados).

---

## Autenticação

Fora `POST /api/auth/login`, do healthcheck (`/actuator/health`, `/actuator/info`) e da
documentação OpenAPI — esta última só fora de produção —, toda requisição precisa do header
`Authorization: Bearer <accessToken>`. O motivo é direto: cada consulta que não vem do cache gasta
cota da The Odds API, que é paga.

Autenticar diz *quem* está chamando; a
[matriz de acesso por rota](docs/documentacao.md#52-matriz-de-acesso-por-rota) diz *o que cada um
pode fazer*. O critério que separa `USER` de `ADMIN` é um só: **escreve na instância inteira ou
gasta cota externa**.

No primeiro boot, se não existir nenhum administrador ativo, a aplicação cria um a partir de
`APP_ADMIN_INICIAL_EMAIL` (padrão `admin@cartolaodds.local`) e `APP_ADMIN_INICIAL_SENHA`. A partir
daí, contas novas nascem de `POST /api/usuarios` — não há auto-cadastro público.

Detalhes de claims, expiração, freio de força bruta e revogação de token:
[`documentacao.md` § 5](docs/documentacao.md#5-autenticação-e-política-de-acesso).

---

## Testes

```bash
# Executar todos os testes
mvn test

# Classe específica
mvn test -Dtest=OddsServiceTest
```

**44 classes de teste — 761 cenários.** A cobertura por classe está em
[`documentacao.md` § 14](docs/documentacao.md#14-testes).

---

## Para onde ir depois

| Quero… | Vá para |
|---|---|
| A lista de endpoints e o JSON de cada resposta | [`documentacao.md` § 8](docs/documentacao.md#8-endpoints) |
| Todas as variáveis de ambiente | [`documentacao.md` § 4.2](docs/documentacao.md#42-variáveis-de-ambiente) |
| Ajustar pesos do score, formação ou `oddLimite` | [`documentacao.md` § 4.3](docs/documentacao.md#43-parâmetros-de-negócio-via-banco-de-dados) |
| Entender o guardrail de cota da The Odds API | [`documentacao.md` § 4.4](docs/documentacao.md#44-cota-da-the-odds-api-guardrail-e-sondagem) |
| A matriz de acesso por rota e o hardening de `prod` | [`documentacao.md` § 5](docs/documentacao.md#5-autenticação-e-política-de-acesso) |
| Criar, listar ou desativar usuários | [`documentacao.md` § 6](docs/documentacao.md#6-gestão-de-usuários) |
| As regras de montagem do time (score, formação, orçamento) | [`documentacao.md` § 9](docs/documentacao.md#9-regras-de-negócio) |
| A estrutura de pacotes e arquivos | [`documentacao.md` § 11](docs/documentacao.md#11-estrutura-do-projeto) |
| Monitorar a aplicação com Prometheus/Grafana | [`documentacao.md` § 17](docs/documentacao.md#17-observabilidade) |
| Saber **por que** algo foi feito assim | [`context.md`](docs/context.md) |

---

## Licença

Distribuído sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.
