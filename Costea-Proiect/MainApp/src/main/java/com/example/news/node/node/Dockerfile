# Folosește imaginea oficială OpenJDK
FROM openjdk:17-jdk-slim

# Setăm un director de lucru
WORKDIR /app

# Copiem JAR-ul construit de Maven (jar-with-dependencies)
# Presupunem că se numește distributed-news-system-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY target/distributed-news-system-1.0-SNAPSHOT-jar-with-dependencies.jar /app/node.jar

# Expunem portul "principal" (5000) pe care nodul ascultă cererile
# + portul bully (de ex. 6000) dacă vrei să-l mapezi
EXPOSE 5000
EXPOSE 6000

# Comanda de start.
# Exemplu: rulăm NodeMain cu parametrii ID și port.
# Ajustezi conform cum e configurat.
CMD ["java", "-cp", "node.jar", "com.example.news.node.NodeMain",
     "--id=1",
     "--port=5000"]
