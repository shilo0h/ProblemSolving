Description

Story 8.1 — Implement Jenkinsfile with all stages
Subtask 8.1.1 Stage skeleton (declarative, agent on K8s pod template maven+docker+kubectl+newman):

Checkout — checkout scm, capture commit SHA → image tag.

Clean Build — ./mvnw -B -U clean compile.

Unit + Slice Tests — ./mvnw test; publish JUnit + JaCoCo.

SonarQube Analysis — ./mvnw sonar:sonar -Dsonar.projectKey=chat-backend; then waitForQualityGate abortPipeline: true inside timeout(10).

Package — ./mvnw -DskipTests package.

Build Docker Image — docker build -t harbor.local/chat-backend-${ENV}/chat-backend:${GIT_SHA} ..

Trivy Scan — trivy image --exit-code 1 --severity HIGH,CRITICAL ....

Push to Harbor — docker login (creds from Jenkins) → docker push. Also tag :${ENV}-latest.

Deploy to K8s — helm upgrade --install chat-backend deploy/helm/chat-backend -n chat-${ENV} -f values-${ENV}.yaml --set image.tag=${GIT_SHA} --wait --timeout 5m.

Health Gate — poll https://chat-${ENV}.internal/actuator/health until UP or fail after 2 min (retry(24){ sleep 5; sh 'curl -fsS ...' }).

Integration Tests (Newman) — newman run postman/chat-backend.postman_collection.json -e postman/${ENV}.postman_environment.json --reporters cli,junit --reporter-junit-export newman-report.xml. Publish JUnit.

Post — always: archive reports; on failure: Slack/Teams notify; on success in prod: tag git release-${BUILD_NUMBER}.

Subtask 8.1.2 Branch strategy:

feature/* → stages 1–7 only (no deploy).

develop → deploy to dev.

release/* → deploy to qa.

main (tagged) → prod, with input manual approval before stage 9.

Subtask 8.1.3 Parameterize: ENV, SKIP_TESTS (default false), FORCE_DEPLOY.

Subtask 8.1.4 Credentials used: harbor-creds, sonar-token, kubeconfig-${ENV}, git-tagger.

Subtask 8.1.5 Add options { timeout(time: 30, unit: 'MINUTES'); buildDiscarder(logRotator(numToKeepStr:'30')); disableConcurrentBuilds() }.

Subtask 8.1.6 Postman collection lives in repo under postman/; environment files contain only non-secret URLs; secrets injected via --env-var token=$QA_TOKEN.

Acceptance: Green pipeline end-to-end on develop → dev cluster; failing Sonar gate blocks; failing healthcheck blocks Newman; rollback documented (helm rollback).