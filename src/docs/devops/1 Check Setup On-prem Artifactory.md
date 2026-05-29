Description

First: Check if this exists and report back to me.

Goal: Single source of truth for Maven/NPM/Helm artifacts across dev/qa/prod.



Story 1.1 — Provision JFrog Artifactory OSS (or Nexus 3) on prem
Subtask 1.1.1 Choose product: JFrog Artifactory OSS vs Nexus Repository OSS. Recommendation: Nexus 3 OSS (free Docker + Maven + npm + Helm in one). Document decision in docs/architecture/artifactory.md.

Subtask 1.1.2 Allocate VM resources: 4 vCPU / 8 GB RAM / 200 GB SSD (separate disk for blob store, mounted /nexus-data).

Subtask 1.1.3 Install via Docker Compose with persistent volume; expose on :8081 (UI) and :8082 (Docker connector). Run behind nginx with TLS (Let's Encrypt internal CA or self‑signed + internal trust).

Subtask 1.1.4 Configure repos:

maven-releases, maven-snapshots, maven-central (proxy), maven-public (group).

npm-proxy, npm-hosted, npm-group.

Subtask 1.1.5 Create roles: ci-deployer (write), developer (read), anonymous disabled. Store creds in Jenkins credentials store.

Subtask 1.1.6 Backup: nightly tar of /nexus-data/blobs + DB to NAS; 14‑day retention. Document restore drill.




# Binary Repository (Artifactory / Nexus) — On-Prem Design

## 1. Purpose
A single, internally-hosted source of truth for every binary artifact the
organisation produces or consumes:

- Java/Maven artifacts (the `chat-backend` JAR and its internal libs).
- npm packages (frontend, Postman/Newman tooling).
- Helm charts (`chat-backend` deployment chart).
- Docker images (delegated to Harbor — see `docker-registry.md`).
- Generic files (installers, SBOMs, signed release bundles).

Goals:
1. Remove the dependency on public registries during a build (reliability + supply-chain control).
2. Enforce **promotion** (dev → qa → prod) instead of rebuilding per environment.
3. Provide a single place for vulnerability scans, retention and audit.

## 2. Product Decision

| Criterion | JFrog Artifactory OSS | Sonatype Nexus 3 OSS | Decision |
|---|---|---|---|
| Maven / npm proxy | ✅ | ✅ | tie |
| Docker registry in OSS tier | ❌ (Pro only) | ✅ | Nexus |
| Helm repo | Pro | ✅ | Nexus |
| Built-in cleanup policies | Pro | ✅ | Nexus |
| RBAC granularity | Good | Good | tie |
| UI/UX | Slightly better | Acceptable | JFrog |
| License cost | Free OSS limited | Free OSS full | **Nexus 3 OSS** |

**Chosen product: Sonatype Nexus Repository 3 OSS.**
Docker images are *not* stored here — Harbor is used for that (image scanning,
signing and replication are first-class in Harbor).

## 3. Topology

```
                  ┌────────────────────────────┐
                  │   nexus.internal:443       │  TLS (internal CA)
                  │   nginx reverse proxy      │
                  └────────────┬───────────────┘
                               │
                  ┌────────────▼───────────────┐
                  │   nexus3 (Docker container)│
                  │   /nexus-data (200 GB SSD) │
                  │   /nexus-backup (NAS mount)│
                  └────────────────────────────┘
```

One Nexus instance shared by **dev / qa / prod** Jenkins. Environment
isolation is enforced via repository naming and RBAC, not separate Nexus
servers (one binary repo per org is industry standard).

## 4. Sizing

| Item | Value | Rationale |
|---|---|---|
| vCPU | 4 | Indexing + concurrent uploads |
| RAM | 8 GB | `-Xms2g -Xmx4g` + OS cache |
| Disk (data) | 200 GB SSD | Grows ~1–2 GB / month at our build rate |
| Disk (backup) | 500 GB NAS | 14 days of nightly snapshots |
| JVM | OpenJDK 17 (bundled in image) | — |
| OS | Ubuntu 22.04 LTS | Same as other VMs |

Re-evaluate when `/nexus-data` > 60 % full or daily uploads > 5 GB.

## 5. Repository Layout

### 5.1 Maven
| Name | Type | Purpose |
|---|---|---|
| `maven-central` | proxy → repo.maven.apache.org | Public deps cache |
| `maven-releases` | hosted | Our release JARs |
| `maven-snapshots` | hosted | Our SNAPSHOT JARs |
| `maven-public` | group | What developers/Jenkins point at |

Developer `~/.m2/settings.xml` only ever references `maven-public`.

### 5.2 npm
| Name | Type |
|---|---|
| `npm-proxy` | proxy → registry.npmjs.org |
| `npm-hosted` | hosted (internal packages) |
| `npm-group` | group |

### 5.3 Helm
| Name | Type |
|---|---|
| `helm-hosted` | hosted (our charts, e.g. `chat-backend`) |
| `helm-stable` | proxy → charts.bitnami.com (optional) |

### 5.4 Raw (generic)
`raw-internal` — SBOMs, signed release bundles, runbook attachments.

## 6. Security

- TLS: nginx terminates TLS using the internal CA. Plain HTTP disabled.
- Authentication: LDAP / AD realm (fallback local admin only).
- Anonymous access: **disabled**.
- Roles:
    - `nx-admin` — platform team only.
    - `ci-deployer` — write on `*-releases`, `*-snapshots`, `helm-hosted`, `raw-internal`. Used by Jenkins.
    - `developer` — read on all `*-public`, `*-group`, `helm-stable`. Write to `*-snapshots`.
    - `read-only` — read on `*-public` (for ad-hoc / scripts).
- All API tokens stored only in Jenkins Credentials (id: `nexus-ci-deployer`).
- HTTP audit log shipped to Loki.
- Disable insecure protocols (`Allow redeploy` = **disabled** on `*-releases`).

## 7. Promotion Strategy

We **build once, promote the same artifact**:

```
build → maven-snapshots  (every commit on develop)
      → maven-releases   (only on tag, by Jenkins release job)
      → "promoted" property added on QA sign-off
      → consumed by prod deploy job
```

No rebuild between environments — environments differ only in config (K8s
values files), never in bytecode.

## 8. Cleanup / Retention

Configured per repository under **Repository → Cleanup Policies**:

| Repo | Policy |
|---|---|
| `maven-snapshots` | Delete components older than 30 days that are not the latest 3 versions. |
| `maven-releases` | Never delete. |
| `npm-proxy`, `maven-central` | Delete cached blobs not downloaded for 90 days. |
| `helm-hosted` | Keep latest 10 versions per chart. |

Scheduled task `Cleanup unused asset blobs` runs nightly at 02:00.

## 9. Backup & DR

- **Nightly** (cron `0 1 * * *` on Nexus host):
    1. `nexus stop`
    2. `tar -czf /nexus-backup/nexus-$(date +%F).tgz /nexus-data`
    3. `nexus start`
    4. Retention: 14 daily + 4 weekly on NAS.
- **Weekly restore drill** in QA: untar latest into a throwaway VM, start
  Nexus, run `mvn dependency:resolve` against it. Documented in
  `docs/architecture/runbooks/nexus-restore.md`.
- RPO: 24 h. RTO: 2 h (full restore on spare VM).

## 10. Client Configuration

### 10.1 Maven (`~/.m2/settings.xml`)
```xml
<settings>
  <mirrors>
    <mirror>
      <id>nexus-public</id>
      <url>https://nexus.internal/repository/maven-public/</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
  <servers>
    <server>
      <id>nexus-snapshots</id>
      <username>${env.NEXUS_USER}</username>
      <password>${env.NEXUS_TOKEN}</password>
    </server>
  </servers>
</settings>
```

### 10.2 `pom.xml` distributionManagement
```xml
<distributionManagement>
  <snapshotRepository>
    <id>nexus-snapshots</id>
    <url>https://nexus.internal/repository/maven-snapshots/</url>
  </snapshotRepository>
  <repository>
    <id>nexus-releases</id>
    <url>https://nexus.internal/repository/maven-releases/</url>
  </repository>
</distributionManagement>
```

### 10.3 npm
```
echo "registry=https://nexus.internal/repository/npm-group/" >> ~/.npmrc
```

## 11. Monitoring

- `node_exporter` on the VM → Prometheus.
- Nexus JMX metrics scraped via `jmx_exporter` agent.
- Grafana dashboard: heap, blob store size, request rate, 5xx rate.
- Alertmanager rules:
    - `nexus_disk_free_percent < 20` → warn.
    - `nexus_disk_free_percent < 10` → page.
    - `nexus_up == 0 for 2m` → page.

## 12. Operational Runbook (summary)

| Task | Command |
|---|---|
| Restart | `sudo systemctl restart nexus` |
| Tail logs | `docker logs -f nexus` |
| Check blob store | UI → System → Support → Analyze blob store |
| Compact DB | UI → System → Tasks → "Admin - Compact blob store" |
| Rotate admin pwd | `nexus-cli security user update admin --password ...` |

## 13. Open Decisions / Follow-ups

- Decide whether to enable **staging suite** (Pro feature) or implement
  promotion via metadata/properties (chosen for now, OSS-only).
- Evaluate `cosign` signing of Maven artifacts (post-Q1).
- Migrate raw secrets store off `raw-internal` once Vault is live.
