# Build multi-stage do SINAL (Spring Boot, Java 17) para o Render.

# 1) Build: compila e empacota o .jar com Maven. Os testes ficam de fora porque dependem de Docker
#    (Testcontainers); eles rodam localmente/CI antes do deploy.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# 2) Execução: só o JRE e o .jar, com usuário sem privilégios.
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 sinal
COPY --from=build /app/target/sinal-0.0.1-SNAPSHOT.jar app.jar
USER sinal

# O Render informa a porta em PORT; fora dele vale 8080.
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT} -jar app.jar"]
