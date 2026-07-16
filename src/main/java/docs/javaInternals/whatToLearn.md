1. Java Internals (Highest Priority)

This is where many developers stop, but it makes a huge difference in interviews and production work.

Learn:

JVM architecture
Heap vs Stack
Garbage Collection (G1, ZGC, Shenandoah)
ClassLoader
Bytecode
JIT Compiler
Escape Analysis
String Pool
Memory leaks
Java Flight Recorder (JFR)
VisualVM

Example questions you should be able to answer:

Why does a memory leak happen in Java?
Why can a HashMap leak memory?
Why does String.intern() exist?
How does the JVM optimize hot code?
2. Concurrency (Very Important)

You already use Kafka and asynchronous processing.

Go deeper into:

Thread
ExecutorService
CompletableFuture
ForkJoinPool
CountDownLatch
Semaphore
CyclicBarrier
ReentrantLock
ReadWriteLock
StampedLock
AtomicInteger
LongAdder
volatile
Java Memory Model

Be able to explain:

race conditions
deadlocks
livelocks
starvation
3. Collections Deep Dive

Don't just know how to use them.

Understand how they work internally.

Know:

HashMap
ConcurrentHashMap
HashSet
TreeMap
LinkedHashMap
ArrayList
LinkedList
PriorityQueue

Questions:

Why is HashMap O(1)?
Why does it resize?
What happens on collisions?
What is treeification?
4. Design Patterns

Not by memorizing names.

Understand when to use them.

Most useful:

Singleton
Factory
Builder
Strategy
Observer
Decorator
Adapter
Proxy
Template Method
Command

Spring itself uses many of these.

5. Clean Code & SOLID

This is something you're already practicing while addressing SonarQube issues.

Learn:

SOLID principles
Dependency Injection
Composition over inheritance
High cohesion
Low coupling
Refactoring techniques
6. Performance

Very valuable for backend work.

Topics:

JVM tuning
Profiling
CPU bottlenecks
Memory bottlenecks
Database optimization
Caching
Batch processing
7. Database Internals

You know SQL.

Now learn how databases work.

Topics:

Indexes
B-tree indexes
Query planner
Execution plans
Transactions
MVCC
Isolation levels
Locking
Deadlocks
8. System Design

You're almost at the point where this becomes very useful.

Learn how to design:

Chat applications
Notification systems
URL shorteners
File storage
Payment systems
Social networks

Think about:

scaling
consistency
replication
sharding
caching
9. Algorithms

Keep practicing.

I would solve:

Arrays
Strings
HashMaps
Trees
Graphs
BFS
DFS
Binary Search
Dynamic Programming

Aim for around 150 to 250 LeetCode-style problems over time.

10. Testing

Go deeper into:

Mockito
Testcontainers
WireMock
Integration testing
Contract testing
Performance testing