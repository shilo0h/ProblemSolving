# Abstract Class vs Interface

  -------------------------------------------------------------------------
Feature                 Abstract Class          Interface
  ----------------------- ----------------------- -------------------------
**Can have fields**     ✅ Yes                  ✅ Only constants
(`public static final`)

**Constructors**        ✅ Yes                  ❌ No

**Instance variables**  ✅ Yes                  ❌ No

**Concrete methods**    ✅ Yes                  ✅ Yes (`default` and
`static` methods)

**Abstract methods**    ✅ Yes                  ✅ Yes

**Inheritance keyword** `extends`               `implements`

**Multiple              ❌ Can extend only one  ✅ Can implement multiple
inheritance**           class                   interfaces
  -------------------------------------------------------------------------

## When to use an Abstract Class

Use an abstract class when: - You want to share common state (fields)
between subclasses. - You want to provide common method
implementations. - The subclasses have an **"is-a"** relationship.

Example:

``` java
abstract class Animal {
    String name;

    void eat() {
        System.out.println("Eating...");
    }

    abstract void makeSound();
}
```

## When to use an Interface

Use an interface when: - You want to define a contract or capability. -
Different, unrelated classes should implement the same behavior. - You
need multiple inheritance of behavior.

Example:

``` java
interface Flyable {
    void fly();
}

class Bird implements Flyable {
    @Override
    public void fly() {
        System.out.println("Bird flying");
    }
}
```

## Rule of Thumb

-   **Abstract Class** = Shared implementation + shared state.
-   **Interface** = Shared contract/capability.