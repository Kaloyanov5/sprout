# Sprout
A lightweight, educational clone of the Spring Framework built to master IoC, AOP, and reflection.

> [!NOTE]
> **This tool is not affiliated with, endorsed by, or associated with Spring Framework.** It's an educational clone built to understand the mechanics, and it's not in production stage.

> [!IMPORTANT]
> **Required Java 21+ and Maven**

## What it does
- Classpath scanning for `@Wireable` classes (both exploded directories and JARs)
- Reflective instantiation and `@Wire` field injection
- Method interception via `@Logged`, `@Transacted`, `@Retried`
- Two proxy strategies, selected per class: JDK dynamic proxies and ByteBuddy subclasses
- Startup-time validation that fails fast when a class can't be proxied

## Project structure

```
src/main/java/com/sprout/
├── core/
│   ├── annotation/          @Wireable, @Wire, @Logged, @Transacted, @Retried
│   ├── exception/           NoSuchBeanDefinitionException, NoUniqueBeanDefinitionException
│   └── ioc/
│       ├── BeanContainer      scanning, instantiation, proxy selection, injection
│       ├── AspectInterceptor  the InvocationHandler shared by both proxy strategies
│       ├── Aop                the set of aspect annotations, in one place
│       └── TransactionScope   begin/commit/rollback with an active-state guard
└── demo/                    sample beans covering the JDK-proxy, subclass-proxy
                             and inherited-annotation paths
```

## Usage example

### 1. The beans:

`PaymentService`
```java
public interface PaymentService {
    @Logged
    @Transacted
    @Retried
    PaymentResult pay(String accountId, double amount);
}
```
`PaymentServiceImpl`
```java
@Wireable
public class PaymentServiceImpl implements PaymentService {
    @Wire
    private PaymentGateway paymentGateway;
    
    // PaymentResult(String accountId, double amount, boolean success)
    @Override
    public PaymentResult pay(String accountId, double amount) {
        // method body
    }
}
```
`PaymentGateway`
```java
@Wireable
public class PaymentGateway {
    public boolean charge(double amount) {
        return amount > 0;
    }
}
```

### 2. Bootstrapping:

`Main`
```java
BeanContainer container = new BeanContainer();
container.start("com.sprout.demo");
PaymentService psv = container.getBean(PaymentService.class);
psv.pay("1234", 50);
```

### 3. Output:

```
--- [LOG] Starting method pay ---
--- BEGIN TX ---
--- COMMIT TX ---
--- [LOG] Finished method pay ---
PaymentResult[accountId=1234, amount=50.0, success=true]
```

## How it works

### Scanning
`getResources()` returns one URL per classpath entry, and the protocol decides how to read it. `file` walks the directory tree recursively. `jar` opens a `JarURLConnection` and filters the archive's flat entry list by path prefix.

The `jar` branch is the part most toy containers skip, and it's the reason scanning works from a packaged artifact and not only from an IDE's exploded output directory.

### Instantiation
Filter to `@Wireable`, skip interfaces and abstract classes, then invoke the no-arg constructor with `setAccessible(true)` so a non-public constructor still works.

### Proxying
A JDK dynamic proxy implements interfaces. It is never an instance of your class, so `getBean(PaymentServiceImpl.class)` on a JDK-proxied bean would throw `ClassCastException` — the bean is registered under its interfaces instead. A Byte Buddy proxy generates a subclass, so it *is* an instance of the class, and gets registered under the class itself.

Selection isn't "does it have interfaces" but "do the interfaces actually declare the advised methods." A class implementing `Serializable` with `@Logged` on its own method has an interface, but a JDK proxy could never intercept that method - so it takes the Byte Buddy path.

Whichever strategy is chosen, both share one `AspectInterceptor`. Marker interfaces are skipped during registration, so implementing `Serializable` doesn't cause two unrelated beans to collide over it.

### Injection
Runs after all beans exist, so declaration order doesn't matter. Fields are set on the target rather than the proxy, since the proxy delegates and it's the target's method bodies that actually read them. The superclass chain is walked so inherited `@Wire` fields are found.

## Design notes

- **JDK proxies can only implement interfaces.** That single constraint is the reason two strategies exist at all — everything else in the proxying layer follows from working around it.
- **Java does not inherit annotations across method boundaries.** An interface method's annotations don't transfer to its implementation, and a superclass method's don't transfer to an override. Annotation resolution has to walk the hierarchy explicitly, or an ordinary `@Override` silently loses its advice.
- **Package-private members can't be overridden by a Byte Buddy subclass** under the default `WRAPPER` class-loading strategy. The generated class is defined in a child classloader, so it lands in a different *runtime* package even though the package names match, and `super()` calls fail at link time.
- **Silent non-interception is worse than a startup crash.** A bean that registers successfully but quietly drops its aspects is far harder to diagnose than one that refuses to start, so unproxyable advised methods fail fast with the specific reason named.

## Known limitations

- Singleton scope only
- Field injection only - no constructor or setter injection
- No circular dependency detection
- One bean per type; no qualifiers or names
- `@Transacted` logs demarcation only, with no real transactional resource behind it
- Self-invocation bypasses the proxy — a target method calling another of its own advised methods is not intercepted
- Annotations on interface *default* methods aren't discovered, so a bean whose only advised method is a default method is never proxied
- Interfaces inherited *through* a superclass are ignored: the bean is neither registered under them nor has their annotations resolved. Declaring `implements` on the bean class itself works fine

## Running it

```bash
mvn clean test
mvn compile exec:java -Dexec.mainClass=com.sprout.demo.Main
```

The `exec:java` goal needs no entry in `pom.xml` - `org.codehaus.mojo` is one of Maven's default plugin groups, so the `exec` prefix resolves and the plugin is fetched on first use.