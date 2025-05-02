FROM openjdk:17

WORKDIR /app

COPY Dispositivo.java .

RUN javac Dispositivo.java

EXPOSE 5000/udp

CMD ["java", "Dispositivo"]