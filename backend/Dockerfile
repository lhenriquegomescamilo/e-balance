# Runtime-only image — build the fat JAR on the host first:
#   cd backend && ./gradlew :server:buildFatJar --no-daemon
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY server/build/libs/server-all.jar app.jar
COPY server/src/main/resources/db/migration /app/db/migration
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
