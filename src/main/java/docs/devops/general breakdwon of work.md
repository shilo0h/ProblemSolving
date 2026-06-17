# Deployment Modernization — Epic Breakdown & Tickets

**Project:** On-Prem Infrastructure Modernization  
**Environments:** DEV · QA · PROD (3 Linux VMs)  
**Stack:** Spring Boot · Kafka · PostgreSQL · Jenkins · Kubernetes  
**Author:** Software Architecture Team

---

## EPIC MAP

| # | Epic | Priority | Est. Effort |
|---|------|----------|-------------|
| EP-01 | Artifactory Setup (On-Prem) | P0 | 3–5 days |
| EP-02 | Docker Registry (On-Prem) | P0 | 2–3 days |
| EP-03 | Spring Boot Dockerfile | P1 | 2–3 days |
| EP-04 | Kubernetes Templates & RBAC | P1 | 3–4 days |
| EP-05 | Jenkins Infrastructure Audit | P1 | 2–3 days |
| EP-06 | PostgreSQL & Kafka Audit + Config | P1 | 3–5 days |
| EP-07 | Kubernetes Cluster Audit | P1 | 2–3 days |
| EP-08 | Advanced Jenkins Pipeline | P2 | 5–7 days |

---

## EP-01 · Artifactory Setup (On-Prem)

**Goal:** Deploy JFrog Artifactory OSS or Pro on-prem to serve as the central binary repository for Maven/Gradle artifacts, Docker images, and Helm charts.

---

### TICKET-01-01 · Provision Artifactory Server

**Type:** Infrastructure  
**Assignee:** DevOps Engineer  
**Depends on:** Nothing

**Background:**  
Artifactory will act as the single source of truth for all build artifacts. It must be reachable from Jenkins (build agent), Kubernetes nodes (image pull), and developer workstations.

**Acceptance Criteria:**
- Artifactory is installed and running as a system service (systemd)
- Accessible via internal hostname `artifactory.internal` on port `8081` (UI) and `8082` (artifact traffic)
- Health endpoint returns HTTP 200: `GET /artifactory/api/system/ping`
- Service auto-starts on VM reboot

**Implementation Notes:**
```bash
# Recommended: Docker-compose install on the build VM (not a K8s node)
# Minimum system requirements for OSS:
#   CPU: 4 cores | RAM: 8 GB | Disk: 200 GB (SSD preferred)

wget https://releases.jfrog.io/artifactory/artifactory-pro/org/artifactory/pro/jfrog-artifactory-pro/[VERSION]/jfrog-artifactory-pro-[VERSION]-linux.tar.gz

# Or via docker-compose (preferred for reproducibility):
version: "3.8"
services:
  artifactory:
    image: releases-docker.jfrog.io/jfrog/artifactory-oss:latest
    ports:
      - "8081:8081"
      - "8082:8082"
    volumes:
      - artifactory_data:/var/opt/jfrog/artifactory
    restart: always
```

**Notes:**
- Use OSS if budget is a constraint; Pro adds SAML SSO, advanced replication
- Mount data volume to a dedicated disk partition, not the OS disk
- Firewall: open 8081/8082 only to Jenkins VM and K8s node IPs

---

### TICKET-01-02 · Configure Repositories in Artifactory

**Type:** Configuration  
**Assignee:** DevOps Engineer  
**Depends on:** TICKET-01-01

**Acceptance Criteria:**
- Local repo created: `libs-release-local` (Maven releases)
- Local repo created: `libs-snapshot-local` (Maven snapshots)
- Remote repo created: `libs-remote` pointing to Maven Central (proxy cache)
- Virtual repo created: `libs-virtual` aggregating local + remote
- Docker local repo created: `docker-local`
- Helm local repo created: `helm-local` (for future use)

**Implementation Notes:**
- Set retention policy on snapshots: keep last 5 builds per artifact
- Enable checksum verification on all repos
- Create service account `ci-user` with deploy permissions on local repos, read-only on remote

---

### TICKET-01-03 · Integrate Maven/Gradle with Artifactory

**Type:** Development  
**Assignee:** Backend Developer  
**Depends on:** TICKET-01-02

**Acceptance Criteria:**
- Spring Boot project resolves dependencies from Artifactory virtual repo
- `mvn deploy` or `./gradlew publish` pushes JARs to `libs-release-local`
- CI pipeline uses Artifactory credentials from Jenkins credentials store (not hardcoded)

**Implementation Notes:**  
For Maven (`settings.xml` on Jenkins agent):
```xml
<settings>
  <servers>
    <server>
      <id>artifactory-releases</id>
      <username>${ARTIFACTORY_USER}</username>
      <password>${ARTIFACTORY_PASS}</password>
    </server>
  </servers>
  <mirrors>
    <mirror>
      <id>artifactory-virtual</id>
      <url>http://artifactory.internal:8082/artifactory/libs-virtual</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

---

## EP-02 · Docker Registry (On-Prem)

**Goal:** Stand up a private Docker registry to store and distribute container images across DEV/QA/PROD environments, acting as a backup and air-gap fallback independent of Docker Hub.

---

### TICKET-02-01 · Deploy Docker Registry (Harbor or Registry v2)

**Type:** Infrastructure  
**Assignee:** DevOps Engineer  
**Depends on:** Nothing (can run in parallel with EP-01)

**Background:**  
Two options: Docker Registry v2 (lightweight, no UI) or Harbor (full-featured, RBAC, image scanning, UI). Harbor is strongly recommended for production-grade on-prem use.

**Acceptance Criteria:**
- Registry reachable at `registry.internal:443` (HTTPS with self-signed or internal CA cert)
- `docker login registry.internal` succeeds from Jenkins VM and all K8s nodes
- `docker push registry.internal/backend/spring-app:1.0.0` succeeds
- `docker pull registry.internal/backend/spring-app:1.0.0` succeeds from all K8s nodes

**Implementation Notes — Harbor (recommended):**
```bash
# Download installer
wget https://github.com/goharbor/harbor/releases/download/v2.x.x/harbor-online-installer-v2.x.x.tgz
tar xzvf harbor-online-installer-v2.x.x.tgz

# Edit harbor.yml:
hostname: registry.internal
https:
  port: 443
  certificate: /certs/registry.crt
  private_key: /certs/registry.key
data_volume: /data/harbor   # mount dedicated disk here

./install.sh --with-trivy   # Trivy = built-in image scanner
```

**Notes:**
- Trivy scanning can block pushes of images with CRITICAL CVEs — configure threshold per environment
- Enable garbage collection schedule (weekly) to reclaim disk from deleted layers
- Minimum resources: 4 CPU, 8 GB RAM, 100 GB disk

---

### TICKET-02-02 · Configure Kubernetes Nodes to Trust Registry

**Type:** Infrastructure  
**Assignee:** DevOps Engineer  
**Depends on:** TICKET-02-01

**Acceptance Criteria:**
- All K8s nodes can pull from `registry.internal` without TLS errors
- `imagePullSecret` created in each namespace
- containerd configured to use registry mirror

**Implementation Notes:**
```bash
# On each K8s node — trust the internal CA cert
cp registry-ca.crt /usr/local/share/ca-certificates/registry-internal.crt
update-ca-certificates
systemctl restart containerd

# Create K8s pull secret (run per namespace):
kubectl create secret docker-registry regcred \
  --docker-server=registry.internal \
  --docker-username=ci-user \
  --docker-password=<PASS> \
  --namespace=<NAMESPACE>
```

---

### TICKET-02-03 · Image Lifecycle and Retention Policy

**Type:** Configuration  
**Assignee:** DevOps Engineer  
**Depends on:** TICKET-02-01

**Acceptance Criteria:**
- DEV images: retain last 10 tags per repo
- QA images: retain last 20 tags per repo
- PROD images: retain all tags (never auto-delete; require manual cleanup approval)
- Image scan report generated on every push; results visible in Harbor UI

---

## EP-03 · Spring Boot Dockerfile (Multistage, Secure, Thin)

**Goal:** Build a production-grade Dockerfile that produces the smallest possible, non-root image using multistage builds and follows security hardening best practices.

---

### TICKET-03-01 · Create Multistage Dockerfile for Spring Boot

**Type:** Development  
**Assignee:** Backend Developer  
**Depends on:** EP-02 (registry must exist to push to)

**Acceptance Criteria:**
- Final image size ≤ 250 MB (baseline without multistage is typically 400–600 MB)
- Application runs as a non-root user (`uid=1000`)
- No build tools (Maven/Gradle, JDK) present in the final image — JRE only
- Image passes Trivy scan with zero CRITICAL vulnerabilities
- `HEALTHCHECK` directive defined
- Build args used for environment-specific config (no secrets baked in)

**Implementation — Dockerfile:**
```dockerfile
# ─── STAGE 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Dependency layer cache — copy POM/Gradle files first
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B

# Source copy and build
COPY src ./src
RUN ./mvnw package -DskipTests -B \
    && java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ─── STAGE 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered JAR (improves image cache reuse across builds)
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/extracted/application/ ./

# No shell in runtime (reduces attack surface)
RUN apk del --no-cache apk-tools

USER appuser

# Build-time args — injected by CI
ARG APP_VERSION=unknown
ARG BUILD_DATE=unknown
ARG GIT_COMMIT=unknown

LABEL org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${GIT_COMMIT}"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
```

**JVM Flags Rationale:**
- `MaxRAMPercentage=75.0` — respects container memory limits (avoids OOM kills)
- `UseContainerSupport` — reads cgroup limits instead of host RAM
- `ExitOnOutOfMemoryError` — lets K8s restart the pod instead of hanging in bad state
- `urandom` — faster startup entropy

---

### TICKET-03-02 · Externalize Configuration and Secrets

**Type:** Development  
**Assignee:** Backend Developer  
**Depends on:** TICKET-03-01

**Acceptance Criteria:**
- No credentials, DB URLs, or Kafka broker addresses hardcoded in image
- Application reads config from environment variables (mapped from K8s ConfigMaps and Secrets)
- `application.yml` uses `${ENV_VAR:default}` pattern for all environment-specific values

**Implementation Notes:**
```yaml
# application.yml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/appdb}
    username: ${DB_USER:app}
    password: ${DB_PASS}   # No default — must be injected
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
```
K8s Secret for DB_PASS:
```bash
kubectl create secret generic db-credentials \
  --from-literal=DB_PASS='<password>' \
  --namespace=backend
```

---

## EP-04 · Kubernetes Templates & RBAC

**Goal:** Create a complete, reusable set of Kubernetes manifests (Namespace, RBAC, Deployment, Service, ConfigMap, HPA, NetworkPolicy) for the Spring Boot backend with proper least-privilege permissions.

---

### TICKET-04-01 · Namespace and RBAC Definition

**Type:** Infrastructure  
**Assignee:** Platform Engineer  
**Depends on:** TICKET-02-02

**Acceptance Criteria:**
- Separate namespaces for `backend-dev`, `backend-qa`, `backend-prod`
- ServiceAccount created per namespace for the application pod
- Role with least-privilege permissions (no cluster-admin)
- CI/CD service account created separately with deploy-only permissions

**Implementation:**
```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: backend-prod
  labels:
    env: production
    team: backend
---
# serviceaccount.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: backend-app-sa
  namespace: backend-prod
  annotations:
    description: "Runtime SA for backend Spring Boot pod"
automountServiceAccountToken: false  # Disable unless needed
---
# role.yaml — minimal permissions
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: backend-app-role
  namespace: backend-prod
rules:
  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["get", "list", "watch"]   # read-only config access
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get"]                    # read-only secret access
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: backend-app-rolebinding
  namespace: backend-prod
subjects:
  - kind: ServiceAccount
    name: backend-app-sa
    namespace: backend-prod
roleRef:
  kind: Role
  apiRef: rbac.authorization.k8s.io/v1
  name: backend-app-role
---
# CI/CD deploy role (for Jenkins)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: cicd-deploy-role
  namespace: backend-prod
rules:
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "create", "update", "patch"]
  - apiGroups: [""]
    resources: ["services", "configmaps"]
    verbs: ["get", "list", "create", "update", "patch"]
```

---

### TICKET-04-02 · Deployment, Service, and HPA Manifests

**Type:** Infrastructure  
**Assignee:** Platform Engineer  
**Depends on:** TICKET-04-01, TICKET-03-01

**Acceptance Criteria:**
- Deployment uses rolling update strategy with `maxUnavailable: 0`
- Resource requests and limits defined (no burstable/BestEffort pods in prod)
- Liveness and readiness probes configured
- HPA scales between 2–8 replicas based on CPU (70%) and memory (80%)
- NetworkPolicy limits ingress to only the ingress controller

**Implementation:**
```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-app
  namespace: backend-prod
  labels:
    app: backend-app
    version: "{{ IMAGE_TAG }}"   # replaced by Jenkins
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: backend-app
  template:
    metadata:
      labels:
        app: backend-app
    spec:
      serviceAccountName: backend-app-sa
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: backend-app
          image: "registry.internal/backend/spring-app:{{ IMAGE_TAG }}"
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: backend-config
          env:
            - name: DB_PASS
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: DB_PASS
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 3
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
      imagePullSecrets:
        - name: regcred
---
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: backend-app-svc
  namespace: backend-prod
spec:
  selector:
    app: backend-app
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP
---
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-app-hpa
  namespace: backend-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend-app
  minReplicas: 2
  maxReplicas: 8
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
---
# networkpolicy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: backend-app-netpol
  namespace: backend-prod
spec:
  podSelector:
    matchLabels:
      app: backend-app
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: ingress-nginx
  egress:
    - {}   # Allow all egress (DB, Kafka) — tighten per environment
```

---

## EP-05 · Jenkins Infrastructure Audit

**Goal:** Audit the existing Jenkins installation on the Linux VM, determine if master/agent configuration is appropriate, identify performance limits, and document required changes.

---

### TICKET-05-01 · Jenkins Installation Audit Checklist

**Type:** Investigation  
**Assignee:** DevOps Engineer  
**Depends on:** Nothing

**Acceptance Criteria:**
- Audit report produced covering all items below
- Recommendations documented with priority

**Audit Checklist — run on Jenkins VM:**
```bash
# 1. Jenkins version and plugin list
java -jar /path/to/jenkins-cli.jar -s http://localhost:8080 list-plugins | tee jenkins-plugins.txt

# 2. Java version (Jenkins 2.400+ requires Java 17)
java -version

# 3. Current executor configuration
# In UI: Manage Jenkins > Nodes > Built-In Node > # of executors
# Recommendation: set built-in node executors to 0 (run nothing on master)

# 4. Heap allocation
grep -i "JAVA_OPTS\|JENKINS_JAVA_OPTS" /etc/default/jenkins /etc/sysconfig/jenkins 2>/dev/null

# 5. Disk usage
df -h /var/lib/jenkins

# 6. Current build queue and executor load
curl -s http://localhost:8080/api/json?tree=executors | python3 -m json.tool
```

**Master vs. Agent Architecture Decision Matrix:**

| Scenario | Recommendation |
|----------|---------------|
| < 5 concurrent builds, single team | Single master, 2–4 executors OK |
| 5–20 concurrent builds | Master + 1 static agent minimum |
| 20+ concurrent builds | Master + dynamic agents (K8s plugin) |
| Builds require different environments | Separate agents per environment |
| **Your case (3 env pipelines)** | **Master + K8s-based dynamic agents** |

**Performance Limits for Single-Node Jenkins:**

| Resource | Warning Threshold | Hard Limit (beyond this = degrade) |
|----------|------------------|--------------------------------------|
| Heap memory | 4 GB | 8 GB (GC pauses become severe) |
| Concurrent executors | 4 | 8 (without dedicated agent) |
| Concurrent pipeline builds | 3 | 6 |
| Jobs in queue | 20 | 50+ causes UI slowness |
| Build history (disk) | 50 GB | 100 GB |

**Recommended Jenkins JVM settings for this workload:**
```bash
# /etc/default/jenkins or systemd override
JAVA_OPTS="-Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dhudson.model.DirectoryBrowserSupport.CSP= \
  -Djenkins.install.runSetupWizard=false"
```

---

### TICKET-05-02 · Setup Jenkins Kubernetes Agent Plugin

**Type:** Configuration  
**Assignee:** DevOps Engineer  
**Depends on:** TICKET-05-01, EP-04

**Acceptance Criteria:**
- Jenkins Kubernetes plugin installed and configured
- Pod template defined with Docker-in-Docker (DinD) or Kaniko sidecar for image builds
- Each pipeline stage runs in an ephemeral agent pod that is destroyed after the build
- Jenkins master runs with 0 executors on the built-in node

**Pod Template (Jenkinsfile agent section):**
```groovy
agent {
  kubernetes {
    yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins-agent-sa
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:latest
    resources:
      requests: { cpu: '200m', memory: '256Mi' }
  - name: maven
    image: eclipse-temurin:21-jdk-alpine
    command: ['sleep', '99d']
    resources:
      requests: { cpu: '500m', memory: '1Gi' }
      limits: { cpu: '1', memory: '2Gi' }
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command: ['sleep', '99d']
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
  volumes:
  - name: docker-config
    secret:
      secretName: regcred
"""
  }
}
```

---

## EP-06 · PostgreSQL & Kafka Audit + Configuration

**Goal:** Audit and configure PostgreSQL and Kafka across DEV/QA/PROD for the given load: baseline 3,000 messages/day (~0.035 msg/sec average), peak capacity target of 400 events/second.

---

### TICKET-06-01 · PostgreSQL Audit and Configuration

**Type:** Investigation + Configuration  
**Assignee:** Database Administrator  
**Depends on:** Nothing

**Audit Checklist:**
```bash
# Check version
psql -U postgres -c "SELECT version();"

# Check current connections
psql -U postgres -c "SELECT count(*) FROM pg_stat_activity;"

# Check running queries > 5 seconds
psql -U postgres -c "SELECT pid, now()-query_start as duration, query FROM pg_stat_activity WHERE state='active' AND now()-query_start > interval '5 seconds';"

# Check database sizes
psql -U postgres -c "SELECT datname, pg_size_pretty(pg_database_size(datname)) FROM pg_database;"

# Check missing indexes
psql -U postgres -d <dbname> -c "SELECT schemaname, tablename, attname, n_distinct, correlation FROM pg_stats WHERE tablename='your_table';"
```

**Recommended postgresql.conf changes per environment:**

```ini
# ─── DEV ─────────────────────────────────────────────────────────────────────
max_connections = 50
shared_buffers = 512MB
work_mem = 4MB
maintenance_work_mem = 64MB
wal_level = minimal
checkpoint_completion_target = 0.7
log_min_duration_statement = 500   # log slow queries > 500ms

# ─── QA ──────────────────────────────────────────────────────────────────────
max_connections = 100
shared_buffers = 1GB
work_mem = 8MB
wal_level = replica
log_min_duration_statement = 200

# ─── PROD ────────────────────────────────────────────────────────────────────
max_connections = 200
shared_buffers = 2GB              # 25% of total RAM (8 GB VM)
effective_cache_size = 6GB        # 75% of RAM
work_mem = 16MB
maintenance_work_mem = 256MB
wal_level = replica
wal_buffers = 16MB
checkpoint_completion_target = 0.9
random_page_cost = 1.1            # Assume SSD
max_wal_size = 2GB
min_wal_size = 512MB
log_min_duration_statement = 100
autovacuum = on
autovacuum_vacuum_scale_factor = 0.05
autovacuum_analyze_scale_factor = 0.02
```

**Connection Pooling — PgBouncer (mandatory for prod):**
```ini
# pgbouncer.ini
[databases]
appdb = host=localhost port=5432 dbname=appdb

[pgbouncer]
pool_mode = transaction        # Best for API workloads
max_client_conn = 500
default_pool_size = 20
min_pool_size = 5
reserve_pool_size = 5
server_idle_timeout = 600
```

---

### TICKET-06-02 · Kafka Audit, Sizing, and Configuration

**Type:** Investigation + Configuration  
**Assignee:** Backend Developer + DevOps Engineer  
**Depends on:** Nothing

**Audit Checklist:**
```bash
# Kafka version
kafka-topics.sh --bootstrap-server localhost:9092 --version

# List topics with partition/replication details
kafka-topics.sh --bootstrap-server localhost:9092 --describe

# Consumer group lag (key health metric)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --all-groups --describe

# Check broker disk usage
du -sh /var/kafka/data/*

# Check under-replicated partitions (0 = healthy)
kafka-topics.sh --bootstrap-server localhost:9092 --describe --under-replicated-partitions
```

**Load Analysis:**

| Metric | Value | Notes |
|--------|-------|-------|
| Baseline throughput | 3,000 msg/day | ~0.035 msg/sec avg |
| Peak target | 400 msg/sec | ~11,520x spike ratio |
| Avg message size (assumed) | 1–5 KB | Adjust if larger |
| Peak data rate | ~2 MB/sec | at 5KB × 400 msg/s |
| Daily data volume | ~15 MB/day baseline | Very low |

**Tradeoffs and Architecture Decision:**

> **Your workload is low-volume baseline with extreme spike tolerance required.** This is a burst pattern, not a sustained high-throughput pattern. The implication is: Kafka configuration should prioritize **latency under burst** over **sustained throughput tuning**.

| Configuration Choice | Option A: Minimal (3k/day only) | Option B: Burst-Ready (400 msg/s) | Recommendation |
|---------------------|--------------------------------|----------------------------------|----------------|
| Partitions per topic | 1 | 12–24 | **Option B** |
| Replication factor | 1 (dev/qa), 2 (prod) | 2 (dev/qa), 3 (prod) | **Option B for prod** |
| `num.io.threads` | 4 | 8 | **Option B** |
| `num.network.threads` | 3 | 6 | **Option B** |
| `replica.fetch.max.bytes` | 1MB | 10MB | **Option B** |
| Consumer group size | 1 consumer | 12 consumers (= partitions) | **Option B** |
| `linger.ms` (producer) | 0 | 5–20ms | **5ms** (balance latency vs batching) |
| `batch.size` (producer) | 16KB | 64KB | **64KB** |

**Kafka broker.config for PROD (400 msg/sec target):**
```properties
# broker.properties
num.network.threads=6
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# Log retention — tune per business requirement
log.retention.hours=168         # 7 days
log.segment.bytes=536870912     # 512MB segments
log.retention.check.interval.ms=300000

# Replication
default.replication.factor=3
min.insync.replicas=2           # Require 2/3 replicas ack on write

# Performance for burst
replica.fetch.max.bytes=10485760
num.replica.fetchers=2
```

**Producer config (Spring Boot `application.yml`):**
```yaml
spring:
  kafka:
    producer:
      acks: all                      # Wait for all ISR (durability)
      retries: 3
      batch-size: 65536              # 64KB
      linger-ms: 5                   # Small delay to batch messages
      compression-type: snappy       # ~50% size reduction, low CPU cost
      properties:
        enable.idempotence: true     # Exactly-once producer semantics
        max.in.flight.requests.per.connection: 5
    consumer:
      group-id: backend-consumer-group
      auto-offset-reset: earliest
      enable-auto-commit: false      # Manual commit for reliability
      max-poll-records: 500
      properties:
        fetch.min.bytes: 1024
        fetch.max.wait.ms: 500
```

**Topic Creation (for 400 msg/s target):**
```bash
# 12 partitions allows 12 parallel consumers
# Rule of thumb: partitions = max_consumers = target_throughput / single_partition_throughput
# Single partition safely handles ~40–50 msg/s → 400/40 = 10, use 12 for headroom

kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic backend-events \
  --partitions 12 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2

# Verify
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic backend-events
```

**Tradeoff Summary:**

| Choice | Benefit | Cost |
|--------|---------|------|
| 12 partitions | Horizontal scale to 400 msg/s | More disk I/O, broker CPU |
| RF=3, min.insync=2 | No data loss on 1 broker failure | Write latency +5–15ms |
| `acks=all` | Strongest durability | Higher producer latency |
| `compression=snappy` | ~50% disk/network savings | Small CPU overhead |
| Manual commit | No message loss on consumer crash | Code complexity |
| Idempotent producer | No duplicate messages | Slight throughput limit |

**On 3 VMs with single-broker Kafka:**
> A single-broker Kafka can physically sustain 400 msg/sec at 1–5KB each (~2MB/s) on modern hardware without issue. The bottleneck will be disk write throughput and network, not Kafka itself. Ensure the Kafka data directory (`log.dirs`) is on a separate disk from the OS. Monitor with `kafka-consumer-groups.sh` lag metric — if lag grows during burst, add consumer instances.

---

## EP-07 · Kubernetes Cluster Audit (On-Prem VMs)

**Goal:** Assess the existing K8s cluster state, node capacity, networking, and storage — then produce a gap analysis report against production readiness requirements.

---

### TICKET-07-01 · Kubernetes Cluster Audit Checklist

**Type:** Investigation  
**Assignee:** Platform Engineer  
**Depends on:** Nothing

**Acceptance Criteria:**
- Full audit report produced
- All items below checked and documented
- Remediation items added as follow-up tickets

**Audit Commands:**
```bash
# ─── Cluster version and component health
kubectl version
kubectl get componentstatuses    # deprecated in 1.26+; use below
kubectl get --raw='/readyz?verbose'

# ─── Node status and capacity
kubectl get nodes -o wide
kubectl describe nodes | grep -A 5 "Capacity:\|Allocatable:\|Conditions:"

# ─── Resource pressure (CPU/Memory/Disk per node)
kubectl top nodes   # requires metrics-server installed

# ─── Workload inventory
kubectl get pods --all-namespaces -o wide
kubectl get deployments --all-namespaces
kubectl get pvc --all-namespaces   # Persistent volume claims

# ─── Networking plugin
kubectl get pods -n kube-system | grep -E "calico|flannel|cilium|weave"

# ─── Storage classes
kubectl get storageclass

# ─── Existing RBAC
kubectl get clusterrolebindings | grep -v system
kubectl get rolebindings --all-namespaces

# ─── Check metrics-server (needed for HPA)
kubectl get deployment metrics-server -n kube-system

# ─── Ingress controller
kubectl get pods --all-namespaces | grep ingress

# ─── API server audit log config
sudo cat /etc/kubernetes/audit-policy.yaml 2>/dev/null || echo "Audit logging not configured"
```

**Production Readiness Checklist:**

| Area | Check | Acceptable State |
|------|-------|-----------------|
| Control plane HA | Single control plane node? | 1 node OK for on-prem dev; document SPOF |
| etcd backup | etcd snapshot job configured? | Required for prod |
| Node count | Minimum nodes for prod | Min 2 worker nodes |
| Metrics server | Installed? | Required for HPA |
| Ingress | NGINX or Traefik installed? | Required |
| StorageClass | Default StorageClass defined? | Required for PVC |
| Network policy | CNI supports NetworkPolicy? | Required (Calico/Cilium) |
| Audit logging | API server audit enabled? | Recommended for prod |
| Pod Security | PodSecurity admission configured? | Recommended |

---

## EP-08 · Advanced Jenkins Pipeline (Jenkinsfile)

**Goal:** Create a production-grade, fully declarative Jenkinsfile with all CI/CD stages, parallel test execution, health-gated integration tests, and environment-specific promotion.

---

### TICKET-08-01 · Create Advanced Declarative Jenkinsfile

**Type:** Development  
**Assignee:** DevOps Engineer + Backend Developer  
**Depends on:** EP-01, EP-02, EP-03, EP-04, EP-05

**Acceptance Criteria:**
- All stages defined and functional end-to-end
- Pipeline fails fast on test/scan failures (no deploy on broken build)
- Integration tests only run after health check confirms pod is ready
- All credentials injected from Jenkins credential store (no plaintext)
- Newman results published as JUnit report in Jenkins UI
- SonarQube quality gate blocks deployment on failure
- Rollback step available as manual trigger post-deployment

**Full Jenkinsfile:**
```groovy
pipeline {
  agent {
    kubernetes {
      yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    pipeline: backend-cicd
spec:
  serviceAccountName: jenkins-agent-sa
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:latest
  - name: maven
    image: eclipse-temurin:21-jdk-alpine
    command: ['sleep', '99d']
    resources:
      requests: { cpu: '500m', memory: '1Gi' }
      limits: { cpu: '2', memory: '2Gi' }
    volumeMounts:
    - name: maven-cache
      mountPath: /root/.m2
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    command: ['sleep', '99d']
    env:
    - name: DOCKER_CONFIG
      value: /kaniko/.docker
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
  - name: kubectl
    image: bitnami/kubectl:latest
    command: ['sleep', '99d']
  - name: newman
    image: postman/newman:alpine
    command: ['sleep', '99d']
  volumes:
  - name: maven-cache
    persistentVolumeClaim:
      claimName: maven-cache-pvc
  - name: docker-config
    secret:
      secretName: regcred
"""
    }
  }

  environment {
    APP_NAME          = 'backend-app'
    REGISTRY          = 'registry.internal'
    IMAGE_REPO        = "${REGISTRY}/backend/spring-app"
    SONAR_URL         = 'http://sonarqube.internal:9000'
    SONAR_PROJECT_KEY = 'backend-spring-app'
    KUBECONFIG_DEV    = credentials('kubeconfig-dev')
    KUBECONFIG_QA     = credentials('kubeconfig-qa')
    KUBECONFIG_PROD   = credentials('kubeconfig-prod')
    SONAR_TOKEN       = credentials('sonarqube-token')
    IMAGE_TAG         = "${env.GIT_COMMIT[0..7]}-${env.BUILD_NUMBER}"
    DEPLOY_NAMESPACE  = "${env.BRANCH_NAME == 'main' ? 'backend-prod' : (env.BRANCH_NAME == 'release' ? 'backend-qa' : 'backend-dev')}"
    KUBECONFIG_CRED   = "${env.BRANCH_NAME == 'main' ? 'kubeconfig-prod' : (env.BRANCH_NAME == 'release' ? 'kubeconfig-qa' : 'kubeconfig-dev')}"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 45, unit: 'MINUTES')
    disableConcurrentBuilds()
    timestamps()
  }

  stages {

    // ── STAGE 1: CLEAN BUILD ─────────────────────────────────────────────────
    stage('Clean Build') {
      steps {
        container('maven') {
          echo "Building ${APP_NAME}:${IMAGE_TAG} from branch ${env.BRANCH_NAME}"
          sh '''
            ./mvnw clean package \
              -DskipTests \
              -Dmaven.repo.local=/root/.m2/repository \
              -Dartifactory.url=http://artifactory.internal:8082/artifactory \
              --batch-mode \
              --no-transfer-progress
          '''
          // Stash JAR for later stages
          stash name: 'build-artifacts', includes: 'target/*.jar, target/extracted/**'
        }
      }
      post {
        failure { echo '❌ Build failed — aborting pipeline' }
      }
    }

    // ── STAGE 2: UNIT TESTS ──────────────────────────────────────────────────
    stage('Unit Tests') {
      steps {
        container('maven') {
          sh '''
            ./mvnw test \
              -Dmaven.repo.local=/root/.m2/repository \
              --batch-mode \
              --no-transfer-progress
          '''
        }
      }
      post {
        always {
          junit 'target/surefire-reports/**/*.xml'
          publishHTML(target: [
            reportDir: 'target/site/jacoco',
            reportFiles: 'index.html',
            reportName: 'JaCoCo Coverage Report'
          ])
        }
        failure { echo '❌ Unit tests failed' }
      }
    }

    // ── STAGE 3: STATIC ANALYSIS + SONARQUBE ────────────────────────────────
    stage('SonarQube Analysis') {
      steps {
        container('maven') {
          sh """
            ./mvnw sonar:sonar \
              -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
              -Dsonar.host.url=${SONAR_URL} \
              -Dsonar.token=${SONAR_TOKEN} \
              -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
              -Dsonar.java.binaries=target/classes \
              --batch-mode \
              --no-transfer-progress
          """
        }
      }
      post {
        failure { echo '❌ SonarQube analysis failed' }
      }
    }

    // ── STAGE 4: SONARQUBE QUALITY GATE ─────────────────────────────────────
    stage('SonarQube Quality Gate') {
      steps {
        timeout(time: 5, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
      post {
        failure { echo '❌ SonarQube Quality Gate FAILED — blocking deployment' }
      }
    }

    // ── STAGE 5: BUILD & PUSH DOCKER IMAGE ──────────────────────────────────
    stage('Build & Push Docker Image') {
      steps {
        container('kaniko') {
          unstash 'build-artifacts'
          sh """
            /kaniko/executor \
              --context=dir://${WORKSPACE} \
              --dockerfile=${WORKSPACE}/Dockerfile \
              --destination=${IMAGE_REPO}:${IMAGE_TAG} \
              --destination=${IMAGE_REPO}:latest \
              --build-arg APP_VERSION=${IMAGE_TAG} \
              --build-arg BUILD_DATE=\$(date -u +%Y-%m-%dT%H:%M:%SZ) \
              --build-arg GIT_COMMIT=${env.GIT_COMMIT} \
              --cache=true \
              --cache-ttl=24h \
              --snapshot-mode=redo \
              --log-format=text
          """
        }
      }
      post {
        success { echo "✅ Image pushed: ${IMAGE_REPO}:${IMAGE_TAG}" }
        failure { echo '❌ Docker image build/push failed' }
      }
    }

    // ── STAGE 6: KUBERNETES DEPLOY ───────────────────────────────────────────
    stage('Deploy to Kubernetes') {
      steps {
        container('kubectl') {
          withCredentials([file(credentialsId: env.KUBECONFIG_CRED, variable: 'KUBECONFIG')]) {
            sh """
              export KUBECONFIG=${KUBECONFIG}

              # Substitute image tag in manifests
              sed -i "s|{{ IMAGE_TAG }}|${IMAGE_TAG}|g" k8s/deployment.yaml

              # Apply manifests
              kubectl apply -f k8s/namespace.yaml
              kubectl apply -f k8s/serviceaccount.yaml
              kubectl apply -f k8s/role.yaml
              kubectl apply -f k8s/rolebinding.yaml
              kubectl apply -f k8s/configmap.yaml -n ${DEPLOY_NAMESPACE}
              kubectl apply -f k8s/deployment.yaml -n ${DEPLOY_NAMESPACE}
              kubectl apply -f k8s/service.yaml -n ${DEPLOY_NAMESPACE}
              kubectl apply -f k8s/hpa.yaml -n ${DEPLOY_NAMESPACE}

              # Wait for rollout to complete (5-minute timeout)
              kubectl rollout status deployment/${APP_NAME} \
                -n ${DEPLOY_NAMESPACE} \
                --timeout=300s
            """
          }
        }
      }
      post {
        failure {
          container('kubectl') {
            withCredentials([file(credentialsId: env.KUBECONFIG_CRED, variable: 'KUBECONFIG')]) {
              sh """
                export KUBECONFIG=${KUBECONFIG}
                echo '🔴 Deployment failed — capturing diagnostics'
                kubectl describe deployment/${APP_NAME} -n ${DEPLOY_NAMESPACE} || true
                kubectl logs -l app=${APP_NAME} -n ${DEPLOY_NAMESPACE} --tail=100 || true
                echo '⏪ Rolling back to previous version'
                kubectl rollout undo deployment/${APP_NAME} -n ${DEPLOY_NAMESPACE}
              """
            }
          }
        }
      }
    }

    // ── STAGE 7: HEALTH CHECK (gate for integration tests) ──────────────────
    stage('Health Check') {
      steps {
        container('kubectl') {
          withCredentials([file(credentialsId: env.KUBECONFIG_CRED, variable: 'KUBECONFIG')]) {
            sh """
              export KUBECONFIG=${KUBECONFIG}

              # Poll readiness probe until service is healthy
              RETRIES=30
              INTERVAL=10
              SERVICE_URL=http://backend-app-svc.${DEPLOY_NAMESPACE}.svc.cluster.local/actuator/health

              echo "Polling health endpoint: ${SERVICE_URL}"
              for i in \$(seq 1 \$RETRIES); do
                STATUS=\$(kubectl exec -n ${DEPLOY_NAMESPACE} \\
                  deploy/${APP_NAME} -- \\
                  wget -qO- http://localhost:8080/actuator/health 2>/dev/null | \\
                  grep -o '"status":"[^"]*"' | head -1 || echo 'DOWN')

                echo "Attempt \$i/\$RETRIES: \$STATUS"

                if echo "\$STATUS" | grep -q 'UP'; then
                  echo "✅ Service is healthy"
                  exit 0
                fi
                sleep \$INTERVAL
              done

              echo "❌ Health check timed out after \$((RETRIES * INTERVAL))s"
              exit 1
            """
          }
        }
      }
    }

    // ── STAGE 8: INTEGRATION TESTS (Newman / Postman) ───────────────────────
    stage('Integration Tests (Newman)') {
      steps {
        container('newman') {
          sh """
            newman run postman/backend-integration-tests.json \
              --environment postman/${DEPLOY_NAMESPACE}-environment.json \
              --reporters cli,junit \
              --reporter-junit-export target/newman/results.xml \
              --bail \
              --timeout-request 10000 \
              --delay-request 100
          """
        }
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'target/newman/results.xml'
        }
        failure { echo '❌ Integration tests failed' }
      }
    }

  } // end stages

  post {
    success {
      echo "✅ Pipeline SUCCESS — ${APP_NAME}:${IMAGE_TAG} deployed to ${DEPLOY_NAMESPACE}"
      // Add Slack/Teams notification here if needed
    }
    failure {
      echo "❌ Pipeline FAILED at stage: ${env.STAGE_NAME}"
    }
    always {
      cleanWs()
    }
  }

} // end pipeline
```

---

### TICKET-08-02 · SonarQube Setup and Quality Gate Configuration

**Type:** Infrastructure  
**Assignee:** DevOps Engineer  
**Depends on:** TICKET-08-01

**Acceptance Criteria:**
- SonarQube running on-prem (or existing instance confirmed accessible)
- `backend-spring-app` project created
- Quality Gate configured with minimum thresholds:
    - Coverage ≥ 70%
    - Duplicated lines < 3%
    - Maintainability rating ≤ B
    - Reliability rating ≤ B (no new bugs)
    - Security rating ≤ A (no new vulnerabilities)
- Webhook configured: `sonarqube → jenkins → /sonarqube-webhook/`

**SonarQube Quality Gate via API:**
```bash
# Create project
curl -u admin:$SONAR_PASS -X POST \
  "http://sonarqube.internal:9000/api/projects/create?name=backend-spring-app&project=backend-spring-app"

# Create webhook pointing to Jenkins
curl -u admin:$SONAR_PASS -X POST \
  "http://sonarqube.internal:9000/api/webhooks/create" \
  -d "name=jenkins&url=http://jenkins.internal:8080/sonarqube-webhook/"
```

---

### TICKET-08-03 · Newman / Postman Test Collection Setup

**Type:** Development  
**Assignee:** Backend Developer + QA Engineer  
**Depends on:** TICKET-08-01

**Acceptance Criteria:**
- Postman collection `backend-integration-tests.json` committed to repo under `/postman/`
- Environment files per namespace: `backend-dev-environment.json`, `backend-qa-environment.json`, `backend-prod-environment.json`
- Collection covers: CRUD happy paths, auth token flow, error response codes (400, 401, 404, 500)
- Newman exit code is non-zero on any test failure (blocks pipeline)

**Environment file template:**
```json
{
  "name": "backend-qa",
  "values": [
    { "key": "BASE_URL", "value": "http://backend-app-svc.backend-qa.svc.cluster.local", "enabled": true },
    { "key": "AUTH_TOKEN", "value": "{{resolved-from-secrets}}", "enabled": true }
  ]
}
```

---

## Delivery Sequence / Dependency Graph

```
EP-01 (Artifactory) ──────────────────────────────┐
EP-02 (Docker Registry) ──► TICKET-02-02 (K8s)    │
         │                        │                │
         ▼                        ▼                │
EP-03 (Dockerfile) ──────► EP-04 (K8s Templates)  │
                                  │                │
EP-05 (Jenkins Audit) ────────────┤                │
EP-06 (PG + Kafka Audit) ─────────┤                │
EP-07 (K8s Audit) ────────────────┘                │
         │                                         │
         ▼                                         ▼
EP-08 (Jenkins Pipeline) ◄─────────────────────────┘
```

**Suggested Sprint Breakdown:**

| Sprint | Epics | Goal |
|--------|-------|------|
| Sprint 1 (Week 1–2) | EP-01, EP-02, EP-07 | Infrastructure foundation |
| Sprint 2 (Week 2–3) | EP-03, EP-04, EP-05 | Containers + K8s + Jenkins readiness |
| Sprint 3 (Week 3–4) | EP-06 | DB + Kafka audit and hardening |
| Sprint 4 (Week 4–5) | EP-08 | Full pipeline automation |

---

*Document version: 1.0 — Generated for architecture review*