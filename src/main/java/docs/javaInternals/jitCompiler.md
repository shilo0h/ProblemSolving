# Java JIT (Just-In-Time) Compiler

The **JIT (Just-In-Time) Compiler** is one of the biggest reasons Java is so fast.

At first glance, Java appears slower than languages like C++ because it **does not compile directly into machine code**. Instead, Java compiles into **bytecode**, which is executed by the JVM.

A natural question is:

> **If the JVM interprets bytecode, why is Java almost as fast as C++ in many applications?**

The answer is the **JIT Compiler**.

---

# The Normal Java Compilation Flow

Consider the following Java program:

```java
public class Main {

    public static void main(String[] args) {
        System.out.println(add(5, 10));
    }

    static int add(int a, int b) {
        return a + b;
    }

}
```

When you compile it:

```bash
javac Main.java
```

Java creates:

```text
Main.class
```

The `.class` file contains **bytecode**, not machine code.

When you execute:

```bash
java Main
```

the JVM starts and begins executing the bytecode.

---

# Without the JIT Compiler

Originally, the JVM only had an **Interpreter**.

Suppose the bytecode for `add()` looks something like:

```text
load 5
load 10
add
return
```

The interpreter executes every instruction one by one.

```text
Instruction 1
Instruction 2
Instruction 3
Instruction 4
```

Now imagine the method is called:

```text
1,000,000 times
```

The JVM must interpret those four instructions:

```text
4,000,000 times
```

Interpreting instructions repeatedly is much slower than executing native machine code.

---

# With the JIT Compiler

The JVM continuously monitors your application's execution.

It collects statistics such as:

```text
Method: add()

Called 1 time
Called 10 times
Called 100 times
Called 1,000 times
Called 10,000 times
```

Eventually, the JVM decides:

> **"This method is executed very frequently. Instead of interpreting it every time, I'll compile it into native machine code."**

This compilation happens **Just In Time**, while the application is running.

---

# The Complete Process

```text
Java Source (.java)
        │
        ▼
      javac
        │
        ▼
Bytecode (.class)
        │
        ▼
JVM Starts
        │
        ▼
Interpreter Executes Bytecode
        │
        ▼
Method Becomes "Hot"
        │
        ▼
JIT Compiles Machine Code
        │
        ▼
CPU Executes Native Instructions
```

---

# What Is a "Hot" Method?

A **hot method** is simply a method that is executed many times.

Example:

```java
for (int i = 0; i < 1_000_000; i++) {
    add(i, i);
}
```

Since `add()` is called repeatedly, the JVM considers it worth optimizing.

---

# Before and After JIT

## Without JIT

```text
add()

    │
    ▼

Bytecode

    │
    ▼

Interpreter

    │
    ▼

CPU
```

Every call goes through the interpreter.

---

## After JIT

```text
add()

    │
    ▼

Native Machine Code

    │
    ▼

CPU
```

The interpreter is bypassed, making execution significantly faster.

---

# Why Doesn't the JVM Compile Everything Immediately?

Imagine a Spring Boot application containing:

```text
15,000 methods
```

During startup, perhaps only:

```text
500 methods
```

are actually executed.

Compiling all 15,000 methods would:

- Increase startup time
- Waste CPU resources
- Consume unnecessary memory

Instead, the JVM compiles **only methods that become hot**.

This is one reason Java applications often start slower but become faster over time.

---

# JIT Optimizations

The JIT Compiler doesn't simply translate bytecode into machine code.

It also performs advanced optimizations.

---

## 1. Method Inlining

Instead of executing:

```java
int result = add(5, 10);
```

the JIT may replace it with:

```java
int result = 5 + 10;
```

The method call disappears completely.

This removes the overhead of creating a stack frame and performing a method call.

---

## 2. Dead Code Elimination

If code can never execute:

```java
if (false) {
    System.out.println("Hello");
}
```

the JIT simply removes it.

No machine code is generated for unreachable code.

---

## 3. Loop Optimization

Example:

```java
for (int i = 0; i < 1000; i++) {
    sum += i;
}
```

The JIT may optimize loops by:

- Reducing repeated calculations
- Reordering instructions safely
- Removing unnecessary checks
- Unrolling loops in some situations

---

## 4. Escape Analysis

Consider:

```java
Point p = new Point(1, 2);
```

If `p` never escapes the current method (it isn't returned or stored elsewhere), the JVM may avoid allocating it on the heap.

Instead, it can:

- Allocate it on the stack
- Eliminate the allocation completely

This reduces object creation and garbage collection pressure.

---

# Why Java Gets Faster Over Time

When a Spring Boot application first starts:

```text
Request #1

        │
        ▼

Interpreter Executes Bytecode
```

A little later:

```text
Request #100

        │
        ▼

Some Methods Compiled by JIT
```

After running for a while:

```text
Request #10,000

        │
        ▼

Most Frequently Used Methods
Already Compiled
```

This warm-up period is why long-running Java applications often become noticeably faster after startup.

---

# Can the JIT Recompile Code?

Yes.

Suppose the JVM observes:

```java
shape.draw();
```

For thousands of calls, `shape` is always a `Circle`.

The JVM optimizes the generated machine code specifically for `Circle`.

Later, many `Rectangle` objects begin appearing.

The JVM can:

1. Detect that its assumption is no longer valid.
2. Discard the optimized machine code (**deoptimization**).
3. Generate a new optimized version.

This ability to adapt while the application is running is one of the JVM's greatest strengths.

---

# JIT vs AOT (Ahead-of-Time Compilation)

| JIT | AOT |
|------|-----|
| Compiles during application execution | Compiles before execution |
| Uses real runtime information | Cannot observe runtime behavior |
| Slower startup | Faster startup |
| Excellent for long-running applications | Excellent for short-lived applications |

Traditional Java uses **JIT**.

Technologies such as **GraalVM Native Image** use **Ahead-of-Time (AOT)** compilation.

---

# How the JIT Fits into the JVM

```text
Java Source (.java)
        │
        ▼
      javac
        │
        ▼
Bytecode (.class)
        │
        ▼
ClassLoader
        │
        ▼
        JVM
         │
         ├──────────────────────────────► Interpreter
         │                                      │
         │                                      ▼
         │                           Executes Bytecode
         │                                      │
         │                                      ▼
         │                          Counts Method Executions
         │                                      │
         │                                      ▼
         │                          Method Becomes "Hot"
         │
         └──────────────────────────────► JIT Compiler
                                                │
                                                ▼
                                     Native Machine Code
                                                │
                                                ▼
                                               CPU
```

---

# JIT + ClassLoader Relationship

The **ClassLoader** and **JIT Compiler** work together but have different responsibilities.

| Component | Responsibility |
|-----------|----------------|
| **ClassLoader** | Finds and loads `.class` files into the JVM |
| **Interpreter** | Executes bytecode instruction by instruction |
| **JIT Compiler** | Compiles frequently executed bytecode into native machine code |

The overall flow is:

```text
Source Code
      │
      ▼
 javac Compiler
      │
      ▼
 Bytecode (.class)
      │
      ▼
 ClassLoader
      │
      ▼
 Interpreter
      │
      ▼
 JIT Compiler (for hot methods)
      │
      ▼
 Native Machine Code
      │
      ▼
 CPU
```

---

# Key Takeaways

- `javac` compiles Java source code into **bytecode**.
- The JVM initially executes bytecode using the **Interpreter**.
- The JVM tracks how often methods are executed.
- Frequently executed (**hot**) methods are compiled by the **JIT Compiler**.
- JIT produces **native machine code**, which executes much faster than interpreted bytecode.
- The JIT performs advanced optimizations such as:
    - Method Inlining
    - Dead Code Elimination
    - Loop Optimization
    - Escape Analysis
- The JVM can even **deoptimize and recompile** code if runtime behavior changes.
- This combination of interpretation, runtime profiling, and adaptive optimization is one of the primary reasons Java delivers excellent performance in long-running applications.