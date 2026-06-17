Description

Story 7.1 — Audit on-prem K8s clusters (dev/qa/prod)
Subtask 7.1.1 Capture: distro (kubeadm? k3s? RKE2?), version (kubectl version), node count, CNI, ingress controller, storage class, cert-manager presence.

Subtask 7.1.2 Run kubectl get nodes -o wide, kubectl top nodes, kube-bench (CIS benchmark), kubescape scan.

Subtask 7.1.3 Verify: RBAC enabled, audit logs on, PodSecurity admission = restricted for app namespaces, NetworkPolicy supported by CNI (Calico/Cilium).

Subtask 7.1.4 Install if missing: metrics-server, ingress-nginx, cert-manager, sealed-secrets, Prometheus + Grafana, Loki for logs.

Subtask 7.1.5 Prod recommendation: 3 control-plane + 3 workers (HA). Dev/QA can be single control-plane + 2 workers.

Subtask 7.1.6 Backup etcd nightly (etcdctl snapshot save) to NAS.

Acceptance: Audit report + remediation tickets created; kube-bench score documented.


# Kubernetes — On-Prem Cluster & Application Deployment Design

## 1. Scope
Covers the three on-prem clusters (`dev`, `qa`, `prod`), how the
`chat-backend` Spring Boot application is packaged, deployed and operated
on them, and the security / RBAC posture.

## 2. Cluster Topology

| Env  | Control-plane nodes | Worker nodes | Total vCPU | Total RAM | Storage |
|------|---------------------|--------------|------------|-----------|---------|
| dev  | 1                   | 2            | 12         | 24 GB     | local SSD |
| qa   | 1                   | 2            | 16         | 32 GB     | local SSD + NFS |
| prod | 3 (HA, stacked etcd)| 3            | 24         | 48 GB     | local SSD + NFS (RWX) |

- Distribution: **RKE2** (production-grade, CIS-hardened defaults, no Docker).
- Container runtime: `containerd`.
- CNI: **Calico** (NetworkPolicy + eBPF dataplane).
- Ingress: `ingress-nginx`.
- DNS: `coredns` (default).
- Storage classes:
    - `local-path` (default, dev/qa).
    - `nfs-rwx` (prod, backed by NAS) for any RWX volumes (currently none — app is stateless).
- Cert management: `cert-manager` with internal ACME via step-ca.
- Secrets at rest: `sealed-secrets` controller in each cluster; sealed
  YAML is committed to git.

## 3. Namespaces

| Namespace      | Purpose                                  |
|----------------|------------------------------------------|
| `kube-system`  | platform                                 |
| `ingress-nginx`| ingress controller                       |
| `cert-manager` | TLS issuance                             |
| `monitoring`   | Prometheus, Grafana, Loki, Alertmanager  |
| `chat-<env>`   | `chat-backend` app (one per env in same cluster only if multi-env shares cluster; in our case one cluster per env) |
| `infra`        | shared infra add-ons (sealed-secrets etc.) |

PodSecurity admission: `restricted` enforced on all `chat-*` namespaces.

## 4. Application Footprint — `chat-backend`

Deployed via a Helm chart that lives in this repo at
`deploy/helm/chat-backend/`. One values file per environment.

### 4.1 Workload shape

| Resource    | dev / qa | prod |
|-------------|----------|------|
| Replicas    | 2        | 3 (min), 6 (max via HPA) |
| CPU request | 250m     | 500m |
| CPU limit   | 1000m    | 1500m |
| Mem request | 768 Mi   | 1 Gi |
| Mem limit   | 1.5 Gi   | 2 Gi |
| JVM         | `-XX:MaxRAMPercentage=75` | same |

HPA: target CPU 70 %, min 2, max 6.
PodDisruptionBudget: `minAvailable: 1` (dev/qa), `minAvailable: 2` (prod).

### 4.2 Health endpoints (Spring Boot Actuator)

- `livenessProbe`  → `GET /actuator/health/liveness`  (initialDelay 30 s, period 10 s)
- `readinessProbe` → `GET /actuator/health/readiness` (initialDelay 10 s, period 5 s, failureThreshold 6)
- `startupProbe`   → `GET /actuator/health` (failureThreshold 30, period 5 s)

### 4.3 Configuration & secrets

- Non-secret config: `ConfigMap` rendered from `values-<env>.yaml`,
  mounted as env vars (Spring `SPRING_*` style).
- Secret config (DB password, Kafka SASL, JWT signing key): `SealedSecret`
  committed to git, decrypted in-cluster to `Secret`, mounted as env vars.
- No secrets in Docker images, no secrets in `values-*.yaml`.

## 5. Helm Chart Layout

```
deploy/helm/chat-backend/
├── Chart.yaml
├── values.yaml                # safe defaults
├── values-dev.yaml
├── values-qa.yaml
├── values-prod.yaml
└── templates/
    ├── _helpers.tpl
    ├── namespace.yaml         # optional, usually pre-created
    ├── serviceaccount.yaml
    ├── role.yaml
    ├── rolebinding.yaml
    ├── configmap.yaml
    ├── sealedsecret.yaml      # generated per env, committed
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    ├── hpa.yaml
    ├── pdb.yaml
    ├── networkpolicy.yaml
    └── servicemonitor.yaml    # Prometheus Operator CRD
```

### 5.1 Key template excerpts

`deployment.yaml` (security-relevant portion):
```yaml
spec:
  template:
    spec:
      serviceAccountName: chat-backend
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        runAsGroup: 1001
        fsGroup: 1001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: chat-backend
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8080
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
          volumeMounts:
            - name: tmp
              mountPath: /tmp
      volumes:
        - name: tmp
          emptyDir: {}
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              app.kubernetes.io/name: chat-backend
```

`networkpolicy.yaml` (default-deny + explicit allows):
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: chat-backend
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: chat-backend
  policyTypes: [Ingress, Egress]
  ingress:
    - from:
        - namespaceSelector:
            matchLabels: { name: ingress-nginx }
      ports:
        - port: 8080
  egress:
    - to: # DNS
        - namespaceSelector: { matchLabels: { kubernetes.io/metadata.name: kube-system } }
      ports: [{ protocol: UDP, port: 53 }]
    - to: # Kafka brokers
        - ipBlock: { cidr: 10.10.20.0/24 }
      ports: [{ port: 9092 }, { port: 9093 }]
    - to: # Postgres
        - ipBlock: { cidr: 10.10.30.0/24 }
      ports: [{ port: 5432 }]
```

`role.yaml` (least privilege — app only reads its own ConfigMap):
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: chat-backend
rules:
  - apiGroups: [""]
    resources: ["configmaps"]
    resourceNames: ["chat-backend-config"]
    verbs: ["get", "list", "watch"]
```

## 6. RBAC Policy (cluster-level)

| Subject | Role | Bound to |
|---|---|---|
| `developer` (LDAP group) | `view` ClusterRole | `chat-dev`, `chat-qa` |
| `oncall` (LDAP group) | `edit` ClusterRole | `chat-dev`, `chat-qa`, `chat-prod` (audited) |
| `sre` (LDAP group) | `cluster-admin` | cluster-wide |
| `jenkins-ci` (ServiceAccount, per env) | custom `app-deployer` Role | `chat-<env>` only |

`app-deployer` allows: `get/list/watch/create/update/patch/delete` on
`deployments`, `services`, `configmaps`, `secrets`, `ingresses`, `hpa`,
`pdb`, `networkpolicies` — scoped to the app namespace only.

## 7. Image Pull

- All images pulled from `harbor.internal/chat-backend-<env>/...`.
- `imagePullSecret` `harbor-pull` (a sealed secret), one per namespace.
- Cluster-wide policy: deny images outside `harbor.internal/*` (validated
  via Kyverno policy `disallow-external-registries`).

## 8. Ingress & TLS

- `ingress-nginx` with TLS terminated at the ingress.
- Hostnames:
    - dev:  `chat-dev.internal`
    - qa:   `chat-qa.internal`
    - prod: `chat.internal`
- Certs issued by `cert-manager` ClusterIssuer `internal-ca`.
- WAF: ModSecurity CRS enabled on ingress (prod only).

## 9. Observability

- **Metrics**: Spring Boot exposes Prometheus at `/actuator/prometheus`.
  A `ServiceMonitor` scrapes every 15 s.
- **Logs**: stdout/stderr → containerd → promtail → Loki.
- **Traces**: Micrometer Tracing → OTLP → Tempo (prod only).
- **Dashboards** (Grafana, all pre-provisioned via ConfigMaps):
    - JVM & Spring Boot
    - Pod / node resources
    - Ingress request rate / 5xx
    - Kafka consumer lag (from `kafka-exporter`)
    - Postgres (from `postgres_exporter`)

## 10. Deployment Flow (Jenkins → cluster)

```
1. Jenkins pipeline (see Jenkinsfile) builds + pushes image to Harbor.
2. Pipeline runs:
     helm upgrade --install chat-backend deploy/helm/chat-backend \
        -n chat-${ENV} \
        -f deploy/helm/chat-backend/values-${ENV}.yaml \
        --set image.tag=${GIT_SHA} \
        --wait --timeout 5m --atomic
3. --atomic causes automatic rollback if rollout fails.
4. Pipeline then polls /actuator/health/readiness until UP (max 2 min).
5. Newman/Postman integration suite runs against the env URL.
6. On prod, an "input" approval gate precedes step 2.
```

Rollback (manual):
```
helm history chat-backend -n chat-prod
helm rollback chat-backend <REVISION> -n chat-prod --wait
```

## 11. Backup / DR

- **etcd**: snapshot every 6 h via RKE2's built-in scheduler; 7-day retention on NAS.
- **Cluster state as code**: Helm charts + sealed secrets in git. A
  cluster can be rebuilt from scratch + `helm upgrade --install` for each
  workload.
- **Application state**: lives in Postgres / Kafka, not in K8s — see
  their respective docs.
- **DR target**: RTO 4 h, RPO 6 h (etcd snapshot interval).

## 12. Security Baseline

- CIS benchmark via `kube-bench` — required score: ≥ 90 %.
- `kubescape scan framework nsa,mitre` run weekly in CI.
- PodSecurity `restricted` enforced on `chat-*` namespaces.
- Kyverno policies (cluster-wide):
    - Disallow `latest` image tag.
    - Require non-root, readOnlyRootFilesystem, drop ALL capabilities.
    - Require resource requests + limits.
    - Allow images only from `harbor.internal/*`.
- Audit log: 30-day retention, shipped to Loki, alert on
  `verb=delete && resource in (deployments, secrets)` outside business hours.

## 13. Capacity & Scaling Triggers

| Trigger | Action |
|---|---|
| Pod CPU > 70 % for 10 min | HPA scales out (already automatic) |
| Node CPU > 75 % cluster-wide for 30 min | Add a worker node |
| Cluster `Pending` pods > 0 for 5 min | Add a worker node / check requests |
| Ingress p99 latency > 500 ms | Check JVM heap, Kafka lag, DB |

## 14. Open Decisions / Follow-ups

- Evaluate moving prod to a managed K8s when on-prem footprint > 6 workers.
- Adopt Argo CD for GitOps once the Helm-via-Jenkins flow is stable.
- Add OPA/Gatekeeper or extend Kyverno for org-wide policies.
- Add chaos testing (LitmusChaos) in qa before prod cutover.
