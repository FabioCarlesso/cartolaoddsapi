# Configuração

> **Papel deste arquivo:** propriedades da aplicação e todas as variáveis de ambiente, com seus padrões.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Arquivo de Propriedades

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

`server.address` não aparece de propósito. Sem a propriedade o Tomcat liga em
`new InetSocketAddress(porta)`, que já é o bind mais abrangente disponível: em JVM
dual-stack o socket sai como coringa IPv6 — o `:::8080` de sempre no `ss`, que também
atende IPv4 — e, num host sem IPv6, cai para `0.0.0.0` sem erro. Declarar `::` não
ampliaria alcance nenhum e trocaria essa degradação silenciosa por uma falha dura
(`UnsupportedAddressTypeException`, aplicação não sobe) em todo ambiente sem IPv6.
Para restringir o bind a uma interface, passe `SERVER_ADDRESS` no ambiente: o binding
relaxado do Spring mapeia a variável para `server.address` sem precisar da linha.

Arquivo: `src/main/resources/application-prod.properties` — só o que muda em produção
(`SPRING_PROFILES_ACTIVE=prod`):

```properties
# Swagger e OpenAPI desligados: as rotas passam a responder 404
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false

# DEBUG imprime o e-mail do dono de um token recusado e a URI de cada 401/403.
# As linhas de operação já identificam o usuário por id.
logging.level.com.cartola=INFO
```

---

## Variáveis de Ambiente

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
| `TRUSTED_PROXIES` | faixas privadas | Regex dos endereços de proxy confiáveis (ver [`seguranca.md` › Cabeçalhos de segurança e proxy confiável](seguranca.md#cabeçalhos-de-segurança-e-proxy-confiável)) |
| `ODDS_API_MIN_REQUESTS_REMAINING` | `50` | Guardrail de cota: abaixo deste saldo restante, o cliente para de chamar a The Odds API e serve o último snapshot conhecido |
| `ODDS_API_CACHE_TTL_MINUTOS` | `60` | TTL do cache `odds` em minutos |
| `ODDS_API_CACHE_TTL_DEGRADADO_MINUTOS` | `10` | TTL de uma resposta de odds **sem nenhum jogo** (provedor fora do ar, fora de temporada) |
| `ODDS_API_SONDA_INTERVALO_HORAS` | `24` | Com o guardrail ativo, intervalo mínimo entre chamadas de sondagem que reavaliam o saldo |

> **Parâmetros de negócio** (odd limite, pesos, formação e regras) **não** são variáveis de
> ambiente: ficam no banco e são gerenciados por `PATCH /api/config` — ver [`banco-de-dados.md` › Parâmetros de Negócio via Banco de Dados](banco-de-dados.md#parâmetros-de-negócio-via-banco-de-dados).

> **Sem API Key configurada:** a aplicação sobe normalmente, o filtro por time favorito é
> desativado e todos os atletas elegíveis por status/preço são considerados.

---

## Ver também

- [`deploy.md`](deploy.md) — como essas variáveis chegam ao container
- [`banco-de-dados.md`](banco-de-dados.md) — os parâmetros que ficam no banco, não no ambiente
- [`operacao.md`](operacao.md) — as propriedades do guardrail de cota em uso
- [`seguranca.md`](seguranca.md) — o efeito de `SPRING_PROFILES_ACTIVE=prod`
