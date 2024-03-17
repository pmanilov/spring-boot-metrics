FROM ubuntu
ENV TZ=Asia/Kolkata
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update -y && apt-get upgrade -y
RUN apt-get install tzdata
RUN apt-get install -y libpcap-dev
RUN apt install -y openjdk-17-jdk
RUN mkdir /app
WORKDIR /app
COPY /server-coap/build/libs/server-coap-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8082
EXPOSE 5683
ENTRYPOINT ["java", "-jar", "/app.jar"]