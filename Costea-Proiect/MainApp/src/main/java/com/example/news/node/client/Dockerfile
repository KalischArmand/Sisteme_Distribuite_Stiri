# Folosește imaginea oficială OpenJDK
FROM openjdk:17-jdk-slim

# Director de lucru
WORKDIR /app

# Copiem JAR-ul din target
COPY target/distributed-news-system-1.0-SNAPSHOT-jar-with-dependencies.jar /app/client.jar

# De obicei clientul nu expune port
# EXPOSE ???

# Comanda de start
CMD ["java", "-cp", "client.jar", "com.example.news.client.ClientMain"]
