# ⚽ Cartola FC — Odds API

> **Stack:** Java 21 · Spring Boot 3.4.5 · Maven · JAR  
> **Versão:** 1.0.0

API REST que monta automaticamente um time competitivo para o Cartola FC cruzando odds do Brasileirão com métricas dos atletas da plataforma.

---

## Índice

1. [Stack e Dependências](#1-stack-e-dependências)
2. [APIs Externas](#2-apis-externas)
3. [Configuração](#3-configuração)
4. [Cache (Caffeine)](#4-cache-caffeine)
5. [Regras de Negócio](#5-regras-de-negócio)
6. [Fluxo de Execução](#6-fluxo-de-execução)
7. [Estrutura do Projeto](#7-estrutura-do-projeto)
8. [Referência de Funções](#8-referência-de-funções)
9. [Referência de Dados](#9-referência-de-dados)
10. [Testes](#10-testes)
11. [Swagger / OpenAPI](#11-swagger--openapi)
12. [Docker](#12-docker)
13. [Como Executar](#13-como-executar)
14. [Melhorias Futuras](#14-melhorias-futuras)

---

## 1. Stack e Dependências

| Tecnologia | Versão | Uso |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework web, IoC, configuração |
| Maven | 3.9+ | Build e gerenciamento de dependências |
| PostgreSQL | 16 | Banco de dados relacional (configuração, produção) |
| H2 | runtime | Banco in-memory (MODE=PostgreSQL) para testes |
| Flyway | 10.x | Migrations de banco de dados |
| Spring Data JPA | via starter-data-jpa | Persistência com Hibernate |
| Lombok | latest | `@Builder`, `@Getter`, `@With` — reduz boilerplate |
| springdoc OpenAPI | 2.8.8 | Swagger UI e spec OpenAPI 3 automática |
| JUnit 5 | via starter-test | Testes unitários e parametrizados |
| Mockito | via starter-test | Mocking de dependências |
| AssertJ | via starter-test | Assertions fluentes |
| MockMvc | via starter-test | Testes de camada web |

**Instalação:**
```bash
mvn clean package -DskipTests
```

---

## 2. APIs Externas

### 2.1 The Odds API

| Atributo | Valor |
|---|---|
| URL base | `https://api.the-odds-api.com/v4` |
| Endpoint | `GET /sports/soccer_brazil_campeonato/odds` |
| Autenticação | Query param `apiKey` |
| Plano gratuito | 500 requisições/mês |
| Header útil | `X-Requests-Remaining` — quota restante |

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

### 2.2 Cartola FC API (pública, sem autenticação)

**URL base:** `https://api.cartola.globo.com`

| Endpoint | Finalidade | Campos principais |
|---|---|---|
| `/mercado/status` | Status do mercado | `status_mercado` (1=aberto), `rodada_atual` |
| `/atletas/mercado` | Atletas disponíveis | `apelido`, `posicao_id`, `clube_id`, `status_id`, `media_num`, `variacao_num`, `preco_num` |
| `/clubes` | Mapa id → nome/sigla | `nome`, `abreviacao` |
| `/partidas` | Partidas da rodada | `clube_casa_id`, `clube_visitante_id` |
| `/atletas/pontuados` | Pontuação pós-rodada | `pontuacao`, `scout` *(melhoria futura)* |

---

## 3. Configuração

### 3.1 Arquivo de Propriedades

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
```

Arquivo: `src/main/resources/application-prod.properties` — só o que muda em produção
(`SPRING_PROFILES_ACTIVE=prod`):

```properties
# Swagger e OpenAPI desligados: as rotas passam a responder 404 (ver 3.6)
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false

# DEBUG imprimiria e-mail de quem autentica e payload de requisição a cada chamada
logging.level.com.cartola=INFO
```

### 3.2 Parâmetros de Negócio via Banco de Dados

Os parâmetros de negócio (odd limite, pesos do score, formação e regras) são armazenados na tabela `configuracao` do PostgreSQL e gerenciados via API REST — sem necessidade de restart.

**Migrations Flyway:**

- `V1__create_configuracao.sql` — cria a tabela com valores padrão (colunas `NUMERIC`)
- `V2__alter_configuracao_numeric_to_double.sql` — converte as colunas de pesos/odds para `DOUBLE PRECISION` (necessário para compatibilidade com o mapeamento Hibernate de `double`)
- `V3__add_evitar_mesmo_clube_defesa.sql` — adiciona a regra configurável para evitar clubes repetidos entre GOL, LAT e ZAG
- `V4__add_limite_atletas_por_clube.sql` — adiciona o limite configurável de atletas titulares por clube
- `V5__add_budget_maximo.sql` — adiciona a constraint de budget máximo em C$ para os titulares (padrão `0` = sem limite)
- `V6__add_peso_desvio.sql` — adiciona o peso da penalidade por desvio padrão do desempenho (padrão `0.05`)
- `V7__create_escalacao_rodada.sql` — cria a tabela `escalacao_rodada` (histórico de escalações por rodada, ver seção 5.12)

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

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/config` | Retorna a configuração atual |
| `PATCH` | `/api/config` | Atualiza um ou mais campos em runtime |
| `POST` | `/api/config/reset` | Restaura todos os defaults |

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

### 3.3 Variáveis de Ambiente (Docker)

| Variável | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | `SUA_API_KEY_AQUI` | **Obrigatório** — chave da The Odds API |
| `APP_PORT` | `8080` | Porta exposta no host (docker-compose) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cartola_odds` | URL do banco |
| `SPRING_DATASOURCE_USERNAME` | `cartola` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `cartola` | Senha do banco |
| `POSTGRES_USER` | `cartola` | Usuário criado no container PostgreSQL |
| `POSTGRES_PASSWORD` | `cartola` | Senha do container PostgreSQL |
| `JWT_SECRET` | — | Segredo HMAC de assinatura dos tokens (mínimo 32 caracteres). Obrigatório em `prod` |
| `JWT_EXPIRATION_MS` | `86400000` | Validade do access token em milissegundos |
| `APP_ADMIN_INICIAL_EMAIL` | `admin@cartolaodds.local` | E-mail do administrador criado no primeiro boot |
| `APP_ADMIN_INICIAL_SENHA` | — | Senha do administrador inicial (mínimo 8 caracteres). Obrigatória enquanto não houver ADMIN ativo |
| `APP_LOGIN_MAX_TENTATIVAS` | `5` | Falhas de login toleradas por e-mail dentro da janela |
| `APP_LOGIN_JANELA_MINUTOS` | `5` | Janela do freio de login, em minutos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens liberadas para CORS, separadas por vírgula |
| `SPRING_PROFILES_ACTIVE` | `default` | Profile do Spring Boot; `prod` desliga Swagger/`api-docs` (`404`) e baixa o log de `com.cartola` para `INFO` |

### 3.4 Autenticação (JWT)

Todos os endpoints exigem `Authorization: Bearer <accessToken>`, exceto `POST /api/auth/login`,
o healthcheck (`/actuator/health`, `/actuator/info`) e a documentação OpenAPI — esta última só fora
de produção. A API consome cota paga da The Odds API a cada chamada que não vem do cache — o acesso
aberto era o que impedia publicá-la.

Autenticar diz *quem* está chamando; a política de acesso da seção 3.6 diz *o que cada um pode
fazer*.

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

**Claims do token:** `sub` (e-mail), `perfil`, `usuarioId` e `tokenVersion`.

A `tokenVersion` é comparada com a do banco a cada requisição: incrementá-la — ao trocar a senha,
desativar o usuário ou rebaixar seu perfil — invalida na hora todos os tokens já emitidos, sem
sessão no servidor.

**Segredos sem padrão versionado.** Nem `JWT_SECRET` nem `APP_ADMIN_INICIAL_SENHA` têm default.

- `APP_ADMIN_INICIAL_SENHA`: exigida em **qualquer perfil** quando não há ADMIN ativo no banco —
  sem ela a aplicação falha ao iniciar, antes de o servidor web abrir a porta
  (`SmartInitializingSingleton`, ainda dentro do refresh do contexto). Com um ADMIN ativo, deixa de ser exigida, para que produção não
  carregue para sempre a senha do primeiro acesso.
- `JWT_SECRET`: obrigatório em `prod`; fora dele, ausente, vira chave efêmera por boot, com aviso.

**Duração em vez de instante.** O login devolve `expiraEmSegundos`, não uma data. O container roda
em UTC e um horário sem fuso seria lido como local pelo cliente, que acharia a sessão mais longa do
que o token é. Mesma escolha do `expires_in` do OAuth 2.

**Freio de força bruta por e-mail.** `LoginThrottle` conta falhas por e-mail normalizado e responde
`429` ao atingir o limite da janela. A chave é o e-mail, e não o IP, porque atrás do nginx e da
borda da plataforma `getRemoteAddr()` é o endereço do proxy — igual para todos. O e-mail descreve o
alvo real do ataque.

> A matriz completa de acesso por rota, o fechamento de Swagger no perfil `prod` e os cabeçalhos de
> segurança estão em 3.6.

### 3.5 Gestão de usuários

Cadastro e manutenção de contas pela própria API, para que liberar acesso não dependa de `INSERT`
manual no banco de produção com hash BCrypt gerado à mão. Não há auto-cadastro público: toda conta
nasce de um administrador.

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

**Regra fina em `@PreAuthorize`, piso em matcher de URL.** As rotas de `/api/usuarios` misturam
operações de administrador com as do próprio usuário (`/me`), e é ao lado do endpoint que essa
distinção fica legível — daí o `@PreAuthorize`, ligado pelo `@EnableMethodSecurity` do
`SecurityConfig`. O `SecurityConfig` declara sobre elas apenas um piso (`/api/usuarios/me` para
qualquer autenticado, o resto de `/api/usuarios/**` para `ADMIN`): ele não repete a regra de cada
método, e cobre o caso de um endpoint novo nascer sem `@PreAuthorize`.

Como consequência, a recusa nasce **dentro** do MVC, e não no filter chain: sem um
`@ExceptionHandler(AccessDeniedException.class)` no `GlobalExceptionHandler`, ela seria capturada
pelo handler genérico e viraria `500`. O handler responde o mesmo texto do `ErroSegurancaHandler`,
para que `403` tenha um corpo só, venha de onde vier.

**A senha nunca sai.** As respostas usam `UsuarioResponse`, e não a entidade `Usuario` — assim um
campo novo na entidade não vaza o hash BCrypt por descuido. `PATCH /api/usuarios/{id}` também não
aceita senha: quem troca é o dono da conta, confirmando a senha atual.

**Desativação é lógica.** `DELETE` marca `ativo = false` e mantém o registro, para não apagar o
histórico de quem o produziu. Repetir sobre alguém já inativo responde `204` sem alterar nada.

**Proteções contra ficar sem administrador** (`409`, sem alterar o registro):

- um `ADMIN` não desativa nem rebaixa a própria conta — perderia o acesso no ato, quase sempre por
  engano;
- ninguém desativa ou rebaixa o **último** `ADMIN` ativo — a instância só voltaria a ter
  administrador por acesso direto ao banco.

A segunda checagem usa `travarAtivosPorPerfil`, com `@Lock(PESSIMISTIC_WRITE)`: uma contagem
simples seria check-then-act, e duas requisições simultâneas leriam "há 2 administradores" e cada
uma removeria o seu. O `SELECT ... FOR UPDATE` serializa as duas, e a segunda já enxerga o estado
que a primeira deixou.

Trocar o **próprio e-mail** não é bloqueado, mas desloga o administrador na prática — o e-mail é o
`subject` do token. O `@Operation` do endpoint avisa disso.

**Freio de força bruta na troca de senha.** `alterarSenha` chama o mesmo `LoginThrottle` do login,
com o mesmo contador por e-mail. Um token roubado tem validade limitada; sem freio, ele renderia
tentativas ilimitadas de adivinhar `senhaAtual` e, no acerto, takeover permanente — a troca derruba
os tokens do dono legítimo. Contadores separados dariam ao atacante duas janelas para o mesmo
segredo.

**Normalização de e-mail.** Gravado sempre em minúsculas: a `UNIQUE` do Postgres é sensível a
caixa, o login (`findByEmailIgnoreCase`) não é. Sem normalizar, `Fabio@x.com` e `fabio@x.com`
coexistiriam na tabela e o login ficaria ambíguo. A checagem de duplicidade acontece no service; a
`UNIQUE` continua sendo a rede para duas criações simultâneas, e o `DataIntegrityViolationException`
resultante também vira `409`.

**Paginação.** `PaginaResponse<T>` em vez do `Page` do Spring Data: o JSON daquela classe é detalhe
interno do framework, muda entre versões e o próprio Spring avisa disso no log. O envelope é
`conteudo`, `pagina`, `tamanho`, `totalElementos`, `totalPaginas` e `ultima`.

O `sort` é restrito a `id`, `nome`, `email`, `perfil`, `ativo` e `criadoEm`. Sem a lista fechada,
uma propriedade inexistente virava `500` com o nome da entidade interna na resposta, e `sort=senha`
era aceito. A mensagem de erro não ecoa o campo recebido — a lista de aceitos basta para corrigir a
chamada, e evita devolver ao cliente um texto que ele mesmo escolheu.

**Payload inválido é `400`, nunca `500`.** O `GlobalExceptionHandler` trata
`HttpMessageNotReadableException` (JSON mal formado, valor fora do enum `Perfil`) e
`PropertyReferenceException` (ordenação desconhecida). Nos dois casos a mensagem original fica só
no log: ela nomeia a classe Java e chega a listar os valores aceitos do enum.

**Fora de escopo (issue #37):** auto-cadastro público, convite por e-mail e recuperação de senha.

### 3.6 Política de acesso por rota e hardening do perfil `prod`

**Matriz de acesso** (declarada no `SecurityConfig`):

| Rota | Acesso |
|---|---|
| `POST /api/auth/login` | Público |
| `/actuator/health`, `/actuator/health/**`, `/actuator/info` | Público — healthcheck da plataforma |
| `/actuator/**` (`metrics`, `prometheus`) | `ADMIN` |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | Público fora de produção; `404` no perfil `prod` |
| `GET /api/time`, `/api/time/comparar`, `/api/ranking`, `/api/favoritos`, `/api/historico**` | Autenticado |
| `GET /api/config` | Autenticado |
| `PATCH /api/config`, `POST /api/config/reset` | `ADMIN` |
| `DELETE /api/cache`, `DELETE /api/cache/{nome}` | `ADMIN` |
| `/api/usuarios/me`, `/api/usuarios/me/senha` | Autenticado |
| `/api/usuarios/**` | `ADMIN` |
| Qualquer outra | Autenticado |

**Por que essas três rotas exigem `ADMIN`.** `PATCH /api/config` e `POST /api/config/reset` mudam
pesos do score, formação e `odd_limite` da instância inteira; `DELETE /api/cache` força chamadas
novas à The Odds API, cuja cota mensal é paga. São operações de dono, não de convidado.

**A ordem dos matchers importa.** Vale o primeiro que casa: `/api/usuarios/me` vem antes de
`/api/usuarios/**`, e as regras de `ADMIN` vêm antes do `anyRequest().authenticated()`. Um matcher
por método cobre só aquele método — **`HEAD` não herda a autorização de `GET`** —, e por isso as
regras de `ADMIN` citam apenas os verbos que escrevem: leitura e `HEAD` caem na regra final, que já
é fechada. Essa regra final ser `authenticated()` é o que faz uma rota nova nascer fechada.

**401 e 403 no contrato `ErrorResponse`.** Os dois nascem no filter chain, antes do MVC, e não
passariam pelo `GlobalExceptionHandler` — sem o `authenticationEntryPoint` e o `accessDeniedHandler`
apontando para o `ErroSegurancaHandler`, o cliente receberia a página de erro do container em vez de
JSON. O `403` que nasce de um `@PreAuthorize` é tratado dentro do MVC (ver 3.5) com o mesmo texto,
para que o corpo seja um só.

**Actuator na porta única.** Antes ele vivia em `management.server.port=9090` com bind em
`127.0.0.1`, e era o bind — não uma regra — que o protegia; uma plataforma que publica uma porta só
não sustenta esse arranjo. Na porta única quem protege é a matriz acima:

- `health` e `info` públicos, para o healthcheck consultar antes de qualquer token existir;
- `metrics` e `prometheus` restritos a `ADMIN` — descrevem uso de memória, latência por rota e
  contagem de erros;
- `management.endpoint.health.show-details=when_authorized` com
  `management.endpoint.health.roles=ADMIN`: o corpo anônimo é só `{"status":"UP"}`, sem estado de
  banco, disco e dependências.

**Perfil `prod` (`application-prod.properties`).** `springdoc.api-docs.enabled=false` e
`springdoc.swagger-ui.enabled=false`. Desligar em vez de proteger é proposital: com o springdoc
desligado as rotas não existem e respondem `404`, enquanto um `401` confirmaria que a documentação
está lá, atrás de uma senha. O contrato completo da API é o mapa que um atacante levaria tempo
montando na mão, e o *Try it out* do Swagger UI deixa disparar as chamadas dali mesmo. No mesmo
arquivo, `logging.level.com.cartola=INFO`: em `DEBUG` o log imprime e-mail de quem autentica e
payload de requisição a cada chamada.

**CORS parametrizado, nunca `*`.** `app.cors.allowed-origins` lê `CORS_ALLOWED_ORIGINS` (padrão
`http://localhost:4200`); métodos e headers são listados um a um. O token viaja em header, e uma
origem curinga deixaria qualquer site chamar a API com o token da vítima. O preflight `OPTIONS`
passa antes da autorização — ele não carrega `Authorization`, e sem isso o navegador levaria `401`
sem chegar a enviar a requisição real.

**Cabeçalhos de segurança na resposta:**

| Cabeçalho | Valor | Origem |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | Padrão do Spring Security |
| `X-Frame-Options` | `DENY` | Padrão do Spring Security |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | `SecurityConfig` |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains` | `SecurityConfig`, só em requisição por TLS |

O HSTS é condicionado a `request.isSecure()` **ou** `X-Forwarded-Proto: https`: atrás da borda da
plataforma o TLS termina no proxy e o Tomcat vê HTTP puro, então `isSecure()` sozinho nunca seria
verdadeiro e o cabeçalho jamais apareceria em produção. A condição evita o outro extremo — mandar
HSTS em `http://localhost` trava o navegador do desenvolvedor em HTTPS para todo o host por um ano.

**Verificação.** `PoliticaAcessoIntegrationTest` percorre a matriz rota a rota nos três estados
(sem token, `USER`, `ADMIN`) e afirma apenas o veredito da autorização, não o status de negócio do
endpoint — amarrar ao status exato faria a matriz quebrar a cada mudança de validação.
`SwaggerProdIntegrationTest` confirma os `404` com `@ActiveProfiles("prod")`, e
`ActuatorEndpointsTest` cobre o Actuator por HTTP real.

> ⚠️ **Atenção — `@Qualifier` com Lombok:** `@Qualifier` em campos `final` com `@RequiredArgsConstructor` **não funciona** — o Lombok ignora a anotação. `OddsClient` e `CartolaClient` usam construtores explícitos com `@Qualifier` no parâmetro do construtor.

## 4. Cache (Caffeine)

### 4.1 Configuração

O projeto usa **Caffeine** (cache em memória JVM) via `spring-boot-starter-cache`.
Não requer infraestrutura externa — o cache sobe junto com a aplicação.

```java
// CacheConfig.java — constantes de nome dos caches
CACHE_ODDS           = "odds"           // TTL: 10 min
CACHE_ATLETAS        = "atletas"        // TTL: 10 min
CACHE_CLUBES         = "clubes"         // TTL: 10 min
CACHE_PARTIDAS       = "partidas"       // TTL: 10 min
CACHE_PONTUADOS      = "pontuados"      // TTL: 10 min (por rodada)
CACHE_STATUS_MERCADO = "statusMercado"  // TTL: 10 min
CACHE_CONFIGURACAO   = "configuracao"   // invalidado via PATCH/POST /api/config
```

### 4.2 Estratégia por endpoint

| Cache | Endpoint cacheado | Justificativa |
|---|---|---|
| `odds` | `GET /sports/.../odds` | Odds mudam pouco durante o dia |
| `atletas` | `GET /atletas/mercado` | Mercado abre/fecha poucas vezes |
| `clubes` | `GET /clubes` | Dados estáticos durante a temporada |
| `partidas` | `GET /partidas` | Partidas da rodada são fixas |
| `pontuados` | `GET /atletas/pontuados` | Histórico muda somente após cada rodada |
| `statusMercado` | `GET /mercado/status` | Consultado com frequência |
| `configuracao` | `GET /api/config` | Invalidado via `PATCH /api/config` ou `POST /api/config/reset` |

### 4.3 Anotações

```java
// CartolaClient — @Cacheable em cada método
@Cacheable(CacheConfig.CACHE_ATLETAS)
public AtletaResponse buscarAtletas() { ... }

// Cache de pontuados com chave por rodada
@Cacheable(value = CacheConfig.CACHE_PONTUADOS, key = "#rodada")
public PontuadosResponse buscarPontuados(int rodada) { ... }
```

### 4.4 Observações

- **Sem Redis:** cache em memória JVM — reiniciado com o container.
- **Tamanho máximo:** 500 entradas por cache (`maximumSize(500)`).
- **Stats:** `recordStats()` habilitado — métricas acessíveis via Actuator futuramente.
- **Thread-safe:** Caffeine garante consistência em ambientes multi-thread.

### 4.5 Invalidação de Cache via API

O endpoint `DELETE /api/cache` permite forçar a atualização dos dados sem reiniciar a aplicação.

#### Invalidar todos os caches

```http
DELETE /api/cache
```

**Resposta `200 OK`:**
```json
{
  "cachesInvalidados": ["odds", "atletas", "clubes", "partidas", "pontuados", "statusMercado"],
  "mensagem": "Todos os caches invalidados com sucesso.",
  "timestamp": "2025-06-01T15:30:00"
}
```

#### Invalidar cache específico

```http
DELETE /api/cache/{nome}
```

**Parâmetro de path:** nome do cache — `odds`, `atletas`, `clubes`, `partidas`, `pontuados` ou `statusMercado`.
O cache `configuracao` é interno da camada de configuração e é invalidado automaticamente por `PATCH /api/config` e `POST /api/config/reset`.

**Resposta `200 OK`:**
```json
{
  "cachesInvalidados": ["atletas"],
  "mensagem": "Cache 'atletas' invalidado com sucesso.",
  "timestamp": "2025-06-01T15:30:00"
}
```

**Resposta `400 Bad Request` (nome inválido):**
```json
{
  "status": 400,
  "erro": "Parametro invalido",
  "mensagem": "Cache 'xyz' nao encontrado. Caches validos: [odds, atletas, clubes, partidas, pontuados, statusMercado]",
  "timestamp": "2025-06-01T15:30:00"
}
```

**Caso de uso típico:** após um erro nos dados externos ou necessidade de forçar busca de odds atualizadas, chamar `DELETE /api/cache` garante que a próxima requisição busque dados frescos.

## 5. Regras de Negócio

### 5.1 Identificação de Times Favoritos

1. Cruza os jogos da Odds API com os confrontos da rodada atual do Cartola (`/partidas` + `/clubes`).
2. Ignora odds de confrontos que não pertencem à rodada atual.
3. Para cada jogo restante, seleciona o time com **menor odd** (maior probabilidade de vitória).
4. Aplica `ODD_LIMITE` (padrão `3.0`):
   - `odd ≤ ODD_LIMITE` → time entra no conjunto `favoritos_norm`
   - `odd > ODD_LIMITE` → jogo descartado (equilibrado ou sem favorito claro)
5. Nomes normalizados antes do cruzamento com dados do Cartola.

Se os confrontos da rodada atual não estiverem disponíveis, o processamento mantém o fallback resiliente e considera todas as odds retornadas.

```
Flamengo x Palmeiras → odds: FLA 2.10 / PAL 3.40
Favorito: Flamengo (2.10 ≤ 3.0) ✅

Fortaleza x Bahia → odds: FOR 3.30 / BAH 3.40
Favorito: Fortaleza (3.30 > 3.0) ⛔ descartado
```

### 5.2 Filtros de Atletas

| Filtro | Regra | Fallback |
|---|---|---|
| `status_id` | `6` (Dúvida) ou `7` (Provável) | Descartado |
| `preco_num` | `> 0` | Descartado |
| Time favorito | Clube em `favoritos_norm` | Descartado |
| Sem odds | `favoritos_norm` vazio | Filtro desativado — usa todos os elegíveis |

### 5.3 Fórmula do Score

O `ScoreService` usa fórmulas específicas por posição para GOL e ATA, mantendo o fallback configurável para LAT, ZAG, MEI e TEC. `fatorCasa` e `timeFavorito` valem `10.0` quando verdadeiros e `0.0` caso contrário.

**Fallback configurável (LAT, ZAG, MEI, TEC):**

```
score = (mediaPontos × pesoMediaPontos)
      + (valorização × pesoValorizacao)
      + (desempenho × pesoDesempenho)
      + (fatorCasa × pesoFatorCasa)
      + (timeFavorito × pesoTimeFavorito)
      - (desvioPadrao × pesoDesvio)
```

**Goleiro (GOL):**

```
score = (desempenho × 0.35) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (defesasDificeis × 0.05) + (penaltisDefendidos × 0.05) - (golsSofridos × 0.02)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      - (desvioPadrao × pesoDesvio)
```

**Atacante (ATA):**

```
score = (desempenho × 0.25) + (mediaPontos × 0.25) + (valorização × 0.10)
      + (gols × 0.08) + (assistencias × 0.05)
      + (fatorCasa × pesoFatorCasa) + (timeFavorito × pesoTimeFavorito)
      - (desvioPadrao × pesoDesvio)
```

Os scouts são acumulados da temporada vindos de `/atletas/mercado`: `DD` -> `defesasDificeis`, `GS` -> `golsSofridos`, `DP` -> `penaltisDefendidos`, `G` -> `gols`, `A` -> `assistencias`. Valores ausentes ou nulos são tratados como `0`.

Os pesos do fallback e os bônus situacionais são configuráveis via `PATCH /api/config`. Os pesos base de GOL e ATA são constantes centralizadas no `ScoreService`.

**Desempenho:** usa a média real das últimas 5 rodadas via `/atletas/pontuados`. Quando o histórico não está disponível, usa `mediaPontos` como proxy.

**Penalização por volatilidade:** o `DesempenhoService` retorna um `DesempenhoAtleta` com `mediaPontos`, `desvioPadrao` (populacional) e `rodadasConsideradas`. O `ScoreService` subtrai `desvioPadrao × pesoDesvio` do score final em todas as posições, penalizando atletas inconsistentes. `pesoDesvio` é configurável via `PATCH /api/config` (padrão `0.05`); com menos de 2 rodadas o `desvioPadrao` é `0.0`, anulando a penalidade. Quando o atleta não tem histórico recente e cai para o proxy (`mediaPontos` da temporada), nenhuma penalidade é aplicada.

**Exposição na API:** o `desvioPadrao` (arredondado para 4 casas decimais) e `rodadasConsideradas` são propagados para o modelo `Atleta` e retornados nos DTOs `AtletaDto` (`GET /api/time`) e `AtletaRankingDto` (`GET /api/ranking`), permitindo ao frontend exibir um indicador de consistência. Atletas que usam o proxy retornam `desvioPadrao = 0.0` e `rodadasConsideradas = 0`. Os campos são documentados no schema OpenAPI via anotações `@Schema` e ficam disponíveis em `/v3/api-docs`.

### 5.4 Formação 4-3-3

| Slot | `posicao_id` | Qtd | Pool elegível |
|---|---|---|---|
| GOL | 1 | 1 | Somente `posicao == GOL` |
| LAT | 2 | 2 | Somente `posicao == LAT` |
| ZAG | 3 | 2 | Somente `posicao == ZAG` |
| MEI | 4 | 3 | Somente `posicao == MEI` |
| ATA | 5 | 3 | Somente `posicao == ATA` |
| TEC | 6 | 1 | Somente `posicao == TEC` |

Cada slot seleciona **exclusivamente** dentro da sua posição.

### 5.5 Seleção de Reservas

- Somente `status == PROVAVEL` (7) — dúvidas não são reservas.
- Preferencialmente mais baratos que o titular mais caro da posição.
- Fallback: qualquer provável da posição se nenhum mais barato existir.
- Sempre da **mesma posição individual** do titular (LAT reserva LAT, ZAG reserva ZAG).
- `TEC` não tem reserva.

### 5.6 Defesa sem Clube Repetido

Quando `evitarMesmoClubeDefesa=true` (padrão), a seleção de titulares não repete clubes entre `GOL`, `LAT` e `ZAG`. O montador percorre os candidatos por score e pula defensores cujo clube já tenha sido usado nessas posições. A regra não limita `MEI`, `ATA` ou `TEC` e pode ser desligada via `PATCH /api/config`.

Caso não haja candidatos suficientes sem repetição (ex: poucos clubes disponíveis na rodada), o montador completa a posição com os melhores atletas restantes — evitando apenas apelidos já escalados — garantindo que a formação nunca fique incompleta.

### 5.7 Capitão e Reserva de Luxo

- **Capitão:** maior score, prioridade `ATA > MEI > ZAG > LAT > GOL > TEC`
- **Reserva de Luxo:** melhor score entre os atletas de `reservas`; `TEC` não concorre porque não tem reserva.
- O capitão tem pontuação **dobrada** no Cartola FC.

### 5.8 Limite Máximo por Clube (inclui TEC)

Durante a montagem dos titulares, o serviço limita a escalação para **até 4 atletas do mesmo clube** no time completo, incluindo o treinador (`TEC`).

Fluxo aplicado:
- tenta selecionar por score respeitando simultaneamente o limite por clube;
- mantém também a regra de defesa sem repetição quando ativada;
- se faltar atleta para completar uma posição, relaxa primeiro a regra de defesa e mantém o limite por clube;
- em último caso, relaxa o limite por clube para não deixar a formação incompleta.

### 5.9 Tratamento de Dúvidas

- Titulares com `status_id == 6` são escalados, mas marcados com `⚠️ DÚVIDA`.
- Sistema busca o melhor substituto `PROVAVEL` na **mesma posição individual**.
- Substituto nunca é outro atleta já escalado como titular.
- Alertas retornados em `alertasDuvida` no `TimeResponse`.

**Opt-out via `excluirDuvida`:** o parâmetro opcional `excluirDuvida=true` em `GET /api/time`
remove os atletas com `status_id == 6` do pool no `PipelineService`, **após o cache** e **antes**
do `ScoreService` — de modo que nenhum jogador em dúvida seja escalado como titular ou reserva e
`alertasDuvida` volte vazio. Como a filtragem é pós-cache, as entradas de cache compartilhadas com
o fluxo padrão não são invalidadas. Padrão `false` (comportamento acima preservado). Quando faltam
prováveis para alguma posição, a resposta continua válida com `formacaoCompleta = false`.


### 5.10 Endpoint de Ranking (`GET /api/ranking`)

Retorna os melhores atletas disponíveis ordenados por score decrescente.  
Aplica os **mesmos filtros do `/api/time`** (status, preço e time favorito).

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| `posicao` | string | *(todos)* | Filtra por posição: `GOL`, `LAT`, `ZAG`, `MEI`, `ATA`, `TEC` |
| `limite` | int | `25` | Número de resultados (1–100) |

**Comportamentos:**
- `limite <= 0` → tratado como `1`
- `limite > 100` → clampado para `100`
- `posicao` inválida → HTTP 400 com mensagem detalhada
- Atletas em dúvida aparecem com `emDuvida: true` no response


### 5.10 Endpoint de Favoritos (`GET /api/favoritos`)

Lista os jogos da rodada atual classificados em **favoritos** e **descartados**.

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| `oddLimite` | double | *valor do properties* | Odd máxima para considerar um time favorito. Deve ser `> 1.0`. |

**Lógica por jogo:**
- Ignora odds de confrontos fora da rodada atual do Cartola.
- Seleciona o time com menor odd (excluindo empate) como candidato a favorito.
- Se a menor odd ≤ `oddLimite` → jogo entra em **favoritos** com todos os detalhes.
- Se a menor odd > `oddLimite` → jogo entra em **descartados** com motivo legível.

**Campos retornados para cada favorito:**
- `timeFavorito`, `oddFavorito` — quem é e qual a odd
- `timeAdversario`, `oddAdversario` — adversário e sua odd
- `oddEmpate` — odd do empate quando disponível
- `favoritoEmCasa` — `true` se o favorito é o time mandante

**Validação:** `oddLimite <= 1.0` retorna HTTP 400 (odd de 1.0 ou menos é matematicamente impossível em apostas reais).

### 5.11 Normalização de Nomes

```java
// Remove acentos, converte para lowercase, troca hífen por espaço,
// colapsa espaços duplicados, elimina especiais e aplica aliases.
NormalizadorUtil.normalizar("Atlético-MG")         // → "atletico mg"
NormalizadorUtil.normalizar("Atlético Mineiro MG") // → "atletico mg"
NormalizadorUtil.normalizar("São Paulo FC")        // → "sao paulo"
NormalizadorUtil.normalizar("Grêmio")              // → "gremio"
NormalizadorUtil.normalizar("Inter")               // → "internacional"
NormalizadorUtil.normalizar("Fluminense FC")       // → "fluminense"
```

Aliases atuais cobrem divergências recorrentes entre The Odds API e Cartola FC, como `atletico mineiro`, `atletico mineiro mg`, `red bull bragantino`, `bragantino sp`, `atletico goianiense`, `america mineiro`, `atletico paranaense`, `athletico paranaense`, `sao paulo fc`, `inter`, `fluminense fc`, `vasco da gama` e variantes como `botafogo fr`/`botafogo rj`, `ec bahia`, `cruzeiro ec`, `palmeiras sp`, `sport recife` e `santos fc`. Entradas como `sportrecife` (sem espaço) existem no mapa para cobrir casos em que uma barra é removida pelo pipeline antes da consulta ao dicionário (ex.: `Sport/Recife` → `sportrecife` após limpeza de caracteres especiais).

Para manter o dicionário, adicione novas entradas em `NormalizadorUtil.ALIASES`. A chave deve estar no formato já normalizado pelo utilitário (sem acentos, lowercase, hífens como espaços e espaços duplicados colapsados) e o valor deve ser o nome canônico usado no cruzamento entre favoritos e clubes do Cartola.

### 5.12 Histórico de Escalações por Rodada

`GET /api/time` persiste a escalação sugerida da rodada (titulares e reservas) na tabela `escalacao_rodada`, permitindo análise retroativa da qualidade das sugestões. A persistência é orquestrada pelo `EscalacaoService`:

- **Idempotência:** `salvarEscalacao(Time, rodadaId)` verifica `existsByRodadaId` antes de gravar; uma rodada já registrada não é sobrescrita.
- **Não bloqueante:** a chamada parte do `TimeController` dentro de um `try/catch`; falhas ao persistir são logadas e não impedem o retorno do time.
- **Exceção para `excluirDuvida=true`:** o `TimeController` não persiste quando o parâmetro é usado. Como a gravação é idempotente por rodada, registrar a variante sem dúvidas faria o histórico gravar a **primeira** consulta feita na rodada em vez da sugestão da rodada — cenário provável, já que confrontar `/api/time` com `/api/time?excluirDuvida=true` é o uso natural do parâmetro. Mesmo critério do `/api/time/comparar`, que já não persiste. O `orcamento`, ao contrário, delimita um teto real de cartoletas e produz a escalação que de fato será usada, então **persiste normalmente**.
- **Flags por atleta:** `capitao`, `reserva_luxo` e `em_duvida` são derivadas do `Time` montado. A `pontuacao_real` nasce `null`.

Após o fechamento da rodada, `atualizarPontuacaoReal(rodadaId)` consulta `/atletas/pontuados` e preenche a `pontuacao_real` dos atletas encontrados (os ausentes permanecem `null`). No cálculo do total da rodada (`pontuacaoRealTotal`), a pontuação do capitão é contada em dobro.

> **Restrição da rodada corrente:** o `/atletas/pontuados` do Cartola expõe somente a rodada atual. Para não gravar a pontuação de uma rodada em outra, `atualizarPontuacaoReal` valida `rodadaId == rodada corrente` (via `/mercado/status`) e lança `IllegalArgumentException` (→ `400`) caso contrário. A leitura e a chamada HTTP ocorrem fora de transação de escrita; apenas o `saveAll` final abre transação.

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

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/historico` | Lista as rodadas registradas com resumo (score sugerido vs. pontuação real) |
| `GET` | `/api/historico/{rodadaId}` | Detalhe da escalação de uma rodada — `404` se não registrada |
| `POST` | `/api/historico/{rodadaId}/atualizar-pontuacao` | Preenche a `pontuacao_real` da rodada — `404` se não registrada |

Rodadas sem escalação registrada resultam em `RecursoNaoEncontradoException`, mapeada para `404 Not Found` pelo `GlobalExceptionHandler`.

---

## 6. Fluxo de Execução

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
5. calcularScores()             → pool com score ponderado por atleta
      │
      ▼
6. selecionar titulares         → top-N por score em cada posição
      │
      ▼
7. selecionar reservas          → provável, mesma posição, mais barato, exceto TEC
      │
      ▼
8. mapear substitutos           → provável da mesma posição para cada dúvida
      │
      ▼
9. eleger capitão/reserva luxo → maior titular e melhor reserva
      │
      ▼
10. TimeResponse.from(time)    → HTTP 200 com JSON
```

---

## 7. Estrutura do Projeto

```
cartola/
├── Dockerfile               # Multi-stage build (JDK 21 build + JRE 21 runtime)
├── docker-compose.yml       # app + postgres:16, healthcheck, resource limits
├── .env.example             # Template de variáveis de ambiente
├── .dockerignore
├── pom.xml
├── README.md
├── docs/
│   ├── documentacao.md
│   └── documentacao.docx
└── src/
    ├── main/java/com/cartola/odds/
    │   ├── CartolaOddsApplication.java
    │   ├── config/
    │   │   ├── CacheConfig.java             # Caffeine: 7 caches, TTL 10 min, maxSize 500
    │   │   ├── OddsProperties.java          # key, baseUrl, sport, regions, markets, timeout
    │   │   ├── CartolaProperties.java       # baseUrl, timeout
    │   │   ├── RestClientConfig.java        # beans oddsRestClient e cartolaRestClient
    │   │   └── OpenApiConfig.java           # metadados Swagger UI
    │   ├── client/
    │   │   ├── OddsClient.java
    │   │   └── CartolaClient.java
    │   ├── repository/
    │   │   └── ConfiguracaoRepository.java  # JpaRepository<Configuracao, Long>
    │   ├── service/
    │   │   ├── ConfiguracaoService.java     # buscarConfig(), atualizar(), resetar()
    │   │   ├── OddsService.java
    │   │   ├── DesempenhoService.java
    │   │   ├── RankingService.java
    │   │   ├── CartolaDataService.java
    │   │   ├── ScoreService.java
    │   │   ├── MontadorTimeService.java
    │   │   └── PipelineService.java
    │   ├── controller/api/
    │   │   ├── TimeApi.java
    │   │   ├── RankingApi.java
    │   │   ├── FavoritosApi.java
    │   │   ├── CacheApi.java
    │   │   └── ConfiguracaoApi.java         # Swagger docs para /api/config
    │   ├── controller/
    │   │   ├── TimeController.java
    │   │   ├── RankingController.java
    │   │   ├── FavoritosController.java
    │   │   ├── CacheController.java
    │   │   ├── ConfiguracaoController.java  # GET/PATCH /api/config, POST /api/config/reset
    │   │   └── GlobalExceptionHandler.java
    │   ├── model/
    │   │   ├── Atleta.java
    │   │   ├── Time.java
    │   │   ├── Configuracao.java            # @Entity — tabela configuracao (linha única)
    │   │   ├── enums/
    │   │   │   ├── Posicao.java
    │   │   │   └── StatusAtleta.java
    │   │   ├── request/
    │   │   │   └── ConfiguracaoRequest.java # DTO PATCH com Bean Validation
    │   │   └── response/
    │   │       ├── TimeResponse.java
    │   │       ├── ErrorResponse.java
    │   │       ├── CacheResponse.java
    │   │       ├── RankingResponse.java
    │   │       ├── FavoritosResponse.java
    │   │       ├── ConfiguracaoResponse.java # DTO GET com factory from(Configuracao)
    │   │       ├── OddsResponse.java
    │   │       ├── AtletaResponse.java
    │   │       ├── ClubeResponse.java
    │   │       ├── MercadoStatusResponse.java
    │   │       └── PartidaResponse.java
    │   └── util/
    │       └── NormalizadorUtil.java
    ├── main/resources/
    │   ├── application.properties
    │   ├── application-prod.properties
    │   └── db/migration/
    │       ├── V1__create_configuracao.sql  # Cria tabela e insere valores padrão
    │       ├── V2__alter_configuracao_numeric_to_double.sql  # Converte NUMERIC → DOUBLE PRECISION
    │       ├── V3__add_evitar_mesmo_clube_defesa.sql         # Regra configurável de defesa
    │       ├── V4__add_limite_atletas_por_clube.sql          # Limite configurável por clube
    │       ├── V5__add_budget_maximo.sql                     # Budget máximo em C$
    │       └── V6__add_peso_desvio.sql                       # Peso da penalidade por desvio padrão
    └── test/
        ├── java/com/cartola/odds/
        │   ├── CartolaOddsApplicationTests.java
        │   ├── controller/
        │   │   ├── TimeControllerTest.java
        │   │   ├── CacheControllerTest.java
        │   │   ├── FavoritosControllerTest.java
        │   │   ├── RankingControllerTest.java
        │   │   └── ConfiguracaoControllerTest.java
        │   ├── model/
        │   │   ├── AtletaTest.java
        │   │   └── EnumsTest.java
        │   ├── service/
        │   │   ├── OddsServiceTest.java
        │   │   ├── CartolaDataServiceTest.java
        │   │   ├── ScoreServiceTest.java
        │   │   ├── MontadorTimeServiceTest.java
        │   │   ├── DesempenhoServiceTest.java
        │   │   ├── RankingServiceTest.java
        │   │   └── PipelineServiceTest.java
        │   └── util/
        │       └── NormalizadorUtilTest.java
        └── resources/
            ├── application.properties       # H2 in-memory (MODE=PostgreSQL) para testes
            └── db/migration/h2/             # Migrations Flyway equivalentes ajustadas para H2
```


---

## 8. Referência de Funções

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

### `CartolaDataService.buscarAtletasFiltrados(Set<String> favoritos) → List<Atleta>`
Busca atletas, clubes, partidas e scouts acumulados da temporada. Aplica filtros de status, preço e time favorito.
Quando `favoritos` está vazio, ignora o filtro por time.

### `CartolaDataService.buscarTimesCasa() → Set<Integer>`
Retorna IDs dos times mandantes da rodada atual.

### `CartolaDataService.buscarConfrontosRodadaAtual() → Set<String>`
Retorna chaves normalizadas dos confrontos da rodada atual para limitar as odds processadas.

### `ScoreService.calcularScores(atletas, timesCasa, favoritos) → List<Atleta>`
Retorna nova lista imutável com campo `score` preenchido para cada atleta, usando fórmulas específicas para GOL/ATA e fallback configurável para as demais posições.

### `MontadorTimeService.montar(pool, rodada, avisoMercado) → Time`
Seleciona titulares, aplica a regra configurável de defesa sem clube repetido, reservas (exceto TEC), capitão, reserva de luxo e substitutos.
Retorna `Time` completo com alertas de dúvida.


### `DesempenhoService.calcularDesempenhoUltimasRodadas(rodadaAtual) → Map<Integer, DesempenhoAtleta>`
Busca até 5 rodadas anteriores via `/atletas/pontuados` (cacheadas por rodada).
Retorna `{atletaId → DesempenhoAtleta(mediaPontos, desvioPadrao, rodadasConsideradas)}`. Atletas sem histórico não aparecem no mapa (o `ScoreService` usa `mediaPontos` como fallback e não aplica penalidade por desvio).

### `OddsService.buscarFavoritosDetalhado(oddLimite) → FavoritosResponse`
Processa todos os jogos da Odds API e classifica cada um em favorito ou descartado.
Retorna odds detalhadas de cada time, quem é favorito, se joga em casa e motivo do descarte.

### `RankingService.buscarRanking(posicao, limite) → RankingResponse`
Retorna os melhores atletas por score. Reutiliza o mesmo pipeline de filtros do `/api/time`.  
`posicao = null` retorna todas as posições. `limite` é clampado entre 1 e 100.

### `PipelineService.executar() → Time`
Orquestra todas as etapas. Lança `IllegalStateException` se pool vazio após filtragem.

### `MercadoStatusResponse.getAvisoMercado() → String|null`
Retorna o texto de aviso quando o mercado não está aberto; `null` quando aberto.
Propagado para todos os responses via `Time.avisoMercado` e `RankingResponse.avisoMercado`.

### `NormalizadorUtil.normalizar(String) → String`
Remove acentos (Unicode NFD), converte para lowercase, transforma hífen em espaço, remove caracteres especiais, colapsa espaços duplicados e aplica aliases de clubes definidos no mapa central `ALIASES`.

### `GlobalExceptionHandler.handleValidation(MethodArgumentNotValidException) → ErrorResponse`
Converte falhas de Bean Validation em HTTP 400 com `erro="Parametro invalido"` e todas as mensagens de campos inválidos concatenadas com `"; "` no corpo da resposta.

### `GlobalExceptionHandler.handleTypeMismatch(MethodArgumentTypeMismatchException) → ErrorResponse`
Converte valores de query param/path variable que não convertem para o tipo esperado (ex.: `?orcamento=abc`, `?excluirDuvida=abc`) em HTTP 400, informando nome do parâmetro, valor recebido e tipo esperado. Sem este handler a exceção cairia no `handleGeneric(Exception)` e seria reportada como `500`, tratando erro de cliente como falha de servidor.

---

## 9. Referência de Dados

### Colunas do domínio `Atleta`

| Campo | Tipo | Origem | Descrição |
|---|---|---|---|
| `apelido` | String | `/atletas/mercado` | Nome popular |
| `posicao` | Posicao | `POS_MAP[posicao_id]` | Enum: GOL/LAT/ZAG/MEI/ATA/TEC |
| `_nome_clube_norm` | String | `normalizar(nomeClube)` | Para cruzamento com odds |
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
| `substitutoProvavel` | Atleta | `MontadorTimeService` | Preenchido somente para dúvidas |


### Status do Mercado (`status_mercado`) — referência completa

| Código | Enum | Label | Exibe Aviso | Aviso retornado |
|---|---|---|---|---|
| 1 | `ABERTO` | Aberto | Não | — |
| 2 | `FECHADO` | Fechado | **Sim** | "Mercado fechado. Rodada em andamento." |
| 3 | `MANUTENCAO` | Manutencao | **Sim** | "Mercado em manutencao ou pre-temporada." |
| 4 | `PARCIAL` | Parcial | **Sim** | "Mercado parcialmente aberto. Alguns jogos ja ocorreram." |
| 6 | `FINALIZANDO` | Finalizando | **Sim** | "Processamento pos-rodada em andamento." |
| outros | `DESCONHECIDO` | Desconhecido | **Sim** | "Status desconhecido. Dados podem estar desatualizados." |

> **`avisoMercado` nos responses:** quando o mercado não está aberto, todos os endpoints
> (`/api/time`, `/api/ranking`) retornam o campo `avisoMercado` preenchido com o texto da tabela acima.
> Quando aberto, o campo é `null` (omitido no JSON).

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

---

## 10. Testes

### Cobertura por camada

| Arquivo | Tipo | Cenários cobertos |
|---|---|---|
| `CartolaOddsApplicationTests` | Integração | Contexto Spring sobe sem erros |
| `TimeControllerTest` | Web (MockMvc) | HTTP 200/422/502/500, corpo JSON, campos obrigatórios, timestamp no erro |
| `FavoritosControllerTest` | Web (MockMvc) | HTTP 200/400/502, campos favorito/descartado, validação oddLimite |
| `RankingControllerTest` | Web (MockMvc) | HTTP completo com filtros posição e limite |
| `CacheControllerTest` | Web (MockMvc) | DELETE todos / DELETE por nome / 400 nome inválido |
| `ConfiguracaoControllerTest` | Web (MockMvc) | GET config, PATCH (válido/inválido/soma/regra de defesa), POST reset |
| `ConfiguracaoServiceTest` | Unitário (Mockito) | Atualização e reset da regra de defesa |
| `AtletaTest` | Unitário | `formatado()`, `isDuvida()`, `isProvavel()`, imutabilidade `@With` |
| `EnumsTest` | Unitário | `fromId()`, `fromSigla()`, `isEscalavel()`, `idsEscalaveis()` para todos os valores |
| `OddsServiceTest` | Unitário (Mockito) | Filtro ODD_LIMITE, normalização, filtro por rodada atual, fallback sem confrontos, múltiplos jogos, jogo sem bookmaker, set imutável |
| `CartolaDataServiceTest` | Unitário (Mockito) | Filtros status/preço/favorito, mapeamento de posição, fallback de sigla, times da casa e confrontos da rodada |
| `ScoreServiceTest` | Unitário (Mockito) | Pesos ponderados, bônus casa/favorito, desempenho real vs proxy, imutabilidade |
| `MontadorTimeServiceTest` | Unitário | Formação 4-3-3, regra de defesa sem clube repetido, limite máximo por clube, fallback intermediário (relaxa defesa mas mantém limite por clube), capitão, reserva de luxo pertencente ao conjunto de reservas, reservas por posição sem TEC, dúvidas com substituto |
| `DesempenhoServiceTest` | Unitário (Mockito) | Média rodadas, fallback null, atleta parcial |
| `RankingServiceTest` | Unitário (Mockito) | Ordenação, limite, filtro posição |
| `PipelineServiceTest` | Unitário (Mockito) | Pipeline completo, cada etapa chamada 1x, pool vazio lança exceção |
| `NormalizadorUtilTest` | Unitário | Acentos, hifens, maiúsculas, nulo, branco, idempotência e aliases |
| `PoliticaAcessoIntegrationTest` | Integração (MockMvc) | Matriz de acesso rota a rota nos três estados (sem token, `USER`, `ADMIN`); contrato `ErrorResponse` em 401/403; cabeçalhos de segurança e HSTS condicionado a `X-Forwarded-Proto` |
| `SwaggerProdIntegrationTest` | Integração (`@ActiveProfiles("prod")`) | Swagger UI e `/v3/api-docs` respondem 404 em produção |
| `ActuatorEndpointsTest` | Integração (HTTP real) | `health`/`info` públicos, `health` sem detalhes para anônimo e com detalhes para `ADMIN`, `metrics`/`prometheus` exigindo `ADMIN`, endpoints sensíveis não expostos |

Os testes de integração usam Flyway em `classpath:db/migration/h2` para manter migrations equivalentes às de produção com sintaxe compatível com H2.

### Executar

```bash
# Todos os testes
mvn test

# Apenas uma classe
mvn test -Dtest=ScoreServiceTest

# Com relatório de cobertura (adicionar JaCoCo ao pom.xml)
mvn test jacoco:report
```

### Exemplo de saída esperada

```
[INFO] Results:
[INFO]
[INFO] Tests run: 258, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 11. Swagger / OpenAPI

| URL | Descrição |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interface gráfica — Try it out habilitado |
| `http://localhost:8080/v3/api-docs` | JSON OpenAPI 3 (importar no Postman/Insomnia) |

> Ambas respondem **`404`** com `SPRING_PROFILES_ACTIVE=prod`: o `application-prod.properties`
> desliga o springdoc. Ver 3.6.

**Endpoints disponíveis:**

| Endpoint | Descrição |
|---|---|
| `GET /api/time` | Monta o time completo da rodada |
| `GET /api/time?orcamento=120.0` | Monta o time de maior score que cabe no orçamento em cartoletas |
| `GET /api/time?excluirDuvida=true` | Monta o time apenas com prováveis, sem jogadores em dúvida |
| `GET /api/ranking` | Top atletas por score com filtros opcionais |
| `GET /api/ranking?posicao=ATA` | Top atacantes |
| `GET /api/ranking?posicao=MEI&limite=10` | Top 10 meias |
| `GET /api/favoritos` | Times favoritos com oddLimite atual |
| `GET /api/favoritos?oddLimite=2.5` | Favoritos com limite customizado |
| `DELETE /api/cache` | Invalida todos os caches |
| `DELETE /api/cache/{nome}` | Invalida um cache específico |
| `GET /api/config` | Retorna configuração atual |
| `PATCH /api/config` | Atualiza parâmetros em runtime |
| `POST /api/config/reset` | Restaura defaults |

Falhas de validação de request body em `PATCH /api/config` retornam HTTP 400 com a mensagem do campo inválido.

**Respostas documentadas em `GET /api/time`:**

| Código | Cenário |
|---|---|
| `200` | Time montado com sucesso |
| `400` | `orcamento` inválido (deve ser > 0), ou valor que não converte para o tipo esperado (`?orcamento=abc`, `?excluirDuvida=abc`) |
| `422` | Pool vazio — ODD_LIMITE muito restritivo ou sem API Key |
| `502` | Falha de comunicação com API externa |
| `500` | Erro interno inesperado |

**Parâmetro `orcamento` (opcional) em `GET /api/time`:** quando informado, o `MontadorTimeService`
resolve um *multiple-choice knapsack* por posição via branch-and-bound (`OtimizadorTitulares`),
escolhendo a combinação de **maior soma de score** que cabe no orçamento — empates de score são
desfeitos pela de **menor custo** (`score / preço` apenas como desempate). Sem o parâmetro, mantém a
ordenação gulosa por score. A estratégia retornada é sempre `SCORE_MAXIMO`. A resposta expõe
`orcamentoInformado`, `custoTotal`, `saldoRestante`, `estrategia`, `formacaoCompleta` e — quando o
orçamento não basta para completar a formação — `avisoOrcamento` (com o melhor time best-effort dentro do teto).

**Parâmetro `excluirDuvida` (opcional, padrão `false`) em `GET /api/time`:** quando `true`, o pool é
restrito aos atletas prováveis (`status_id == 7`) antes do cálculo de score, garantindo um time sem
jogadores em dúvida entre titulares e reservas (ver 5.9). É combinável com `orcamento`.

---


## 12. Docker

### 12.1 Arquivos

| Arquivo | Descrição |
|---|---|
| `Dockerfile` | Build multi-stage: stage `build` (JDK 21 Alpine) + stage `runtime` (JRE 21 Alpine) |
| `docker-compose.yml` | Orquestração com variáveis de ambiente, healthcheck e resource limits |
| `.env.example` | Template de variáveis — copiar para `.env` antes de usar |
| `.dockerignore` | Exclui `target/`, `src/test/`, `docs/` e arquivos de IDE do contexto |
| `application.properties` | Lê variáveis de ambiente com fallback para valores padrão |

### 12.2 Dockerfile — Multi-stage Build

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

### 12.3 Variáveis de Ambiente

O `application.properties` usa sintaxe `${VAR:default}` para ler variáveis do ambiente com fallback:

```properties
odds.api.key=${ODDS_API_KEY:SUA_API_KEY_AQUI}
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/cartola_odds}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:cartola}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:cartola}
```

| Variável | Padrão | Descrição |
|---|---|---|
| `ODDS_API_KEY` | `SUA_API_KEY_AQUI` | **Obrigatório** — chave da The Odds API |
| `APP_PORT` | `8080` | Porta exposta no host (somente docker-compose) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/cartola_odds` | URL do banco PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `cartola` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `cartola` | Senha do banco |
| `POSTGRES_USER` | `cartola` | Usuário criado no container PostgreSQL |
| `POSTGRES_PASSWORD` | `cartola` | Senha do container PostgreSQL |
| `SPRING_PROFILES_ACTIVE` | `default` | Profile do Spring Boot |

> Parâmetros de negócio (odd limite, pesos, formação) são gerenciados via `PATCH /api/config` — não precisam de variáveis de ambiente.

### 12.4 Comandos

```bash
# Início rápido
cp .env.example .env           # 1. copiar template
# editar .env com ODDS_API_KEY  # 2. inserir API Key
docker compose up -d           # 3. subir container

# Rebuild após mudança de código
docker compose up -d --build

# Ver logs
docker compose logs -f cartola-odds

# Status e healthcheck
docker compose ps

# Parar
docker compose down

# Build manual
docker build -t cartola-odds:1.0.0 .

# Executar sem Compose (passando variáveis diretamente)
docker run -p 8080:8080 \
  -e ODDS_API_KEY=sua_chave \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/cartola_odds \
  -e SPRING_DATASOURCE_USERNAME=cartola \
  -e SPRING_DATASOURCE_PASSWORD=cartola \
  cartola-odds:1.0.0
```

### 12.5 Resource Limits (docker-compose.yml)

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

### 12.6 Healthcheck

O container verifica automaticamente se a aplicação está respondendo a cada 30 segundos:

```
GET http://localhost:8080/v3/api-docs → 200 OK = healthy
```

`start_period: 60s` — aguarda a JVM e o PostgreSQL inicializarem antes de começar as verificações.

## 13. Como Executar

```bash
# 1. Configure a API Key
# src/main/resources/application.properties
# odds.api.key=sua_chave_aqui

# 2. Build
mvn clean package -DskipTests

# 3. Run
java -jar target/cartola-odds-1.0.0.jar

# 4. Ou direto pelo Maven
mvn spring-boot:run
```

**Testar sem API Key:** se a chave não estiver configurada, o filtro por time favorito é desativado automaticamente e o sistema usa todos os atletas elegíveis por status e preço.

---

## 14. Melhorias Futuras

### Dados e Algoritmos
- [ ] **Score específico por posição** (goleiros: defesas difíceis; atacantes: gols + assistências)
- [x] **Dicionário de aliases** para nomes de clubes divergentes entre as APIs
- [ ] Ponderar a odd como **variável contínua** em vez de bônus binário

### Infraestrutura
- [ ] **Retry** com backoff exponencial via Spring Retry
- [ ] **Métricas** com Spring Actuator + Micrometer
- [ ] **Cobertura de testes** com JaCoCo + relatório HTML

### Regras de Negócio
- [ ] **Constraint de budget** máximo (C$) com OptaPlanner ou Timefold
- [ ] **Formações alternativas** configuráveis: 4-4-2, 3-5-2, 4-5-1
- [ ] **Simulação** de diferentes `ODD_LIMITE` para comparar times resultantes

### Qualidade
- [ ] **Testes de integração** com WireMock simulando as APIs externas

---

## Referências

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

*Projeto de automação pessoal para Cartola FC com dados públicos.*
