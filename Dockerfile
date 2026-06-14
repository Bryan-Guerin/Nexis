# syntax=docker/dockerfile:1

# ─────────────────────────────────────────────────────────────────────────────
# Étape 1 — Build (front Svelte + back Micronaut) → fat jar « all-in-one »
# Le frontend-maven-plugin télécharge Node tout seul et compile le front dans
# nexis-app/src/main/resources/public, qui est ensuite embarqué dans le jar.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Cache des dépendances : on copie d'abord le wrapper + les pom, puis le reste.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY nexis-core/pom.xml        nexis-core/
COPY nexis-security/pom.xml    nexis-security/
COPY nexis-gendarmerie/pom.xml nexis-gendarmerie/
COPY nexis-sapeurs/pom.xml     nexis-sapeurs/
COPY nexis-app/pom.xml         nexis-app/
RUN chmod +x mvnw && ./mvnw -B -ntp -pl nexis-app -am dependency:go-offline -DskipTests -Denforcer.skip=true || true

# Sources
COPY . .
RUN ./mvnw -B -ntp -pl nexis-app -am clean package -DskipTests -Denforcer.skip=true \
 && cp nexis-app/target/nexis-app-*.jar /workspace/app.jar

# ─────────────────────────────────────────────────────────────────────────────
# Étape 2 — Runtime (JRE seul, image légère, user non-root)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl : requis par le HEALTHCHECK (interroge /health).
# /logs : créé et possédé par nexis pour que la trace stdout soit écrivable même
# sans volume monté (en prod, le volume /logs le recouvre, possédé par PUID).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system nexis \
 && useradd --system --gid nexis --home /app nexis \
 && mkdir -p /logs && chown nexis:nexis /logs
COPY --from=build /workspace/app.jar /app/app.jar
USER nexis

EXPOSE 8080
ENV JAVA_OPTS=""

# /health renvoie 200 (UP) / 503 (DOWN, ex: DB injoignable) → curl -f échoue si DOWN.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/health || exit 1

# Trace complète : tout le stdout/stderr (banner JVM, attach de l'agent Glowroot,
# démarrage Micronaut) est affiché (docker logs) ET copié dans ${LOG_DIR}/nexis-stdout.log.
# bash requis pour la substitution de processus ; exec → java reçoit bien les signaux.
ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar > >(tee -a \"${LOG_DIR:-/logs}/nexis-stdout.log\") 2>&1"]
