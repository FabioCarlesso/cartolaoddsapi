# Desenvolvimento

> **Papel deste arquivo:** como rodar os testes e o que cada classe de teste cobre.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Testes

**44 classes de teste — 761 cenários**, cobrindo serviços, controllers, segurança, domínio,
utilitários e endpoints de observabilidade. Os testes usam migrations Flyway próprias em
`src/test/resources/db/migration/h2`, equivalentes às de produção e ajustadas para a sintaxe do H2.

### Executar

```bash
# Todos os testes
mvn test

# Apenas uma classe
mvn test -Dtest=ScoreServiceTest

# Com relatório de cobertura (requer JaCoCo no pom.xml)
mvn test jacoco:report
```

### Cobertura por classe

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

### Saída esperada

```
[INFO] Results:
[INFO]
[INFO] Tests run: 761, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

---

## Ver também

- [`arquitetura.md`](arquitetura.md) — as camadas que os testes cobrem
- [`seguranca.md`](seguranca.md) — a matriz que o `PoliticaAcessoIntegrationTest` percorre
- [`operacao.md`](operacao.md) — os artefatos que o `ArtefatosObservabilidadeTest` amarra
