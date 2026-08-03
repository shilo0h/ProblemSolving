# Big O Notation 🚀

Big O Notation describes how the runtime or memory usage of an algorithm grows as the input size (`n`) increases.

---

# O(1) - Constant Time

- Runtime does **not** change as the input size grows.
- Fastest possible complexity.

**Example**
```java
int first = array[0];
```

---

# O(log n) - Logarithmic Time

- Each operation cuts the remaining work roughly in half.
- Very efficient for large datasets.

**Example**
- Binary Search

```java
while (left <= right) {
    int mid = (left + right) / 2;

    if (array[mid] == target)
        return mid;
    else if (array[mid] < target)
        left = mid + 1;
    else
        right = mid - 1;
}
```

---

# O(n) - Linear Time

- Runtime increases directly with the number of elements.
- One pass through the data.

**Example**

```java
for (int i = 0; i < n; i++) {
    System.out.println(array[i]);
}
```

---

# O(n log n)

- Combines linear work with logarithmic splitting.
- Common in efficient sorting algorithms.

**Examples**
- Merge Sort
- Heap Sort
- Quick Sort (Average Case)

---

# O(n × m)

- Two nested loops over two different collections.
- `n` and `m` represent different input sizes.

**Example**

```java
for (User user : users) {
    for (Order order : orders) {
        // Compare user with order
    }
}
```

---

# O(n²) - Quadratic Time

- Usually caused by nested loops over the same collection.
- Gets slow as the input size grows.

**Example**

```java
for (int i = 0; i < n; i++) {
    for (int j = 0; j < n; j++) {
        // Work
    }
}
```

---

# O(2ⁿ) - Exponential Time

- Every additional input approximately doubles the work.
- Becomes impractical very quickly.

**Example**

Recursive Fibonacci

```java
int fib(int n) {
    if (n <= 1)
        return n;

    return fib(n - 1) + fib(n - 2);
}
```

---

# O(n!) - Factorial Time

- Tries every possible arrangement (permutation).
- Extremely slow even for medium-sized inputs.

**Examples**
- Generating all permutations
- Brute-force Traveling Salesman Problem

---

# Complexity Order (Best → Worst)

| Complexity | Description | Example |
|------------|-------------|---------|
| **O(1)** | Constant | Array index access |
| **O(log n)** | Logarithmic | Binary Search |
| **O(n)** | Linear | Single loop |
| **O(n log n)** | Linearithmic | Merge Sort |
| **O(n × m)** | Two Inputs | Comparing two lists |
| **O(n²)** | Quadratic | Bubble Sort |
| **O(2ⁿ)** | Exponential | Recursive Fibonacci |
| **O(n!)** | Factorial | Permutations |

---

# Growth Graph

```text
Operations
^
|                                                            O(n!)
|                                                         *
|                                                      *
|                                                   *
|                                               O(2ⁿ)
|                                            *
|                                        *
|                                    *
|                              O(n²)
|                           *
|                       *
|                  O(n log n)
|               *
|            *
|         O(n)
|      *
|   *
| O(log n)
| *
|_______________________________________________> Input Size (n)
  O(1)
```

The higher the curve, the slower the algorithm grows as the input size increases.

**Fastest → Slowest**

```
O(1)
   ↓
O(log n)
   ↓
O(n)
   ↓
O(n log n)
   ↓
O(n × m)
   ↓
O(n²)
   ↓
O(2ⁿ)
   ↓
O(n!)
```