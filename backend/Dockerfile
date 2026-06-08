# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
RUN mvn -q -DskipTests dependency:go-offline

COPY backend/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && install -d -m 0755 /usr/share/postgresql-common/pgdg \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
        | gpg --dearmor -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg \
    && . /etc/os-release \
    && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.gpg] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" \
        > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client-16 \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --home-dir /app voyager
COPY --from=build /workspace/target/*.jar /app/app.jar
RUN mkdir -p /app/data/backups && chown -R voyager:voyager /app

USER voyager
EXPOSE 18080

ENV SERVER_ADDRESS=0.0.0.0
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
