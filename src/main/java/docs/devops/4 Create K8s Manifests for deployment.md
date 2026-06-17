Description

Story 4.1 — Base manifests with RBAC
Subtask 4.1.1 Create Helm chart deploy/helm/chat-backend/ with templates:

namespace.yaml (one per env: chat-dev, chat-qa, chat-prod).

serviceaccount.yaml (one per app, no cluster-admin).

role.yaml + rolebinding.yaml — only get/list on configmaps/secrets in own namespace.

deployment.yaml — replicas from values, resources.requests/limits (start 500m/1Gi, limit 1500m/2Gi), readinessProbe /actuator/health/readiness, livenessProbe /actuator/health/liveness, securityContext: runAsNonRoot, readOnlyRootFilesystem, allowPrivilegeEscalation:false, capabilities.drop:[ALL].

service.yaml — ClusterIP, port 8080.

ingress.yaml — nginx-ingress, TLS via cert-manager.

configmap.yaml + secret.yaml (sealed-secrets or external-secrets operator).

hpa.yaml — min 2, max 6, CPU 70%.

networkpolicy.yaml — deny-all egress except Kafka/Postgres/DNS.

pdb.yaml — minAvailable: 1.

Subtask 4.1.2 values-dev.yaml, values-qa.yaml, values-prod.yaml (image tag, replicas, resources, hostnames).

Subtask 4.1.3 helm lint + kubeval in CI.

Subtask 4.1.4 Document in docs/architecture/k8s.md.

Acceptance: helm upgrade --install works in all 3 envs; pod runs non-root; RBAC verified via kubectl auth can-i.