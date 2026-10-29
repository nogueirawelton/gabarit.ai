# syntax=docker/dockerfile:1

ARG JDK_IMAGE=eclipse-temurin:25-jdk
ARG JRE_IMAGE=eclipse-temurin:25-jre

# ==========================================
# DEPS: baixa as dependências do pom (camada só muda quando o pom muda)
# ==========================================
FROM ${JDK_IMAGE} AS deps
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline

# ==========================================
# DEV: JDK + deps; código entra via bind mount (src e pom), target/ fica dentro do container
# Mudou o código? `make restart s=core` recompila. Ciclo rápido: rode pela IDE no host.
# ==========================================
FROM deps AS dev
CMD ["./mvnw", "-q", "-B", "spring-boot:run"]

# ==========================================
# BUILD: empacota e separa o jar em camadas (deps mudam pouco, código muda sempre)
# ==========================================
FROM deps AS build
COPY src src
RUN ./mvnw -q -B package -DskipTests \
    && java -Djarmode=tools -jar target/core.jar extract --layers --destination /extracted

# ==========================================
# PROD: só JRE + camadas do jar, sem root
# ==========================================
FROM ${JRE_IMAGE} AS prod
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
# uma camada por grupo, da que muda menos para a que muda mais
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
EXPOSE 8080
# heap = 60% do limite de memória do container (docker-compose.prod.yml); o resto é metaspace,
# threads e buffers. Sem limite no container, a JVM calcularia sobre a RAM do servidor inteiro
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "core.jar"]
