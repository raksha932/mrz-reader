FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
COPY mrtd-core/pom.xml mrtd-core/pom.xml
COPY mrtd-api/pom.xml mrtd-api/pom.xml

RUN mvn -pl mrtd-api -am dependency:go-offline

COPY mrtd-core/src mrtd-core/src
COPY mrtd-api/src mrtd-api/src

RUN mvn -pl mrtd-api -am package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system mrtd \
    && adduser --system --ingroup mrtd mrtd

COPY --from=build /workspace/mrtd-api/target/mrtd-api-*.jar app.jar

USER mrtd

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
