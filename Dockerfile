FROM openjdk:22
WORKDIR /app
COPY ./nowhere-0.0.1-SNAPSHOT.jar /app
EXPOSE 8080
CMD ["java", "-jar", "nowhere-0.0.1-SNAPSHOT.jar"]