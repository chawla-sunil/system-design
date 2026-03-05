# Builder Pattern

## What is it? (One-liner)
Builder separates the **construction of a complex object** from its representation, allowing the same construction process to create different representations.

## When to Use (Interview Answer)
> "I'd use Builder when an object has **many optional parameters** (telescoping constructor problem), or when the construction process involves multiple steps. It makes code readable and prevents errors from passing wrong arguments to constructors."

## The Problem It Solves
```java
// Telescoping constructor anti-pattern
new HttpRequest("url", "POST", "body", "json", "auth", 5000, true); // What's what??

// Builder - readable and clear
new HttpRequest.Builder("url")
    .method("POST")
    .body("body")
    .contentType("json")
    .authorization("auth")
    .timeout(5000)
    .build();
```

## How to Implement
```java
public class HttpRequest {
    private final String url;       // required
    private final String method;    // optional with default
    private final String body;      // optional
    private final int timeout;      // optional with default

    private HttpRequest(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.body = builder.body;
        this.timeout = builder.timeout;
    }

    public static class Builder {
        private final String url;               // Required
        private String method = "GET";          // Optional with default
        private String body = null;             // Optional
        private int timeout = 30000;            // Optional with default

        public Builder(String url) {
            this.url = url;
        }

        public Builder method(String method) {
            this.method = method;
            return this;  // enables method chaining
        }
        public Builder body(String body) { this.body = body; return this; }
        public Builder timeout(int timeout) { this.timeout = timeout; return this; }

        public HttpRequest build() {
            // Validation here
            return new HttpRequest(this);
        }
    }
}
```

## UML Structure
```
┌─────────────────────────┐
│       HttpRequest       │
├─────────────────────────┤
│ - url: String           │
│ - method: String        │
│ - body: String          │
├─────────────────────────┤
│ - HttpRequest(Builder)  │
└───────────┬─────────────┘
            │ inner class
┌───────────┴─────────────┐
│     HttpRequest.Builder │
├─────────────────────────┤
│ - url: String           │
│ - method: String = "GET"│
├─────────────────────────┤
│ + Builder(url)          │
│ + method(): Builder     │
│ + body(): Builder       │
│ + build(): HttpRequest  │
└─────────────────────────┘
```

## Real-World Examples
- `StringBuilder` / `StringBuffer`
- `Stream.Builder`
- `HttpClient.newBuilder()` (Java 11+)
- `Lombok @Builder`
- `Protobuf` message builders
- `OkHttp Request.Builder`

## Interview Deep-Dive Questions

**Q: Builder vs Telescoping Constructor vs JavaBeans setter pattern?**
| Approach | Immutability | Readability | Validation |
|----------|-------------|-------------|------------|
| Telescoping Constructor | Yes | Poor (arg order) | At construction |
| JavaBeans (setters) | No | Good | Hard to enforce |
| Builder | Yes | Excellent | At build() time |

**Q: When NOT to use Builder?**
> "If a class has only 2-3 parameters, a constructor is simpler. Builder adds boilerplate. Use Lombok's @Builder to reduce that in practice."

**Q: Can Builder be used with inheritance?**
> "Yes, using the 'recursive generics' pattern (Effective Java Item 2). The child builder extends the parent builder."

## Key Points to Mention in Interview
1. Solves telescoping constructor problem
2. Produces **immutable** objects (final fields, no setters)
3. Validation happens in `build()` method
4. Method chaining via `return this`
5. Mention Lombok `@Builder` as practical shortcut
6. Effective Java Item 2: "Consider a builder when faced with many constructor parameters"
