FROM node:20-alpine AS frontend

WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend

WORKDIR /src
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY --from=frontend /src/frontend/dist backend/src/main/resources/static
RUN mvn -B -ntp -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=backend /src/backend/target/ir-backend-1.0.0.jar /app/app.jar

EXPOSE 8090
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
