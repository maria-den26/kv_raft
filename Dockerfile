FROM maven:3.9-eclipse-temurin-11 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:11-jre

WORKDIR /app
COPY --from=build /app/target/kv_raft-1.0-SNAPSHOT.jar app.jar

EXPOSE 9001 9002 9003

ENTRYPOINT ["java", "-jar", "app.jar"]






