# Static in Java

## What does `static` mean?

The `static` keyword means that a field or method **belongs to the class itself**, not to individual objects (instances) created from that class.

> **Important:** `static` **does not mean the value cannot change**. A static field can still be modified unless it is also marked as `final`.

---

## Static Fields (Variables)

A static field is shared by **all objects** of the class. There is only **one copy** of the field, regardless of how many objects are created.

### When should you use a static field?

Use a static field when:
- The value should be shared by every object.
- You only need one copy of the value.
- The value represents something common to the entire class.

Examples:
- A counter that tracks how many objects have been created.
- A configuration value.
- Mathematical constants (`PI`, `E`, etc.), usually with `static final`.

### Accessing a static field

You should access a static field using the class name:

```java
Person.country = "USA";
```

Instead of:

```java
Person person = new Person();
person.country = "USA"; // Works, but not recommended
```

Using the class name makes it clear that the field belongs to the class, not to a specific object.

---

## Static Methods

A static method also belongs to the class instead of an object.

This means you **do not need to create an object** to call it.

Example:

```java
Math.max(10, 20);
```

or

```java
Person.printCompanyName();
```

You call it using:

```java
ClassName.staticMethod();
```

---

## When are static methods available?

Static methods become available **when the JVM loads the class**.

Since they belong to the class, there is no need to create an object with `new` before calling them.

For example, the JVM starts every Java program by calling:

```java
public static void main(String[] args)
```

without creating a `Main` object first.

---

## Static methods cannot directly access instance members

Since a static method does not belong to a particular object, it cannot directly access non-static fields or methods.

Example:

```java
class Person {
    String name;

    static void printName() {
        // Error
        System.out.println(name);
    }
}
```

The method doesn't know **which object's** `name` should be used.

However, it **can** access:
- Other static fields
- Other static methods
- Objects passed as parameters

---

## Summary

| Static Field | Static Method |
|--------------|---------------|
| Belongs to the class | Belongs to the class |
| One shared copy | One shared method |
| Access with `ClassName.field` | Access with `ClassName.method()` |
| Shared by all objects | Can be called without creating an object |
| Often used for constants, counters, configuration | Often used for utility/helper methods |

---

## Key Points

- `static` means **belongs to the class**, not to an object.
- There is only **one copy** of a static field.
- Static fields are shared by all objects.
- Static methods can be called without creating an object.
- Use `ClassName.member` instead of `object.member` for static members.
- `static` does **not** make a variable immutable. Use `final` as well (`static final`) if you want a constant.