# Kafka Notes for My Chat Backend

## 1. What Kafka Is

Kafka is a distributed event streaming platform.

Think of Kafka as a giant append-only log.

Instead of sending messages directly from one service to another:

```text
User A -> Chat Service -> User B
```

we do:

```text
User A
   ↓
Producer
   ↓
Kafka
   ↓
Consumer
   ↓
User B
```

Kafka acts as a durable storage layer between producers and consumers.

---

# 2. Core Kafka Components

## Broker

A Kafka server.

Example:

```text
localhost:9092
```

or

```text
kafka1:9092
kafka2:9092
kafka3:9092
```

A Kafka cluster contains one or more brokers.

---

## Topic

A category of messages.

Examples:

```text
chat-messages
offline-messages
notifications
```

Messages are written to topics.

---

## Producer

A producer writes messages into Kafka.

Example:

```java
producer.send(record);
```

Think:

> "Put this message into Kafka."

---

## Consumer

A consumer reads messages from Kafka.

Example:

```java
consumer.poll(...)
```

Think:

> "Give me new messages."

---

# 3. Partitions

A topic is split into partitions.

Example:

```text
offline-messages

Partition 0
Partition 1
Partition 2
Partition 3
...
Partition 11
```

Your chat system currently uses:

```java
PARTITION_COUNT = 12
```

---

## Why Partitions Exist

Without partitions:

```text
One giant queue
```

With partitions:

```text
Many queues in parallel
```

Benefits:

* Scalability
* Parallel processing
* Better throughput

---

# 4. Kafka Offsets

Every message inside a partition gets a number.

Example:

```text
Partition 3

Offset 0
Offset 1
Offset 2
Offset 3
Offset 4
```

Offsets are unique only inside a partition.

Example:

```text
Partition 0:
Offset 0
Offset 1

Partition 1:
Offset 0
Offset 1
```

This is completely normal.

---

## What Is An Offset?

An offset is simply a position in a partition.

Think of it like a page number in a book.

```text
Page 0
Page 1
Page 2
Page 3
```

---

## Why Offsets Matter

Consumers use offsets to remember:

> "Where did I stop reading?"

Example:

```text
Last read offset = 100
```

Next read starts at:

```text
101
```

---

# 5. Consumer Position

Kafka consumers maintain a position.

Example:

```java
consumer.seek(tp, 100);
```

Meaning:

> "Start reading from offset 100."

---

## Seek

Moves the consumer pointer.

Example:

```java
consumer.seek(tp, 500);
```

Think:

> Jump directly to page 500.

---

# 6. Poll

Consumers retrieve records using poll.

Example:

```java
ConsumerRecords<String, Event> records =
        consumer.poll(Duration.ofMillis(200));
```

Think:

> "Give me any messages currently available."

---

# 7. Producer Keys

A Kafka message can contain a key.

Example:

```java
key = "123"
```

where 123 is a user id.

Kafka uses the key to determine the partition.

---

# 8. Murmur2 Partitioning

Kafka uses Murmur2 hashing.

Example:

```java
int partition =
    Math.abs(
        Utils.murmur2(userId.toString().getBytes())
    ) % PARTITION_COUNT;
```

---

## Why Hash?

Suppose:

```text
User 5
User 8
User 20
```

We want each user to always go to the same partition.

Example:

```text
User 5  -> Partition 3
User 8  -> Partition 9
User 20 -> Partition 1
```

Every message for User 5 will always land in Partition 3.

---

## Why Is This Important?

Kafka guarantees ordering inside a partition.

Therefore:

```text
Message 1
Message 2
Message 3
```

for User 5 always stay in order.

---

# 9. Kafka Ordering

Kafka only guarantees ordering inside a partition.

Example:

Partition 3:

```text
Offset 100 -> Hello
Offset 101 -> How are you?
Offset 102 -> Bye
```

Kafka guarantees:

```text
Hello
How are you?
Bye
```

Consumers will see them in that order.

---

# 10. Consumer Groups

Consumers can belong to a group.

Example:

```java
GROUP_ID_CONFIG
```

A group shares work.

Example:

```text
Consumer A
Consumer B
Consumer C
```

Kafka assigns partitions among them.

---

# 11. Auto Commit

Kafka can automatically save offsets.

Example:

```java
ENABLE_AUTO_COMMIT_CONFIG = true
```

Then Kafka remembers:

> "This consumer already read up to offset X."

---

## Why We Disabled It

Your replay system uses:

```java
ENABLE_AUTO_COMMIT_CONFIG = false
```

Because you store offsets yourself.

---

# 12. Manual Offset Storage

Instead of Kafka remembering offsets:

```java
user.backlogOffset
```

stores them in the database.

Example:

```text
User 5 -> 105
User 8 -> 220
```

This gives you full control.

---

# 13. Your Offline Message Design

When a user is offline:

```text
Chat Message
      ↓
Kafka
      ↓
offline-messages topic
```

Messages accumulate.

---

# 14. Replay Process

When the user reconnects:

```text
User Online
      ↓
BacklogReplayService
      ↓
Kafka
      ↓
WebSocket
```

The service replays missed messages.

---

# 15. Shared Consumer

Your replay service creates:

```java
KafkaConsumer<String, BackloggedEvent>
```

This consumer is used only for replaying offline messages.

---

# 16. Why synchronized(sharedConsumer)?

KafkaConsumer is NOT thread-safe.

Bad:

```text
Thread A -> seek partition 3
Thread B -> seek partition 8
Thread A -> poll
```

Chaos.

Therefore:

```java
synchronized(sharedConsumer)
```

ensures only one thread uses it at a time.

---

# 17. Active Replay Protection

```java
activeReplays.putIfAbsent(...)
```

Prevents:

```text
Replay User 5
Replay User 5
Replay User 5
```

from running simultaneously.

---

# 18. Reading Backlog Messages

Example partition:

```text
100 -> User 5
101 -> User 8
102 -> User 5
103 -> User 8
104 -> User 5
```

User 5 reconnects.

Replay starts:

```java
seek(partition, 100)
```

Consumer reads everything.

---

# 19. Filtering By User

Your code checks:

```java
if(userId.toString().equals(record.key()))
```

Only matching messages are sent.

Result:

```text
100 -> send
101 -> ignore
102 -> send
103 -> ignore
104 -> send
```

---

# 20. Why Advance Past Other Users' Messages?

This was the most important concept.

You use:

```java
lastProcessedOffset =
    Math.max(
        lastProcessedOffset,
        record.offset() + 1
    );
```

---

## What This Means

You are NOT remembering:

> Last message sent to User 5

You ARE remembering:

> Furthest position inspected in the partition

---

Example:

```text
100 -> User 5
101 -> User 8
102 -> User 5
103 -> User 8
104 -> User 5
```

After inspecting all records:

```text
backlogOffset = 105
```

---

## Why?

Because offsets 100-104 have already been checked.

No reason to read them again.

---

# 21. Kafka As A Book

Best mental model:

Partition:

```text
Page 100
Page 101
Page 102
Page 103
Page 104
```

Offset:

```text
Current bookmark
```

Seek:

```text
Jump to page X
```

Poll:

```text
Read next pages
```

Consumer:

```text
Reader
```

Producer:

```text
Writer
```

Topic:

```text
Book
```

Partition:

```text
Chapter
```

Offset:

```text
Page number
```

---

# 22. BACKLOG_COMPLETE Sentinel

After replay finishes:

```java
BACKLOG_COMPLETE
```

is sent.

Purpose:

```text
Frontend knows replay is finished.
```

This allows the UI to:

* stop loading
* enable interactions
* know all old messages arrived

---

# 23. My Current Architecture

Message arrives:

```text
Sender
  ↓
Kafka Producer
  ↓
offline-messages Topic
  ↓
Partition Determined By Murmur2(userId)
```

User reconnects:

```text
BacklogReplayService
  ↓
Find Partition
  ↓
Seek To Saved Offset
  ↓
Poll Messages
  ↓
Filter By UserId
  ↓
Send Through WebSocket
  ↓
Save New Offset
  ↓
Send BACKLOG_COMPLETE
```

This guarantees:

* Ordered delivery per user
* Durable offline storage
* Efficient replay
* No duplicate replay jobs
* No rescanning of already inspected offsets
* Controlled offset management through the database
