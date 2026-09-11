# Segurança e Acesso

> **Papel deste arquivo:** autenticação JWT, matriz de acesso por rota, hardening do perfil `prod` e gestão de usuários.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Autenticação e Política de Acesso

### Autenticação (JWT)

Todos os endpoints exigem `Authorization: Bearer <accessToken>`, exceto `POST /api/auth/login`,
o healthcheck (`/actuator/health`, `/actuator/info`) e a documentação OpenAPI — esta última só fora
de produção. A API consome cota paga da The Odds API a cada chamada que não vem do cache.

Autenticar diz *quem* está chamando; a [matriz de acesso](seguranca.md#matriz-de-acesso-por-rota) diz
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

O fluxo de autenticação (login, captura do `accessToken`, uso no header) está no
[README](../README.md#início-rápido-com-docker). No **Swagger UI**, o botão **Authorize** recebe apenas o valor do `accessToken`.

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
`PATCH /api/usuarios/me/senha` (ver [Gestão de Usuários](seguranca.md#gestão-de-usuários)).

### Matriz de acesso por rota

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
cada método é a regra fina (ver [Gestão de Usuários](seguranca.md#gestão-de-usuários)).

**Verificação.** `PoliticaAcessoIntegrationTest` percorre a matriz rota a rota nos três estados
(sem token, `USER`, `ADMIN`) e afirma apenas o veredito da autorização, não o status de negócio do
endpoint — amarrar ao status exato faria a matriz quebrar a cada mudança de validação.
`SwaggerProdIntegrationTest` confirma os `404` com `@ActiveProfiles("prod")`, e
`ActuatorEndpointsTest` cobre o Actuator por HTTP real.

*Por que a separação é essa, e por que o Actuator saiu da porta 9090: [`context.md` › Política de
acesso por rota](context.md#política-de-acesso-por-rota-e-hardening-do-perfil-prod).*

### Erros de autorização (401 e 403)

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

### Cabeçalhos de segurança e proxy confiável

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

### Perfil de produção

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

### CORS

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

---

## Gestão de Usuários

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

### Fluxo típico

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

### Contrato

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

### Formato da paginação

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

### Recuperação de senha

Não existe nesta versão: sem envio de e-mail, quem esquece a senha depende do administrador da
instância. Auto-cadastro público, convite por e-mail e redefinição de senha ficaram fora do escopo
da [issue #37](https://github.com/FabioCarlesso/cartolaoddsapi/issues/37).

---

---

## Ver também

- [`api.md`](api.md) — o mapa completo de rotas
- [`configuracao.md`](configuracao.md) — `JWT_SECRET`, `TRUSTED_PROXIES` e as variáveis do freio de login
- [`operacao.md`](operacao.md) — o acesso `ADMIN` às métricas e à cota
- [`context.md`](context.md) — por que JWT com `tokenVersion` e por que `native` e não `framework`
