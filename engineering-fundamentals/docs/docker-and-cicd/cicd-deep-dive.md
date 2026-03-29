# 🚀 CI/CD Deep Dive — Senior Engineer's Complete Reference

> Everything a senior engineer should know about CI/CD.  
> From pipeline basics to advanced deployment strategies, GitOps, and production best practices.

---

## Table of Contents

1. [What is CI/CD — Really?](#1-what-is-cicd--really)
2. [CI — Continuous Integration Deep Dive](#2-ci--continuous-integration-deep-dive)
3. [CD — Continuous Delivery vs Continuous Deployment](#3-cd--continuous-delivery-vs-continuous-deployment)
4. [Pipeline Architecture — Stages, Jobs, Steps](#4-pipeline-architecture--stages-jobs-steps)
5. [GitHub Actions — Complete Guide](#5-github-actions--complete-guide)
6. [Jenkins — Declarative & Scripted Pipelines](#6-jenkins--declarative--scripted-pipelines)
7. [GitLab CI — Overview](#7-gitlab-ci--overview)
8. [Deployment Strategies — Deep Dive](#8-deployment-strategies--deep-dive)
9. [GitOps — ArgoCD & Flux](#9-gitops--argocd--flux)
10. [Testing Strategy in CI/CD](#10-testing-strategy-in-cicd)
11. [Secret Management](#11-secret-management)
12. [Artifact Management](#12-artifact-management)
13. [Infrastructure as Code (IaC) in Pipelines](#13-infrastructure-as-code-iac-in-pipelines)
14. [Monitoring & Observability Post-Deployment](#14-monitoring--observability-post-deployment)
15. [Branching Strategies for CI/CD](#15-branching-strategies-for-cicd)
16. [Pipeline Security — DevSecOps](#16-pipeline-security--devsecops)
17. [Performance Optimization — Fast Pipelines](#17-performance-optimization--fast-pipelines)
18. [Rollback Strategies](#18-rollback-strategies)
19. [Real-World Pipeline Examples](#19-real-world-pipeline-examples)
20. [Interview Q&A — 30 Questions](#20-interview-qa--30-questions)

---

## 1. What is CI/CD — Really?

CI/CD is a set of **practices and automation** that enables teams to deliver code changes **frequently, reliably, and safely**.

### The Evolution

```
Manual Build     → Nightly Builds     → CI (every push)     → CD (auto-deploy)
(2000s)           (2005s)               (2010s)                (2015s+)

"Build it on       "Cron builds         "Build + test          "Push to main =
 Friday and         at midnight"          on every commit"       deployed in
 pray"                                                           minutes"
```

### The CI/CD Flywheel

```
       Code ──────▶ Build ──────▶ Test
        ▲                            │
        │                            ▼
    Monitor ◀────── Deploy ◀───── Release
```

Each cycle gets **faster** and **safer** over time because:
- Tests catch regressions automatically
- Small changes are easier to debug
- Fast rollback reduces blast radius
- Teams gain confidence to ship more often

---

## 2. CI — Continuous Integration Deep Dive

### What CI Actually Does

```
Developer pushes to feature branch
        │
        ▼
┌───────────────────────────────────────────────────────┐
│                   CI Pipeline                          │
│                                                       │
│  1. Checkout code                                     │
│  2. Resolve dependencies (mvn, npm install)           │
│  3. Compile / Build                                   │
│  4. Run unit tests                                    │
│  5. Run integration tests                             │
│  6. Static code analysis (SonarQube, ESLint)          │
│  7. Security scan (Snyk, Trivy, OWASP Dependency Check)│
│  8. Code coverage check (JaCoCo, Istanbul)            │
│  9. Build artifact (JAR, Docker image)                │
│  10. Upload artifact to registry                      │
│                                                       │
│  Result: ✅ Green check or ❌ Red X on PR             │
└───────────────────────────────────────────────────────┘
```

### CI Best Practices

| Practice | Why |
|----------|-----|
| **Build on every push** | Catch issues immediately |
| **Keep builds fast** | < 10 min for CI. Developers won't wait longer |
| **Fix broken builds immediately** | A broken main branch blocks everyone |
| **Run tests in parallel** | Speed up pipeline |
| **Cache dependencies** | Don't download the internet every build |
| **Build artifacts once** | Same artifact across all environments |
| **Fail fast** | Run quick checks (lint, compile) before slow ones (integration tests) |

### The "Build Once, Deploy Many" Principle

```
                    ┌──── Dev Environment
                    │
Build → Artifact ──├──── Staging Environment
  (once)   (JAR/   │
            Image)  └──── Production Environment

✅ Same artifact everywhere
❌ Don't rebuild for each environment — use environment-specific config
```

---

## 3. CD — Continuous Delivery vs Continuous Deployment

| | Continuous Delivery | Continuous Deployment |
|--|-------------------|---------------------|
| **Deploy to production** | Manual approval gate | Fully automated |
| **Confidence required** | High (thorough tests) | Very high (comprehensive tests + monitoring) |
| **Rollback** | Manual or automated | Automated |
| **Use case** | Regulated industries, critical systems | SaaS, web apps, microservices |

```
Continuous Delivery:
Code → Build → Test → Stage → [MANUAL APPROVAL] → Production

Continuous Deployment:
Code → Build → Test → Stage → E2E Tests → Production (auto!)
```

### When to Use Each

- **Continuous Delivery:** Banks, healthcare, regulated industries where compliance requires manual sign-off
- **Continuous Deployment:** SaaS products like Netflix, Facebook, Etsy where speed matters and monitoring catches issues

---

## 4. Pipeline Architecture — Stages, Jobs, Steps

### Anatomy of a Pipeline

```yaml
Pipeline                    # The whole CI/CD process
├── Stage: Build            # Logical group of jobs
│   └── Job: compile        # Runs on one machine/container
│       ├── Step: checkout   # Atomic unit of work
│       ├── Step: setup-java
│       └── Step: mvn compile
├── Stage: Test
│   ├── Job: unit-tests     # Jobs in a stage can run in parallel
│   ├── Job: integration-tests
│   └── Job: security-scan
├── Stage: Package
│   └── Job: docker-build
├── Stage: Deploy-Staging
│   └── Job: deploy-to-staging
├── Stage: E2E-Test
│   └── Job: run-e2e
└── Stage: Deploy-Production
    └── Job: deploy-to-prod  # May require manual approval
```

### Parallel vs Sequential

```
Sequential (slow):
Build ──▶ Unit Test ──▶ Integration Test ──▶ Security Scan ──▶ Package

Parallel (fast):
Build ──▶ ┌── Unit Test ─────────┐
          ├── Integration Test ──┤──▶ Package
          └── Security Scan ─────┘
```

---

## 5. GitHub Actions — Complete Guide

### Key Concepts

| Concept | What It Is |
|---------|-----------|
| **Workflow** | YAML file in `.github/workflows/`. Defines the pipeline. |
| **Event** | Trigger: `push`, `pull_request`, `schedule`, `workflow_dispatch` |
| **Job** | Set of steps running on one runner. Jobs run in parallel by default. |
| **Step** | Individual task: run a command or use an action. |
| **Action** | Reusable unit (marketplace): `actions/checkout@v4`, `docker/build-push-action@v5` |
| **Runner** | Machine that executes the job: `ubuntu-latest`, `macos-latest`, self-hosted |
| **Secret** | Encrypted variable: `${{ secrets.MY_SECRET }}` |
| **Artifact** | File passed between jobs: `actions/upload-artifact`, `actions/download-artifact` |
| **Matrix** | Run job across multiple configurations (Java 11, 17, 21) |
| **Cache** | Cache dependencies between runs for speed |

### Complete Production Pipeline

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  JAVA_VERSION: '17'
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  # ─── Stage 1: Build & Test ───
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: maven

      - name: Build & Test
        run: mvn clean verify -B

      - name: Upload Coverage
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco/

      - name: Upload JAR
        uses: actions/upload-artifact@v4
        with:
          name: app-jar
          path: target/*.jar

  # ─── Stage 2: Code Quality ───
  quality:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: SonarQube Scan
        uses: sonarqube-quality-gate-action@v1
        with:
          scannerMode: CLI
          args: >
            -Dsonar.projectKey=myapp
            -Dsonar.host.url=${{ secrets.SONAR_URL }}

  # ─── Stage 3: Security Scan ───
  security:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run Trivy vulnerability scanner
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: fs
          severity: CRITICAL,HIGH

  # ─── Stage 4: Docker Build & Push ───
  docker:
    needs: [build, quality, security]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - uses: actions/download-artifact@v4
        with:
          name: app-jar
          path: target/

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and Push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest

  # ─── Stage 5: Deploy to Staging ───
  deploy-staging:
    needs: docker
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - name: Deploy to Staging
        run: |
          kubectl set image deployment/myapp \
            myapp=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} \
            --namespace staging

  # ─── Stage 6: E2E Tests ───
  e2e-tests:
    needs: deploy-staging
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run E2E Tests
        run: npm run test:e2e -- --base-url https://staging.myapp.com

  # ─── Stage 7: Deploy to Production ───
  deploy-production:
    needs: e2e-tests
    runs-on: ubuntu-latest
    environment:
      name: production
      url: https://myapp.com
    steps:
      - name: Deploy to Production
        run: |
          kubectl set image deployment/myapp \
            myapp=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} \
            --namespace production
```

### Matrix Builds — Test Across Versions

```yaml
jobs:
  test:
    strategy:
      matrix:
        java: [11, 17, 21]
        os: [ubuntu-latest, macos-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
      - run: mvn test
```

### Caching Dependencies

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: temurin
    cache: maven              # Auto-caches ~/.m2/repository

# Or manual caching:
- uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: ${{ runner.os }}-maven-
```

---

## 6. Jenkins — Declarative & Scripted Pipelines

### Declarative Pipeline (Preferred)

```groovy
pipeline {
    agent {
        docker { image 'maven:3.9-eclipse-temurin-17' }
    }

    environment {
        DOCKER_REGISTRY = 'mycompany.azurecr.io'
        APP_NAME = 'myapp'
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean compile -B'
            }
        }

        stage('Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        sh 'mvn test -B'
                    }
                    post {
                        always {
                            junit '**/target/surefire-reports/*.xml'
                            jacoco(execPattern: '**/target/jacoco.exec')
                        }
                    }
                }
                stage('Integration Tests') {
                    steps {
                        sh 'mvn verify -Pintegration-tests -B'
                    }
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn package -DskipTests -B'
                sh "docker build -t ${DOCKER_REGISTRY}/${APP_NAME}:${BUILD_NUMBER} ."
            }
        }

        stage('Deploy to Staging') {
            steps {
                sh "kubectl set image deployment/${APP_NAME} ${APP_NAME}=${DOCKER_REGISTRY}/${APP_NAME}:${BUILD_NUMBER} -n staging"
            }
        }

        stage('Deploy to Production') {
            input {
                message "Deploy to production?"
                ok "Yes, deploy!"
            }
            steps {
                sh "kubectl set image deployment/${APP_NAME} ${APP_NAME}=${DOCKER_REGISTRY}/${APP_NAME}:${BUILD_NUMBER} -n production"
            }
        }
    }

    post {
        success { slackSend color: 'good', message: "Build #${BUILD_NUMBER} succeeded" }
        failure { slackSend color: 'danger', message: "Build #${BUILD_NUMBER} failed" }
        always  { cleanWs() }
    }
}
```

### Jenkins vs GitHub Actions

| Feature | Jenkins | GitHub Actions |
|---------|---------|---------------|
| Hosting | Self-hosted | Cloud (GitHub-hosted or self-hosted) |
| Config | `Jenkinsfile` (Groovy) | `.github/workflows/*.yml` (YAML) |
| Plugins | 1800+ plugins | Marketplace actions |
| Scalability | Master-agent architecture | Auto-scaling runners |
| Learning curve | Steep | Gentle |
| Cost | Free (infra costs) | Free tier + pay per minute |
| Best for | Enterprise, complex pipelines | GitHub projects, simplicity |

---

## 7. GitLab CI — Overview

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - deploy

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"

cache:
  paths:
    - .m2/repository

build:
  stage: build
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn clean compile -B
  artifacts:
    paths:
      - target/

test:
  stage: test
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn test -B
  artifacts:
    reports:
      junit: target/surefire-reports/*.xml

deploy-staging:
  stage: deploy
  script:
    - kubectl set image deployment/myapp myapp=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA -n staging
  environment:
    name: staging
  only:
    - main

deploy-production:
  stage: deploy
  script:
    - kubectl set image deployment/myapp myapp=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA -n production
  environment:
    name: production
  when: manual
  only:
    - main
```

---

## 8. Deployment Strategies — Deep Dive

### 1. Recreate Deployment

```
Step 1: Stop all v1 instances      ████████ v1 → xxxxxxxx (DOWNTIME)
Step 2: Start all v2 instances     xxxxxxxx → ████████ v2
```

**Pros:** Simple, clean state  
**Cons:** Downtime during transition  
**Use when:** Dev/test environments, DB migrations that break backwards compatibility

### 2. Rolling Deployment

```
Step 1: ████████ v1  (4 instances)
Step 2: ██████░░ v1 + ██ v2  (replace 1)
Step 3: ████░░░░ v1 + ████ v2  (replace 2)
Step 4: ██░░░░░░ v1 + ██████ v2  (replace 3)
Step 5: ████████ v2  (all replaced)
```

**Pros:** Zero downtime, gradual rollout  
**Cons:** Two versions running simultaneously (handle API compatibility!)  
**Use when:** Default strategy. Most common.

**Kubernetes Rolling Update:**
```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%        # Extra pods during update
      maxUnavailable: 25%  # Pods that can be unavailable
```

### 3. Blue-Green Deployment

```
                Load Balancer
                     │
          ┌──────────┼──────────┐
          ▼                     ▼
    ┌──────────┐         ┌──────────┐
    │   Blue   │         │  Green   │
    │  (v1.0)  │         │  (v2.0)  │
    │ CURRENT  │         │   NEW    │
    └──────────┘         └──────────┘
    
Step 1: Deploy v2.0 to Green (Blue still serves traffic)
Step 2: Test Green thoroughly
Step 3: Switch LB from Blue → Green
Step 4: Green is now CURRENT
Step 5: Blue becomes the new staging (or idle)

Rollback: Switch LB back to Blue (instant!)
```

**Pros:** Instant rollback, zero downtime, full testing before switch  
**Cons:** 2x infrastructure cost, database migrations need care  
**Use when:** Critical services, need instant rollback capability

### 4. Canary Deployment

```
Phase 1:  ████████████████████ v1 (100%) + ░ v2 (1 instance)
          Monitor errors, latency, business metrics

Phase 2:  ██████████████████ v1 (90%) + ██ v2 (10%)
          Still monitoring...

Phase 3:  ████████████ v1 (60%) + ████████ v2 (40%)
          Looking good...

Phase 4:  ████████████████████ v2 (100%)
          Full rollout!

If any phase shows issues → route 100% back to v1
```

**Pros:** Lowest risk, real-world testing with small blast radius  
**Cons:** Complex setup, need good monitoring  
**Use when:** New features with uncertain impact, production testing needed

**Kubernetes Canary with Istio:**
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
spec:
  http:
    - route:
        - destination:
            host: myapp
            subset: stable
          weight: 90
        - destination:
            host: myapp
            subset: canary
          weight: 10
```

### 5. A/B Testing (Feature-Based Routing)

```
                    Load Balancer
                         │
                 ┌───────┴───────┐
                 ▼               ▼
           ┌──────────┐   ┌──────────┐
           │ Version A │   │ Version B │
           │ (control) │   │ (variant) │
           └──────────┘   └──────────┘
           
Route by: user geography, browser, user ID, feature flag
Purpose: Measure business impact (conversion, engagement)
```

---

## 9. GitOps — ArgoCD & Flux

### What is GitOps?

**Git is the single source of truth for infrastructure and application state.**

```
Traditional:
Developer → CI → Build → Push Artifact → CD Tool → Deploy to K8s

GitOps:
Developer → Push manifest to Git → ArgoCD detects change → Syncs to K8s

Two Repos:
1. App Repo:      source code → CI builds image → pushes to registry
2. Config Repo:   K8s manifests → ArgoCD watches → deploys to cluster
```

### ArgoCD Flow

```
┌─────────────┐    ┌──────────────┐    ┌───────────────┐
│   Git Repo  │───▶│   ArgoCD     │───▶│  Kubernetes   │
│ (manifests) │    │ (controller) │    │  (cluster)    │
└─────────────┘    └──────────────┘    └───────────────┘
                          │
                   Continuous sync:
                   Desired state (Git) == Actual state (K8s)
                   If drift detected → auto-sync or alert
```

### Benefits of GitOps
- **Audit trail:** Every change is a Git commit
- **Rollback:** `git revert` = rollback deployment
- **Consistency:** Declarative state, not imperative commands
- **Security:** No direct cluster access needed; ArgoCD applies changes

---

## 10. Testing Strategy in CI/CD

### The Testing Pyramid

```
         ╱ ╲
        ╱ E2E ╲           Few, slow, expensive
       ╱───────╲          (Selenium, Cypress, Playwright)
      ╱         ╲
     ╱Integration╲        Moderate number
    ╱─────────────╲       (TestContainers, WireMock)
   ╱               ╲
  ╱   Unit Tests    ╲     Many, fast, cheap
 ╱───────────────────╲   (JUnit, Mockito)
```

### When to Run What

| Test Type | When | Duration | What It Catches |
|-----------|------|----------|----------------|
| Unit tests | Every push | Seconds | Logic errors, edge cases |
| Integration tests | Every PR | Minutes | API contracts, DB queries |
| E2E tests | Pre-deploy to staging | 10-30 min | Full user flows |
| Load tests | Pre-deploy to prod | Hours | Performance regressions |
| Smoke tests | Post-deploy | Seconds | "Is it alive?" |

---

## 11. Secret Management

### Never Do This

```yaml
# ❌ NEVER commit secrets
env:
  DB_PASSWORD: "super-secret-password"
  API_KEY: "sk-1234567890"
```

### Do This Instead

| Tool | Best For |
|------|----------|
| **GitHub Secrets** | GitHub Actions pipelines |
| **HashiCorp Vault** | Enterprise, multi-cloud |
| **AWS Secrets Manager** | AWS-native applications |
| **Azure Key Vault** | Azure applications |
| **SOPS** | Encrypted secrets in Git |

```yaml
# ✅ Reference secrets, never hardcode
env:
  DB_PASSWORD: ${{ secrets.DB_PASSWORD }}

# ✅ Or fetch from Vault at runtime
steps:
  - uses: hashicorp/vault-action@v2
    with:
      url: https://vault.mycompany.com
      secrets: |
        secret/data/myapp DB_PASSWORD | DB_PASSWORD
```

---

## 12. Artifact Management

### Artifact Flow

```
Source Code → CI Build → Artifact → Artifact Registry → CD Deploy
                         (JAR, Docker image)

Common Registries:
- Docker Hub / GHCR / ECR / GCR / ACR  (Docker images)
- Nexus / Artifactory / GitHub Packages  (JARs, npm packages)
```

### Tagging Strategy

```bash
# Semantic versioning
myapp:1.2.3

# Git SHA (traceability)
myapp:abc123f

# Build number
myapp:build-456

# Combined (recommended for production)
myapp:1.2.3-abc123f
```

---

## 13. Infrastructure as Code (IaC) in Pipelines

```yaml
# Terraform in CI/CD
jobs:
  terraform:
    steps:
      - run: terraform init
      - run: terraform plan -out=tfplan
      - run: terraform apply tfplan    # Only on main branch
```

### IaC Tools

| Tool | Language | Provider |
|------|----------|----------|
| Terraform | HCL | Multi-cloud |
| CloudFormation | YAML/JSON | AWS |
| Pulumi | TypeScript/Python/Go | Multi-cloud |
| Bicep | Bicep | Azure |
| CDK | TypeScript/Python | AWS |

---

## 14. Monitoring & Observability Post-Deployment

### The Three Pillars

```
┌─────────┐  ┌──────────┐  ┌──────────┐
│  Logs   │  │ Metrics  │  │ Traces   │
│         │  │          │  │          │
│ What    │  │ How much │  │ Where    │
│ happened│  │ /how fast│  │ (flow)   │
└─────────┘  └──────────┘  └──────────┘
 ELK/Loki    Prometheus     Jaeger/Zipkin
              Grafana        OpenTelemetry
```

### Post-Deploy Health Checks

```yaml
# After deployment, verify:
- name: Smoke Test
  run: |
    for i in {1..30}; do
      STATUS=$(curl -s -o /dev/null -w "%{http_code}" https://myapp.com/health)
      if [ "$STATUS" = "200" ]; then
        echo "✅ Health check passed"
        exit 0
      fi
      echo "Waiting... ($i/30)"
      sleep 10
    done
    echo "❌ Health check failed — initiating rollback"
    kubectl rollout undo deployment/myapp
    exit 1
```

---

## 15. Branching Strategies for CI/CD

### Trunk-Based Development (Recommended for CI/CD)

```
main ──●──●──●──●──●──●──●──●──●──  (always deployable)
        ╲      ╱  ╲    ╱
         feat-1     feat-2
      (short-lived branches, < 1 day)
```

### Git Flow

```
main ──────●──────────────●─────────  (production releases)
           │              │
develop ──●──●──●──●──●──●──●──●──  (integration branch)
           ╲  ╱    ╲    ╱
           feat-1   feat-2
                         ╲
                     release/1.0  ← stabilization branch
```

### GitHub Flow (Simple)

```
main ──●──●──●──●──●──●──  (always deployable)
        ╲      ╱
         feature-branch
         (PR + review + merge)
```

**Recommendation:** Trunk-based for CI/CD. Short-lived branches. Feature flags for incomplete work.

---

## 16. Pipeline Security — DevSecOps

```
Shift Left: Find security issues EARLY in the pipeline, not in production.

┌─────────┬────────────┬──────────┬──────────┬──────────┐
│  Code   │   Build    │  Deploy  │ Runtime  │ Monitor  │
├─────────┼────────────┼──────────┼──────────┼──────────┤
│ SAST    │ Dependency │ Image    │ RASP     │ SIEM     │
│ Secrets │ Scan       │ Scan     │ WAF      │ Alerting │
│ Lint    │ License    │ IaC Scan │ Network  │ Audit    │
│         │ Check      │          │ Policy   │ Logs     │
└─────────┴────────────┴──────────┴──────────┴──────────┘
```

### Security Tools in Pipeline

| Stage | Tool | What It Does |
|-------|------|-------------|
| SAST | SonarQube, Semgrep | Static code analysis |
| Secrets | GitLeaks, TruffleHog | Detect committed secrets |
| Dependencies | Snyk, OWASP Dep-Check | CVE scanning |
| Container | Trivy, Scout | Image vulnerability scan |
| IaC | Checkov, tfsec | Terraform/K8s misconfig |

---

## 17. Performance Optimization — Fast Pipelines

| Technique | Impact |
|-----------|--------|
| **Cache dependencies** | Maven: ~/.m2, npm: node_modules |
| **Parallel jobs** | Run unit + integration + security in parallel |
| **Incremental builds** | Only build changed modules |
| **Docker layer caching** | Cache unchanged layers |
| **Self-hosted runners** | Avoid cold-start of cloud runners |
| **Test splitting** | Distribute tests across multiple runners |
| **Skip unnecessary steps** | Use path filters to skip CI for docs-only changes |
| **Smaller Docker images** | Faster pull times in deployment |

```yaml
# Path-based triggers — skip CI for docs changes
on:
  push:
    paths-ignore:
      - 'docs/**'
      - '*.md'
      - 'LICENSE'
```

---

## 18. Rollback Strategies

| Strategy | How | Speed |
|----------|-----|-------|
| **Kubernetes rollout undo** | `kubectl rollout undo deployment/myapp` | Seconds |
| **Blue-Green switch** | Route traffic back to old environment | Instant |
| **Canary abort** | Route 100% back to stable | Seconds |
| **Redeploy previous version** | Re-run pipeline with old tag | Minutes |
| **Git revert** | `git revert` → triggers new pipeline | Minutes |
| **Feature flag** | Toggle feature off | Instant |

---

## 19. Real-World Pipeline Examples

### Microservices CI/CD Architecture

```
┌──────────────┐
│  App Repo    │
│  (source)    │──── CI Pipeline ──── Build Image ──── Push to ECR
└──────────────┘                                          │
                                                          │
┌──────────────┐                                          │
│  Config Repo │◀── Update image tag ─────────────────────┘
│  (K8s YAML)  │
└──────┬───────┘
       │
┌──────▼───────┐
│   ArgoCD     │──── Detect change ──── Sync to K8s ──── Running!
└──────────────┘
```

---

## 20. Interview Q&A — 30 Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | CI vs CD? | CI = build/test on every push. CD = automated deployment (delivery has gate, deployment is fully auto) |
| 2 | Why CI/CD? | Faster, safer, more frequent releases. Catch bugs early. |
| 3 | Pipeline as Code? | CI/CD config stored in repo alongside code (Jenkinsfile, .github/workflows/) |
| 4 | Blue-Green vs Canary? | Blue-Green = full switch. Canary = gradual traffic shift. |
| 5 | What is GitOps? | Git as source of truth for deployments. ArgoCD/Flux syncs Git → K8s. |
| 6 | How to handle flaky tests? | Quarantine, retry, fix root cause. Never ignore. |
| 7 | How to keep pipelines fast? | Cache, parallel jobs, incremental builds, skip unchanged. |
| 8 | What is a build artifact? | Output of build: JAR, Docker image. Built once, deployed many times. |
| 9 | How to manage secrets? | Never in code. Use Vault, GitHub Secrets, AWS SSM. |
| 10 | Trunk-based vs Git Flow? | Trunk = short branches, CI-friendly. Git Flow = long branches, for releases. |
| 11 | What is shift left? | Find issues (bugs, security) earlier in the pipeline. |
| 12 | How to rollback? | K8s undo, Blue-Green switch, feature flag, git revert. |
| 13 | Jenkins vs GitHub Actions? | Jenkins = self-hosted, flexible. GH Actions = cloud, simpler, GitHub-native. |
| 14 | What is a runner? | Machine/container that executes pipeline jobs. |
| 15 | How to test DB migrations? | Run migrations in CI against a test DB (Flyway/Liquibase). |
| 16 | What are environment gates? | Manual approval steps between stages (staging → production). |
| 17 | How to handle DB in blue-green? | Both versions must work with same DB schema. Use backwards-compatible migrations. |
| 18 | What is a smoke test? | Quick post-deploy check: "Is the app alive and responding?" |
| 19 | Feature flags vs branches? | Feature flags = toggle in code (runtime). Branches = toggle in VCS (build-time). |
| 20 | What is DevSecOps? | Integrating security into every CI/CD stage, not just at the end. |
| 21 | How to handle monorepo CI? | Path-based triggers, build only changed services, shared pipeline templates. |
| 22 | What is IaC? | Infrastructure defined in code (Terraform, CloudFormation). Versioned, reviewed, automated. |
| 23 | How to do zero-downtime deploy? | Rolling update, Blue-Green, or Canary. Never recreate in production. |
| 24 | What is a deployment manifest? | K8s YAML or Helm chart defining desired state. |
| 25 | How ArgoCD works? | Watches Git repo. Compares desired (Git) vs actual (K8s). Syncs if different. |
| 26 | What is container orchestration? | Managing container lifecycle at scale: K8s, ECS, Docker Swarm. |
| 27 | How to promote artifacts? | Same artifact moves through environments. Never rebuild. |
| 28 | What is observability? | Logs + Metrics + Traces. Know what's happening in production. |
| 29 | How to handle config per env? | Environment variables, ConfigMaps, Vault, feature flags. Not different builds. |
| 30 | What are self-hosted runners? | Your own machines running pipeline jobs. More control, persistent cache. |

---

> **Pro Tip for Interviews:** Talk about CI/CD in terms of **outcomes** — faster releases, fewer bugs in production, instant rollback capability. That's what companies care about.

