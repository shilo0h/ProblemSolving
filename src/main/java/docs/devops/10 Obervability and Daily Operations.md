Description

First: Check the equivelant technologies that we can setup  on Azure.

This needs to be discussed if we want to set it up. Nice to have on the operational side.

Story 9.1 Prometheus + Grafana dashboards: JVM, Kafka lag, Postgres, K8s pod, Jenkins build duration.

Story 9.2 Loki + Promtail for centralized logs; correlation via traceId (Micrometer Tracing already in Spring Boot 3).

Story 9.3 Alertmanager rules: consumer lag, pod restarts > 3/15 min, p99 latency, disk > 80 %.

Story 9.4 Runbooks in docs/architecture/runbooks/.