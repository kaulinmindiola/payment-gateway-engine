# syntax=docker/dockerfile:1

# ---------- Etapa 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuario sin privilegios: el proceso no corre como root dentro del contenedor.
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /workspace/target/payment-gateway-engine-*.jar app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage: la JVM dimensiona el heap según el límite de memoria del contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]