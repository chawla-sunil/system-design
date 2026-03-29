# 🐳 Docker in 5 Minutes — Interview Cheat Sheet

> Quick-fire Docker concepts for interview prep. Print this before walking in.

---

## What is Docker?

**One line:** Docker is a **platform to build, ship, and run applications in isolated containers** — lightweight, portable, and consistent across environments.

**Think of it as:** A shipping container for your code. It packages your app + dependencies + runtime so it runs the same **everywhere** (dev, staging, prod).

---

## Containers vs VMs — The Interview Classic

| Feature | Container | Virtual Machine |
|---------|-----------|----------------|
| **Isolation** | Process-level (shares host OS kernel) | Full OS-level (own kernel) |
| **Startup** | Seconds | Minutes |
| **Size** | MBs (10-200 MB) | GBs (1-10 GB) |
| **Performance** | Near-native | ~5-10% overhead |
| **Density** | 100s per host | 10s per host |
| **Use Case** | Microservices, CI/CD | Full OS isolation, legacy apps |

**Interview Q:** *"Why containers over VMs?"*  
**A:** Containers share the host kernel, making them lightweight. Faster startup, less resource usage, better for microservices. VMs run a full OS, which adds overhead.

---

## Docker Architecture (3 Components)

```
┌─────────────────────────────────────┐
│           Docker Client             │  ← You type commands here
│         (docker CLI / API)          │
└──────────────┬──────────────────────┘
               │ REST API
┌──────────────▼──────────────────────┐
│          Docker Daemon              │  ← Does the real work
│           (dockerd)                 │
│  ┌──────────┐ ┌──────────┐         │
│  │ Images   │ │Containers│         │
│  └──────────┘ └──────────┘         │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Docker Registry             │  ← Docker Hub, ECR, GCR
│       (stores images)               │
└─────────────────────────────────────┘
```

---

## Core Concepts — Know These Cold

| Concept | What It Is |
|---------|-----------|
| **Image** | Read-only template with app + dependencies. Built from a `Dockerfile`. |
| **Container** | Running instance of an image. Has its own filesystem, network, PID space. |
| **Dockerfile** | Recipe to build an image. Series of instructions (`FROM`, `RUN`, `COPY`). |
| **Volume** | Persistent storage that survives container restarts/removal. |
| **Network** | Enables container-to-container communication. |
| **Registry** | Storage for images (Docker Hub, AWS ECR, GCR). |
| **Layer** | Each Dockerfile instruction creates a layer. Layers are cached and reused. |

---

## Dockerfile — The 10 Instructions You Must Know

```dockerfile
FROM openjdk:17-slim          # Base image — ALWAYS first
LABEL maintainer="sunil"      # Metadata
WORKDIR /app                  # Set working directory
COPY target/app.jar app.jar   # Copy files from host → image
ADD https://url/file.tar /app # Like COPY, but can extract archives & fetch URLs
RUN apt-get update && \
    apt-get install -y curl   # Execute commands during BUILD
ENV SPRING_PROFILES_ACTIVE=prod  # Set environment variables
EXPOSE 8080                   # Document which port the app listens on (doesn't publish)
VOLUME /data                  # Mount point for persistent data
ENTRYPOINT ["java", "-jar", "app.jar"]  # Main command (can't be overridden easily)
CMD ["--server.port=8080"]    # Default arguments (can be overridden at runtime)
```

### COPY vs ADD
| | COPY | ADD |
|--|------|-----|
| Copy local files | ✅ | ✅ |
| Extract .tar archives | ❌ | ✅ |
| Fetch from URL | ❌ | ✅ |
| **Best practice** | ✅ Use this | Only when you need extraction |

### ENTRYPOINT vs CMD
| | ENTRYPOINT | CMD |
|--|-----------|-----|
| Purpose | Main command | Default arguments |
| Override at runtime | Hard (`--entrypoint`) | Easy (`docker run img newcmd`) |
| Combined | `ENTRYPOINT ["java"]` + `CMD ["-jar", "app.jar"]` → `java -jar app.jar` |

---

## 15 Commands You Must Know

```bash
# Images
docker build -t myapp:1.0 .           # Build image from Dockerfile
docker images                          # List all images
docker pull nginx:latest               # Download image from registry
docker push myrepo/myapp:1.0           # Push image to registry
docker rmi myapp:1.0                   # Remove an image

# Containers
docker run -d -p 8080:8080 myapp:1.0  # Run container (detached, port mapped)
docker ps                              # List running containers
docker ps -a                           # List ALL containers (including stopped)
docker stop <container_id>             # Graceful stop (SIGTERM → SIGKILL)
docker rm <container_id>               # Remove stopped container
docker logs -f <container_id>          # Tail container logs
docker exec -it <container_id> bash    # Shell into running container

# Volumes & Cleanup
docker volume create mydata            # Create named volume
docker system prune -a                 # Remove ALL unused images, containers, networks
docker stats                           # Live resource usage (CPU, memory)
```

---

## Docker Networking — 4 Types

| Driver | What | When to Use |
|--------|------|-------------|
| **bridge** | Default. Containers on same bridge can talk via IP. | Single-host, dev |
| **host** | Container shares host's network namespace. No isolation. | Performance-critical |
| **none** | No networking. | Security/isolation |
| **overlay** | Multi-host networking (Swarm/K8s). | Production clusters |

```bash
docker network create my-net
docker run --network my-net --name api myapp
docker run --network my-net --name db postgres
# api can reach db by hostname "db"
```

---

## Multi-Stage Builds — The Interview Favorite

**Problem:** Build tools (Maven, npm) bloat the final image.  
**Solution:** Multi-stage builds — build in one stage, copy only the artifact to a slim final image.

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline          # Cache dependencies
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run (slim image)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Result:** Image goes from ~800MB → ~150MB 🚀

---

## Docker Compose — Multi-Container Apps

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - db
      - redis
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/mydb

  db:
    image: postgres:15
    volumes:
      - pgdata:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: mydb
      POSTGRES_PASSWORD: secret

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  pgdata:
```

```bash
docker compose up -d        # Start all services
docker compose down          # Stop and remove
docker compose logs -f app   # Tail logs of one service
docker compose ps            # Status of services
```

---

## Image Optimization Tips (Interview Gold)

1. **Use slim/alpine base images** — `openjdk:17-slim` or `eclipse-temurin:17-jre-alpine`
2. **Multi-stage builds** — Separate build and runtime stages
3. **Order Dockerfile for caching** — Put rarely-changing layers first (dependencies before source code)
4. **Combine RUN commands** — `RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*`
5. **Use .dockerignore** — Exclude `target/`, `.git/`, `node_modules/`
6. **Don't run as root** — `RUN adduser --system appuser && USER appuser`
7. **Pin image versions** — `FROM node:18.17.0-alpine` not `FROM node:latest`

---

## 🔥 Top 10 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | Container vs VM? | Container shares kernel = lightweight. VM has full OS = heavy. |
| 2 | What happens in `docker run`? | Pull image (if needed) → Create container → Start process |
| 3 | How are layers formed? | Each Dockerfile instruction = new layer. Layers are cached. |
| 4 | ENTRYPOINT vs CMD? | ENTRYPOINT = fixed command. CMD = default args (overridable). |
| 5 | How to reduce image size? | Multi-stage builds + alpine base + .dockerignore |
| 6 | What is a dangling image? | Image with no tag (intermediate build). `docker image prune` removes them. |
| 7 | How do containers communicate? | Via Docker networks. Same network = reachable by container name. |
| 8 | Volumes vs bind mounts? | Volumes managed by Docker (persist). Bind mounts map host paths. |
| 9 | What is Docker Compose? | Tool to define and run multi-container apps via YAML. |
| 10 | How to debug a crashed container? | `docker logs`, `docker inspect`, `docker exec` (if running). |

---

## Quick Reference

```
Image = Blueprint (class)
Container = Running instance (object)
Dockerfile = Recipe to build image
Volume = Persistent storage
Network = Container-to-container communication
Registry = Image storage (Docker Hub)
Compose = Multi-container orchestration
```

