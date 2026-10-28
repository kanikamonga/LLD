# Java Interview Concepts — Core Notes

## 1. Passing an `ArrayList` to a Method and Reassigning It

Java is **always pass-by-value**. When an object is passed to a method, the value being copied is the object's reference.

### Example

```java
import java.util.*;

public class Main {

    static void modifyList(ArrayList<Integer> list) {
        list.add(4);                 // modifies the original object

        list = new ArrayList<>();    // reassigns only the local reference
        list.add(100);               // modifies the new object
    }

    public static void main(String[] args) {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        modifyList(list);

        System.out.println(list);
    }
}
```

### Output

```text
[1, 2, 3, 4]
```

### Why?

Initially, both references point to the same object:

```text
main()                    modifyList()
list ───────────────┐     list ───────────────┐
                    ▼                         ▼
                ArrayList [1, 2, 3]
```

Therefore:

```java
list.add(4);
```

modifies the shared object.

But:

```java
list = new ArrayList<>();
```

changes only the **local copy of the reference** inside `modifyList()`.

After reassignment:

```text
main()                    modifyList()
list ───────────────► [1, 2, 3, 4]

                          list ───────────► [100]
```

The caller's reference is unchanged.

### Interview takeaway

> Mutation of an object through a parameter is visible to the caller, but reassigning the parameter to a new object is local to the method.

---

# 2. `String ==` vs `equals()`

The `==` operator and `.equals()` serve different purposes for Strings.

## `==`

`==` compares **references** for objects.

It checks whether two references point to the same object.

```java
String s1 = "hello";
String s2 = "hello";

System.out.println(s1 == s2);
```

Output:

```text
true
```

Both literals refer to the same pooled String.

## `.equals()`

`.equals()` compares **String content**.

```java
String s1 = new String("hello");
String s2 = new String("hello");

System.out.println(s1 == s2);
System.out.println(s1.equals(s2));
```

Output:

```text
false
true
```

The two objects are different, but their contents are equal.

### Important comparison

| Operation | What it checks |
|---|---|
| `==` | Same object/reference |
| `equals()` | Same String content |

### Interview rule

> Use `.equals()` when you want to compare String values.

---

# 3. Java Memory Management

Java memory is managed by the JVM. The key concepts are:

- Stack
- Heap
- Garbage Collector

The JVM specification does not require a particular physical memory layout, but the stack/heap model is useful for understanding Java programs.

---

## 3.1 Stack

Each thread has its own stack.

The stack contains method execution frames, including things such as:

- Local variables
- Method parameters
- Object references
- Return information
- Intermediate values

Example:

```java
public void calculate() {
    int x = 10;
    Employee e = new Employee();
}
```

Conceptually:

```text
Stack
┌─────────────────────┐
│ calculate() frame   │
│ x = 10              │
│ e ───────────────┐  │
└───────────────────│──┘
                    │
                    ▼
                 Heap
              ┌───────────┐
              │ Employee  │
              └───────────┘
```

When the method finishes, its stack frame is removed.

### Stack characteristics

- Per-thread
- Used for method execution
- Very fast access
- Automatically cleaned up when frames disappear
- Excessive recursion can cause `StackOverflowError`

---

## 3.2 Heap

The heap is shared among threads.

Objects and arrays are generally allocated on the heap.

```java
Employee e = new Employee();
```

Conceptually:

```text
Stack                  Heap

e ───────────────────► Employee object
```

A simplified heap view:

```text
Heap
┌──────────────────────────────┐
│ Young Generation             │
│                              │
│ Eden → Survivor → Survivor   │
│                              │
├──────────────────────────────┤
│ Old Generation               │
│                              │
│ Long-lived objects           │
└──────────────────────────────┘
```

Exact heap behavior depends on the JVM and selected garbage collector.

---

## 3.3 Garbage Collector

Java automatically manages heap memory using Garbage Collection.

An object becomes eligible for garbage collection when it is no longer reachable from the JVM's GC roots.

Example:

```java
Employee e = new Employee();

e = null;
```

If no other reachable reference points to the `Employee`, it can become eligible for GC.

Important:

> Eligible for GC does not mean that the object is immediately collected.

The JVM decides when and how garbage collection occurs.

### Common GC roots

Examples include:

- Active thread stacks
- Static references
- JNI references
- Other JVM-maintained roots

---

## 3.4 Stack vs Heap

| Feature | Stack | Heap |
|---|---|---|
| Purpose | Method execution/local data | Objects and arrays |
| Ownership | Per thread | Shared |
| Management | JVM | JVM + GC |
| Typical lifetime | Method/frame lifetime | Until unreachable + GC |
| Typical error | `StackOverflowError` | `OutOfMemoryError` |

### Important nuance

Avoid saying:

> "Primitive variables are always on the stack and objects are always on the heap."

This is an oversimplification. JVM/JIT optimizations such as escape analysis and scalar replacement can change how allocations are implemented.

---

# 4. `String` vs `StringBuilder`

The main difference is **mutability**.

- `String` → immutable
- `StringBuilder` → mutable

---

## 4.1 String

Once a String object is created, its contents cannot be changed.

```java
String s = "Hello";
s = s + " World";

System.out.println(s);
```

The original `"Hello"` is not modified. A new String value is produced.

Conceptually:

```text
Before:

s ──► "Hello"

After:

s ──► "Hello World"
```

This matters for repeated concatenation:

```java
String result = "";

for (int i = 0; i < 10000; i++) {
    result += i;
}
```

Repeated concatenation can result in many intermediate String objects.

---

## 4.2 StringBuilder

`StringBuilder` is mutable.

```java
StringBuilder sb = new StringBuilder("Hello");

sb.append(" World");
sb.append("!");

System.out.println(sb);
```

Output:

```text
Hello World!
```

The builder modifies its internal character storage rather than requiring a new String for every append.

### Typical use

```java
StringBuilder result = new StringBuilder();

for (int i = 0; i < 1000; i++) {
    result.append(i);
}

String output = result.toString();
```

---

## 4.3 Performance

For repeated modifications, `StringBuilder` is generally more efficient than repeatedly constructing Strings.

However, modern Java compilers/JITs can optimize some String concatenation expressions.

For example:

```java
String s = a + b + c;
```

should not automatically be described as creating a new String object for every individual `+` at runtime.

### Rule of thumb

| Requirement | Choice |
|---|---|
| Fixed text | `String` |
| Simple/few concatenations | `String` |
| Many modifications/concatenations | `StringBuilder` |
| Shared mutable string with synchronization requirement | `StringBuffer` |

---

# 5. String Constant Pool (SCP)

The String Constant Pool is a JVM-managed pool containing canonical String instances.

Example:

```java
String s1 = "Java";
String s2 = "Java";

System.out.println(s1 == s2);
```

Output:

```text
true
```

Conceptually:

```text
s1 ──┐
     ├──► SCP: "Java"
s2 ──┘
```

Strings can safely be shared because they are immutable.

---

## `new String()`

```java
String s1 = "Java";
String s2 = new String("Java");

System.out.println(s1 == s2);
System.out.println(s1.equals(s2));
```

Output:

```text
false
true
```

Conceptually:

```text
SCP
└── "Java" ◄── s1

Heap
└── new String("Java") ◄── s2
```

The literal `"Java"` is pooled, while `new String("Java")` explicitly creates another String object.

---

## StringBuilder and SCP

A `StringBuilder` object is not stored in the String Constant Pool.

```java
StringBuilder sb = new StringBuilder("Java");
```

Conceptually:

```text
SCP
└── "Java"

Heap
└── StringBuilder
       └── internal character storage
```

When:

```java
String result = sb.toString();
```

a String representation is produced. It is not automatically placed into the SCP merely because it came from a StringBuilder.

To request the canonical pooled String:

```java
String pooled = sb.toString().intern();
```

---

# 6. `StringBuffer` vs `StringBuilder`

Both are mutable character sequences.

The major difference is **thread safety**.

---

## 6.1 StringBuilder

- Mutable
- Not thread-safe
- Methods are not synchronized
- Generally faster when synchronization is unnecessary

```java
StringBuilder sb = new StringBuilder();
sb.append("Hello");
```

If the same builder is shared between threads, external synchronization or another concurrency design is required.

---

## 6.2 StringBuffer

- Mutable
- Thread-safe at the method level
- Its methods are synchronized
- Synchronization can introduce overhead

```java
StringBuffer sb = new StringBuffer();
sb.append("Hello");
```

Individual operations such as `append()` are synchronized.

### Important concurrency nuance

Thread-safe methods do not automatically make a sequence of multiple operations atomic.

For example:

```java
if (buffer.length() < 100) {
    buffer.append("Hello");
}
```

Another thread can modify the buffer between `length()` and `append()`.

If those operations must form one atomic operation, additional synchronization may be necessary.

---

## 6.3 Performance and Use Cases

| Feature | StringBuilder | StringBuffer |
|---|---|---|
| Mutable | Yes | Yes |
| Thread-safe | No | Yes |
| Methods synchronized | No | Yes |
| Typical single-thread performance | Generally faster | Generally slower |
| Best for | Local string construction | Shared mutable access where synchronization is required |

### StringBuilder use cases

- String construction in a single thread
- Loop-based concatenation
- Building SQL/JSON/text
- Local response construction
- Temporary mutable text

### StringBuffer use cases

- Legacy code
- A genuinely shared mutable character sequence requiring synchronized operations

In modern concurrent applications, avoiding shared mutable state is often preferable to sharing a single StringBuffer.

---

# 7. Java Parameter Passing

Java is **strictly pass-by-value**.

The important difference is what value gets copied.

- Primitive → primitive value is copied
- Object → reference value is copied

Java does **not** use pass-by-reference.

---

## 7.1 Primitive Parameters

Example:

```java
static void change(int x) {
    x = 100;
}

public static void main(String[] args) {
    int a = 10;

    change(a);

    System.out.println(a);
}
```

Output:

```text
10
```

Conceptually:

```text
main()                  change()

a = 10  ──copy──►       x = 10

                        x = 100
```

`x` is a separate copy, so changing it does not change `a`.

---

# 8. Object Parameters

Consider:

```java
class Person {
    int age;
}

static void change(Person p) {
    p.age = 30;
}

public static void main(String[] args) {
    Person person = new Person();
    person.age = 20;

    change(person);

    System.out.println(person.age);
}
```

Output:

```text
30
```

Why?

The reference value is copied:

```text
main()                       change()

person ───────┐
              │
              ▼
          Person object
          age = 20
              ▲
              │
p ────────────┘
```

Both references point to the same object.

Therefore:

```java
p.age = 30;
```

modifies the shared object.

The caller sees the change.

---

# 9. Object Reassignment Does Not Affect the Caller

This is the most important follow-up interview question.

```java
static void change(Person p) {
    p = new Person();
    p.age = 50;
}

public static void main(String[] args) {
    Person person = new Person();
    person.age = 20;

    change(person);

    System.out.println(person.age);
}
```

Output:

```text
20
```

Initially:

```text
main()
person ─────────► Person A
                  age = 20

method()
p ──────────────► Person A
```

After:

```java
p = new Person();
```

the local reference changes:

```text
main()
person ─────────► Person A
                  age = 20

method()
p ──────────────► Person B
                  age = 50
```

The caller's `person` still points to Person A.

---

# 10. Mutation vs Reassignment

This distinction is worth memorizing for interviews.

### Mutation

```java
void modify(Person p) {
    p.age = 50;
}
```

The object is modified.

**Caller sees the change.**

### Reassignment

```java
void modify(Person p) {
    p = new Person();
}
```

The local parameter is made to point somewhere else.

**Caller does not see the reassignment.**

---

# 11. Why Java Is Strictly Pass-by-Value

A common incorrect statement is:

> "Java passes objects by reference."

The precise statement is:

> **Java always passes arguments by value. For object parameters, the value being copied is the object's reference.**

This explains both behaviors:

```java
p.age = 50;
```

The copied reference still points to the same object, so the object's state changes.

But:

```java
p = new Person();
```

Only the local copy of the reference changes.

---

# 12. Quick Interview Revision Table

| Concept | Key Point |
|---|---|
| ArrayList parameter | Mutating it affects caller; reassigning parameter does not |
| `==` for String | Compares references |
| `String.equals()` | Compares content |
| `String` | Immutable |
| `StringBuilder` | Mutable, not thread-safe |
| `StringBuffer` | Mutable, synchronized/thread-safe at method level |
| SCP | Pool for canonical String instances |
| Stack | Per-thread method execution memory |
| Heap | Shared area where objects/arrays are generally allocated |
| GC | Reclaims unreachable heap objects |
| Primitive parameter | Copy of primitive value |
| Object parameter | Copy of reference value |
| Object mutation | Caller can observe the changed state |
| Object reassignment | Caller cannot observe the new reference |

---

# 13. Short Interview Answers

### String `==` vs `equals()`

> "`==` compares references for objects, while `equals()` compares String content. For String value comparison, I use `equals()`."

### String vs StringBuilder

> "String is immutable, whereas StringBuilder is mutable. StringBuilder is generally preferable for repeated string modifications because it avoids creating a new String value for every modification."

### StringBuilder vs StringBuffer

> "Both are mutable. StringBuilder is not synchronized and is generally preferred when the builder isn't shared between threads. StringBuffer synchronizes its methods and is useful when synchronized shared mutable access is specifically required."

### Stack vs Heap

> "Each thread has its own stack for method execution frames and local data, while the heap is shared and generally contains objects and arrays. The garbage collector manages reclaiming unreachable heap objects."

### Java parameter passing

> "Java is strictly pass-by-value. For primitives, the primitive value is copied. For objects, the reference value is copied. Therefore, modifying the referenced object's state is visible to the caller, but reassigning the parameter to another object is not."

