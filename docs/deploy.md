# Deploy e Execução

> **Papel deste arquivo:** Docker, Compose, healthcheck e limites de recurso.
>
> Índice da documentação: [`docs/README.md`](README.md) · Como subir o projeto: [README](../README.md)

---

## Docker

### Arquivos

| Arquivo | Descrição |
|---|---|
| `Dockerfile` | Build multi-stage: stage `build` (JDK 21 Alpine) + stage `runtime` (JRE 21 Alpine) |
| `docker-compose.yml` | Orquestração com variáveis de ambiente, healthcheck e resource limits |
| `.env.example` | Template de variáveis — copiar para `.env` antes de usar |
| `.dockerignore` | Exclui `target/`, `src/test/`, `docs/` e arquivos de IDE do contexto |
| `application.properties` | Lê variáveis de ambiente com fallback para valores padrão (ver [4.2](configuracao.md#variáveis-de-ambiente)) |

### Dockerfile — Multi-stage Build

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

### Comandos

O início rápido está no [README](../README.md#início-rápido-com-docker). Os demais comandos:

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

# Executar sem Compose (passando variáveis diretamente)
docker run -p 8080:8080 \
  -e ODDS_API_KEY=sua_chave \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/cartola_odds \
  -e SPRING_DATASOURCE_USERNAME=cartola \
  -e SPRING_DATASOURCE_PASSWORD=cartola \
  cartola-odds:1.0.0
```

### Resource Limits (docker-compose.yml)

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

### Healthcheck

O container verifica automaticamente se a aplicação está respondendo a cada 30 segundos:

```
GET http://localhost:8080/v3/api-docs → 200 OK = healthy
```

`start_period: 60s` — aguarda a JVM e o PostgreSQL inicializarem antes de começar as verificações.

---
