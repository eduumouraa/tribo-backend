# ══════════════════════════════════════════════════════
# Dockerfile — Tribo Invest Play Backend
# Multi-stage build: compila com JDK 21, roda com JRE 21
# ══════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copia o pom.xml primeiro (camada de dependências — cache do Docker)
COPY pom.xml .

# Baixa dependências sem compilar o código (maximiza cache)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B

# Copia o restante do código-fonte
COPY src ./src

# Compila e empacota (sem rodar testes — testes rodam no CI)
RUN mvn package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuário não-root por segurança
RUN addgroup -S tribo && adduser -S tribo -G tribo

# Copia apenas o JAR compilado do stage anterior
COPY --from=builder /app/target/*.jar app.jar

# Define o usuário de runtime
USER tribo

# Porta padrão da aplicação (configurada em application.yml)
EXPOSE 8081

# JVM flags otimizados para containers:
#   -XX:+UseContainerSupport       : detecta CPU/RAM do container (não do host)
#   -XX:MaxRAMPercentage=75.0      : usa até 75% da RAM do container
#   -Djava.security.egd=...        : entropia mais rápida para UUID/JWT
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
