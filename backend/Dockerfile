# Main Responsibility: Multi-stage Docker image for the Spring Boot backend.
# Stage 1 builds the JAR with Maven; stage 2 runs it on a slim JRE 21 image.
# Exposes port 8080 (same as application.yml / docker-compose).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Skip tests in the image build to keep CI/local image builds fast.
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
