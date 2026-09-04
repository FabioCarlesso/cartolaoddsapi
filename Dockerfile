# ══════════════════════════════════════════════════════════════════════
# Stage 1 — Build
# ══════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Copia descritores de dependência primeiro para aproveitar o cache de camadas.
# Se apenas o código fonte mudar, o Maven não redownload as dependências.
COPY pom.xml .
COPY src ./src

RUN ./mvnw clean package -DskipTests 2>/dev/null || \
    (apt-get update -q && apt-get install -y --no-install-recommends maven && mvn clean package -DskipTests)

# ══════════════════════════════════════════════════════════════════════
# Stage 2 — Runtime
# ══════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="cartola-odds" \
      org.opencontainers.image.description="API REST de montagem automatica de time do Cartola FC baseada em odds" \
      org.opencontainers.image.version="1.0.0"

# Cria usuario nao-root para executar a aplicacao (boas praticas de seguranca)
RUN groupadd -r cartola && useradd -r -g cartola cartola

WORKDIR /app

# Copia apenas o JAR gerado no stage de build
COPY --from=build /workspace/target/cartola-odds-*.jar app.jar

# Ajusta permissoes
RUN chown cartola:cartola app.jar

USER cartola

# Porta unica: aplicacao e Actuator compartilham a mesma, como exige a
# plataforma de deploy. Quem protege /actuator/metrics e /actuator/prometheus
# e a regra de ADMIN no SecurityConfig, nao a separacao de portas.
EXPOSE 8080

# Healthcheck — /actuator/health e publico de proposito, para a plataforma
# poder consultar sem token
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health 2>/dev/null || exit 1

# Configuracoes recomendadas de JVM para containers:
#   -XX:+UseContainerSupport     respeita os limites de CPU/memória do container
#   -XX:MaxRAMPercentage=75.0    usa até 75% da RAM disponível para o heap
#   -Djava.security.egd          inicialização mais rápida do SecureRandom
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
