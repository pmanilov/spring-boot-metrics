FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY server-mqtt-quic/build/libs/server-mqtt-quic-1.0.0.jar /app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app.jar"]
