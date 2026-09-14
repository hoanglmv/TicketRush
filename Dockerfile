# ========== Build stage ==========
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Cache dependencies
RUN ./gradlew dependencies --no-daemon || true

COPY backend/ backend/
RUN ./gradlew bootJar --no-daemon -x test

# ========== Run stage ==========
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
