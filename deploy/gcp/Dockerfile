FROM --platform=$BUILDPLATFORM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS backend-build
WORKDIR /workspace/backend

COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY backend/src src
COPY --from=frontend-build /workspace/frontend/dist/ src/main/resources/static/
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ARG APP_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="Value Investing Support" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="https://github.com/marcello-mazzoni/ValueInvestingSupport"

RUN groupadd --system --gid 10001 vis \
    && useradd --system --uid 10001 --gid vis --home-dir /app --shell /usr/sbin/nologin vis

COPY --from=backend-build --chown=vis:vis /workspace/backend/target/*.jar app.jar

USER 10001:10001

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError" \
    SERVER_SHUTDOWN=graceful

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

