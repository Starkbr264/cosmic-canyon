# syntax=docker/dockerfile:1

# ---- Build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Camada de dependencias (cacheada enquanto o pom nao muda)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Codigo-fonte e testes
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Runtime --------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# usuario nao-root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /build/target/tradewind-backend.jar app.jar

EXPOSE 8080

# APP_VERSION e GIT_COMMIT alimentam /actuator/info, o que permite confirmar
# qual build esta no ar sem precisar entrar no container.
ARG APP_VERSION=dev
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC" \
    SPRING_PROFILES_ACTIVE=local \
    APP_VERSION=${APP_VERSION} \
    TZ=UTC

# O container nao traz curl; wget vem do busybox e ja esta na imagem.
# 127.0.0.1 em vez de localhost porque o nome resolve primeiro para ::1.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
