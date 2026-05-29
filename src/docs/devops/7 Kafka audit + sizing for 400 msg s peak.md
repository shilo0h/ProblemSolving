Description

Story 6.2 — Kafka audit + sizing for 400 msg/s peak
Subtask 6.2.1 Inventory: broker count, version, server.properties, topic list, current replication factor, ZooKeeper vs KRaft.

Subtask 6.2.2 Load math:

Steady state: 3 000 msg/day ≈ 0.035 msg/s. Trivial.

Peak target: 400 msg/s. Assume avg message 2 KB → 0.8 MB/s ingress. Well within a single broker's capacity (a single broker easily does 50–100 MB/s on commodity HW).

Subtask 6.2.3 Recommended topology:

Prod: 3 brokers, RF=3, min.insync.replicas=2, acks=all on producer → tolerates 1 broker loss with zero data loss.

QA: 3 brokers, RF=2 (cost compromise) or 1 broker if budget tight.

Dev: 1 broker, RF=1.

Migrate to KRaft (no ZooKeeper) if Kafka ≥ 3.5.

Subtask 6.2.4 Topic config for chat-events (and similar):

partitions = 6 (gives you headroom; 400 msg/s ÷ 6 = ~67 msg/s/partition, room to scale consumers to 6 in parallel).

replication.factor = 3, min.insync.replicas = 2.

retention.ms = 7 days (tune to compliance).

compression.type = lz4 (producer side too).

Subtask 6.2.5 Producer config: acks=all, enable.idempotence=true, linger.ms=10, batch.size=32768, compression.type=lz4.

Subtask 6.2.6 Consumer config: enable.auto.commit=false (manual commit after processing), max.poll.records=500, set a consumer group per service.

Subtask 6.2.7 Tradeoffs to document: | Choice | Pro | Con | |---|---|---| | acks=all + min.isr=2 | No data loss on 1 broker failure | ~2–3× latency vs acks=1 (still < 20 ms locally) | | 6 partitions vs 1 | Parallel consumers, easy scale to 400 msg/s | Per-partition ordering only; cross-key ordering lost | | RF=3 | Survives 1 broker loss | 3× disk usage | | LZ4 compression | ~50–70 % bandwidth save | small CPU cost on producer | | KRaft | No ZK to operate | Newer, less battle-tested in your org |

Subtask 6.2.8 Monitoring: JMX → Prometheus (kafka-exporter for consumer lag), alert if lag > 1 000 or under-replicated partitions > 0.

Subtask 6.2.9 Headroom test: load test at 2× peak = 800 msg/s for 30 min in QA; record p99 latency.

Acceptance: Sizing doc merged; QA load test green at 800 msg/s; alerts firing in test.