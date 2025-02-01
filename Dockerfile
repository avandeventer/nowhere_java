FROM openjdk:22
WORKDIR /app
COPY build/libs/nowhere-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]