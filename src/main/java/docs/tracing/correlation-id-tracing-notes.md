# Correlation ID Tracing in Spring Boot

## Goal

Follow one request across different parts of the backend using the same
correlation ID.

The correlation ID is added to logs so related log messages can be found
easily, even when work moves between threads or is sent through Kafka.

## 1. Add a correlation ID to incoming HTTP requests

Create `CorrelationIdFilter`.

-   Read `X-Correlation-ID` from the incoming request.
-   Accept it only if it matches `^[a-zA-Z0-9_-]{8,64}$`.
-   If it is missing or invalid, generate a UUID.
-   Put the ID in MDC using the key `correlationId`.
-   Return the ID in the response header.
-   Remove it from MDC in `finally`, so a reused servlet thread does not
    keep the previous request's ID.

``` java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER_NAME);

        if (correlationId == null ||
                !SAFE_ID_PATTERN.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER_NAME, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

## 2. Include the correlation ID in logs

In `log4j2.xml`, add `%X{correlationId}` to the `PatternLayout` pattern:

``` xml
<PatternLayout
    pattern="%d{yyyy-MM-dd HH:mm:ss} %-5level [%t] [%X{correlationId}] %logger{36} - %msg%n"/>
```

The same pattern is used for the Console and RollingFile appenders.

The appenders are both placed inside the `MaskedAppenders` Rewrite
appender. Keep them there so logging continues through the masking
policy.

The configuration also rotates the log file by date or when it reaches
100 MB, and keeps up to 30 rolled files.

Example log:

``` text
2026-11-17 10:00:00 INFO [http-nio-8080-exec-1] [abc-123] ...
```

## 3. Propagate MDC when work moves to another thread

MDC is associated with the current thread. A worker thread in a thread
pool does not automatically inherit the submitting thread's MDC.

Create `MdcTaskDecorator` and register it on the relevant Spring task
executor.

``` java
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Captured now, on the thread submitting the task.
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            // Runs later, on the worker thread.
            Map<String, String> previousContext = MDC.getCopyOfContextMap();

            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                } else {
                    MDC.clear();
                }

                runnable.run();
            } finally {
                // Restore the worker thread's previous MDC.
                if (previousContext != null) {
                    MDC.setContextMap(previousContext);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
```

Register it on the WebSocket inbound executor:

``` java
executor.setTaskDecorator(mdcTaskDecorator);
```

Why are there two maps?

-   `contextMap`: the MDC captured from the thread submitting the task.
-   `previousContext`: the MDC that was already on the worker thread
    before this task runs.

The worker thread is reused, so its previous context must be restored
after the task finishes.

## 4. Send the correlation ID through Kafka

When producing a Kafka record, read the ID from MDC and add it to the
Kafka header `X-Correlation-ID`.

``` java
private void sendWithCorrelationId(String topic, String key, Object payload) {
    ProducerRecord<String, Object> record =
            new ProducerRecord<>(topic, key, payload);

    String correlationId = MDC.get("correlationId");

    if (correlationId != null) {
        record.headers().add(new RecordHeader(
                "X-Correlation-ID",
                correlationId.getBytes(StandardCharsets.UTF_8)
        ));
    }

    kafkaTemplate.send(record);
}
```

The producer methods for message inserts, updates, reactions, and
presence updates call this helper.

## 5. Restore the correlation ID in a Kafka listener

Kafka listeners run on consumer threads, not on the original HTTP
request thread. Read the correlation ID from the Kafka record header and
put it into the listener thread's MDC.

``` java
@KafkaListener(
    topics = "${spring.profiles.active}-message-insert",
    groupId = "chatroom-group"
)
@Transactional
public void messageInsertListener(
        ConsumerRecord<String, MessageDTO> record
) {
    var header = record.headers().lastHeader("X-Correlation-ID");

    String correlationId = header != null
            ? new String(header.value(), StandardCharsets.UTF_8)
            : UUID.randomUUID().toString();

    try {
        MDC.put("correlationId", correlationId);

        MessageDTO messageDTO = record.value();
        SocketMessageResponse response =
                getMessageResponse(messageDTO, SocketMessageType.MESSAGE_INSERT);

        response.setServerSeq(record.offset());
        routeMessage(messageDTO.getChatroomId(), response);

    } finally {
        MDC.remove("correlationId");
    }
}
```

The `finally` block is important because Kafka consumer threads are
reused.

## 6. Important coverage note

The `sendChatroomUpdate` method shown in the implementation sends
directly with `kafkaTemplate.send(...)`. It does **not** call
`sendWithCorrelationId(...)`, so that method currently does not add the
correlation ID header.

If chatroom-update events should be traced too, add the same header when
creating that `ProducerRecord`, or use a shared helper that supports the
required transaction-after-commit behavior.

Also, the HTTP filter starts tracing HTTP requests. It does not
automatically add correlation IDs to every independently initiated
WebSocket connection or STOMP message. Those flows need their own
correlation-ID creation/propagation if they must be traced.

## Request-to-Kafka flow

1.  HTTP request arrives.
2.  `CorrelationIdFilter` reads or creates the correlation ID.
3.  The ID is stored in MDC.
4.  Log4j2 includes it in log messages.
5.  The Kafka producer copies it into the Kafka record header.
6.  The Kafka listener reads the header and puts the ID into its MDC.
7.  Listener logs use the same correlation ID.
8.  MDC is cleaned up when the work finishes.

**Remember:** MDC stores the ID for logging on the current thread. The
Kafka header carries the ID between services/threads. The task decorator
copies MDC when work moves to a different executor thread.