Description

Story 3.1 — Author Dockerfile for chat-backend
Subtask 3.1.1 Multi-stage:

Stage 1 eclipse-temurin:21-jdk-alpine — ./mvnw -B -DskipTests package and java -Djarmode=layertools -jar app.jar extract.

Stage 2 eclipse-temurin:21-jre-alpine (or distroless gcr.io/distroless/java21) — copy layered dirs: dependencies/, spring-boot-loader/, snapshot-dependencies/, application/.

Subtask 3.1.2 Security hardening:

USER 1001:1001 non-root.

HEALTHCHECK hitting /actuator/health/liveness.

Read-only root FS; only /tmp writable.

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}","org.springframework.boot.loader.launch.JarLauncher"].

No secrets baked in; consume via env/K8s Secret.

Subtask 3.1.3 Add .dockerignore (target/, .git, .log, keys/, hs_err_.log).

Subtask 3.1.4 Image size target < 250 MB; verify with docker images.

Subtask 3.1.5 Add Trivy scan stage in Jenkins; fail on HIGH/CRITICAL.

Acceptance: Image builds < 3 min on warm cache, runs as non-root, passes Trivy, starts in < 30 s.