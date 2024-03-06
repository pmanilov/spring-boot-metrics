FROM ubuntu
ENV TZ=Asia/Kolkata
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update -y && apt-get upgrade -y
RUN apt-get install tzdata
RUN apt-get install -y libpcap-dev
RUN apt install -y openjdk-17-jdk
RUN mkdir /app
WORKDIR /app
COPY /server/build/libs/metrics-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]