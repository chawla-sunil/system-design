# 🏗️ Maven Deep Dive — Senior Engineer's Complete Reference

> Everything a senior engineer should know about Maven.  
> From "what is a POM" to "how does the reactor resolve module build order."

---

## Table of Contents

1. [What is Maven — Really?](#1-what-is-maven--really)
2. [The POM — Every Tag Explained](#2-the-pom--every-tag-explained)
3. [Coordinates (GAV) — How Maven Identifies Anything](#3-coordinates-gav)
4. [Build Lifecycles — The Full Picture](#4-build-lifecycles--the-full-picture)
5. [Phases, Plugins, Goals — The Execution Model](#5-phases-plugins-goals--the-execution-model)
6. [Dependencies — Scopes, Transitivity, Conflicts](#6-dependencies--scopes-transitivity-conflicts)
7. [dependencyManagement vs dependencies](#7-dependencymanagement-vs-dependencies)
8. [Multi-Module Projects — Reactor, Aggregation, Inheritance](#8-multi-module-projects)
9. [Repositories — Local, Remote, Mirrors, Settings.xml](#9-repositories)
10. [Profiles — Environment-Specific Builds](#10-profiles)
11. [Common Plugins — What They Do](#11-common-plugins)
12. [Properties and Variable Substitution](#12-properties)
13. [SNAPSHOT vs Release](#13-snapshot-vs-release)
14. [Effective POM — What Maven Actually Sees](#14-effective-pom)
15. [Maven Wrapper (mvnw)](#15-maven-wrapper)
16. [BOM (Bill of Materials)](#16-bom)
17. [Command Reference — Every Flag Explained](#17-command-reference)
18. [Maven vs Gradle — Honest Comparison](#18-maven-vs-gradle)
19. [Troubleshooting Cheat Sheet](#19-troubleshooting)
20. [Real-World Project Structure Example](#20-real-world-example)

---

## 1. What is Maven — Really?

Maven is **three things in one**:

### a) Build Tool
Compiles your Java source → runs tests → packages into JAR/WAR → deploys to a server or repository.

### b) Dependency Manager
You declare what libraries you need. Maven downloads them (and their dependencies, and *their* dependencies...) from a central repository.

### c) Convention Enforcer
Maven says: "Put source here, tests here, resources here." Every Maven project looks the same. A new developer can navigate any Maven project instantly.

### The Philosophy: Convention Over Configuration
Maven works with **zero configuration** if you follow its conventions. You only write config when you deviate.

```
"If you follow the rules, you write almost no XML.
 If you fight the rules, you write a LOT of XML."
```

### How it works internally

```
You type: mvn clean package

Maven does:
1. Reads pom.xml (and all parent POMs up to the Super POM)
2. Resolves the "effective POM" (merge of all inherited config)
3. Determines which lifecycle phases to execute (clean → ... → package)
4. For each phase, looks up which plugin:goal is bound to it
5. Executes each plugin:goal in order
6. Each plugin may download itself from Maven Central if not cached
7. Dependencies are resolved and downloaded to ~/.m2/repository
8. Source is compiled, tests run, JAR created in target/
```

---

## 2. The POM — Every Tag Explained

`pom.xml` = **Project Object Model**. It's the single source of truth for your project.

### Minimal POM (every project needs at least this)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>  <!-- Always 4.0.0 — the POM schema version -->

    <groupId>com.mycompany</groupId>     <!-- WHO owns it (reverse domain) -->
    <artifactId>my-app</artifactId>      <!-- WHAT is it (project name) -->
    <version>1.0.0</version>             <!-- WHICH version -->
</project>
```

That's it. Maven will:
- Assume `<packaging>jar</packaging>` (default)
- Use its Super POM defaults for everything else
- Compile source from `src/main/java`
- Run tests from `src/test/java`
- Output to `target/`

### Complete POM — All Sections

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <!-- ═══ COORDINATES ═══ -->
    <groupId>com.mycompany</groupId>
    <artifactId>my-app</artifactId>
    <version>2.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>           <!-- jar | war | pom | ear | maven-plugin -->
    <name>My Application</name>          <!-- Human-readable name (optional) -->
    <description>Does cool stuff</description>
    <url>https://github.com/me/my-app</url>

    <!-- ═══ PARENT ═══ -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>                  <!-- empty = look in Maven repo, not local filesystem -->
    </parent>

    <!-- ═══ MODULES (only in parent/aggregator POMs) ═══ -->
    <modules>
        <module>module-api</module>
        <module>module-service</module>
        <module>module-web</module>
    </modules>

    <!-- ═══ PROPERTIES ═══ -->
    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
    </properties>

    <!-- ═══ DEPENDENCY MANAGEMENT (version control, NOT inclusion) ═══ -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>    <!-- BOM import -->
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- ═══ DEPENDENCIES (actually included in build) ═══ -->
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <!-- version inherited from parent's dependencyManagement -->
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>      <!-- not in final JAR -->
            <optional>true</optional>    <!-- not transitive -->
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- ═══ BUILD ═══ -->
    <build>
        <!-- Default source/output dirs (usually don't need to set these) -->
        <sourceDirectory>src/main/java</sourceDirectory>
        <testSourceDirectory>src/test/java</testSourceDirectory>
        <outputDirectory>target/classes</outputDirectory>

        <!-- Resources to include in JAR -->
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>  <!-- replace ${} placeholders -->
            </resource>
        </resources>

        <!-- Plugins -->
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                    <compilerArgs>
                        <arg>-parameters</arg>  <!-- preserve method param names -->
                    </compilerArgs>
                </configuration>
            </plugin>
        </plugins>

        <!-- Plugin Management (like dependencyManagement but for plugins) -->
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.2.5</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>

    <!-- ═══ PROFILES ═══ -->
    <profiles>
        <profile>
            <id>production</id>
            <properties>
                <spring.profiles.active>prod</spring.profiles.active>
            </properties>
        </profile>
    </profiles>

    <!-- ═══ REPOSITORIES ═══ -->
    <repositories>
        <repository>
            <id>central</id>
            <url>https://repo.maven.apache.org/maven2</url>
        </repository>
    </repositories>

    <!-- ═══ DISTRIBUTION MANAGEMENT (where to deploy artifacts) ═══ -->
    <distributionManagement>
        <repository>
            <id>releases</id>
            <url>https://nexus.mycompany.com/repository/releases/</url>
        </repository>
        <snapshotRepository>
            <id>snapshots</id>
            <url>https://nexus.mycompany.com/repository/snapshots/</url>
        </snapshotRepository>
    </distributionManagement>
</project>
```

---

## 3. Coordinates (GAV)

Every artifact in Maven is uniquely identified by **GAV** (+ packaging + classifier):

```
groupId    : com.google.guava          ← WHO (reverse domain of org)
artifactId : guava                     ← WHAT (project name)
version    : 32.1.3-jre               ← WHICH version
packaging  : jar                       ← HOW it's packaged (default: jar)
classifier : sources / javadoc / jdk8  ← VARIANT (optional)
```

**Where does the JAR live in `~/.m2/repository`?**

```
~/.m2/repository/
  com/google/guava/          ← groupId (dots → slashes)
    guava/                   ← artifactId
      32.1.3-jre/            ← version
        guava-32.1.3-jre.jar ← the actual JAR
        guava-32.1.3-jre.pom ← its POM (so Maven knows ITS dependencies)
```

**In POM:**
```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>
```

---

## 4. Build Lifecycles — The Full Picture

Maven has **3 independent lifecycles**:

### a) `clean` Lifecycle

| Phase | What it does |
|-------|-------------|
| `pre-clean` | Hook before cleaning |
| **`clean`** | Deletes `target/` directory |
| `post-clean` | Hook after cleaning |

### b) `default` Lifecycle (the main one)

| Phase | What it does | Plugin:Goal bound by default |
|-------|-------------|------------------------------|
| `validate` | Check POM is correct | — |
| `initialize` | Set up build state | — |
| `generate-sources` | Generate source code (e.g., from WSDL) | — |
| `process-sources` | Process source code | — |
| `generate-resources` | Generate resources | — |
| `process-resources` | Copy resources to `target/classes` | `resources:resources` |
| **`compile`** | Compile `src/main/java` → `target/classes` | `compiler:compile` |
| `process-classes` | Post-process compiled classes | — |
| `generate-test-sources` | Generate test sources | — |
| `process-test-sources` | Process test sources | — |
| `generate-test-resources` | Generate test resources | — |
| `process-test-resources` | Copy test resources | `resources:testResources` |
| `test-compile` | Compile test sources | `compiler:testCompile` |
| `process-test-classes` | Post-process test classes | — |
| **`test`** | Run unit tests | `surefire:test` |
| `prepare-package` | Pre-package steps | — |
| **`package`** | Create JAR/WAR in `target/` | `jar:jar` or `war:war` |
| `pre-integration-test` | Set up integration test env | — |
| `integration-test` | Run integration tests | `failsafe:integration-test` |
| `post-integration-test` | Tear down integration test env | — |
| **`verify`** | Run checks (code coverage, etc.) | `failsafe:verify` |
| **`install`** | Copy artifact to `~/.m2/repository` | `install:install` |
| **`deploy`** | Upload artifact to remote repo | `deploy:deploy` |

### c) `site` Lifecycle

| Phase | What it does |
|-------|-------------|
| `pre-site` | Hook |
| `site` | Generate project documentation site |
| `post-site` | Hook |
| `site-deploy` | Deploy site to a web server |

### The Key Rule

**Invoking a phase runs ALL phases before it (in that lifecycle).**

```bash
mvn package
# Actually runs: validate → ... → compile → test → package

mvn clean package
# Two lifecycles invoked:
#   clean lifecycle: clean
#   default lifecycle: validate → ... → package
```

---

## 5. Phases, Plugins, Goals — The Execution Model

This is the part most people get confused about. Here's how it really works:

### Terminology

| Term | What it is | Example |
|------|-----------|---------|
| **Lifecycle** | A sequence of phases | `default`, `clean`, `site` |
| **Phase** | A step in a lifecycle | `compile`, `test`, `package` |
| **Plugin** | A JAR that contains executable code | `maven-compiler-plugin` |
| **Goal** | A specific task in a plugin | `compiler:compile`, `compiler:testCompile` |
| **Binding** | A goal attached to a phase | `compiler:compile` is bound to `compile` phase |

### How They Connect

```
Lifecycle: default
  │
  ├── Phase: compile
  │     └── Bound Goal: maven-compiler-plugin:compile
  │
  ├── Phase: test
  │     └── Bound Goal: maven-surefire-plugin:test
  │
  ├── Phase: package
  │     └── Bound Goal: maven-jar-plugin:jar
  │
  └── Phase: install
        └── Bound Goal: maven-install-plugin:install
```

### You Can Run Goals Directly (Bypass Lifecycle)

```bash
# Run a specific goal without running the whole lifecycle
mvn compiler:compile          # just compile, skip validate etc.
mvn dependency:tree           # analyze dependencies (not a lifecycle phase)
mvn exec:java -Dexec.mainClass="com.example.Main"  # run a class
```

### You Can Bind Extra Goals to Phases

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
            <!-- This goal runs during the "initialize" phase by default -->
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>     <!-- Bind "report" goal to "verify" phase -->
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## 6. Dependencies — Scopes, Transitivity, Conflicts

### Dependency Scopes — Complete Table

| Scope | Compile Classpath | Test Classpath | Runtime Classpath | In Final JAR? | Transitive? |
|-------|:-:|:-:|:-:|:-:|:-:|
| `compile` (default) | ✅ | ✅ | ✅ | ✅ | ✅ |
| `provided` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `runtime` | ❌ | ✅ | ✅ | ✅ | ✅ |
| `test` | ❌ | ✅ | ❌ | ❌ | ❌ |
| `system` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `import` | — | — | — | — | — (BOM only) |

### When to Use Each

```
compile   → Spring, Guava, Jackson — your code NEEDS them at runtime
provided  → Servlet API, Lombok — the container/compiler provides them
runtime   → MySQL JDBC driver — code compiles against javax.sql, driver is loaded at runtime
test      → JUnit, Mockito, H2 — only for tests
import    → Spring Cloud BOM — imports version management from another POM
```

### Transitive Dependencies

```
Your project → depends on A (compile)
                    A → depends on B (compile)
                           B → depends on C (compile)

Maven downloads: A + B + C (all transitive)
```

**Scope Transitivity Rules:**

| Your dep scope → | compile | provided | runtime | test |
|:-:|:-:|:-:|:-:|:-:|
| **Transitive compile** | compile | — | runtime | — |
| **Transitive provided** | provided | — | provided | — |
| **Transitive runtime** | runtime | — | runtime | — |
| **Transitive test** | — | — | — | — |

Translation: If you depend on A (compile), and A depends on B (runtime), then B becomes runtime for you.

### Conflict Resolution

**Problem:** A needs `guava:30`, B needs `guava:29`. Which wins?

**Maven's strategy: Nearest Definition Wins**

```
Your project
├── A (depth 1) → guava:30 (depth 2)  ← WINS (nearer)
└── B (depth 1) → C (depth 2) → guava:29 (depth 3)
```

If two dependencies are at the **same depth**, the **first declared in POM** wins.

**How to fix conflicts:**

```xml
<!-- Option 1: dependencyManagement — force a version globally -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>32.1.3-jre</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Option 2: Exclude the transitive dependency -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>library-b</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Option 3: Declare it directly (makes it depth 1 = nearest) -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>32.1.3-jre</version>
</dependency>
```

### Debugging Dependencies

```bash
# Full dependency tree (see what's pulled in and from where)
mvn dependency:tree

# See why a specific dependency is included
mvn dependency:tree -Dincludes=com.google.guava

# Analyze unused/undeclared dependencies
mvn dependency:analyze

# Output:
# [WARNING] Used undeclared dependencies:    ← you use it but didn't declare it (fragile!)
# [WARNING] Unused declared dependencies:    ← you declared it but don't use it (bloat)
```

---

## 7. dependencyManagement vs dependencies

This is one of the **most confusing** things in Maven. Here's the definitive explanation:

### `<dependencies>` — "I NEED this library"

```xml
<dependencies>
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>32.1.3-jre</version>
    </dependency>
</dependencies>
```

**Effect:** Guava is downloaded, added to classpath, and included in the build. Period.

### `<dependencyManagement>` — "IF anyone needs this, use THIS version"

```xml
<!-- In parent POM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>32.1.3-jre</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Effect:** Nothing is downloaded. Nothing is on the classpath. It's a **version declaration**.

But now, in child modules:
```xml
<!-- In child POM — no version needed! -->
<dependencies>
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <!-- version 32.1.3-jre is inherited from parent's dependencyManagement -->
    </dependency>
</dependencies>
```

### Why This Matters

In a multi-module project with 20 modules, you don't want each module declaring its own Guava version. `dependencyManagement` in the parent ensures **one version across the entire project**.

### Same Pattern for Plugins

```xml
<!-- pluginManagement = "IF anyone uses this plugin, use THIS version and config" -->
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

---

## 8. Multi-Module Projects

### Structure

```
company-platform/                    ← Aggregator / Parent
├── pom.xml                          ← packaging = pom, lists modules
├── platform-common/                 ← Shared code (models, utils)
│   └── pom.xml
├── platform-service/                ← Business logic
│   └── pom.xml                      ← depends on platform-common
└── platform-web/                    ← REST API / Web layer
    └── pom.xml                      ← depends on platform-service
```

### Parent POM (Aggregator)

```xml
<project>
    <groupId>com.company</groupId>
    <artifactId>company-platform</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>          <!-- MUST be pom for parent -->

    <modules>
        <module>platform-common</module>
        <module>platform-service</module>
        <module>platform-web</module>
    </modules>

    <dependencyManagement>...</dependencyManagement>
    <properties>...</properties>
</project>
```

### Child POM

```xml
<project>
    <parent>
        <groupId>com.company</groupId>
        <artifactId>company-platform</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>platform-service</artifactId>

    <dependencies>
        <!-- Depend on sibling module -->
        <dependency>
            <groupId>com.company</groupId>
            <artifactId>platform-common</artifactId>
            <version>${project.version}</version>   <!-- same version as parent -->
        </dependency>
    </dependencies>
</project>
```

### Reactor — Build Order

When you run `mvn clean install` at root, Maven's **reactor** analyzes inter-module dependencies and builds in the correct order:

```
[INFO] Reactor Build Order:
[INFO]   company-platform          (pom)     ← parent first
[INFO]   platform-common           (jar)     ← no deps, builds first
[INFO]   platform-service          (jar)     ← depends on common
[INFO]   platform-web              (jar)     ← depends on service
```

### Reactor Flags

```bash
mvn clean install -pl platform-service           # build ONLY this module
mvn clean install -pl platform-service -am        # build this + ALL modules it depends on
mvn clean install -pl platform-common -amd         # build this + ALL modules that depend on it
mvn clean install -rf platform-service             # resume from this module (skip earlier ones)
```

### Aggregation vs Inheritance

| Concept | What it does | Mechanism |
|---------|-------------|-----------|
| **Aggregation** | "Build these modules together" | `<modules>` in parent |
| **Inheritance** | "Inherit my config/versions" | `<parent>` in child |

You can have aggregation WITHOUT inheritance (rare) and inheritance WITHOUT aggregation (common with Spring Boot parent).

---

## 9. Repositories

### Types of Repositories

| Type | Location | Purpose |
|------|----------|---------|
| **Local** | `~/.m2/repository` | Cache of all downloaded artifacts |
| **Central** | `https://repo.maven.apache.org/maven2` | Maven's default public repo |
| **Remote/Private** | Nexus, Artifactory, GitHub Packages | Company's internal repo |

### Resolution Order

```
1. Check ~/.m2/repository (local cache)
2. If not found → check remote repositories declared in POM
3. If not found → check Maven Central
4. If found → download to ~/.m2/repository (cache for next time)
5. If not found anywhere → BUILD FAILURE
```

### settings.xml (~/.m2/settings.xml)

Global Maven configuration (NOT per-project):

```xml
<settings>
    <!-- Credentials for private repos -->
    <servers>
        <server>
            <id>nexus-releases</id>      <!-- must match <repository><id> in POM -->
            <username>deploy-user</username>
            <password>secret</password>
        </server>
    </servers>

    <!-- Mirror: redirect ALL repo requests through your proxy -->
    <mirrors>
        <mirror>
            <id>company-proxy</id>
            <mirrorOf>*</mirrorOf>        <!-- intercept ALL repos -->
            <url>https://nexus.company.com/repository/maven-public/</url>
        </mirror>
    </mirrors>

    <!-- Active profiles -->
    <activeProfiles>
        <activeProfile>company-defaults</activeProfile>
    </activeProfiles>
</settings>
```

**Interview insight:** `settings.xml` is where you put **secrets** (passwords, tokens). Never put credentials in `pom.xml` (that's committed to Git). `settings.xml` stays on the developer's machine / CI server.

---

## 10. Profiles

Profiles let you **change build behavior** based on environment, OS, or explicit activation.

```xml
<profiles>
    <!-- Activated manually: mvn package -Pprod -->
    <profile>
        <id>prod</id>
        <properties>
            <spring.profiles.active>production</spring.profiles.active>
            <log.level>WARN</log.level>
        </properties>
        <dependencies>
            <dependency>
                <groupId>com.newrelic</groupId>
                <artifactId>newrelic-agent</artifactId>
            </dependency>
        </dependencies>
    </profile>

    <!-- Auto-activated when running on macOS -->
    <profile>
        <id>macos</id>
        <activation>
            <os>
                <family>mac</family>
            </os>
        </activation>
        <properties>
            <native.lib.path>/usr/local/lib</native.lib.path>
        </properties>
    </profile>

    <!-- Auto-activated when a file exists -->
    <profile>
        <id>docker</id>
        <activation>
            <file>
                <exists>Dockerfile</exists>
            </file>
        </activation>
    </profile>

    <!-- Auto-activated when JDK 17+ -->
    <profile>
        <id>jdk17</id>
        <activation>
            <jdk>[17,)</jdk>
        </activation>
    </profile>
</profiles>
```

### Activation Methods

```bash
mvn package -Pprod              # explicit
mvn package -Pprod,docker       # multiple profiles
mvn package -P!dev              # deactivate a profile
```

---

## 11. Common Plugins — What They Do

| Plugin | Phase | What It Does |
|--------|-------|-------------|
| `maven-compiler-plugin` | compile | Compiles Java source. Configure source/target version, compiler args. |
| `maven-surefire-plugin` | test | Runs **unit tests** (files named `*Test.java`, `Test*.java`). |
| `maven-failsafe-plugin` | integration-test | Runs **integration tests** (files named `*IT.java`). |
| `maven-jar-plugin` | package | Creates JAR file. Configure manifest (Main-Class). |
| `maven-war-plugin` | package | Creates WAR file for web apps. |
| `maven-shade-plugin` | package | Creates **fat/uber JAR** with all dependencies included. |
| `maven-assembly-plugin` | package | Creates custom distributions (ZIP, TAR). |
| `spring-boot-maven-plugin` | package | Creates executable Spring Boot JAR (embedded Tomcat). |
| `maven-resources-plugin` | process-resources | Copies resources, does placeholder filtering. |
| `maven-install-plugin` | install | Copies artifact to `~/.m2/repository`. |
| `maven-deploy-plugin` | deploy | Uploads artifact to remote repo (Nexus). |
| `maven-source-plugin` | verify | Generates `-sources.jar` (source code attachment). |
| `maven-javadoc-plugin` | verify | Generates `-javadoc.jar`. |
| `jacoco-maven-plugin` | verify | Code coverage report. |
| `maven-enforcer-plugin` | validate | Enforces rules (Java version, no SNAPSHOTs in release). |
| `exec-maven-plugin` | — | Runs a Java class or system command. |
| `maven-dependency-plugin` | — | Analyze, copy, unpack dependencies. |
| `versions-maven-plugin` | — | Check for newer dependency versions. |

### Fat JAR Example (Shade Plugin)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>com.example.Main</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 12. Properties

Properties are Maven's variable system. Use `${property.name}` to reference.

### Types

```xml
<properties>
    <!-- 1. User-defined properties -->
    <app.version>2.1.0</app.version>

    <!-- 2. Built-in project properties -->
    <!-- ${project.version}      → from <version> -->
    <!-- ${project.artifactId}   → from <artifactId> -->
    <!-- ${project.basedir}      → project root dir -->
    <!-- ${project.build.directory} → target/ -->

    <!-- 3. Settings properties -->
    <!-- ${settings.localRepository} → ~/.m2/repository -->

    <!-- 4. Environment variables -->
    <!-- ${env.JAVA_HOME}        → system JAVA_HOME -->
    <!-- ${env.PATH}             → system PATH -->

    <!-- 5. Java System properties -->
    <!-- ${java.version}         → JVM version -->
    <!-- ${os.name}              → Operating system -->
</properties>
```

### Using Properties

```xml
<!-- In dependencies -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>my-lib</artifactId>
    <version>${app.version}</version>
</dependency>

<!-- In resource filtering (src/main/resources/application.properties) -->
<!-- app.name=@project.name@ -->
<!-- app.version=@project.version@ -->
```

### Command-Line Properties

```bash
mvn package -Dapp.version=3.0.0    # override any property
mvn test -DskipTests                # common: skip tests
mvn test -Dmaven.test.skip=true     # skip test compilation + execution
```

---

## 13. SNAPSHOT vs Release

| | SNAPSHOT | Release |
|-|---------|---------|
| **Version** | `1.0.0-SNAPSHOT` | `1.0.0` |
| **Meaning** | "Work in progress, may change" | "Final, immutable" |
| **Maven behavior** | Re-downloads periodically (checks for updates) | Downloaded once, cached forever |
| **Deploy** | Can overwrite same version in repo | Cannot overwrite (immutable) |
| **Use in production?** | ❌ Never | ✅ Always |

### How SNAPSHOTs Work

When you deploy `1.0.0-SNAPSHOT`:
- Maven transforms it to: `1.0.0-20260309.143022-1` (timestamp-based)
- Next deploy: `1.0.0-20260309.153022-2`
- When another project depends on `1.0.0-SNAPSHOT`, Maven checks if a newer timestamp exists

### Update Policy

```xml
<repository>
    <id>snapshots</id>
    <url>https://nexus.company.com/snapshots</url>
    <snapshots>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>  <!-- always | daily | interval:60 | never -->
    </snapshots>
</repository>
```

```bash
# Force update check
mvn clean install -U   # -U = force update of SNAPSHOTs
```

---

## 14. Effective POM

The **effective POM** is what Maven actually sees after merging:
1. Super POM (Maven's built-in defaults)
2. Parent POM(s)
3. Your POM
4. Active profiles

```bash
mvn help:effective-pom              # print the merged POM
mvn help:effective-pom -Doutput=effective-pom.xml  # save to file
```

**When to use:** Debugging "where is this config coming from?" or "why is this plugin version different?"

---

## 15. Maven Wrapper (mvnw)

Like Gradle wrapper. Ensures everyone uses the **exact same Maven version**.

```bash
# Generate wrapper files
mvn wrapper:wrapper -Dmaven=3.9.6

# Now use ./mvnw instead of mvn
./mvnw clean install
```

Creates:
```
.mvn/
  wrapper/
    maven-wrapper.jar
    maven-wrapper.properties    ← specifies Maven version
mvnw                            ← Unix script
mvnw.cmd                        ← Windows script
```

**Best practice:** Commit `mvnw`, `mvnw.cmd`, and `.mvn/` to Git. Team doesn't need to install Maven.

---

## 16. BOM (Bill of Materials)

A BOM is a POM that **only** contains `<dependencyManagement>`. It's a centralized version catalog.

### Importing a BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>         <!-- MUST be pom -->
            <scope>import</scope>    <!-- MUST be import -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Effect:** All version declarations from that BOM are now available. You can use any Spring Cloud dependency without specifying a version.

### Creating Your Own BOM

```xml
<!-- company-bom/pom.xml -->
<project>
    <groupId>com.company</groupId>
    <artifactId>company-bom</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.google.guava</groupId>
                <artifactId>guava</artifactId>
                <version>32.1.3-jre</version>
            </dependency>
            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>2.17.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

**Why BOMs matter for seniors:** In a microservices architecture with 50 services, you create a company BOM to ensure all services use the same library versions. One update to the BOM = all services get the new version on next build.

---

## 17. Command Reference — Every Flag Explained

### Common Commands

```bash
# ── Build Commands ──────────────────────────────────────────
mvn clean                          # Delete target/
mvn compile                        # Compile main source
mvn test-compile                   # Compile main + test source
mvn test                           # Compile + run tests
mvn package                        # Compile + test + create JAR
mvn verify                         # package + integration tests
mvn install                        # verify + copy to ~/.m2
mvn deploy                         # install + upload to remote repo
mvn clean install                  # The classic: fresh build + install

# ── Skip Flags ──────────────────────────────────────────────
mvn install -DskipTests            # skip test execution (still compiles tests)
mvn install -Dmaven.test.skip=true # skip test compilation AND execution
mvn install -DskipITs              # skip integration tests only

# ── Module Flags ────────────────────────────────────────────
mvn install -pl module-a           # build only module-a (-pl = project list)
mvn install -pl module-a -am       # module-a + all modules it depends on (-am = also make)
mvn install -pl module-a -amd      # module-a + all modules depending on it (-amd = also make dependents)
mvn install -rf module-b           # resume reactor from module-b (-rf = resume from)
mvn install -pl module-a,module-b  # build multiple specific modules

# ── Output Flags ────────────────────────────────────────────
mvn install -q                     # quiet (minimal output)
mvn install -X                     # debug (VERY verbose, shows every resolution step)
mvn install -e                     # show error stack traces

# ── Dependency Commands (not lifecycle phases) ──────────────
mvn dependency:tree                # show full dependency tree
mvn dependency:tree -Dincludes=com.google.guava  # filter tree
mvn dependency:analyze             # find unused/undeclared deps
mvn dependency:resolve             # resolve and download all deps
mvn dependency:copy-dependencies   # copy all deps to target/dependency/

# ── Info Commands ───────────────────────────────────────────
mvn help:effective-pom             # show merged POM
mvn help:effective-settings        # show merged settings.xml
mvn help:describe -Dplugin=compiler  # show plugin goals and params
mvn help:active-profiles           # show which profiles are active

# ── Version Commands ────────────────────────────────────────
mvn versions:display-dependency-updates    # check for newer deps
mvn versions:display-plugin-updates        # check for newer plugins
mvn versions:set -DnewVersion=2.0.0       # update project version
mvn versions:use-latest-releases           # auto-update to latest releases

# ── Update/Refresh ──────────────────────────────────────────
mvn clean install -U               # force re-download of SNAPSHOTs
mvn clean install -nsu             # no snapshot updates (opposite of -U)
mvn install -o                     # offline mode (use only local cache)

# ── Profile Flags ───────────────────────────────────────────
mvn package -Pprod                 # activate profile "prod"
mvn package -Pprod,docker          # activate multiple profiles
mvn package -P!dev                 # deactivate profile "dev"
```

### Property Override Pattern

```bash
# Any <property> in POM can be overridden with -D
mvn package -Djava.version=21
mvn package -Dspring.profiles.active=dev
mvn package -DskipTests -Dcheckstyle.skip=true -Dpmd.skip=true
```

---

## 18. Maven vs Gradle — Honest Comparison

| Aspect | Maven | Gradle |
|--------|-------|--------|
| **Config format** | XML (`pom.xml`) | Groovy/Kotlin DSL (`build.gradle`) |
| **Philosophy** | Convention over configuration | Flexibility over convention |
| **Build speed** | Slower (no incremental, no daemon) | Faster (incremental builds, daemon, build cache) |
| **Learning curve** | Lower (XML is verbose but predictable) | Higher (DSL is powerful but harder to debug) |
| **Dependency management** | `dependencyManagement` + BOMs | `platform()` + version catalogs |
| **Multi-module** | Reactor, well-understood | Composite builds, more flexible |
| **IDE support** | Excellent everywhere | Excellent (IntelliJ native) |
| **Community/Ecosystem** | Largest (most Java libraries have Maven examples) | Growing fast (Android, Spring now default to Gradle) |
| **Reproducibility** | Deterministic by default | Requires careful configuration |
| **Custom tasks** | Write a plugin (heavy) | Write a task (lightweight) |
| **Enterprise adoption** | Still dominant (banks, insurance, government) | Growing (startups, Android, Spring) |

### When to Use What

- **Maven:** Enterprise projects, teams that prefer convention, existing Maven infrastructure
- **Gradle:** Android, large monorepos needing fast builds, teams comfortable with scripting

### Senior Answer for Interviews

> "Both are production-ready. Maven is simpler and more predictable — great for teams that want zero surprises. Gradle is faster and more flexible — great for complex builds. I'd choose Maven for a new backend service in a company already using Maven, and Gradle for a greenfield project where build speed matters."

---

## 19. Troubleshooting Cheat Sheet

| Problem | Solution |
|---------|----------|
| `Could not resolve dependencies` | Check internet. Run `mvn clean install -U`. Check `settings.xml` mirror/server config. |
| `Non-resolvable parent POM` | Parent not in local repo. Run `mvn install` on parent first, or check `<relativePath>`. |
| `Package X does not exist` | Dependency missing or wrong scope. Run `mvn dependency:tree`. |
| `Compilation failure: source/target version` | Set `maven.compiler.source` and `maven.compiler.target` in properties. |
| `Tests fail but code is correct` | Run `mvn test -X` for debug output. Check surefire reports in `target/surefire-reports/`. |
| `OutOfMemoryError during build` | Set `export MAVEN_OPTS="-Xmx1024m"` |
| `Plugin not found` | Check `<pluginRepositories>` in POM or `settings.xml`. |
| `Artifact is SNAPSHOT but repo doesn't allow` | Release repos don't accept SNAPSHOTs. Deploy to snapshot repo or change version to release. |
| `Build succeeds locally but fails in CI` | Local `~/.m2` has cached artifacts CI doesn't. Run `mvn clean install -U` in CI. |
| `Wrong dependency version` | Run `mvn dependency:tree -Dincludes=groupId:artifactId`. Use `<dependencyManagement>` to force version. |
| `Cannot re-deploy release artifact` | Release artifacts are immutable. Bump version or delete from repo (not recommended). |

### Nuclear Options

```bash
# Delete entire local repo cache (re-downloads everything)
rm -rf ~/.m2/repository

# Delete only a problematic artifact
rm -rf ~/.m2/repository/com/google/guava

# Force full re-resolution
mvn clean install -U -Purge-local-repo
```

---

## 20. Real-World Project Structure Example

A realistic Spring Boot microservice setup:

```
company-platform/
├── pom.xml                              ← Parent POM (packaging=pom)
│     ├── <modules>: all children
│     ├── <dependencyManagement>: company BOM + Spring Cloud BOM
│     ├── <pluginManagement>: compiler, surefire, jacoco, docker
│     └── <properties>: java.version, spring-boot.version
│
├── platform-bom/                        ← Company BOM
│   └── pom.xml                          ← Only dependencyManagement (shared versions)
│
├── platform-common/                     ← Shared models, utils, exceptions
│   ├── pom.xml                          ← depends on nothing internal
│   └── src/main/java/
│       └── com/company/common/
│           ├── model/                   ← Shared DTOs
│           ├── exception/               ← Custom exceptions
│           └── util/                    ← Common utilities
│
├── user-service/                        ← Microservice
│   ├── pom.xml                          ← depends on platform-common
│   └── src/main/java/
│       └── com/company/user/
│           ├── UserApplication.java
│           ├── controller/
│           ├── service/
│           └── repository/
│
├── order-service/                       ← Another microservice
│   ├── pom.xml                          ← depends on platform-common
│   └── src/main/java/
│
└── gateway-service/                     ← API Gateway
    ├── pom.xml
    └── src/main/java/
```

### Build Commands for This Setup

```bash
# Build everything
mvn clean install

# Build only user-service + its dependency (platform-common)
mvn clean install -pl user-service -am

# Build common, then everything that depends on it
mvn clean install -pl platform-common -amd

# Skip tests in CI for faster feedback
mvn clean package -DskipTests

# Release build
mvn clean deploy -Pprod -DskipTests
```

---

## Final Interview Wisdom

> **Junior:** "I run `mvn clean install` and it works."
>
> **Mid-level:** "I understand the lifecycle, scopes, and can fix dependency conflicts."
>
> **Senior:** "I design the multi-module structure, manage BOMs for version consistency, configure profiles for environments, optimize build times with reactor flags, and set up CI/CD pipelines with Maven."

Know the difference. Be the senior. 🎯

