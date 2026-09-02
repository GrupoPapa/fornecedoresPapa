# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# baixa as dependencias primeiro para aproveitar o cache do Docker
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 -m cfp

ENV TZ=America/Sao_Paulo \
    JAVA_OPTS="-Xms256m -Xmx768m"

COPY --from=build /app/target/*.jar /app/app.jar
RUN chown -R cfp:cfp /app
USER cfp

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
