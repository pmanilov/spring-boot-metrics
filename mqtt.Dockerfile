FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY server-mqtt/build/libs/server-mqtt-1.0.0.jar /app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]