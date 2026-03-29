# 🐳 Docker Deep Dive — Senior Engineer's Complete Reference

> Everything a senior engineer should know about Docker.  
> From "what is a container" to "optimizing production images and debugging in Kubernetes."

---

## Table of Contents

1. [What is Docker — Really?](#1-what-is-docker--really)
2. [Docker Architecture — Under the Hood](#2-docker-architecture--under-the-hood)
3. [Images — Layers, Caching, and Building](#3-images--layers-caching-and-building)
4. [Dockerfile — Every Instruction Explained](#4-dockerfile--every-instruction-explained)
5. [Containers — Lifecycle and Management](#5-containers--lifecycle-and-management)
6. [Networking — Bridge, Host, Overlay, None](#6-networking--bridge-host-overlay-none)
7. [Volumes & Storage — Persistence Done Right](#7-volumes--storage--persistence-done-right)
8. [Docker Compose — Multi-Container Apps](#8-docker-compose--multi-container-apps)
9. [Multi-Stage Builds — Production-Ready Images](#9-multi-stage-builds--production-ready-images)
10. [Image Security & Best Practices](#10-image-security--best-practices)
11. [Docker in CI/CD Pipelines](#11-docker-in-cicd-pipelines)
12. [Docker vs Podman vs Containerd](#12-docker-vs-podman-vs-containerd)
13. [Docker Swarm (Overview)](#13-docker-swarm-overview)
14. [Debugging Containers](#14-debugging-containers)
15. [Resource Limits & cgroups](#15-resource-limits--cgroups)
16. [Docker Registry — Private & Public](#16-docker-registry--private--public)
17. [.dockerignore — What to Exclude](#17-dockerignore--what-to-exclude)
18. [Health Checks](#18-health-checks)
19. [Real-World Production Dockerfile Examples](#19-real-world-production-dockerfile-examples)
20. [Interview Q&A — 30 Questions](#20-interview-qa--30-questions)

---

## 1. What is Docker — Really?

Docker is a platform that uses **OS-level virtualization** to deliver software in **containers**.

### The Problem Docker Solves

```
Developer: "It works on my machine!"
Ops:       "Well, your machine isn't in production."

Docker: "Ship the machine along with the code."
```

A container bundles:
- Your application code
- Runtime (JDK, Node.js, Python)
- System tools and libraries
- Configuration files

**Result:** The same container runs identically on a developer's laptop, in CI/CD, and in production.

### Container vs VM — Deep Comparison

```
┌─────────────────────────────────┐     ┌─────────────────────────────────┐
│         Virtual Machine          │     │          Container              │
│ ┌─────┐ ┌─────┐ ┌─────┐        │     │ ┌─────┐ ┌─────┐ ┌─────┐       │
│ │App A│ │App B│ │App C│        │     │ │App A│ │App B│ │App C│       │
│ ├─────┤ ├─────┤ ├─────┤        │     │ ├─────┤ ├─────┤ ├─────┤       │
│ │Bins │ │Bins │ │Bins │        │     │ │Bins │ │Bins │ │Bins │       │
│ ├─────┤ ├─────┤ ├─────┤        │     │ └──┬──┘ └──┬──┘ └──┬──┘       │
│ │Guest│ │Guest│ │Guest│        │     │    └────────┼───────┘          │
│ │ OS  │ │ OS  │ │ OS  │        │     │    ┌────────▼──────────┐       │
│ └─────┘ └─────┘ └─────┘        │     │    │  Container Engine  │       │
│ ┌───────────────────────┐       │     │    │   (Docker/containerd)│     │
│ │      Hypervisor        │       │     │    └────────┬──────────┘       │
│ └───────────────────────┘       │     │    ┌────────▼──────────┐       │
│ ┌───────────────────────┐       │     │    │     Host OS        │       │
│ │      Host OS           │       │     │    └───────────────────┘       │
│ └───────────────────────┘       │     │                                │
└─────────────────────────────────┘     └─────────────────────────────────┘
```

Each VM runs a **full guest OS** (with its own kernel). Containers share the **host OS kernel**.

---

## 2. Docker Architecture — Under the Hood

### Client-Server Architecture

```
┌─────────────┐  REST API  ┌─────────────────────────────────┐
│ Docker CLI  │──────────▶│        Docker Daemon (dockerd)   │
│ (client)    │            │                                  │
└─────────────┘            │  ┌──────────┐  ┌─────────────┐  │
                           │  │containerd│  │ Image Store  │  │
                           │  │(runtime) │  │  (layers)    │  │
                           │  └────┬─────┘  └─────────────┘  │
                           │       │                          │
                           │  ┌────▼─────┐                    │
                           │  │  runc    │ ← OCI runtime      │
                           │  │(creates  │                    │
                           │  │containers)│                   │
                           │  └──────────┘                    │
                           └─────────────────────────────────┘
```

### Key Components

| Component | Role |
|-----------|------|
| **Docker CLI** | Client that sends commands to the daemon |
| **dockerd** | Daemon that manages images, containers, networks, volumes |
| **containerd** | Container runtime — manages container lifecycle |
| **runc** | Low-level runtime — actually creates containers using Linux kernel features |
| **Docker Registry** | Remote storage for images (Docker Hub, ECR, GCR, ACR) |

### What Happens When You Type `docker run`?

```
docker run -d -p 8080:8080 myapp:1.0

1. CLI sends request to dockerd via REST API
2. dockerd checks if image "myapp:1.0" exists locally
3. If not → pulls from registry (layer by layer)
4. dockerd tells containerd to create a container
5. containerd calls runc to set up:
   - Namespaces (PID, NET, MNT, UTS, IPC, USER)
   - cgroups (CPU, memory limits)
   - Root filesystem (union mount of image layers + writable layer)
6. runc starts the container process
7. Port mapping (iptables rules: host:8080 → container:8080)
8. Container is running!
```

### Linux Kernel Features Docker Uses

| Feature | What It Does |
|---------|-------------|
| **Namespaces** | Isolate PID, network, mounts, users, hostname |
| **cgroups** | Limit CPU, memory, I/O resources |
| **Union FS** | Layer filesystem (OverlayFS) for images |
| **chroot** | Change apparent root directory |
| **seccomp** | Filter system calls for security |
| **AppArmor/SELinux** | Mandatory access control |

---

## 3. Images — Layers, Caching, and Building

### What Is a Docker Image?

An image is an **ordered collection of filesystem layers** plus metadata (how to run it).

### Layers Explained

```dockerfile
FROM ubuntu:22.04       # Layer 1: Base OS (~77MB)
RUN apt-get update      # Layer 2: Package index (~40MB)
RUN apt-get install -y curl  # Layer 3: curl binary (~5MB)
COPY app.jar /app/      # Layer 4: Your application (~30MB)
CMD ["java", "-jar", "/app/app.jar"]  # No new layer (metadata only)
```

```
Image = Stack of read-only layers

┌──────────────────────┐  ← Layer 4: COPY app.jar (30MB)
├──────────────────────┤  ← Layer 3: RUN install curl (5MB)
├──────────────────────┤  ← Layer 2: RUN apt-get update (40MB)
├──────────────────────┤  ← Layer 1: FROM ubuntu:22.04 (77MB)
└──────────────────────┘

Container = Image layers + thin writable layer on top

┌──────────────────────┐  ← Writable layer (container)
├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
│    Image layers       │  ← Read-only
│    (shared)           │
└──────────────────────┘
```

### Layer Caching — How Docker Builds Fast

Docker caches each layer. When you rebuild:
- If a layer's instruction **hasn't changed** → use cache
- If a layer's instruction **changed** → rebuild this layer **AND all layers after it**

**Cache busting order matters!**

```dockerfile
# ❌ BAD: Source code changes bust ALL caches
COPY . /app
RUN mvn clean package

# ✅ GOOD: Dependencies cached separately from source code
COPY pom.xml /app/
RUN mvn dependency:go-offline    # Cached until pom.xml changes
COPY src /app/src                # Only this and below re-run on code change
RUN mvn clean package -DskipTests
```

### Image Tagging Best Practices

```bash
# Tag format: registry/repository:tag
docker build -t mycompany/myapp:1.2.3 .
docker build -t mycompany/myapp:latest .

# Best practices:
# ✅ Use semantic versioning: myapp:1.2.3
# ✅ Use git SHA for traceability: myapp:abc123f
# ❌ Don't rely on :latest in production (mutable tag!)
# ✅ Immutable tags in production: myapp:v1.2.3-20240101
```

---

## 4. Dockerfile — Every Instruction Explained

### Complete Instruction Reference

| Instruction | Build/Run | What It Does |
|-------------|-----------|-------------|
| `FROM` | Build | Sets the base image. MUST be first. |
| `RUN` | Build | Executes command, creates new layer. |
| `CMD` | Run | Default command when container starts. Overridable. |
| `ENTRYPOINT` | Run | Main command. Not easily overridden. |
| `COPY` | Build | Copy files from build context to image. |
| `ADD` | Build | Like COPY + extract tar + fetch URL. |
| `WORKDIR` | Build | Set working directory for subsequent instructions. |
| `ENV` | Both | Set environment variable. |
| `ARG` | Build | Build-time variable (not in final image). |
| `EXPOSE` | Metadata | Document which port the app listens on. |
| `VOLUME` | Run | Create mount point for external volumes. |
| `USER` | Run | Set the user to run commands as. |
| `LABEL` | Metadata | Add metadata key-value pairs. |
| `HEALTHCHECK` | Run | Define health check command. |
| `SHELL` | Build | Override default shell. |
| `STOPSIGNAL` | Run | Signal sent to stop the container. |
| `ONBUILD` | Build | Trigger instruction when image is used as base. |

### ENTRYPOINT + CMD — The Combo

```dockerfile
# Shell form (runs in /bin/sh -c)
CMD java -jar app.jar

# Exec form (preferred — PID 1 is your process)
CMD ["java", "-jar", "app.jar"]

# Combined: ENTRYPOINT (fixed) + CMD (default args)
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=prod"]

# Override CMD at runtime:
# docker run myapp --spring.profiles.active=dev
# → java -jar app.jar --spring.profiles.active=dev
```

**Interview Q:** *"Why use exec form over shell form?"*  
**A:** Exec form runs the process directly as **PID 1**, so it receives signals (SIGTERM) properly. Shell form wraps in `/bin/sh -c`, making `/bin/sh` PID 1 — your app won't receive signals for graceful shutdown.

### ARG vs ENV

```dockerfile
# ARG: Available only during build
ARG JAR_VERSION=1.0.0
COPY target/app-${JAR_VERSION}.jar app.jar

# ENV: Available during build AND at runtime
ENV JAVA_OPTS="-Xmx512m"
CMD java $JAVA_OPTS -jar app.jar

# Override ARG at build time:
# docker build --build-arg JAR_VERSION=2.0.0 .

# Override ENV at runtime:
# docker run -e JAVA_OPTS="-Xmx1g" myapp
```

---

## 5. Containers — Lifecycle and Management

### Container Lifecycle

```
Created → Running → Paused → Running → Stopped → Removed
   │         │                            │
   │    docker pause/unpause              │
   │                                      │
   └──── docker start ────────────────────┘
```

### Run Options Explained

```bash
docker run \
  -d                          # Detached mode (background)
  --name api-server           # Give it a name
  -p 8080:8080                # Map host:container port
  -p 9090:9090                # Multiple port mappings
  -e DB_HOST=postgres         # Environment variable
  -e DB_PORT=5432             # Another env var
  --env-file .env             # Load env vars from file
  -v /host/data:/app/data     # Bind mount
  -v myvolume:/app/logs       # Named volume
  --network my-net            # Connect to network
  --memory 512m               # Memory limit
  --cpus 1.5                  # CPU limit
  --restart unless-stopped    # Restart policy
  --health-cmd "curl -f http://localhost:8080/health"
  --health-interval 30s
  myapp:1.0
```

### Restart Policies

| Policy | Behavior |
|--------|----------|
| `no` | Never restart (default) |
| `on-failure` | Restart only on non-zero exit code |
| `on-failure:5` | Restart on failure, max 5 attempts |
| `always` | Always restart (even on manual stop after daemon restarts) |
| `unless-stopped` | Always restart except when manually stopped |

---

## 6. Networking — Bridge, Host, Overlay, None

### Default Bridge Network

When you run a container without specifying a network, it joins the default `bridge` network.

```bash
# Containers on default bridge can communicate via IP (not hostname)
docker run --name web nginx
docker run --name api myapp
# api CANNOT reach "web" by name on default bridge

# Create a custom bridge network (recommended)
docker network create app-net
docker run --name web --network app-net nginx
docker run --name api --network app-net myapp
# api CAN reach "web" by name (Docker DNS) ✅
```

### Network Types Deep Dive

#### Bridge Network
```
Host Machine
┌─────────────────────────────────────┐
│  docker0 bridge (172.17.0.1)       │
│  ┌──────────┐  ┌──────────┐        │
│  │Container A│  │Container B│       │
│  │172.17.0.2 │  │172.17.0.3│       │
│  └──────────┘  └──────────┘        │
└─────────────────────────────────────┘
```
- Default for standalone containers
- Containers get private IPs
- NAT for external access
- Custom bridge enables DNS resolution

#### Host Network
```bash
docker run --network host nginx
# nginx listens directly on host's port 80
# No port mapping needed
# No network isolation
```
- Best performance (no NAT overhead)
- Container shares host's network stack
- Use when performance > isolation

#### Overlay Network
```
Host A                    Host B
┌─────────┐              ┌─────────┐
│Container│◄── VXLAN ──►│Container│
│   1     │   tunnel     │   2     │
└─────────┘              └─────────┘
```
- Multi-host networking
- Used in Docker Swarm and Kubernetes
- VXLAN encapsulation

#### None Network
```bash
docker run --network none myapp
# No network access at all
```

### Port Mapping

```bash
-p 8080:80          # host:8080 → container:80
-p 127.0.0.1:8080:80  # Only localhost can access
-p 8080:80/tcp      # TCP only (default)
-p 8080:80/udp      # UDP
-P                   # Publish all exposed ports to random host ports
```

---

## 7. Volumes & Storage — Persistence Done Right

### The Problem

Container filesystem is **ephemeral**. When a container is removed, all data inside is lost.

### Three Types of Mounts

| Type | Managed by | Persists | Host Access | Use Case |
|------|-----------|----------|-------------|----------|
| **Volume** | Docker | ✅ | Via Docker | Databases, app data |
| **Bind Mount** | You | ✅ | Direct | Dev: live code reload |
| **tmpfs** | Kernel | ❌ | No | Secrets, temp data |

### Volumes (Recommended for Production)

```bash
# Create a named volume
docker volume create pgdata

# Use it
docker run -v pgdata:/var/lib/postgresql/data postgres:15

# Inspect volume
docker volume inspect pgdata

# List all volumes
docker volume ls

# Remove unused volumes
docker volume prune
```

### Bind Mounts (Great for Development)

```bash
# Mount current directory into container
docker run -v $(pwd)/src:/app/src myapp

# Read-only bind mount
docker run -v $(pwd)/config:/app/config:ro myapp
```

### Volume in Dockerfile

```dockerfile
VOLUME /var/lib/mysql
# Creates anonymous volume at /var/lib/mysql
# Data survives container recreation
# But anonymous volumes are hard to manage — prefer named volumes at runtime
```

---

## 8. Docker Compose — Multi-Container Apps

### Why Compose?

Real apps need multiple services: app server, database, cache, message queue. Compose defines all of them in one YAML file.

### Complete docker-compose.yml Example

```yaml
version: '3.8'

services:
  api:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        JAR_VERSION: "1.0.0"
    container_name: api-server
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/mydb
      SPRING_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - backend
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  db:
    image: postgres:15-alpine
    container_name: postgres-db
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    environment:
      POSTGRES_DB: mydb
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"
    networks:
      - backend
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d mydb"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: redis-cache
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data
    command: redis-server --appendonly yes
    networks:
      - backend

  rabbitmq:
    image: rabbitmq:3-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"
    networks:
      - backend

networks:
  backend:
    driver: bridge

volumes:
  pgdata:
  redisdata:
```

### Compose Commands

```bash
docker compose up -d              # Start all services (detached)
docker compose up -d --build      # Rebuild images then start
docker compose down               # Stop and remove containers, networks
docker compose down -v            # Also remove volumes
docker compose logs -f api        # Follow logs for one service
docker compose ps                 # Show running services
docker compose exec api bash      # Shell into a running service
docker compose restart api        # Restart one service
docker compose pull               # Pull latest images
docker compose config             # Validate and view resolved compose file
```

### depends_on — Startup Order

```yaml
depends_on:
  db:
    condition: service_healthy     # Wait until db healthcheck passes
  redis:
    condition: service_started     # Just wait for container to start
```

---

## 9. Multi-Stage Builds — Production-Ready Images

### Why Multi-Stage?

Without multi-stage:
```
JDK (300MB) + Maven (200MB) + dependencies (200MB) + source + JAR = ~800MB image
```

With multi-stage:
```
JRE (150MB) + JAR (30MB) = ~180MB image
```

### Java Spring Boot Example

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Extract layers (Spring Boot layered JAR)
FROM eclipse-temurin:17-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Final image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy layers in order of change frequency
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### Node.js Example

```dockerfile
# Stage 1: Install dependencies
FROM node:18-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

# Stage 2: Build
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 3: Production
FROM node:18-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=deps /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
USER appuser
EXPOSE 3000
CMD ["node", "dist/main.js"]
```

---

## 10. Image Security & Best Practices

### Security Checklist

```dockerfile
# 1. Use official, minimal base images
FROM eclipse-temurin:17-jre-alpine  # ✅ Not ubuntu:latest

# 2. Don't run as root
RUN addgroup -S app && adduser -S app -G app
USER app

# 3. Use COPY, not ADD (unless extracting tar)
COPY app.jar /app/                  # ✅
# ADD https://example.com/f /app/  # ❌ (use curl in RUN)

# 4. Scan for vulnerabilities
# docker scout cves myapp:1.0
# trivy image myapp:1.0

# 5. Pin versions
FROM node:18.17.0-alpine3.18       # ✅ Pinned
# FROM node:latest                 # ❌ Mutable

# 6. Don't store secrets in images
# ❌ ENV DB_PASSWORD=secret
# ✅ Pass at runtime: docker run -e DB_PASSWORD=secret
# ✅ Use Docker secrets or Vault

# 7. Use .dockerignore
```

### .dockerignore Example

```
.git
.gitignore
target/
*.md
docker-compose*.yml
.env
.idea
*.iml
node_modules
.DS_Store
```

---

## 11. Docker in CI/CD Pipelines

### GitHub Actions Example

```yaml
name: Build and Push Docker Image

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and Push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            mycompany/myapp:${{ github.sha }}
            mycompany/myapp:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

---

## 12. Docker vs Podman vs Containerd

| Feature | Docker | Podman | Containerd |
|---------|--------|--------|------------|
| Daemon | Yes (dockerd) | Daemonless | Yes (containerd) |
| Root required | Yes (default) | No (rootless) | Yes |
| CLI compatible | — | Docker-compatible | crictl (different) |
| Compose | Docker Compose | podman-compose | nerdctl compose |
| Kubernetes | Via containerd | Podman play kube | Native K8s runtime |
| OCI compliant | Yes | Yes | Yes |

**Interview Q:** *"Why is Podman gaining traction?"*  
**A:** No daemon = no single point of failure. Rootless by default = better security. CLI-compatible with Docker, so easy migration.

---

## 13. Docker Swarm (Overview)

```bash
# Initialize swarm
docker swarm init

# Deploy a stack
docker stack deploy -c docker-compose.yml mystack

# Scale a service
docker service scale mystack_api=5

# List services
docker service ls
```

> **Note:** Most companies use Kubernetes instead of Swarm. Swarm is simpler but less feature-rich.

---

## 14. Debugging Containers

### Essential Debug Commands

```bash
# See what's happening
docker logs <container>              # View logs
docker logs -f --tail 100 <container>  # Follow last 100 lines
docker logs --since 2h <container>   # Last 2 hours

# Get inside
docker exec -it <container> bash     # Shell into container
docker exec -it <container> sh       # For alpine (no bash)

# Inspect everything
docker inspect <container>           # Full JSON metadata
docker inspect --format='{{.State.Health}}' <container>  # Health status
docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <container>

# Resource usage
docker stats                         # Live CPU, memory, I/O
docker top <container>               # Processes inside container

# Filesystem
docker diff <container>              # Changes to filesystem
docker cp <container>:/app/log.txt . # Copy file from container

# Debug a stopped/crashed container
docker logs <dead-container>         # Logs persist after stop
docker inspect <dead-container>      # Check exit code
docker commit <container> debug-img  # Save state as image
docker run -it debug-img sh          # Explore the state
```

### Debug a Container That Won't Start

```bash
# Override the entrypoint
docker run -it --entrypoint sh myapp:1.0
# Now you're inside the container without the app starting
# Check files, permissions, environment variables, etc.
```

---

## 15. Resource Limits & cgroups

```bash
# Memory limit
docker run --memory 512m myapp        # Hard limit: 512MB
docker run --memory 512m --memory-reservation 256m myapp  # Soft + hard

# CPU limit
docker run --cpus 1.5 myapp           # 1.5 CPU cores
docker run --cpu-shares 512 myapp     # Relative weight (default 1024)
docker run --cpuset-cpus "0,1" myapp  # Pin to specific cores

# I/O limits
docker run --device-read-bps /dev/sda:10mb myapp   # Read limit
docker run --device-write-bps /dev/sda:10mb myapp   # Write limit

# PID limit
docker run --pids-limit 100 myapp     # Max 100 processes

# In Compose
services:
  api:
    deploy:
      resources:
        limits:
          cpus: '1.5'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M
```

---

## 16. Docker Registry — Private & Public

### Docker Hub (Public)

```bash
docker login
docker tag myapp:1.0 username/myapp:1.0
docker push username/myapp:1.0
docker pull username/myapp:1.0
```

### AWS ECR (Private)

```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

docker tag myapp:1.0 123456789.dkr.ecr.us-east-1.amazonaws.com/myapp:1.0
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/myapp:1.0
```

### Self-Hosted Registry

```bash
docker run -d -p 5000:5000 --name registry registry:2
docker tag myapp:1.0 localhost:5000/myapp:1.0
docker push localhost:5000/myapp:1.0
```

---

## 17. .dockerignore — What to Exclude

```
# Version control
.git
.gitignore

# Build outputs
target/
build/
dist/
node_modules/

# IDE files
.idea/
.vscode/
*.iml

# Docker files (avoid recursive builds)
Dockerfile
docker-compose*.yml

# Sensitive files
.env
*.pem
*.key

# OS files
.DS_Store
Thumbs.db

# Documentation
*.md
LICENSE
docs/
```

---

## 18. Health Checks

### In Dockerfile

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

### In Compose

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

### Health Status

```bash
docker inspect --format='{{json .State.Health.Status}}' mycontainer
# "healthy" | "unhealthy" | "starting"
```

---

## 19. Real-World Production Dockerfile Examples

### Spring Boot (Production-Ready)

```dockerfile
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --chown=spring:spring target/app.jar app.jar

USER spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

**Key JVM flags for containers:**
- `-XX:+UseContainerSupport` — JVM respects container memory limits (default since Java 10)
- `-XX:MaxRAMPercentage=75.0` — Use 75% of container memory for heap
- `-Djava.security.egd=file:/dev/./urandom` — Faster startup (non-blocking random)

---

## 20. Interview Q&A — 30 Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | What is a container? | Process running in isolated namespaces with resource limits via cgroups |
| 2 | Container vs VM? | Container shares kernel (lightweight). VM has own OS (heavy). |
| 3 | What is a Docker image? | Stack of read-only filesystem layers + metadata |
| 4 | What are layers? | Each Dockerfile instruction creates a layer. Layers are cached and shared. |
| 5 | What is Union FS? | Filesystem that overlays multiple layers into a single view (OverlayFS) |
| 6 | What happens in `docker build`? | Reads Dockerfile, executes instructions, each creates a layer, produces image |
| 7 | What happens in `docker run`? | Creates container from image, sets up namespaces/cgroups, starts process |
| 8 | ENTRYPOINT vs CMD? | ENTRYPOINT = fixed command. CMD = default args (overridable). |
| 9 | COPY vs ADD? | COPY just copies. ADD can extract tar and fetch URLs. Prefer COPY. |
| 10 | ARG vs ENV? | ARG = build-time only. ENV = build + runtime. |
| 11 | How does caching work? | Layers cached until instruction changes. Change invalidates all below. |
| 12 | What is multi-stage build? | Multiple FROM stages. Copy artifacts between stages. Slim final image. |
| 13 | How to reduce image size? | Alpine base, multi-stage, .dockerignore, combine RUN, remove cache |
| 14 | What is a dangling image? | Image with no tag. Created during rebuild. `docker image prune` cleans. |
| 15 | Named volume vs bind mount? | Volume = Docker-managed. Bind mount = host path mapping. |
| 16 | How do containers communicate? | Docker networks. Same network → reachable by container name (DNS). |
| 17 | What are namespaces? | Linux kernel isolation: PID, NET, MNT, UTS, IPC, USER |
| 18 | What are cgroups? | Resource limits: CPU, memory, I/O, PIDs |
| 19 | What is Docker Compose? | Tool for defining multi-container apps in YAML |
| 20 | What is docker0? | Default bridge network interface |
| 21 | What is containerd? | Container runtime that manages container lifecycle |
| 22 | What is runc? | Low-level OCI runtime that creates containers |
| 23 | Docker vs Podman? | Podman is daemonless, rootless, CLI-compatible with Docker |
| 24 | What is OCI? | Open Container Initiative — standards for container format and runtime |
| 25 | How to persist data? | Volumes (production) or bind mounts (dev) |
| 26 | How to handle secrets? | Docker secrets, env vars at runtime, or Vault. Never bake into image. |
| 27 | What is Docker Scout/Trivy? | Vulnerability scanning for container images |
| 28 | What is .dockerignore? | Excludes files from build context (like .gitignore for Docker) |
| 29 | What is health check? | Periodic command to verify container health. Docker restarts unhealthy. |
| 30 | Why not run as root? | Security — compromised container = root on host if no user namespace. |

---

> **Pro Tip for Interviews:** Don't just know the commands — understand *why*. "Multi-stage builds reduce image size because build tools aren't needed at runtime" shows depth.

