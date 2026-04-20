# ══════════════════════════════════════════════════════════════════════
# Stage 1 — Build
# ══════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copia descritores de dependência primeiro para aproveitar o cache de camadas.
# Se apenas o código fonte mudar, o Maven não redownload as dependências.
COPY pom.xml .
COPY src ./src

RUN ./mvnw clean package -DskipTests 2>/dev/null || \
    (apk add --no-cache maven && mvn clean package -DskipTests)

# ══════════════════════════════════════════════════════════════════════
# Stage 2 — Runtime
# ══════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="cartola-odds" \
      org.opencontainers.image.description="API REST de montagem automatica de time do Cartola FC baseada em odds" \
      org.opencontainers.image.version="1.0.0"

# Cria usuario nao-root para executar a aplicacao (boas praticas de seguranca)
RUN addgroup -S cartola && adduser -S cartola -G cartola

WORKDIR /app

# Copia apenas o JAR gerado no stage de build
COPY --from=build /workspace/target/cartola-odds-*.jar app.jar

# Ajusta permissoes
RUN chown cartola:cartola app.jar

USER cartola

# Porta padrao da aplicacao
EXPOSE 8080

# Healthcheck — verifica o endpoint de status do mercado como proxy de saude
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health 2>/dev/null || \
      wget -qO- http://localhost:8080/v3/api-docs 2>/dev/null || exit 1

# Configuracoes recomendadas de JVM para containers:
#   -XX:+UseContainerSupport     respeita os limites de CPU/memória do container
#   -XX:MaxRAMPercentage=75.0    usa até 75% da RAM disponível para o heap
#   -Djava.security.egd          inicialização mais rápida do SecureRandom
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
