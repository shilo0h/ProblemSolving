🔹 == vs .equals() in Java

In Java, == and .equals() are both used to compare things, but they do very different jobs.

🧠 == (Reference Comparison)
Checks whether two references point to the exact same object in memory.
Works for primitive types (int, char, etc.) and object references.
Example:
String a = new String("hello");
String b = new String("hello");

System.out.println(a == b); // false

👉 Even though the values look the same, a and b are different objects, so == returns false.

🔤 .equals() (Value Comparison)

Defined in the Java Object class.

Used to compare the actual content (value) of objects.
Many classes (like String) override it for meaningful comparison.
Example:
String a = new String("hello");
String b = new String("hello");

System.out.println(a.equals(b)); // true

👉 .equals() compares the text inside, so it returns true.

⚖️ Key Differences
Feature	==	.equals()
Comparison type	Memory reference	Object content/value
Works on primitives	✅ Yes	❌ No
Works on objects	✅ Yes	✅ Yes
Overridable	❌ No	✅ Yes
🧪 Special Case: String Pool
String a = "hello";
String b = "hello";

System.out.println(a == b); // true

👉 Java uses a string pool, so both references may point to the same object.

🎯 Rule of Thumb
Use == → when checking if two references are the same object
Use .equals() → when checking if two objects have the same value
🧺 Internal Working of Java HashMap

A HashMap in Java is like a well-organized magical warehouse where keys are turned into addresses using a hash function, so retrieval is extremely fast.

🧠 Core Idea

A HashMap stores data in key → value pairs.

Internally it uses:

An array of buckets
A hash function
Collision handling (linked list / tree)
Index calculation:
index = hash(key) % arraySize
📥 put(key, value) — Insertion Flow
Step 1: Hashing
hash = key.hashCode()
Step 2: Bucket Index
index = (n - 1) & hash

Where:

n = array size (power of 2)
& = bitwise AND (faster than modulo)
Step 3: Store Entry

Each bucket can hold:

null
linked list of nodes
red-black tree (Java 8+)
class Node {
int hash;
K key;
V value;
Node next;
}
⚔️ Collision Handling
Case 1: Linked List
index 3 → [A → B → C]
Case 2: Tree (Java 8+)

If entries > ~8:

Linked List → Red-Black Tree

👉 Improves performance:

O(n) → O(log n)
🔍 get(key) — Retrieval
map.get(key)

Steps:

Compute hash
Find bucket index
Traverse bucket
Match using:
node.hash == hash && node.key.equals(key)

👉 Both hashCode() and equals() are required.

⚡ Why HashMap is Fast
Operation	Complexity
put	O(1)
get	O(1)
remove	O(1)

Worst case:

O(n)
O(log n) (tree bins)
🔄 Resize (Rehashing)

When load factor exceeds ~0.75:

Array size doubles
Entries redistributed

👉 Expensive operation: O(n)

🧠 Mental Model
Hash function = postal code 📮
Array = building 🏢
Bucket = floor
Node = room
equals() = checking identity inside room
⚠️ Interview Traps
❌ Only hashCode() matters → wrong
❌ HashMap is thread-safe → wrong
❌ Order is preserved → wrong

Use:

Java ConcurrentHashMap for thread safety
LinkedHashMap for ordering
🧠 Java Memory Model (JVM Memory Structure)

When a Java program runs, memory is split into regions inside the Java Virtual Machine.

🏗️ Heap Memory
Stores objects
Shared across threads
Garbage collected
User u = new User(); // heap
🧵 Stack Memory
Each thread has its own stack
Stores method calls + local variables
int x = 10; // stack
🧠 Method Area (Metaspace)
Class metadata
Static variables
Method definitions
📍 Program Counter (PC Register)

Tracks:

Current executing instruction per thread
🔌 Native Stack

Used for native (C/C++) methods via JNI

🧩 Memory Layout
Method Area (Metaspace)
Heap (Objects)
Stack (Per Thread)
PC Register
Native Stack
⚖️ Heap vs Stack
Feature	Stack	Heap
Stores	methods, locals	objects
Speed	fast	slower
Scope	thread-specific	shared
GC	no	yes
🧹 Garbage Collection
User u = new User();
u = null; // eligible for GC
🧵 Concurrency vs Thread Safety
⚡ Concurrency

Multiple tasks progressing at the same time (interleaved execution).

Example:

Thread A: DB read
Thread B: processing
Thread C: logging

👉 It’s about execution structure

🔒 Thread Safety

Ensures shared data is not corrupted under multiple threads.

🧨 Non-thread-safe example
class Counter {
int count = 0;

    void increment() {
        count++;
    }
}

Problem:

count++ is not atomic
🛠 Fixes
1. synchronized
   synchronized void increment() {
   count++;
   }
2. Locks
   Lock lock = new ReentrantLock();
3. Atomic classes

From Java AtomicInteger

AtomicInteger count = new AtomicInteger();
count.incrementAndGet();
4. Thread-safe collections

From Java ConcurrentHashMap

🧰 Concurrency Tools
Threads (Thread, Runnable, Callable)
Synchronization (synchronized, Lock)
Executors (ExecutorService)
⚖️ Key Difference
Concept	Meaning
Concurrency	Many tasks running together
Thread safety	Shared data remains correct
🍳 Mental Model
Concurrency → many chefs cooking 🍳
Thread safety → only one uses salt shaker 🧂
🏁 Spring Boot Reality

In Spring:

Each request = new thread
Controllers are multi-threaded

👉 Therefore:

beans must be thread-safe
services should be stateless