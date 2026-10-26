# Java Exception Handling --- Interview Notes

## 1. Exception Handling Basics

Exception handling in Java is used to handle runtime problems gracefully
and keep failures controlled.

### Exception hierarchy

``` text
Throwable
├── Error
│   ├── OutOfMemoryError
│   └── StackOverflowError
└── Exception
    ├── RuntimeException
    │   ├── NullPointerException
    │   ├── ArithmeticException
    │   ├── IllegalArgumentException
    │   └── ArrayIndexOutOfBoundsException
    └── Checked Exceptions
        ├── IOException
        └── SQLException
```

-   **Error**: serious JVM/system-level problems; generally not handled
    by application code.
-   **Exception**: conditions an application can potentially handle.
-   **Checked exception**: compiler requires it to be caught or
    declared.
-   **Unchecked exception**: `RuntimeException` and its subclasses;
    compiler does not require explicit handling.

------------------------------------------------------------------------

# 2. try-catch

Basic syntax:

``` java
try {
    // risky code
} catch (ExceptionType e) {
    // handling
}
```

Example:

``` java
try {
    int result = 10 / 0;
} catch (ArithmeticException e) {
    System.out.println("Cannot divide by zero");
}
```

Once an exception occurs inside the `try`, the remaining statements in
that `try` block are skipped.

------------------------------------------------------------------------

# 3. Multiple Catch Blocks

Multiple catch blocks can handle different exceptions differently.

``` java
try {
    int[] arr = {1, 2, 3};
    System.out.println(arr[5]);
} catch (ArithmeticException e) {
    System.out.println("Arithmetic");
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("Array");
} catch (Exception e) {
    System.out.println("Generic");
}
```

Output:

``` text
Array
```

### Important rule

Only the **first matching catch block** executes.

Catch blocks should be ordered from:

``` text
Specific → General
```

Correct:

``` java
catch (NullPointerException e) {
}
catch (RuntimeException e) {
}
catch (Exception e) {
}
```

Incorrect:

``` java
catch (Exception e) {
}
catch (RuntimeException e) {
}
```

The second block is unreachable because `RuntimeException` is a subclass
of `Exception`.

------------------------------------------------------------------------

# 4. Exception vs NullPointerException

The hierarchy is:

``` text
Exception
   ↑
RuntimeException
   ↑
NullPointerException
```

Therefore:

``` java
try {
    String name = null;
    System.out.println(name.length());
} catch (NullPointerException e) {
    System.out.println("NPE");
} catch (RuntimeException e) {
    System.out.println("Runtime");
} catch (Exception e) {
    System.out.println("Exception");
}
```

Output:

``` text
NPE
```

If `Exception` comes first, the later `NullPointerException` catch is
unreachable.

------------------------------------------------------------------------

# 5. finally

`finally` normally executes whether an exception occurs or not.

``` java
try {
    System.out.println("A");
    int x = 10 / 0;
} catch (ArithmeticException e) {
    System.out.println("B");
} finally {
    System.out.println("C");
}

System.out.println("D");
```

Output:

``` text
A
B
C
D
```

If no exception occurs:

``` java
try {
    System.out.println("A");
} catch (Exception e) {
    System.out.println("B");
} finally {
    System.out.println("C");
}

System.out.println("D");
```

Output:

``` text
A
C
D
```

------------------------------------------------------------------------

# 6. Exception Not Caught

``` java
try {
    int x = 10 / 0;
} catch (NullPointerException e) {
    System.out.println("A");
} finally {
    System.out.println("B");
}
```

Output before the exception propagates:

``` text
B
```

The `ArithmeticException` is not handled by the `NullPointerException`
catch, but `finally` still executes.

------------------------------------------------------------------------

# 7. return and finally

A `finally` block executes before a method actually returns.

``` java
public static int test() {
    try {
        return 10;
    } finally {
        System.out.println("Finally");
    }
}
```

``` java
System.out.println(test());
```

Output:

``` text
Finally
10
```

------------------------------------------------------------------------

# 8. return in try and finally

If both `try` and `finally` return, the `finally` return wins.

``` java
public static int test() {
    try {
        return 10;
    } finally {
        return 20;
    }
}
```

Output:

``` text
20
```

### Interview rule

Avoid `return` inside `finally`.

It can override both: - a return from `try`/`catch` - an exception
thrown from `try`/`catch`

------------------------------------------------------------------------

# 9. Exception in try + return in finally

``` java
public static int test() {
    try {
        int x = 10 / 0;
        return 10;
    } finally {
        return 20;
    }
}
```

Result:

``` text
20
```

The exception is suppressed by the `return` in `finally`.

------------------------------------------------------------------------

# 10. return in catch + finally

``` java
public static int test() {
    try {
        throw new RuntimeException();
    } catch (RuntimeException e) {
        System.out.println("Catch");
        return 10;
    } finally {
        System.out.println("Finally");
    }
}
```

Output:

``` text
Catch
Finally
10
```

The `finally` runs before the return completes.

------------------------------------------------------------------------

# 11. Modifying a Local Variable in finally

``` java
public static int test() {
    int x = 10;

    try {
        return x;
    } finally {
        x = 20;
    }
}
```

Result:

``` text
10
```

The return value `10` was evaluated before `finally` changed `x`.

------------------------------------------------------------------------

# 12. Mutable Object + finally

With a mutable object, the returned reference can point to an object
modified by `finally`.

``` java
public static StringBuilder test() {
    StringBuilder sb = new StringBuilder("A");

    try {
        return sb;
    } finally {
        sb.append("B");
    }
}
```

Result:

``` text
AB
```

The reference points to the same `StringBuilder` object.

------------------------------------------------------------------------

# 13. Exception in catch

``` java
try {
    System.out.println("A");
    int x = 10 / 0;
} catch (ArithmeticException e) {
    System.out.println("B");
    int y = 10 / 0;
} finally {
    System.out.println("C");
}
```

Output:

``` text
A
B
C
```

The second exception from the `catch` propagates after `finally`.

------------------------------------------------------------------------

# 14. Exception in finally

``` java
try {
    System.out.println("A");
} finally {
    System.out.println("B");
    throw new RuntimeException();
}
```

Output:

``` text
A
B
```

Then the `RuntimeException` propagates.

------------------------------------------------------------------------

# 15. Nested try-catch

``` java
try {
    System.out.println("A");

    try {
        System.out.println("B");
        int x = 10 / 0;
    } catch (ArithmeticException e) {
        System.out.println("C");
    }

    System.out.println("D");

} catch (Exception e) {
    System.out.println("E");
}

System.out.println("F");
```

Output:

``` text
A
B
C
D
F
```

The inner catch handles the exception, so the outer catch is not
executed.

------------------------------------------------------------------------

# 16. Nested try where Inner Catch Does Not Match

``` java
try {
    System.out.println("A");

    try {
        System.out.println("B");
        String s = null;
        s.length();
    } catch (ArithmeticException e) {
        System.out.println("C");
    }

    System.out.println("D");

} catch (NullPointerException e) {
    System.out.println("E");
}

System.out.println("F");
```

Output:

``` text
A
B
E
F
```

The inner catch does not handle `NullPointerException`, so the exception
propagates to the outer catch.

`D` is skipped.

------------------------------------------------------------------------

# 17. Nested try + finally

``` java
try {
    System.out.println("A");

    try {
        System.out.println("B");
        int x = 10 / 0;
    } finally {
        System.out.println("C");
    }

} catch (Exception e) {
    System.out.println("D");
} finally {
    System.out.println("E");
}
```

Output:

``` text
A
B
C
D
E
```

Execution:

``` text
Outer try
   ↓
Inner try
   ↓
Exception
   ↓
Inner finally
   ↓
Outer catch
   ↓
Outer finally
```

------------------------------------------------------------------------

# 18. throw

`throw` explicitly throws an exception.

``` java
public void withdraw(double amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("Amount must be positive");
    }
}
```

Important:

> `throw` actually throws an exception.

------------------------------------------------------------------------

# 19. throws

`throws` declares that a method may propagate an exception.

``` java
public void readFile() throws IOException {
    Files.readString(Path.of("data.txt"));
}
```

Important:

> `throws` declares that a method can throw an exception.

### throw vs throws

``` text
throw  → actually throws an exception
throws → declares possible exceptions
```

------------------------------------------------------------------------

# 20. Checked Exception

A checked exception must be caught or declared.

This produces a compile-time error:

``` java
public void read() {
    Files.readString(Path.of("test.txt"));
}
```

Correct:

``` java
public void read() throws IOException {
    Files.readString(Path.of("test.txt"));
}
```

or:

``` java
public void read() {
    try {
        Files.readString(Path.of("test.txt"));
    } catch (IOException e) {
        // handle exception
    }
}
```

------------------------------------------------------------------------

# 21. Custom Exception

``` java
class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
```

Usage:

``` java
public void withdraw(double balance, double amount) {
    if (amount > balance) {
        throw new InsufficientBalanceException(
            "Insufficient balance"
        );
    }
}
```

Custom exceptions are useful for representing business/domain failures.

------------------------------------------------------------------------

# 22. Multi-Catch

Java supports multi-catch:

``` java
try {
    // code
} catch (IOException | SQLException e) {
    System.out.println("Operation failed");
}
```

Use it when different exception types require the **same handling
logic**.

Instead of:

``` java
catch (IOException e) {
    System.out.println("Operation failed");
}
catch (SQLException e) {
    System.out.println("Operation failed");
}
```

you can use:

``` java
catch (IOException | SQLException e) {
    System.out.println("Operation failed");
}
```

------------------------------------------------------------------------

# 23. Multi-Catch Compile-Time Errors

This is invalid:

``` java
catch (Exception | RuntimeException e) {
}
```

Why?

``` text
RuntimeException extends Exception
```

A multi-catch cannot contain types where one is a subtype of another.

This is also invalid:

``` java
catch (IOException | IOException e) {
}
```

The same exception type cannot appear twice.

------------------------------------------------------------------------

# 24. try-with-resources

For resources implementing `AutoCloseable`, prefer try-with-resources.

``` java
try (BufferedReader reader =
         new BufferedReader(new FileReader("data.txt"))) {

    String line = reader.readLine();

} catch (IOException e) {
    System.out.println("Unable to read file");
}
```

The resource is automatically closed.

------------------------------------------------------------------------

# 25. Compile-Time Error Scenarios

## A. Parent catch before child catch

``` java
try {
    int x = 10 / 0;
} catch (Exception e) {
    System.out.println("Exception");
} catch (ArithmeticException e) {
    System.out.println("Arithmetic");
}
```

Compile-time error because `ArithmeticException` is already covered by
`Exception`.

------------------------------------------------------------------------

## B. Catching a checked exception that cannot occur

``` java
try {
    System.out.println("Hello");
} catch (IOException e) {
    System.out.println("IO");
}
```

Compile-time error because the `try` body does not throw a checked
`IOException`.

Note: unchecked exceptions such as `RuntimeException` can generally be
caught even if the compiler cannot prove that they occur.

------------------------------------------------------------------------

## C. catch without try

``` java
catch (Exception e) {
}
```

Compile-time error.

------------------------------------------------------------------------

## D. finally without try

``` java
finally {
}
```

Compile-time error.

------------------------------------------------------------------------

## E. try without catch or finally

``` java
try {
    System.out.println("Hello");
}
```

Compile-time error.

A `try` must have at least one `catch` or a `finally`.

------------------------------------------------------------------------

## F. Duplicate catch

``` java
try {
    int x = 10 / 0;
} catch (ArithmeticException e) {
} catch (ArithmeticException e) {
}
```

Compile-time error because the second catch is unreachable.

------------------------------------------------------------------------

## G. Checked exception not handled

``` java
public void test() {
    throw new IOException();
}
```

Compile-time error.

Correct:

``` java
public void test() throws IOException {
    throw new IOException();
}
```

------------------------------------------------------------------------

## H. throw requires Throwable

Invalid:

``` java
throw new String("Error");
```

`throw` can only throw objects derived from `Throwable`.

Valid:

``` java
throw new Exception();
throw new RuntimeException();
throw new IOException();
```

------------------------------------------------------------------------

## I. Missing return

``` java
public static int test() {
    try {
        return 10;
    } catch (Exception e) {
        System.out.println("Error");
    }
}
```

Compile-time error because the `catch` path doesn't return an `int`.

------------------------------------------------------------------------

## J. Unreachable statement

``` java
public static int test() {
    try {
        return 10;
        System.out.println("Hello");
    } catch (Exception e) {
        return 20;
    }
}
```

Compile-time error because the `println` is unreachable.

------------------------------------------------------------------------

# 26. Error vs Exception

`Error` and `Exception` are siblings under `Throwable`.

``` text
Throwable
├── Exception
└── Error
```

Therefore:

``` java
try {
    throw new StackOverflowError();
} catch (Exception e) {
    System.out.println("Caught");
}
```

The `Exception` catch does not catch `StackOverflowError`.

------------------------------------------------------------------------

# 27. Common Output Questions

## Question 1

``` java
try {
    System.out.println("A");
    int x = 10 / 0;
    System.out.println("B");
} catch (ArithmeticException e) {
    System.out.println("C");
}

System.out.println("D");
```

Output:

``` text
A
C
D
```

------------------------------------------------------------------------

## Question 2

``` java
try {
    return 10;
} finally {
    return 20;
}
```

Result:

``` text
20
```

------------------------------------------------------------------------

## Question 3

``` java
try {
    throw new RuntimeException();
} finally {
    return 20;
}
```

Result:

``` text
20
```

The exception is suppressed by the return in `finally`.

------------------------------------------------------------------------

## Question 4

``` java
try {
    System.out.println("A");
} catch (Exception e) {
    System.out.println("B");
} finally {
    System.out.println("C");
}
```

Output:

``` text
A
C
```

------------------------------------------------------------------------

# 28. Interview Rules to Memorize

### Rule 1

Once an exception occurs, remaining statements in that `try` block are
skipped.

### Rule 2

Only the first matching catch block executes.

### Rule 3

Catch blocks should be ordered:

``` text
Specific → General
```

### Rule 4

`finally` normally executes regardless of exception or return.

### Rule 5

A `return` in `finally` overrides a return or exception from
`try`/`catch`.

### Rule 6

Checked exceptions must be caught or declared with `throws`.

### Rule 7

`RuntimeException` and its subclasses are unchecked.

### Rule 8

`Exception` does not catch `Error`.

### Rule 9

A multi-catch cannot contain parent-child exception types:

``` java
catch (Exception | RuntimeException e) // invalid
```

### Rule 10

Use try-with-resources for resources that implement `AutoCloseable`.

------------------------------------------------------------------------

# 29. Spring Boot Exception Handling

For backend applications, exceptions can be handled centrally using
`@RestControllerAdvice`.

``` java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(
            UserNotFoundException e) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(
            Exception e) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error");
    }
}
```

Service:

``` java
public User getUser(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() ->
            new UserNotFoundException("User not found: " + id));
}
```

Typical flow:

``` text
Controller
    ↓
Service
    ↓
Repository
    ↓
Exception
    ↓
@RestControllerAdvice
    ↓
HTTP Response
```

Benefits:

-   Consistent API error responses
-   No repetitive exception handling in every controller
-   Centralized logging
-   Easier maintenance

------------------------------------------------------------------------

# 30. Production Best Practices

-   Catch specific exceptions rather than blindly catching `Exception`.
-   Catch exceptions where you can actually recover or add useful
    context.
-   Do not silently swallow exceptions.
-   Preserve the original cause when wrapping:

``` java
throw new ServiceException("Failed to process order", e);
```

-   Do not expose stack traces to API clients.
-   Use centralized exception handling in Spring Boot.
-   Include correlation/trace IDs in logs.
-   Do not use exceptions for normal control flow.
-   Use domain-specific exceptions for business failures.
-   Use try-with-resources for resource cleanup.
-   Retry only failures that are potentially transient and safe to
    retry.
-   Make retryable operations idempotent.

------------------------------------------------------------------------

# 31. Senior Interview Answer

> In Java, I use exceptions to separate error handling from normal
> business logic. I prefer specific exceptions and propagate them when
> the current layer cannot recover. For business failures, I use custom
> domain exceptions. In Spring Boot, I handle exceptions centrally with
> `@RestControllerAdvice` so clients receive consistent error responses.
> For checked exceptions, I either handle them or declare them with
> `throws`. I also use try-with-resources for resource cleanup, preserve
> the original cause when wrapping exceptions, and avoid using
> exceptions for normal control flow.

------------------------------------------------------------------------

# Quick Revision Cheat Sheet

``` text
try
  ↓
risky code

catch
  ↓
handle exception

finally
  ↓
cleanup / always executes normally

throw
  ↓
explicitly throw exception

throws
  ↓
declare exception

Checked Exception
  ↓
catch OR throws required

RuntimeException
  ↓
unchecked

Multiple catch
  ↓
specific → general

Multi-catch
  ↓
catch (A | B e)

finally return
  ↓
overrides try/catch return and can suppress exception

try-with-resources
  ↓
automatic resource cleanup

Spring Boot
  ↓
@RestControllerAdvice + @ExceptionHandler
```
