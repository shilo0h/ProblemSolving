# Spring Proxies and `@Async`

## What is a Spring Proxy?

A **Spring proxy** is an object created by Spring that sits **in front
of your real bean**.

Instead of other classes calling your service directly, they usually
call the proxy first.

``` text
Another Bean
     |
     v
 Spring Proxy
     |
     v
 Real Service
```

The proxy can add extra behavior before calling the real method.

Examples include:

-   `@Async`
-   `@Transactional`
-   `@Cacheable`

------------------------------------------------------------------------

## How `@Async` Works

When another bean calls a method marked with `@Async`, the proxy
intercepts the call.

Instead of executing the method immediately, it submits it to a thread
pool.

``` text
Another Bean
     |
     v
 Spring Proxy
     |
     +--> Sees @Async
     |
     +--> Executes method on another thread
```

------------------------------------------------------------------------

## Why `this.method()` Doesn't Work

Consider this class:

``` java
@Service
public class MyService {

    @Async
    public void second() {
    }

    public void first() {
        this.second();
    }
}
```

`this.second()` is a **normal Java method call**.

Since the call stays inside the same object, it **never goes through the
Spring proxy**.

``` text
Real Object
     |
     v
this.second()
```

Because the proxy is bypassed, Spring never sees the `@Async`
annotation, so the method runs **synchronously**.

------------------------------------------------------------------------

## Correct Approach

Move the async method to another Spring bean.

``` java
@Service
public class Worker {

    @Async
    public void second() {
    }
}
```

``` java
@Service
public class MyService {

    private final Worker worker;

    public void first() {
        worker.second();
    }
}
```

Now the call goes through the proxy:

``` text
MyService
    |
    v
Worker Proxy
    |
    v
Real Worker
```

The proxy detects `@Async` and executes the method on a background
thread.

------------------------------------------------------------------------

## Remember

-   `this.method()` -\> **Bypasses the proxy** -\> `@Async` does **not**
    work.
-   Calling another Spring bean -\> **Goes through the proxy** -\>
    `@Async` works.
-   `@EnableAsync` must be enabled so Spring creates async proxies.