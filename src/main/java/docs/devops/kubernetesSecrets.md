# Developer Guide: Spring Boot Database Credentials with Kubernetes Secrets

## 1. Purpose

This guide configures a Spring Boot service to receive database credentials from Kubernetes without storing usernames or passwords in source control, application YAML, Helm values, or container images.

The target flow is:

```text
External Secret Manager
        ↓
Kubernetes secret synchronization
        ↓
Kubernetes Secret
        ↓
Container environment variables
        ↓
Spring Boot datasource configuration
        ↓
Database connection pool
```

Delivering credentials does not complete database security. The database account must be environment-specific, restricted to the application's required schema operations, protected with TLS, monitored, and rotated without interrupting in-flight work.

## 2. Responsibilities

| Responsibility | Owner |
|---|---|
| Provision an application-specific database account | Database/platform team |
| Define least-privilege database grants | Database and service teams |
| Store and rotate the external secret | Platform/security team |
| Configure secret synchronization and workload identity | Platform team |
| Reference the Kubernetes Secret from the Deployment | Service team |
| Bind and validate the Spring datasource properties | Service team |
| Test connection-pool behavior during rotation | Platform and service teams |

## 3. Naming convention

Use consistent names across each layer:

| Layer | Recommended name |
|---|---|
| External secret path | `/services/payment-service/dev/database` |
| External JSON property | `username` |
| External JSON property | `password` |
| Kubernetes Secret | `payment-service-database` |
| Environment variable | `DB_USERNAME` |
| Environment variable | `DB_PASSWORD` |
| Spring property | `spring.datasource.username` |
| Spring property | `spring.datasource.password` |

Change `payment-service`, `payments-dev`, and the external secret path to match the service and namespace being deployed.

The JDBC URL is normally non-secret configuration and can come from a ConfigMap or deployment value as `DB_URL`. If the URL contains embedded credentials or other sensitive parameters, redesign it so credentials remain separate.

## 4. Spring Boot configuration

Add the following to `application.yml`:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Do not provide credential defaults:

```yaml
# Do not do this
spring:
  datasource:
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:password}
```

Missing credentials should cause startup to fail rather than silently enabling a known or privileged default. Use startup validation and a database readiness check that reports availability without returning credentials or sensitive connection details.

Spring Boot maps these values into the configured datasource and connection pool. Do not add a second custom database credential namespace unless the service genuinely manages multiple datasources.

## 5. External secret

Create the following record in the external secret manager.

**Secret path**

```text
/services/payment-service/dev/database
```

**Secret value**

```json
{
  "username": "payment_service_dev",
  "password": "<random-generated-password>"
}
```

Development, QA, and production must use separate accounts and secrets:

```text
/services/payment-service/dev/database
/services/payment-service/qa/database
/services/payment-service/prod/database
```

Never copy production database credentials into development. Do not use `root`, database-owner, migration-admin, or shared human credentials as the runtime application account.

The runtime account should have only the required data operations. Run schema migrations through a separately controlled identity when elevated DDL privileges are required.

## 6. Kubernetes synchronization

If the platform already creates a Kubernetes Secret named `payment-service-database`, skip this section.

For External Secrets Operator, define:

```yaml
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata:
  name: payment-service-database
  namespace: payments-dev
spec:
  refreshInterval: 15m
  secretStoreRef:
    name: dev-secret-store
    kind: SecretStore

  target:
    name: payment-service-database
    creationPolicy: Owner

  data:
    - secretKey: DB_USERNAME
      remoteRef:
        key: /services/payment-service/dev/database
        property: username

    - secretKey: DB_PASSWORD
      remoteRef:
        key: /services/payment-service/dev/database
        property: password
```

Apply it:

```bash
kubectl apply -f payment-service-database-secret.yaml
```

This manifest can be committed because it contains secret references, not secret values. See the [External Secrets Operator guide](https://external-secrets.io/latest/introduction/getting-started/).

The synchronizing operator should use workload identity and least-privilege access to the exact external secret. Avoid long-lived cloud access keys stored inside another Kubernetes Secret.

## 7. Deployment configuration

Inject only the required keys into the application container:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: payments-dev
spec:
  template:
    spec:
      containers:
        - name: payment-service
          env:
            - name: DB_URL
              valueFrom:
                configMapKeyRef:
                  name: payment-service-database-config
                  key: DB_URL

            - name: DB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: payment-service-database
                  key: DB_USERNAME

            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: payment-service-database
                  key: DB_PASSWORD
```

Prefer explicit `secretKeyRef` mappings over `envFrom`. This exposes only the values the container requires. Kubernetes documents this pattern in its [Secret injection guide](https://kubernetes.io/docs/tasks/inject-data-application/distribute-credentials-secure/).

The application Pod does not need permission to call the external secret manager when an operator performs synchronization. Do not grant the application service account direct secret-manager access unnecessarily.

## 8. Deployment verification

Confirm that synchronization succeeded:

```bash
kubectl wait \
  --for=condition=Ready \
  externalsecret/payment-service-database \
  -n payments-dev \
  --timeout=60s
```

Confirm that the Kubernetes Secret exists:

```bash
kubectl describe secret payment-service-database -n payments-dev
```

`kubectl describe` shows key names and sizes without displaying their values.

Restart and monitor the application:

```bash
kubectl rollout restart deployment/payment-service -n payments-dev
kubectl rollout status deployment/payment-service -n payments-dev
```

Check startup logs:

```bash
kubectl logs deployment/payment-service -n payments-dev
```

Verify that the datasource and readiness check succeed without printing the JDBC URL, username, password, or full driver exception containing sensitive parameters.

Do not use a state-changing SQL statement as a health check. A read-only `SELECT 1`-style probe is sufficient to validate connectivity.

## 9. Rotation

Environment variables are fixed when a container starts. Updating the Kubernetes Secret does not update an existing process until its Pod restarts.

Connection pools add a second constraint: existing authenticated connections can remain usable while new connections use the new credential. A password change can therefore appear healthy until the pool replaces a connection.

The preferred rotation sequence is:

1. Create a new database credential while the old credential remains valid. A second database user is safer than changing one password in place.
2. Update the external secret to the new username and password.
3. Wait for the Kubernetes Secret to synchronize.
4. Roll the Deployment so every Pod starts a new connection pool.
5. Verify new connections, readiness, transaction processing, and error rates.
6. Confirm that no old Pods or background workers remain.
7. Revoke the old database credential.
8. Record who rotated it, when it changed, and which deployment consumed it.

After the synchronized Secret changes:

```bash
kubectl rollout restart deployment/payment-service -n payments-dev
kubectl rollout status deployment/payment-service -n payments-dev
```

If the database supports only an in-place password change for one username, coordinate the database update, secret synchronization, and rollout as one controlled operation. Expect a transient authentication risk and define a rollback credential before starting.

For a money-moving service, do not blindly retry business operations after database-authentication failures. Retry only at transaction-safe boundaries and preserve request idempotency so recovery cannot duplicate a debit or payment instruction.

## 10. Local IntelliJ development

Use the same environment-variable contract locally:

```text
DB_URL=<local-or-developer-database-jdbc-url>
DB_USERNAME=<developer-specific-database-user>
DB_PASSWORD=<developer-specific-random-password>
```

Configure these in a private IntelliJ Run Configuration or inject them through an approved password-manager launch integration.

Do not store credentials in:

- `application-local.yml`
- `.env` committed to Git
- Helm values
- Dockerfiles
- shared onboarding documents
- shell scripts committed to the repository

Prefer a local containerized database or an individual development account. Do not download a shared cluster or production database credential to a developer laptop.

## 11. Security requirements

Before considering the setup complete:

- Use a separate database account for each service and environment.
- Grant only the required schema, table, and operation privileges.
- Keep DDL and migration privileges out of the runtime account.
- Require database TLS and validate the server certificate.
- Restrict database network access to the required workloads.
- Enable Kubernetes Secret encryption at rest.
- Use namespace-scoped secret synchronization.
- Grant least-privilege Kubernetes RBAC and external-manager IAM.
- Do not grant the application service account permission to list or read arbitrary Secrets.
- Audit database logins, grant changes, credential rotation, and deployment restart.
- Never place credential values in Git, CI logs, exception messages, or observability labels.

Kubernetes Secrets are Base64-encoded and are not necessarily encrypted in `etcd`. Follow the official [Kubernetes Secret security guidance](https://kubernetes.io/docs/concepts/configuration/secret/).

## 12. Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| `ExternalSecret` is not ready | Wrong remote path, property name, or IAM permission | Run `kubectl describe externalsecret payment-service-database -n payments-dev` |
| Kubernetes Secret is missing | Synchronization operator failed | Check the `ExternalSecret` events and operator logs |
| Pod reports a missing variable | Secret key or namespace mismatch | Compare `secretKeyRef` with the Kubernetes Secret keys |
| Database reports authentication failure | Wrong username/password, unsynchronized rotation, or revoked account | Check secret versions and rollout state without printing values |
| Database connection times out | Network policy, DNS, firewall, JDBC URL, or database availability | Diagnose connectivity before changing credentials |
| Rotation has no immediate effect | Existing Pods or pooled connections still use the old session | Roll all workloads and force new connection pools |
| Authentication failures are intermittent | Mixed Pod versions or old credential revoked too early | Inspect replica age and complete or roll back the rotation |
| Database reaches its connection limit | Replica count multiplied by pool size exceeds capacity | Reduce pool size or replicas; this is not a secret failure |
| Credentials appear in logs | Unsafe debugging or exception handling | Remove the values and rotate the exposed credential |

## 13. Definition of done

The integration is complete only when:

1. No secret values exist in Git, Helm values, or application configuration.
2. The database account is unique to the service and environment.
3. The runtime account has no unnecessary administrative or DDL privileges.
4. The external secret synchronizes successfully into the correct namespace.
5. The Pod starts only when the URL, username, and password are present.
6. The datasource and readiness check establish a TLS-protected connection.
7. Rotation creates new connection pools before the old credential is revoked.
8. The new credential succeeds and the old credential fails after cutover.
9. Failed connections and stuck rollouts produce actionable alerts.
10. Secret reads, database authentication, rotations, and grants have an audit trail.