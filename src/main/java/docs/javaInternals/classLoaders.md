# Java Class Loaders

Class Loaders are one of the most important JVM concepts. They answer one simple question:

> **How does Java turn a `.class` file into a class that your program can use?**

Let's build it from scratch.

---

# Example Project Structure

```text
chat-backend/
│
├── target/
│   └── classes/
│       └── UserService.class
│
└── lib/
    └── kafka.jar
```

When you run:

```bash
java -jar app.jar
```

the JVM does **not** immediately know what `UserService` is.

The JVM only understands **bytecode**. Before your application can use `UserService`, someone must:

1. Locate the `.class` file.
2. Read the bytecode.
3. Verify that the bytecode is valid.
4. Convert it into a Java `Class` object.
5. Store it in the JVM.

That "someone" is the **ClassLoader**.

---

# What Happens When You Create an Object?

Consider the following code:

```java
UserService service = new UserService();
```

The JVM first asks:

```text
Do I already know UserService?
```

If **yes**, it simply uses the already loaded class.

If **no**, it delegates the work to a **ClassLoader**.

The loading process looks like this:

```text
Find UserService.class
        │
        ▼
Read bytecode
        │
        ▼
Verify bytecode
        │
        ▼
Create a Class object
        │
        ▼
Store it inside the JVM
        │
        ▼
new UserService()
```

Only after this process completes can Java create an instance of `UserService`.

---

# Every Loaded Class Has a `Class` Object

Whenever a class is loaded, the JVM creates a `Class` object that represents it.

For example:

```java
String s = "Hello";
```

Behind the scenes there is also:

```java
Class<String>
```

This is why you can write:

```java
System.out.println(String.class);
```

Output:

```text
class java.lang.String
```

The `ClassLoader` was responsible for creating that `Class` object.

---

# Java's Built-in Class Loaders

Java normally has **three** built-in ClassLoaders.

```text
Bootstrap ClassLoader
        ▲
Platform ClassLoader
        ▲
Application ClassLoader
```

Think of them as three librarians.

Each one is responsible for loading a different set of classes.

---

# 1. Bootstrap ClassLoader

The Bootstrap ClassLoader loads Java's core classes.

Examples include:

- `String`
- `Object`
- `Integer`
- `List`
- `HashMap`
- `Thread`

Everything inside packages such as:

```text
java.lang.*
java.util.*
java.io.*
```

is loaded by the Bootstrap ClassLoader.

Example:

```java
System.out.println(String.class.getClassLoader());
```

Output:

```text
null
```

### Why is it `null`?

The Bootstrap ClassLoader is **not written in Java**.

It is implemented directly inside the JVM using native code, so Java represents it as `null`.

---

# 2. Platform ClassLoader

The Platform ClassLoader loads Java platform modules that are not part of the core runtime.

Examples include:

```text
java.sql
java.xml
javax.crypto
```

---

# 3. Application ClassLoader

The Application ClassLoader loads your application's classes and dependencies.

Examples:

```text
UserController.class
UserService.class
KafkaProducer.class
```

It also loads libraries such as:

- Spring Boot
- Kafka
- Hibernate
- Jackson

Example:

```java
System.out.println(UserService.class.getClassLoader());
```

Output:

```text
jdk.internal.loader.ClassLoaders$AppClassLoader
```

---

# Parent Delegation Model

This is the most important concept to understand.

Suppose Java needs to load:

```text
String.class
```

The Application ClassLoader **does not immediately try to load it itself**.

Instead, it asks its parent.

```text
Application ClassLoader
        │
        ▼
Platform ClassLoader
        │
        ▼
Bootstrap ClassLoader
```

If the Bootstrap ClassLoader already knows the class, it loads it.

If not, the request travels back down until someone can load it.

---

## Why does Java do this?

Imagine someone creates a fake class:

```text
java/lang/String.class
```

inside their project.

Without parent delegation, Java might accidentally load the fake `String` instead of the real one.

That would be a huge security problem.

Instead, the process looks like this:

```text
Application ClassLoader
        │
        ▼
Bootstrap ClassLoader
        │
        ▼
Real String.class already exists
        │
        ▼
Use the real class
```

The fake class is ignored.

---

# Why Are ClassLoaders Useful?

Many frameworks create their own custom ClassLoaders.

## Spring Boot

Spring Boot executable JARs contain:

```text
BOOT-INF/classes
BOOT-INF/lib
```

Spring Boot uses a custom ClassLoader capable of loading classes directly from this structure.

---

## Tomcat

Each deployed web application gets its own ClassLoader.

```text
Application A
        │
        ▼
ClassLoader A

Application B
        │
        ▼
ClassLoader B
```

This allows two applications to use different versions of the same library without conflicting.

For example:

```text
App A → jackson 2.13

App B → jackson 2.17
```

Both applications run correctly because each has its own ClassLoader.

---

## Other Examples

Many systems rely on custom ClassLoaders:

- IntelliJ plugins
- Gradle plugins
- Minecraft plugins
- OSGi modules

---

# Loading vs Initialization

Loading a class is **not** the same as initializing it.

Consider:

```java
class Test {

    static {
        System.out.println("Loaded");
    }

}
```

### Loading

```text
Read bytecode
Create Class object
Verify bytecode
```

### Initialization

```text
Execute static fields
Execute static blocks
```

These are two different phases of the JVM lifecycle.

---

# Loading Classes Manually

We can also ask the ClassLoader to load a class ourselves.

Example:

```java
Class<?> clazz = Class.forName("java.lang.String");
```

Or one of our own classes:

```java
Class<?> clazz = Class.forName("com.example.UserService");
```

This is commonly used by:

- Reflection
- Dependency Injection
- Frameworks like Spring and Hibernate

---

# How Spring Uses ClassLoaders

When Spring Boot starts, it scans your project looking for annotations such as:

```java
@Component
@Service
@Repository
@Controller
@Configuration
```

Spring uses the ClassLoader to load each discovered class.

Once loaded, Spring creates the appropriate beans and stores them inside the Application Context.

Without ClassLoaders, Spring wouldn't even be able to discover your application's classes.

---

# Complete Flow

```text
           UserService.class
                   │
                   ▼
        Application ClassLoader
                   │
                   ▼
      Is the class already loaded?
          │               │
         Yes              No
          │               │
          ▼               ▼
     Use existing    Read bytecode
                          │
                          ▼
                  Verify bytecode
                          │
                          ▼
                 Create Class object
                          │
                          ▼
                  Store inside JVM
                          │
                          ▼
                 new UserService()
```

---

# Key Takeaways

- A **ClassLoader** is responsible for locating and loading `.class` files into the JVM.
- Every loaded class has a corresponding `Class<?>` object.
- A class is loaded only **once per ClassLoader**.
- Java has three built-in ClassLoaders:
    - Bootstrap ClassLoader
    - Platform ClassLoader
    - Application ClassLoader
- Java follows the **Parent Delegation Model**, where child ClassLoaders ask their parent to load classes first.
- Frameworks like **Spring Boot**, **Tomcat**, **Gradle**, and plugin systems use custom ClassLoaders to isolate applications and load classes dynamically.
- **Loading** a class and **initializing** a class are two different phases in the JVM lifecycle.