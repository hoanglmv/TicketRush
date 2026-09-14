# ========== Build stage ==========
FROM gradle:jdk21 AS builder

USER root
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY backend/ backend/

RUN gradle bootJar --no-daemon -x test

# ========== Run stage ==========
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

