FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY server-mqtt-quic/build/libs/server-mqtt-quic-1.0.0.jar /app.jar
EXPOSE 8083
EXPOSE 1885/udp
ENTRYPOINT ["java", "-jar", "/app.jar"]
