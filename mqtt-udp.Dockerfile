FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY server-mqtt-udp/build/libs/server-mqtt-udp-1.0.0.jar /app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app.jar"]