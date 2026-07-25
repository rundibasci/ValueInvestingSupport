# K1 Cloud Run — build from repository root (Cloud Build)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
RUN sed -i 's/\r$//' backend/mvnw && chmod +x backend/mvnw
RUN cd backend && ./mvnw -q -DskipTests dependency:go-offline

COPY backend/src backend/src
RUN cd backend && ./mvnw -q -DskipTests package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
