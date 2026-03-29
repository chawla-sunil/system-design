# ⚡ Maven in 5 Minutes — Interview Cheat Sheet

> Print this. Read it before the interview. You'll sound senior.

---

## What is Maven?

**One line:** Maven is a **build automation + dependency management + project structure convention** tool for Java (and JVM languages).

**Think of it as:** `npm` for Java. It downloads libraries, compiles code, runs tests, and packages JARs — all based on a single config file (`pom.xml`).

---

## The 3 Things Maven Does

| # | What | How |
|---|------|-----|
| 1 | **Build** your code | `mvn compile` → `mvn package` → produces a `.jar` / `.war` |
| 2 | **Manage dependencies** | You declare `<dependency>` in `pom.xml`, Maven downloads it from Maven Central |
| 3 | **Enforce project structure** | Every Maven project has the same folder layout (convention over configuration) |

---

## Standard Directory Layout (memorize this)

```
my-project/
├── pom.xml                    ← THE config file
├── src/
│   ├── main/
│   │   ├── java/              ← Your source code
│   │   └── resources/         ← Config files (application.properties, etc.)
│   └── test/
│       ├── java/              ← Test source code
│       └── resources/         ← Test config files
└── target/                    ← Build output (generated, never commit)
```

---

## Build Lifecycle (the interview favorite)

Maven has **3 lifecycles**. The one they always ask about is the **default lifecycle**:

```
validate → compile → test → package → verify → install → deploy
```

**Key rule:** When you run a phase, **all previous phases run first**.

```bash
mvn package     # runs: validate → compile → test → package
mvn install     # runs: validate → compile → test → package → verify → install
mvn test        # runs: validate → compile → test
```

### Clean Lifecycle (separate)
```bash
mvn clean       # deletes target/ folder
mvn clean package  # clean THEN run default lifecycle up to package
```

---

## 10 Commands You Must Know

```bash
mvn clean                  # Delete target/
mvn compile                # Compile src/main/java
mvn test                   # Compile + run tests
mvn package                # Compile + test + build JAR/WAR
mvn install                # package + copy JAR to ~/.m2/repository
mvn clean install          # Most common: fresh build + install
mvn clean install -DskipTests  # Skip tests (fast build)
mvn dependency:tree        # Show all dependencies (transitive too)
mvn versions:display-dependency-updates  # Check for newer versions
mvn -pl module-name compile  # Build only one module
```

---

## POM.xml — The 7 Things in Every POM

```xml
<project>
    <!-- 1. COORDINATES — uniquely identify this project -->
    <groupId>com.mycompany</groupId>       <!-- like a package name -->
    <artifactId>my-app</artifactId>        <!-- project name -->
    <version>1.0.0</version>               <!-- version -->
    <packaging>jar</packaging>             <!-- jar / war / pom -->

    <!-- 2. PARENT — inherit config from a parent POM -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <!-- 3. PROPERTIES — variables you can reuse -->
    <properties>
        <java.version>17</java.version>
    </properties>

    <!-- 4. DEPENDENCIES — libraries your code needs -->
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>

    <!-- 5. BUILD / PLUGINS — customize build behavior -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
        </plugins>
    </build>

    <!-- 6. MODULES — for multi-module projects -->
    <modules>
        <module>service-a</module>
        <module>service-b</module>
    </modules>

    <!-- 7. REPOSITORIES — where to download dependencies from -->
    <repositories>
        <repository>
            <id>central</id>
            <url>https://repo.maven.apache.org/maven2</url>
        </repository>
    </repositories>
</project>
```

---

## Dependency Scopes (they ask this)

| Scope | Available at | Example |
|-------|-------------|---------|
| `compile` (default) | Compile + Test + Runtime | Spring, Guava |
| `provided` | Compile + Test only (NOT in JAR) | Servlet API, Lombok |
| `runtime` | Test + Runtime only (NOT at compile) | JDBC drivers |
| `test` | Test only | JUnit, Mockito |
| `system` | Like provided, but you give the JAR path | Legacy JARs (avoid this) |

**Interview answer:** "compile scope is bundled in the final JAR. provided means the container (Tomcat) supplies it. test is only for testing."

---

## Transitive Dependencies (interview gold)

If your project depends on **A**, and **A** depends on **B**, Maven automatically pulls **B** too.

**Problem:** What if A needs `guava:30` and B needs `guava:29`?

**Maven's rule:** **Nearest wins** (shortest path in dependency tree).

**How to fix conflicts:**
```xml
<!-- Force a specific version -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>32.1.3-jre</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Or exclude a transitive dependency -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>library-a</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## Multi-Module Project (like this repo)

```
parent-pom (packaging = pom)
├── module-a (packaging = jar)
├── module-b (packaging = jar)
└── module-c (packaging = jar)
```

- Parent POM has `<modules>` listing children.
- Children have `<parent>` pointing back.
- `mvn clean install` at root builds ALL modules in order.
- `mvn -pl module-a compile` builds ONLY module-a.

---

## Quick Answers for Interview Questions

| Question | Answer |
|----------|--------|
| Maven vs Gradle? | Maven = XML, convention-based, mature. Gradle = Groovy/Kotlin DSL, flexible, faster (incremental builds). Most enterprises still use Maven. |
| What is `~/.m2/repository`? | Local cache of downloaded dependencies. Maven checks here before downloading from remote. |
| What is a SNAPSHOT? | `1.0-SNAPSHOT` = "still in development, may change". Maven re-downloads SNAPSHOTs. Release versions are cached forever. |
| What is `dependencyManagement`? | Declares versions centrally in parent POM. Children inherit the version without specifying it. Does NOT add the dependency — just controls the version. |
| What is a Maven plugin? | A plugin adds goals (tasks) to the lifecycle. E.g., `maven-compiler-plugin` handles the `compile` phase, `maven-surefire-plugin` handles `test`. |
| `install` vs `deploy`? | `install` copies JAR to local `~/.m2`. `deploy` uploads JAR to a remote repository (Nexus, Artifactory). |
| What is an archetype? | A project template. `mvn archetype:generate` creates a new project from a template. |

