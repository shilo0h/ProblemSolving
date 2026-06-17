# Complete DevOps Guide — Spring Boot + React Native
> Artifactory · Docker · Jenkins · Kubernetes · Kafka · PostgreSQL

---

## Table of Contents
1. [Artifactory Setup](#1-artifactory-setup)
2. [Docker Repository for Image Backup](#2-docker-repository-for-image-backup)
3. [Jenkins Machine Check & Config](#3-jenkins-machine-check--config)
4. [Kubernetes Cluster Check (On-Prem)](#4-kubernetes-cluster-check-on-prem)
5. [Advanced Jenkins Pipeline Script](#5-advanced-jenkins-pipeline-script)
6. [Dev vs Production Setup Guide](#6-dev-vs-production-setup-guide)
7. [Kafka Configuration](#7-kafka-configuration)
8. [PostgreSQL Configuration](#8-postgresql-configuration)
9. [Dev vs Prod Differences — Full Comparison](#9-dev-vs-prod-differences--full-comparison)

---

## 1. Artifactory Setup

### 1.1 Install JFrog Artifactory (Self-Hosted, Linux)

```bash
# Option A: via Docker Compose (recommended for on-prem)
mkdir -p /opt/artifactory && cd /opt/artifactory

cat > docker-compose.yml <<EOF
version: '3.7'
services:
  artifactory:
    image: releases-docker.jfrog.io/jfrog/artifactory-oss:latest
    container_name: artifactory
    ports:
      - "8081:8081"   # Artifactory UI
      - "8082:8082"   # Access Federation
    volumes:
      - artifactory_data:/var/opt/jfrog/artifactory
    environment:
      - JF_SHARED_DATABASE_TYPE=derby   # Use PostgreSQL in prod (see below)
    restart: always

volumes:
  artifactory_data:
EOF

docker-compose up -d
```

### 1.2 Connect Artifactory to PostgreSQL (Production)

```yaml
# In docker-compose.yml, add:
environment:
  - JF_SHARED_DATABASE_TYPE=postgresql
  - JF_SHARED_DATABASE_DRIVER=org.postgresql.Driver
  - JF_SHARED_DATABASE_URL=jdbc:postgresql://postgres:5432/artifactory
  - JF_SHARED_DATABASE_USERNAME=artifactory
  - JF_SHARED_DATABASE_PASSWORD=yourpassword
```

### 1.3 Create Repositories in Artifactory

After login at `http://<host>:8081/artifactory`:

| Repo Key              | Type    | Package Type | Purpose                        |
|-----------------------|---------|--------------|--------------------------------|
| `libs-release-local`  | Local   | Maven        | Spring Boot JARs (release)     |
| `libs-snapshot-local` | Local   | Maven        | Spring Boot JARs (snapshot)    |
| `docker-local`        | Local   | Docker       | Built Docker images            |
| `npm-local`           | Local   | npm          | React Native / Node packages   |
| `libs-release`        | Virtual | Maven        | Aggregates local + remote      |
| `npm-virtual`         | Virtual | npm          | Aggregates npm repos           |

### 1.4 Spring Boot — Connect to Artifactory (Maven)

```xml
<!-- settings.xml (~/.m2/settings.xml or Jenkins credential) -->
<settings>
  <servers>
    <server>
      <id>artifactory-releases</id>
      <username>admin</username>
      <password>your_password</password>
    </server>
    <server>
      <id>artifactory-snapshots</id>
      <username>admin</username>
      <password>your_password</password>
    </server>
  </servers>
</settings>
```

```xml
<!-- pom.xml -->
<distributionManagement>
  <repository>
    <id>artifactory-releases</id>
    <url>http://artifactory-host:8081/artifactory/libs-release-local</url>
  </repository>
  <snapshotRepository>
    <id>artifactory-snapshots</id>
    <url>http://artifactory-host:8081/artifactory/libs-snapshot-local</url>
  </snapshotRepository>
</distributionManagement>
```

### 1.5 React Native — Connect npm to Artifactory

```bash
# .npmrc (in project root or global ~/.npmrc)
registry=http://artifactory-host:8081/artifactory/api/npm/npm-virtual/
//artifactory-host:8081/artifactory/api/npm/npm-virtual/:_authToken=<your-token>
always-auth=true
```

---

## 2. Docker Repository for Image Backup

### 2.1 Artifactory as Docker Registry

```bash
# Login to Artifactory Docker repo
docker login artifactory-host:8082

# Tag image for Artifactory
docker tag myapp:latest artifactory-host:8082/docker-local/myapp:latest

# Push image
docker push artifactory-host:8082/docker-local/myapp:latest

# Pull image
docker pull artifactory-host:8082/docker-local/myapp:latest
```

### 2.2 Backup Script — Daily Docker Image Snapshot

```bash
#!/bin/bash
# /opt/scripts/docker-backup.sh

REGISTRY="artifactory-host:8082/docker-local"
DATE=$(date +%Y%m%d)
IMAGES=("myapp-backend" "myapp-frontend")

for IMAGE in "${IMAGES[@]}"; do
  echo "Backing up $IMAGE..."
  docker pull $REGISTRY/$IMAGE:latest
  docker tag $REGISTRY/$IMAGE:latest $REGISTRY/$IMAGE:backup-$DATE
  docker push $REGISTRY/$IMAGE:backup-$DATE
  echo "Backup $IMAGE:backup-$DATE done."
done

# Cleanup backups older than 30 days via Artifactory AQL API
curl -u admin:password -X POST \
  "http://artifactory-host:8081/artifactory/api/search/aql" \
  -H "Content-Type: text/plain" \
  -d 'items.find({"repo":"docker-local","name":{"$match":"*backup*"},"created":{"$before":"30d"}})'
```

```bash
# Schedule with cron
crontab -e
# 0 2 * * * /opt/scripts/docker-backup.sh >> /var/log/docker-backup.log 2>&1
```

### 2.3 Artifactory Retention Policy (UI)

Go to **Admin → Artifactory → Cleanup Policies** and set:
- Delete artifacts not downloaded in 60 days
- Keep minimum 5 versions per image

---

## 3. Jenkins Machine Check & Config

### 3.1 Verify Jenkins Is Running

```bash
# Check service
systemctl status jenkins

# Check Java version (Jenkins requires Java 17+)
java -version

# Check Jenkins version
cat /var/lib/jenkins/config.xml | grep version

# Check Jenkins listening port
ss -tlnp | grep 8080

# Check logs for errors
journalctl -u jenkins -n 100 --no-pager
tail -f /var/log/jenkins/jenkins.log
```

### 3.2 Required Plugins — Verify Installation

Go to **Manage Jenkins → Plugin Manager → Installed** and confirm:

```
✅ Pipeline
✅ Pipeline: Stage View
✅ Git / GitHub
✅ Docker Pipeline
✅ Kubernetes
✅ SonarQube Scanner
✅ Artifactory (JFrog)
✅ JUnit
✅ Jacoco (code coverage)
✅ Slack Notification (optional)
✅ Credentials Binding
✅ Blue Ocean (optional, better UI)
```

Install missing plugins:
```bash
# Via Jenkins CLI
java -jar jenkins-cli.jar -s http://localhost:8080/ install-plugin \
  docker-workflow sonar kubernetes artifactory jacoco -restart
```

### 3.3 Configure Jenkins Tools (Manage Jenkins → Global Tool Configuration)

```
JDK:
  Name: JDK-17
  JAVA_HOME: /usr/lib/jvm/java-17-openjdk-amd64

Maven:
  Name: Maven-3.9
  (auto-install from Apache)

Docker:
  Make sure jenkins user is in docker group:
  sudo usermod -aG docker jenkins
  sudo systemctl restart jenkins

SonarQube Scanner:
  Name: SonarScanner
  (auto-install)
```

### 3.4 Jenkins Credentials Setup

Go to **Manage Jenkins → Credentials → (global)**:

| ID                      | Type                | Value                            |
|-------------------------|---------------------|----------------------------------|
| `artifactory-creds`     | Username/Password   | Artifactory admin credentials    |
| `docker-registry-creds` | Username/Password   | Docker registry login            |
| `sonar-token`           | Secret text         | SonarQube project token          |
| `k8s-config`            | Secret file         | kubeconfig file                  |
| `github-token`          | Username/Password   | GitHub token                     |
| `postgres-password`     | Secret text         | DB password (for integration tests)|

### 3.5 Configure SonarQube in Jenkins

Go to **Manage Jenkins → Configure System → SonarQube Servers**:
```
Name: SonarQube
Server URL: http://sonarqube-host:9000
Server authentication token: (use sonar-token credential)
```

### 3.6 Jenkins System Check Script

```bash
#!/bin/bash
# Run on Jenkins Linux VM to verify environment

echo "=== Jenkins Environment Check ==="

echo "--- Java ---"
java -version

echo "--- Maven ---"
mvn -version 2>/dev/null || echo "Maven NOT found in PATH"

echo "--- Docker ---"
docker --version
docker info | grep -E "Server Version|Storage Driver"

echo "--- kubectl ---"
kubectl version --client

echo "--- Disk Space ---"
df -h /var/lib/jenkins

echo "--- Memory ---"
free -h

echo "--- Jenkins process ---"
ps aux | grep jenkins | grep -v grep

echo "--- Docker group ---"
groups jenkins

echo "=== Done ==="
```

---

## 4. Kubernetes Cluster Check (On-Prem)

### 4.1 Cluster Health Check

```bash
# Check nodes
kubectl get nodes -o wide

# Check all system pods
kubectl get pods -n kube-system

# Check cluster version
kubectl version

# Check available resources
kubectl top nodes        # requires metrics-server
kubectl top pods -A

# Check namespaces
kubectl get namespaces

# Check persistent volumes
kubectl get pv,pvc -A

# Check ingress controller
kubectl get pods -n ingress-nginx
```

### 4.2 Required Cluster Components

```bash
# Metrics Server (for HPA and kubectl top)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Check if storage class exists
kubectl get storageclass

# Check if ingress controller is running
kubectl get svc -n ingress-nginx

# Check cluster DNS
kubectl get svc kube-dns -n kube-system
```

### 4.3 Namespace Setup for Your Apps

```bash
# Create namespaces
kubectl create namespace dev
kubectl create namespace staging
kubectl create namespace production

# Label namespaces
kubectl label namespace production environment=production
kubectl label namespace dev environment=dev
```

### 4.4 Create Required Kubernetes Secrets

```bash
# Docker registry secret (pull from Artifactory)
kubectl create secret docker-registry artifactory-secret \
  --docker-server=artifactory-host:8082 \
  --docker-username=admin \
  --docker-password=yourpassword \
  --docker-email=admin@company.com \
  -n production

# PostgreSQL secret
kubectl create secret generic postgres-secret \
  --from-literal=POSTGRES_USER=appuser \
  --from-literal=POSTGRES_PASSWORD=securepassword \
  --from-literal=POSTGRES_DB=appdb \
  -n production

# Kafka secret
kubectl create secret generic kafka-secret \
  --from-literal=KAFKA_USERNAME=kafkauser \
  --from-literal=KAFKA_PASSWORD=kafkapassword \
  -n production
```

### 4.5 Cluster Node Check

```bash
# Verify node labels for scheduling
kubectl get nodes --show-labels

# Add labels for node affinity (if needed)
kubectl label node worker-node-1 role=worker
kubectl label node worker-node-2 role=worker

# Check node taints
kubectl describe nodes | grep -A5 Taints

# Check RBAC for Jenkins service account
kubectl create serviceaccount jenkins-deployer -n production
kubectl create clusterrolebinding jenkins-deployer-binding \
  --clusterrole=cluster-admin \
  --serviceaccount=production:jenkins-deployer
```

---

## 5. Advanced Jenkins Pipeline Script

### 5.1 Backend — Spring Boot Pipeline

```groovy
// Jenkinsfile.backend
pipeline {
    agent any

    environment {
        // ── Registry ──────────────────────────────────────────
        DOCKER_REGISTRY     = "artifactory-host:8082"
        ARTIFACTORY_URL     = "http://artifactory-host:8081/artifactory"
        IMAGE_NAME          = "docker-local/myapp-backend"
        IMAGE_TAG           = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"

        // ── Sonar ─────────────────────────────────────────────
        SONAR_PROJECT_KEY   = "myapp-backend"
        SONAR_HOST_URL      = "http://sonarqube-host:9000"

        // ── Kubernetes ────────────────────────────────────────
        K8S_NAMESPACE       = "production"
        K8S_DEPLOYMENT      = "backend-deployment"

        // ── Credentials ───────────────────────────────────────
        DOCKER_CREDS        = "docker-registry-creds"
        SONAR_TOKEN         = credentials("sonar-token")
        ARTIFACTORY_CREDS   = credentials("artifactory-creds")
    }

    tools {
        jdk     "JDK-17"
        maven   "Maven-3.9"
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: "10"))
        timeout(time: 45, unit: "MINUTES")
        disableConcurrentBuilds()
        timestamps()
    }

    stages {

        // ══════════════════════════════════════════════════════
        stage("Checkout") {
        // ══════════════════════════════════════════════════════
            steps {
                checkout scm
                script {
                    env.GIT_BRANCH_NAME = sh(
                        script: "git rev-parse --abbrev-ref HEAD",
                        returnStdout: true
                    ).trim()
                    echo "Branch: ${env.GIT_BRANCH_NAME}"
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Clean Build") {
        // ══════════════════════════════════════════════════════
            steps {
                sh """
                    echo "=== Clean Build ==="
                    mvn clean package \
                        -DskipTests=false \
                        -Dmaven.test.failure.ignore=false \
                        -Dspring.profiles.active=ci \
                        --batch-mode \
                        -s /var/lib/jenkins/.m2/settings.xml
                """
            }
            post {
                always {
                    // Publish JUnit test results
                    junit "**/target/surefire-reports/*.xml"
                    // Publish code coverage
                    jacoco(
                        execPattern: "**/target/jacoco.exec",
                        classPattern: "**/target/classes",
                        sourcePattern: "**/src/main/java",
                        exclusionPattern: "**/test/**"
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("SonarQube Analysis") {
        // ══════════════════════════════════════════════════════
            steps {
                withSonarQubeEnv("SonarQube") {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                            -Dsonar.host.url=${SONAR_HOST_URL} \
                            -Dsonar.login=${SONAR_TOKEN} \
                            -Dsonar.java.coveragePlugin=jacoco \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            --batch-mode
                    """
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Quality Gate") {
        // ══════════════════════════════════════════════════════
            steps {
                timeout(time: 10, unit: "MINUTES") {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Build Docker Image") {
        // ══════════════════════════════════════════════════════
            steps {
                script {
                    docker.build(
                        "${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}",
                        "--build-arg SPRING_PROFILE=production \
                         --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} \
                         -f Dockerfile ."
                    )
                    // Also tag as latest
                    sh "docker tag ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} \
                                   ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Push to Docker Repo") {
        // ══════════════════════════════════════════════════════
            steps {
                script {
                    docker.withRegistry("http://${DOCKER_REGISTRY}", DOCKER_CREDS) {
                        sh """
                            docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest
                        """
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Publish Artifacts to Artifactory") {
        // ══════════════════════════════════════════════════════
            steps {
                sh """
                    mvn deploy \
                        -DskipTests=true \
                        -s /var/lib/jenkins/.m2/settings.xml \
                        --batch-mode
                """
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Kubernetes Deploy") {
        // ══════════════════════════════════════════════════════
            steps {
                withCredentials([file(credentialsId: "k8s-config", variable: "KUBECONFIG")]) {
                    sh """
                        export KUBECONFIG=$KUBECONFIG

                        # Replace image tag in deployment manifest
                        sed -i "s|IMAGE_TAG|${IMAGE_TAG}|g" k8s/backend-deployment.yaml
                        sed -i "s|DOCKER_REGISTRY|${DOCKER_REGISTRY}|g" k8s/backend-deployment.yaml

                        # Apply configs and secrets first
                        kubectl apply -f k8s/backend-configmap.yaml -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/backend-deployment.yaml -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/backend-service.yaml    -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/backend-ingress.yaml    -n ${K8S_NAMESPACE}

                        # Wait for rollout
                        kubectl rollout status deployment/${K8S_DEPLOYMENT} \
                            -n ${K8S_NAMESPACE} --timeout=300s
                    """
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Integration Tests") {
        // ══════════════════════════════════════════════════════
            steps {
                sh """
                    echo "=== Running Integration Tests ==="
                    # Wait for service to be ready
                    sleep 20

                    # Run integration tests against deployed service
                    mvn verify \
                        -Dspring.profiles.active=integration \
                        -Dtest.api.url=http://backend-service.${K8S_NAMESPACE}.svc.cluster.local:8080 \
                        -Dgroups=integration \
                        --batch-mode
                """
            }
            post {
                always {
                    junit "**/target/failsafe-reports/*.xml"
                }
            }
        }

        // ══════════════════════════════════════════════════════
        stage("Smoke Test") {
        // ══════════════════════════════════════════════════════
            steps {
                sh """
                    echo "=== Smoke Test: Health Check ==="
                    # Basic smoke test — hit health endpoint
                    for i in 1 2 3 4 5; do
                        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
                            http://your-domain.com/api/actuator/health)
                        if [ "$STATUS" = "200" ]; then
                            echo "✅ Health check passed (attempt $i)"
                            exit 0
                        fi
                        echo "⚠️  Attempt $i failed (status: $STATUS). Retrying in 10s..."
                        sleep 10
                    done
                    echo "❌ Smoke test failed after 5 attempts"
                    exit 1
                """
            }
        }

    } // end stages

    post {
        success {
            echo "✅ Pipeline completed successfully! Image: ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
            // slackSend channel: "#deployments", message: "✅ Backend deployed: ${IMAGE_TAG}"
        }
        failure {
            echo "❌ Pipeline failed! Rolling back..."
            withCredentials([file(credentialsId: "k8s-config", variable: "KUBECONFIG")]) {
                sh """
                    export KUBECONFIG=$KUBECONFIG
                    kubectl rollout undo deployment/${K8S_DEPLOYMENT} -n ${K8S_NAMESPACE}
                """
            }
            // slackSend channel: "#deployments", color: "danger", message: "❌ Backend deploy FAILED"
        }
        always {
            // Cleanup local Docker images to save disk space
            sh """
                docker rmi ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} || true
                docker system prune -f || true
            """
            cleanWs()
        }
    }
}
```

### 5.2 Frontend — React Native / React Web Pipeline

```groovy
// Jenkinsfile.frontend
pipeline {
    agent any

    environment {
        DOCKER_REGISTRY  = "artifactory-host:8082"
        IMAGE_NAME       = "docker-local/myapp-frontend"
        IMAGE_TAG        = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
        SONAR_PROJECT_KEY = "myapp-frontend"
        K8S_NAMESPACE    = "production"
        DOCKER_CREDS     = "docker-registry-creds"
        SONAR_TOKEN      = credentials("sonar-token")
        NODE_ENV         = "production"
    }

    tools {
        nodejs "NodeJS-18"    // configure in Global Tool Configuration
    }

    stages {

        stage("Checkout") {
            steps { checkout scm }
        }

        stage("Install Dependencies") {
            steps {
                sh """
                    npm ci --prefer-offline \
                        --registry http://artifactory-host:8081/artifactory/api/npm/npm-virtual/
                """
            }
        }

        stage("Lint & Type Check") {
            parallel {
                stage("ESLint") {
                    steps {
                        sh "npm run lint -- --format junit --output-file reports/eslint.xml || true"
                    }
                    post { always { junit allowEmptyResults: true, testResults: "reports/eslint.xml" } }
                }
                stage("TypeScript") {
                    steps {
                        sh "npx tsc --noEmit"
                    }
                }
            }
        }

        stage("Unit Tests") {
            steps {
                sh """
                    npm test -- \
                        --watchAll=false \
                        --coverage \
                        --coverageReporters=lcov \
                        --ci
                """
            }
            post {
                always {
                    junit "**/junit.xml"
                    publishHTML(target: [
                        reportDir: "coverage/lcov-report",
                        reportFiles: "index.html",
                        reportName: "Coverage Report"
                    ])
                }
            }
        }

        stage("SonarQube Analysis") {
            steps {
                withSonarQubeEnv("SonarQube") {
                    sh """
                        npx sonar-scanner \
                            -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                            -Dsonar.sources=src \
                            -Dsonar.tests=src \
                            -Dsonar.test.inclusions=**/*.test.ts,**/*.spec.ts \
                            -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info \
                            -Dsonar.host.url=${SONAR_HOST_URL} \
                            -Dsonar.login=${SONAR_TOKEN}
                    """
                }
                timeout(time: 10, unit: "MINUTES") {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage("Build") {
            steps {
                sh "npm run build"
            }
        }

        stage("Build Docker Image") {
            steps {
                script {
                    docker.build("${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}", "-f Dockerfile .")
                    sh "docker tag ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} \
                                   ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
                }
            }
        }

        stage("Push to Docker Repo") {
            steps {
                script {
                    docker.withRegistry("http://${DOCKER_REGISTRY}", DOCKER_CREDS) {
                        sh """
                            docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:latest
                        """
                    }
                }
            }
        }

        stage("Kubernetes Deploy") {
            steps {
                withCredentials([file(credentialsId: "k8s-config", variable: "KUBECONFIG")]) {
                    sh """
                        export KUBECONFIG=$KUBECONFIG
                        sed -i "s|IMAGE_TAG|${IMAGE_TAG}|g" k8s/frontend-deployment.yaml
                        kubectl apply -f k8s/frontend-deployment.yaml -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/frontend-service.yaml    -n ${K8S_NAMESPACE}
                        kubectl apply -f k8s/frontend-ingress.yaml    -n ${K8S_NAMESPACE}
                        kubectl rollout status deployment/frontend-deployment \
                            -n ${K8S_NAMESPACE} --timeout=180s
                    """
                }
            }
        }

        stage("E2E Tests") {
            steps {
                sh """
                    # Run Cypress or Playwright E2E tests
                    npx cypress run \
                        --config baseUrl=https://your-domain.com \
                        --reporter junit \
                        --reporter-options mochaFile=reports/e2e.xml
                """
            }
            post {
                always { junit allowEmptyResults: true, testResults: "reports/e2e.xml" }
            }
        }
    }

    post {
        always {
            sh "docker rmi ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} || true"
            cleanWs()
        }
    }
}
```

### 5.3 Kubernetes Manifests

```yaml
# k8s/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-deployment
  namespace: production
  labels:
    app: backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  template:
    metadata:
      labels:
        app: backend
    spec:
      imagePullSecrets:
        - name: artifactory-secret
      containers:
        - name: backend
          image: DOCKER_REGISTRY/docker-local/myapp-backend:IMAGE_TAG
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "production"
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: POSTGRES_PASSWORD
            - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: backend-configmap
                  key: KAFKA_BOOTSTRAP_SERVERS
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
```

```yaml
# k8s/backend-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-configmap
  namespace: production
data:
  KAFKA_BOOTSTRAP_SERVERS: "kafka-broker-1:9092,kafka-broker-2:9092"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres-service:5432/appdb"
  SPRING_DATASOURCE_USERNAME: "appuser"
  SERVER_PORT: "8080"
```

---

## 6. Dev vs Production Setup Guide

### 6.1 Spring Boot — application profiles

```yaml
# src/main/resources/application.yml (common)
spring:
  application:
    name: myapp-backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

---
# src/main/resources/application-dev.yml
spring:
  config:
    activate:
      on-profile: dev

  datasource:
    url: jdbc:postgresql://localhost:5432/appdb_dev
    username: devuser
    password: devpassword
    hikari:
      maximum-pool-size: 5       # Small pool in dev

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      auto-offset-reset: latest
      group-id: myapp-dev

  jpa:
    hibernate:
      ddl-auto: create-drop      # Recreate schema on restart in dev
    show-sql: true               # Show SQL queries in dev

logging:
  level:
    root: INFO
    com.yourcompany: DEBUG       # Verbose logging in dev

management:
  endpoints:
    web:
      exposure:
        include: "*"             # All actuator endpoints exposed in dev

---
# src/main/resources/application-production.yml
spring:
  config:
    activate:
      on-profile: production

  datasource:
    url: jdbc:postgresql://postgres-service:5432/appdb
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    hikari:
      maximum-pool-size: 20      # Larger pool in production
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      auto-offset-reset: earliest
      group-id: myapp-production

  jpa:
    hibernate:
      ddl-auto: validate         # NEVER recreate schema in production
    show-sql: false              # No SQL logging in production

logging:
  level:
    root: WARN
    com.yourcompany: INFO        # Less verbose in production

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # Only safe endpoints
  endpoint:
    health:
      show-details: never        # Hide sensitive health details
```

### 6.2 React Native — Environment Configuration

```bash
# Install react-native-config
npm install react-native-config

# Create environment files
.env.dev
.env.staging
.env.production
```

```bash
# .env.dev
API_BASE_URL=http://localhost:8080/api
KAFKA_WEBSOCKET_URL=ws://localhost:8080/ws
DEBUG_MODE=true
LOG_LEVEL=debug
ENABLE_FLIPPER=true

# .env.production
API_BASE_URL=https://api.yourdomain.com/api
KAFKA_WEBSOCKET_URL=wss://api.yourdomain.com/ws
DEBUG_MODE=false
LOG_LEVEL=error
ENABLE_FLIPPER=false
ENABLE_SENTRY=true
SENTRY_DSN=https://your-sentry-dsn
```

```javascript
// src/config/api.ts
import Config from "react-native-config";

export const API_CONFIG = {
  baseURL: Config.API_BASE_URL,
  timeout: __DEV__ ? 30000 : 10000,
  headers: {
    "Content-Type": "application/json",
    "X-App-Version": "1.0.0",
  },
};
```

---

## 7. Kafka Configuration

### 7.1 Dev Kafka (Docker Compose — Local)

```yaml
# docker-compose.dev.yml
version: "3.8"
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1    # 1 replica in dev (no HA)
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"       # Auto-create topics in dev
      KAFKA_LOG_RETENTION_HOURS: 24                 # Short retention in dev

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: dev-cluster
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
```

### 7.2 Production Kafka — Spring Boot Config

```yaml
# application-production.yml (Kafka section)
spring:
  kafka:
    bootstrap-servers: kafka-broker-1:9092,kafka-broker-2:9092,kafka-broker-3:9092

    # SASL/SSL for production security
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: PLAIN
      sasl.jaas.config: >
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_USERNAME}"
        password="${KAFKA_PASSWORD}";

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all               # Wait for all replicas (production durability)
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 1
        compression.type: snappy

    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: myapp-production
      auto-offset-reset: earliest
      enable-auto-commit: false         # Manual commit in production
      max-poll-records: 100
      properties:
        spring.json.trusted.packages: "*"
        isolation.level: read_committed  # Transactional reads
```

```java
// KafkaTopicConfig.java — Production topic setup
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
            .partitions(6)          // Higher partitions in production
            .replicas(3)            // 3 replicas for HA
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7 days
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
            .build();
    }

    // Dev equivalent:
    // .partitions(1).replicas(1).config(RETENTION_MS_CONFIG, "86400000") // 1 day
}
```

### 7.3 Kafka Dev vs Production Key Differences

| Setting                   | Dev                  | Production                          |
|---------------------------|----------------------|-------------------------------------|
| Broker count              | 1                    | 3+ (high availability)              |
| Replication factor        | 1                    | 3                                   |
| Topic partitions          | 1–2                  | 6–12 (based on throughput)          |
| Auto-create topics        | ✅ Enabled           | ❌ Disabled (explicit creation only) |
| Security (SASL/SSL)       | ❌ None              | ✅ SASL_SSL                         |
| Auto-commit               | ✅ (fine for dev)    | ❌ Manual commit                     |
| `acks` (producer)         | `1` (fast)           | `all` (durable)                     |
| Log retention             | 24 hours             | 7 days                              |
| Schema Registry           | Optional             | ✅ Required (Confluent or Apicurio)  |
| Consumer group reset      | Often (testing)      | Careful — data loss risk            |
| Monitoring                | Kafka UI (simple)    | Prometheus + Grafana + Alerting     |

---

## 8. PostgreSQL Configuration

### 8.1 Dev PostgreSQL (Docker)

```yaml
# docker-compose.dev.yml
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: appdb_dev
      POSTGRES_USER: devuser
      POSTGRES_PASSWORD: devpassword
    ports:
      - "5432:5432"
    volumes:
      - postgres_dev_data:/var/lib/postgresql/data
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
```

### 8.2 Production PostgreSQL — PostgreSQL Config (`postgresql.conf`)

```ini
# postgresql.conf — Production tuning

# ── Connections ──────────────────────────────────────────────
max_connections = 200            # Match with Hikari max pool size × replicas
superuser_reserved_connections = 5

# ── Memory ───────────────────────────────────────────────────
shared_buffers = 4GB             # 25% of total RAM
effective_cache_size = 12GB      # 75% of total RAM
work_mem = 64MB                  # Per sort/hash operation
maintenance_work_mem = 1GB       # For VACUUM, CREATE INDEX

# ── WAL & Replication ────────────────────────────────────────
wal_level = replica              # Required for streaming replication
max_wal_senders = 3
wal_buffers = 64MB
checkpoint_completion_target = 0.9
min_wal_size = 1GB
max_wal_size = 4GB

# ── Query Tuning ─────────────────────────────────────────────
random_page_cost = 1.1           # For SSD storage
effective_io_concurrency = 200   # SSD: 200, HDD: 2
default_statistics_target = 100

# ── Logging ──────────────────────────────────────────────────
log_min_duration_statement = 1000  # Log queries > 1 second
log_checkpoints = on
log_connections = off            # Too noisy in production
log_disconnections = off
log_lock_waits = on

# ── Security ─────────────────────────────────────────────────
ssl = on
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'
```

### 8.3 Production pg_hba.conf (Access Control)

```
# TYPE  DATABASE  USER      ADDRESS          METHOD
local   all       postgres                   peer
host    appdb     appuser   10.0.0.0/8       scram-sha-256   # App servers only
host    all       all       0.0.0.0/0        reject          # Block everything else
```

### 8.4 Flyway — Database Migration (Spring Boot)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true   # Fail if checksums mismatch — critical for prod
    out-of-order: false         # Strict ordering in production
```

```sql
-- src/main/resources/db/migration/V1__initial_schema.sql
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMP DEFAULT NOW()
);

-- V2__add_orders_table.sql, V3__add_kafka_outbox.sql etc.
```

### 8.5 Dev vs Production PostgreSQL Key Differences

| Setting                     | Dev                         | Production                        |
|-----------------------------|-----------------------------|-----------------------------------|
| `ddl-auto`                  | `create-drop`               | `validate`                        |
| `show-sql`                  | `true`                      | `false`                           |
| Connection pool size         | 5                           | 20–50                             |
| Migrations                  | Flyway (auto-run)           | Flyway (versioned, reviewed)      |
| SSL                         | ❌ Not required             | ✅ Required (`ssl=on`)            |
| Replication                 | Single instance             | Primary + 1–2 read replicas       |
| Backups                     | None / manual               | pg_dump + WAL-G continuous backup |
| `log_min_duration_statement`| `0` (log all)               | `1000ms` (only slow queries)      |
| pg_hba.conf                 | `trust` (local dev)         | `scram-sha-256`                   |
| Point-in-time recovery      | ❌                          | ✅ via WAL archiving              |
| Connection pooler           | None                        | PgBouncer (transaction mode)      |
| Monitoring                  | None                        | pg_stat_statements + Prometheus   |

---

## 9. Dev vs Prod Differences — Full Comparison

### 9.1 Architecture Overview

```
DEV ENVIRONMENT
───────────────
Developer Laptop / Single VM
  ├── Docker Compose
  │     ├── Spring Boot (dev profile, hot reload DevTools)
  │     ├── React Native Metro bundler
  │     ├── PostgreSQL (single instance, create-drop)
  │     └── Kafka (single broker, no auth)
  └── Local IDE + Debugger

PRODUCTION ENVIRONMENT
───────────────────────
Kubernetes Cluster (On-Prem)
  ├── namespace: production
  │     ├── backend Deployment (2+ replicas, HPA)
  │     ├── frontend Deployment (2+ replicas)
  │     ├── PgBouncer (connection pooler)
  │     └── Ingress (TLS/SSL)
  ├── namespace: kafka
  │     └── Kafka (3 brokers, ZooKeeper or KRaft, SASL)
  ├── namespace: monitoring
  │     ├── Prometheus
  │     └── Grafana
  └── External: PostgreSQL (primary + replica, SSL)
```

### 9.2 Complete Dev vs Production Matrix

| Area                  | Development                           | Production                                    |
|-----------------------|---------------------------------------|-----------------------------------------------|
| **Deployment**        | Docker Compose locally                | Kubernetes with HPA                           |
| **Replicas**          | 1 instance each                       | 2+ instances, auto-scaling                    |
| **Spring Profile**    | `dev`                                 | `production`                                  |
| **DB ddl-auto**       | `create-drop`                         | `validate`                                    |
| **DB pool size**      | 5                                     | 20–50 + PgBouncer                             |
| **DB SSL**            | Off                                   | On (TLS)                                      |
| **Kafka brokers**     | 1, no auth, auto-create topics        | 3, SASL/SSL, manual topics, Schema Registry   |
| **Secrets**           | `.env` files / hardcoded              | Kubernetes Secrets / Vault                    |
| **Logging**           | DEBUG, console output                 | INFO/WARN, centralized (ELK/Loki)             |
| **Actuator**          | All endpoints exposed                 | Only health, metrics, prometheus               |
| **CORS**              | Allow `*`                             | Strict origin whitelist                        |
| **TLS/HTTPS**         | Self-signed or HTTP                   | Valid certificate (Let's Encrypt / internal CA)|
| **Monitoring**        | None / basic                          | Prometheus + Grafana + Alerting                |
| **CI/CD**             | Manual / local builds                 | Jenkins pipeline (full)                        |
| **Image registry**    | Local Docker daemon                   | Artifactory Docker repo                        |
| **Backups**           | None                                  | Automated daily DB + WAL continuous            |
| **Rate limiting**     | None                                  | API Gateway / Nginx rate limit                 |
| **Feature flags**     | Hardcoded                             | Dynamic (LaunchDarkly / Spring Cloud Config)   |

---

## Quick Reference — Environment Variables

```bash
# === SPRING BOOT ===
SPRING_PROFILES_ACTIVE=production
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-service:5432/appdb
SPRING_DATASOURCE_USERNAME=appuser
SPRING_DATASOURCE_PASSWORD=<from-secret>
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
KAFKA_USERNAME=<from-secret>
KAFKA_PASSWORD=<from-secret>
SERVER_PORT=8080

# === REACT NATIVE BUILD ===
API_BASE_URL=https://api.yourdomain.com/api
NODE_ENV=production

# === ARTIFACTORY ===
ARTIFACTORY_URL=http://artifactory-host:8081/artifactory
ARTIFACTORY_USER=admin
ARTIFACTORY_PASSWORD=<from-secret>
DOCKER_REGISTRY=artifactory-host:8082

# === KUBERNETES ===
KUBECONFIG=/path/to/kubeconfig
K8S_NAMESPACE=production
```

---

*Guide version 1.0 — Spring Boot + React Native DevOps Stack*