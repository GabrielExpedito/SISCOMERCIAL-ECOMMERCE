# Build do frontend React/Vite
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Build do backend Spring Boot e incorpora os arquivos estáticos do React
FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /app/backend
COPY backend/ ./
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/
RUN mvn -B clean package -DskipTests

# Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S siscomercial && adduser -S siscomercial -G siscomercial
COPY --from=backend-build /app/backend/target/*.jar /app/app.jar
RUN mkdir -p /var/data/uploads && chown -R siscomercial:siscomercial /app /var/data/uploads
USER siscomercial
EXPOSE 10000
ENV PORT=10000
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-10000} -Dspring.profiles.active=prod -jar /app/app.jar"]
