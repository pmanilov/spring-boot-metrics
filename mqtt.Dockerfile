FROM ubuntu
ENV TZ=Asia/Kolkata
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update -y && apt-get upgrade -y
RUN apt-get install tzdata
RUN apt-get install -y libpcap-dev
RUN apt install -y openjdk-21-jdk
RUN mkdir /app
WORKDIR /app
COPY server-mqtt/build/libs/server-mqtt-1.0.0.jar /app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]