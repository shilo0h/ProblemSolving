# Spring Boot Actuator Guide
## Setup · Security · Environment Profiles · Kafka & PostgreSQL

---

## 1. Adding the Actuator Dependency

**Maven (`pom.xml`)**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Required for secured actuator endpoints -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**Gradle (`build.gradle`)**
```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'org.springframework.boot:spring-boot-starter-security'
```

---

## 2. Understanding Key Endpoints

| Endpoint | Description | Sensitive? |
|---|---|---|
| `/actuator/health` | App & component health status | Partially |
| `/actuator/info` | App metadata (version, git, etc.) | No |
| `/actuator/metrics` | JVM, HTTP, custom metrics | Yes |
| `/actuator/env` | Environment properties & config | **Very** |
| `/actuator/loggers` | View & change log levels at runtime | Yes |
| `/actuator/threaddump` | JVM thread dump | Yes |
| `/actuator/heapdump` | JVM heap dump (download) | **Very** |
| `/actuator/httptrace` | Recent HTTP request/response traces | Yes |
| `/actuator/scheduledtasks` | Scheduled task definitions | Yes |
| `/actuator/kafka` | Kafka consumer group info | Yes |

---

## 3. Project Structure for Multi-Environment Config

```
src/main/resources/
├── application.yml              # Shared/base config
├── application-staging.yml      # Staging overrides
└── application-prod.yml         # Production overrides
```

Activate profiles via:
- Environment variable: `SPRING_PROFILES_ACTIVE=prod`
- JVM arg: `-Dspring.profiles.active=prod`
- Kubernetes/Docker: ENV in your deployment manifest

---

## 4. Base Configuration (`application.yml`)

```yaml
spring:
  application:
    name: my-service

# Base actuator config (restrictive by default)
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health, info       # Minimal by default; profiles add more
  endpoint:
    health:
      show-details: never           # Overridden per profile
      show-components: never
    info:
      enabled: true
  info:
    env:
      enabled: true
    git:
      mode: simple                  # Includes git commit info
    build:
      enabled: true

# App info (appears in /actuator/info)
info:
  app:
    name: ${spring.application.name}
    version: @project.version@      # Maven/Gradle token replacement
    description: My Spring Boot Service
```

---

## 5. Staging Configuration (`application-staging.yml`)

Wide open for debugging, but still on a non-standard port and path.

```yaml
management:
  server:
    port: 8081                       # Separate port; not exposed externally in K8s
  endpoints:
    web:
      exposure:
        include: "*"                 # All endpoints available in staging
  endpoint:
    health:
      show-details: always           # Show DB, Kafka, disk health details
      show-components: always
    env:
      show-values: always            # Show actual env var values (staging only!)
  health:
    db:
      enabled: true
    kafka:
      enabled: true
    diskspace:
      enabled: true

# Staging: Prometheus scraping enabled
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      environment: staging
      application: ${spring.application.name}
```

**Staging Security — permit all on the management port:**

```java
@Configuration
@Profile("staging")
public class StagingActuatorSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
```

---

## 6. Production Configuration (`application-prod.yml`)

Locked down: minimal exposure, details hidden, management on a separate internal port.

```yaml
management:
  server:
    port: 8081                        # Internal-only port; block at network/K8s level
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, loggers
        # Deliberately excluded: env, heapdump, threaddump, httptrace, shutdown
  endpoint:
    health:
      show-details: when-authorized   # Only show internals to authenticated ACTUATOR_ADMIN
      show-components: when-authorized
    env:
      show-values: never              # Never expose raw env values in prod
    loggers:
      enabled: true                   # Useful for runtime log level changes
    shutdown:
      enabled: false                  # Never enable in prod
  health:
    db:
      enabled: true
    kafka:
      enabled: true
    diskspace:
      enabled: true

  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      environment: prod
      application: ${spring.application.name}
```

**Production Security — role-protected actuator:**

```java
@Configuration
@Profile("prod")
public class ProdActuatorSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                // Public health check (liveness/readiness probes)
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                // Prometheus scraping — permit from internal network (or use IP filter)
                .requestMatchers(EndpointRequest.to("prometheus")).hasRole("PROMETHEUS")
                // Everything else requires ACTUATOR_ADMIN role
                .anyRequest().hasRole("ACTUATOR_ADMIN")
            )
            .httpBasic(Customizer.withDefaults())   // Or replace with your SSO/OAuth2
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
```

**Corresponding `application-prod.yml` security users (use Vault/K8s secrets in practice):**

```yaml
spring:
  security:
    user:
      name: actuator-admin
      password: ${ACTUATOR_ADMIN_PASSWORD}   # Injected from secret
      roles: ACTUATOR_ADMIN
```

---

## 7. PostgreSQL Health & Metrics

Spring Boot auto-configures the `DataSource` health indicator when your datasource is on the classpath. No extra code needed, but tune it:

```yaml
# application.yml
management:
  health:
    db:
      enabled: true

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      connection-timeout: 3000
      maximum-pool-size: 20
      minimum-idle: 5
      # Exposes Hikari pool metrics to /actuator/metrics
      register-mbeans: true
```

**What `/actuator/health` shows with PostgreSQL:**

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

**Key PostgreSQL metrics available at `/actuator/metrics`:**

```
hikaricp.connections.active
hikaricp.connections.idle
hikaricp.connections.pending
hikaricp.connections.timeout
hikaricp.connections.acquire      (latency histogram)
hikaricp.connections.creation
hikaricp.connections.usage
```

**Custom DB health indicator (e.g., check replication lag):**

```java
@Component
public class PostgresReplicationHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public PostgresReplicationHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Long lagBytes = jdbcTemplate.queryForObject(
                "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) " +
                "FROM pg_stat_replication LIMIT 1", Long.class);

            if (lagBytes != null && lagBytes > 50_000_000L) { // 50MB lag threshold
                return Health.down()
                    .withDetail("replicationLagBytes", lagBytes)
                    .withDetail("threshold", "50MB")
                    .build();
            }
            return Health.up()
                .withDetail("replicationLagBytes", lagBytes != null ? lagBytes : 0)
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## 8. Kafka Health & Metrics

Add the Kafka dependency if not already present:

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Spring Boot auto-wires Kafka health when `spring-kafka` is on the classpath.

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: my-service-group
      auto-offset-reset: earliest
    producer:
      acks: all
      retries: 3

management:
  health:
    kafka:
      enabled: true
```

**What `/actuator/health` shows with Kafka:**

```json
{
  "status": "UP",
  "components": {
    "kafka": {
      "status": "UP",
      "details": {
        "clusterId": "abc123",
        "nodes": 3
      }
    }
  }
}
```

**Key Kafka metrics available at `/actuator/metrics`:**

```
kafka.consumer.fetch-latency-avg
kafka.consumer.records-consumed-rate
kafka.consumer.records-lag                  ← most important
kafka.consumer.records-lag-max
kafka.consumer.commit-latency-avg
kafka.producer.record-send-rate
kafka.producer.request-latency-avg
kafka.producer.record-error-rate
```

**Custom consumer lag health indicator:**

```java
@Component
public class KafkaConsumerLagHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;
    private static final long MAX_LAG_THRESHOLD = 10_000L;

    public KafkaConsumerLagHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Map<TopicPartition, Long> lags = getConsumerGroupLag(adminClient, "my-service-group");

            long maxLag = lags.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            long totalLag = lags.values().stream().mapToLong(Long::longValue).sum();

            if (maxLag > MAX_LAG_THRESHOLD) {
                return Health.down()
                    .withDetail("maxLag", maxLag)
                    .withDetail("totalLag", totalLag)
                    .withDetail("threshold", MAX_LAG_THRESHOLD)
                    .withDetail("partitionLags", lags)
                    .build();
            }
            return Health.up()
                .withDetail("maxLag", maxLag)
                .withDetail("totalLag", totalLag)
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    private Map<TopicPartition, Long> getConsumerGroupLag(AdminClient adminClient, String groupId)
            throws ExecutionException, InterruptedException {
        // Implement using listConsumerGroupOffsets + listOffsets
        // omitted for brevity
        return Map.of();
    }
}
```

---

## 9. Kubernetes Liveness & Readiness Probes

Spring Boot 2.3+ has built-in liveness and readiness groups:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState        # App is alive (restart if fails)
        readiness:
          include: readinessState, db, kafka   # Ready to serve traffic
```

**Kubernetes deployment manifest:**

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081                         # Management port
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  initialDelaySeconds: 20
  periodSeconds: 5
  failureThreshold: 3
```

> **Important:** The management port (8081) should be exposed within the cluster via a ClusterIP service but **never** via an Ingress or LoadBalancer.

---

## 10. Custom Metrics with Micrometer

```java
@Service
public class OrderService {

    private final Counter ordersCreated;
    private final Timer orderProcessingTime;
    private final Gauge pendingOrdersGauge;
    private final AtomicInteger pendingOrders = new AtomicInteger(0);

    public OrderService(MeterRegistry meterRegistry) {
        this.ordersCreated = Counter.builder("orders.created")
            .description("Total orders created")
            .tag("service", "order-service")
            .register(meterRegistry);

        this.orderProcessingTime = Timer.builder("orders.processing.time")
            .description("Order processing duration")
            .register(meterRegistry);

        this.pendingOrdersGauge = Gauge.builder("orders.pending", pendingOrders, AtomicInteger::get)
            .description("Currently pending orders")
            .register(meterRegistry);
    }

    public Order createOrder(OrderRequest request) {
        return orderProcessingTime.record(() -> {
            pendingOrders.incrementAndGet();
            try {
                Order order = processOrder(request);
                ordersCreated.increment();
                return order;
            } finally {
                pendingOrders.decrementAndGet();
            }
        });
    }
}
```

---

## 11. Prometheus + Grafana Integration

```yaml
# application.yml
management:
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true       # Histograms for HTTP latency
      percentiles:
        http.server.requests: 0.5, 0.90, 0.95, 0.99
      slo:
        http.server.requests: 50ms, 100ms, 250ms, 500ms
    tags:
      application: ${spring.application.name}
      environment: ${SPRING_PROFILES_ACTIVE:local}
      region: ${AWS_REGION:unknown}
```

**Prometheus `scrape_config`:**

```yaml
scrape_configs:
  - job_name: 'my-service'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['my-service:8081']
    # If secured with basic auth:
    basic_auth:
      username: prometheus
      password: <prometheus-password>
```

---

## 12. Testing Actuator Endpoints

**Unit test for a custom health indicator:**

```java
@ExtendWith(MockitoExtension.class)
class KafkaConsumerLagHealthIndicatorTest {

    @Mock
    private KafkaAdmin kafkaAdmin;

    @InjectMocks
    private KafkaConsumerLagHealthIndicator indicator;

    @Test
    void health_returnsUp_whenLagBelowThreshold() {
        // Configure mock AdminClient behavior
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_returnsDown_whenLagExceedsThreshold() {
        // Configure mock to return high lag
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("maxLag");
    }
}
```

**Integration test for actuator endpoints:**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("staging")
class ActuatorIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpoint_returnsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void infoEndpoint_returnsAppInfo() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/info", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("app");
    }

    @Test
    void metricsEndpoint_returnsMetricsList() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/metrics", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("names");
    }

    @Test
    void sensitiveEndpoints_areNotExposedInProd() {
        // Switch to prod profile for this test
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/env", String.class);
        // In prod profile env is not in the include list → 404
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

**MockMvc slice test for security:**

```java
@WebMvcTest
@Import(ProdActuatorSecurityConfig.class)
@ActiveProfiles("prod")
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_isPublicInProd() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void metricsEndpoint_requiresAuthInProd() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsEndpoint_allowsAuthenticatedActuatorAdmin() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .with(httpBasic("actuator-admin", "secret")))
            .andExpect(status().isOk());
    }

    @Test
    void envEndpoint_isNotExposedInProd() throws Exception {
        mockMvc.perform(get("/actuator/env")
                .with(httpBasic("actuator-admin", "secret")))
            .andExpect(status().isNotFound());   // Not included in prod exposure list
    }
}
```

---

## 13. Production Recommendations Summary

### Expose Only What's Needed

```
✅ EXPOSE:  health, info, prometheus, loggers, metrics
❌ EXCLUDE: env, heapdump, threaddump, httptrace, shutdown, beans, configprops
```

### Network Isolation

- Run management on a **separate port** (e.g., 8081).
- In Kubernetes: expose 8081 via `ClusterIP` only — never add it to your Ingress.
- Use a `NetworkPolicy` to restrict Prometheus scraping to only the monitoring namespace.

### Secrets Management

- Never hardcode actuator credentials — inject via Kubernetes `Secret` or HashiCorp Vault.
- Rotate credentials regularly; use short-lived tokens if your setup supports it.

### Key Alerts to Set Up

| Metric | Alert Condition |
|---|---|
| `health` endpoint | Status != UP |
| `kafka.consumer.records-lag-max` | > 10,000 messages |
| `hikaricp.connections.pending` | > 5 for 2+ minutes |
| `hikaricp.connections.timeout` | > 0 in any 5m window |
| `http.server.requests` p99 | > 500ms |
| `jvm.memory.used{area="heap"}` | > 85% of max |
| `process.cpu.usage` | > 80% sustained |

### Runtime Log Level Changes (without restart)

```bash
# Temporarily enable DEBUG for a package in prod — no restart needed
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}' \
  -u actuator-admin:$PASSWORD \
  https://my-service-internal:8081/actuator/loggers/com.mycompany.service

# Restore to INFO after debugging
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "INFO"}' \
  -u actuator-admin:$PASSWORD \
  https://my-service-internal:8081/actuator/loggers/com.mycompany.service
```

---

## 14. Quick Reference — What's Different Per Environment

| Feature | Staging | Production |
|---|---|---|
| Exposed endpoints | All (`*`) | `health, info, metrics, prometheus, loggers` |
| Health details | `always` | `when-authorized` |
| Env values | `always` | `never` |
| Security | Permit all | Role-based (`ACTUATOR_ADMIN`) |
| Prometheus auth | None | Basic auth / token |
| Management port | 8081 (open in cluster) | 8081 (ClusterIP only, NetworkPolicy) |
| `shutdown` endpoint | Disabled | Disabled |
| Heap/thread dumps | Available | Not exposed |