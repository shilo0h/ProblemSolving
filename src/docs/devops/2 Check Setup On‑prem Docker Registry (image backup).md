Description

First:Check status of what we currently have on-prem and report back to me. There must be an equivalent on Azure, but i don’t know if we are paying for it (check with sead)

Story 2.1 — Enable Docker registry inside Nexus (or standalone Harbor)
Subtask 2.1.1 Decision: Harbor if you need image scanning + signing + replication; Nexus Docker repo if you want one tool. Recommendation for your scale: Harbor (free Trivy scan + RBAC + retention policies).

Subtask 2.1.2 Install Harbor via harbor-installer offline package; storage 500 GB; Postgres + Redis bundled.

Subtask 2.1.3 Configure HTTPS with internal CA. Add the CA to every Docker daemon (/etc/docker/certs.d/<registry>/ca.crt) and to K8s nodes (containerd config).

Subtask 2.1.4 Create projects per env: chat-backend-dev, chat-backend-qa, chat-backend-prod. Enable Trivy vulnerability scan on push and prevent pull if CRITICAL.

Subtask 2.1.5 Retention policy: keep last 10 tags per project + everything tagged prod-* forever.

Subtask 2.1.6 Replicate prod project to a secondary location (NAS or DR VM) nightly.

Acceptance: docker push from Jenkins works; scan report visible; pull works from K8s.


