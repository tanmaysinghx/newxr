# syntax=docker/dockerfile:1
# Newxr in one image: Angular console + Spring Boot server. Data lives in /data (mount a volume).
#   docker build -t newxr .
#   docker run -p 8080:8080 -v newxr-data:/data newxr

FROM node:22-alpine AS frontend
WORKDIR /src/newxr-frontend
COPY newxr-frontend/package.json newxr-frontend/package-lock.json ./
RUN npm ci
COPY newxr-frontend/ ./
RUN npx ng build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src/newxr-backend
COPY newxr-backend/gradlew newxr-backend/settings.gradle newxr-backend/build.gradle ./
COPY newxr-backend/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY newxr-backend/src ./src
COPY --from=frontend /src/newxr-frontend/dist /src/newxr-frontend/dist
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && groupadd --system newxr && useradd --system --gid newxr --home-dir /data --shell /usr/sbin/nologin newxr \
    && mkdir -p /data /opt/newxr && chown newxr:newxr /data
COPY --from=backend /src/newxr-backend/build/libs/newxr.jar /opt/newxr/newxr.jar
ENV NEWXR_HOME=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
USER newxr
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/newxr/newxr.jar --httpPort=8080 \"$@\"", "--"]
