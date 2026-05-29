Description

Story 6.1 — Postgres audit (dev/qa/prod)
Subtask 6.1.1 Capture version, postgresql.conf deltas vs default, pg_hba.conf, current DB size, slow query log status.

Subtask 6.1.2 Baseline tuning for your VMs (assume 8 GB RAM):

shared_buffers = 2GB, effective_cache_size = 6GB, work_mem = 16MB, maintenance_work_mem = 256MB, wal_level = replica, max_wal_size = 2GB, checkpoint_completion_target = 0.9.

Subtask 6.1.3 Enable pg_stat_statements; ship metrics to Prometheus via postgres_exporter.

Subtask 6.1.4 Backups: pg_basebackup weekly + WAL archiving to NAS; PITR tested in QA.

Subtask 6.1.5 TLS between app and DB; per-env DB users (least privilege); Flyway migrations from src/main/resources/db.migration run by app on startup in dev/qa, manual gated job in prod.