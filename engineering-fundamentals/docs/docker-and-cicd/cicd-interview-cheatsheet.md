# ⚡ CI/CD in 5 Minutes — Interview Cheat Sheet

> Quick-fire CI/CD concepts, tools, and pipeline design. Read this before the interview.

---

## What is CI/CD?

**CI (Continuous Integration):** Automatically build, test, and validate code every time a developer pushes to the shared repository.

**CD (Continuous Delivery):** Automatically deploy validated code to staging/production with **manual approval**.

**CD (Continuous Deployment):** Automatically deploy validated code to production **without** manual approval.

```
Developer pushes code
        │
        ▼
   ┌─────────┐     ┌─────────┐     ┌──────────────┐     ┌────────────┐
   │  Build   │────▶│  Test   │────▶│   Deploy to  │────▶│  Deploy to │
   │  & Lint  │     │ (Unit,  │     │   Staging     │     │ Production │
   └─────────┘     │ Integ.) │     └──────────────┘     └────────────┘
                    └─────────┘
   ◄────── CI ──────▶         ◄──── CD (Delivery) ─────▶
                              ◄──── CD (Deployment) ────────────────▶
```

---

## Why CI/CD? (Interview Answer)

| Without CI/CD | With CI/CD |
|--------------|-----------|
| Manual builds break in production | Automated builds catch issues early |
| "Works on my machine" syndrome | Consistent build environment |
| Big-bang releases (risky) | Small, frequent, safe releases |
| Manual testing is slow | Automated tests run on every push |
| Deployment is stressful | Deployment is a non-event |

---

## CI/CD Pipeline Stages (Standard)

```
1. Source      → Code pushed / PR created
2. Build       → Compile code, resolve dependencies
3. Test        → Unit tests, integration tests, code coverage
4. Quality     → Static analysis (SonarQube), security scan (Snyk, Trivy)
5. Package     → Build Docker image, create artifact (JAR, WAR)
6. Deploy STG  → Deploy to staging environment
7. E2E Test    → Run end-to-end / smoke tests on staging
8. Approve     → Manual approval gate (for Continuous Delivery)
9. Deploy PROD → Deploy to production
10. Monitor    → Health checks, alerts, rollback if needed
```

---

## Popular CI/CD Tools

| Tool | Type | Best For |
|------|------|----------|
| **GitHub Actions** | Cloud-native | GitHub repos, simple to complex pipelines |
| **Jenkins** | Self-hosted | Enterprise, highly customizable |
| **GitLab CI** | Integrated | GitLab repos, built-in CI/CD |
| **CircleCI** | Cloud | Fast builds, good caching |
| **Travis CI** | Cloud | Open source projects |
| **Azure DevOps** | Cloud/Hybrid | Microsoft ecosystem |
| **ArgoCD** | GitOps | Kubernetes deployments |
| **Tekton** | Cloud-native | Kubernetes-native pipelines |

---

## GitHub Actions — Quick Example

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: mvn clean verify
      - uses: actions/upload-artifact@v4
        with:
          name: app-jar
          path: target/*.jar

  deploy:
    needs: build-and-test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - run: echo "Deploy to production"
```

---

## Jenkins Pipeline — Quick Example

```groovy
pipeline {
    agent any
    stages {
        stage('Build')   { steps { sh 'mvn clean compile' } }
        stage('Test')    { steps { sh 'mvn test' } }
        stage('Package') { steps { sh 'mvn package -DskipTests' } }
        stage('Deploy')  { steps { sh './deploy.sh' } }
    }
    post {
        failure { mail to: 'team@example.com', subject: 'Build Failed!' }
    }
}
```

---

## Deployment Strategies — Interview Must-Know

| Strategy | How It Works | Risk | Rollback Speed |
|----------|-------------|------|---------------|
| **Recreate** | Kill old → Start new | ⬆ Downtime | Slow |
| **Rolling** | Replace instances one by one | ⬇ Low | Medium |
| **Blue-Green** | Two identical environments, switch traffic | ⬇ Low | Instant (switch back) |
| **Canary** | Route small % traffic to new version | ⬇ Lowest | Fast |
| **A/B Testing** | Route by user attributes (not just %) | ⬇ Low | Fast |

### Blue-Green Deployment

```
         Load Balancer
              │
    ┌─────────┴──────────┐
    ▼                     ▼
┌─────────┐        ┌─────────┐
│  Blue   │        │  Green  │
│ (v1.0)  │        │ (v2.0)  │
│ ACTIVE  │        │ STANDBY │
└─────────┘        └─────────┘

After validation: switch traffic from Blue → Green
Rollback: switch back to Blue (instant!)
```

### Canary Deployment

```
Load Balancer
     │
     ├── 95% → v1.0 (stable)
     │
     └── 5%  → v2.0 (canary)

Monitor errors/latency on canary.
If OK → gradually increase to 100%.
If BAD → route 100% back to v1.0.
```

---

## Key CI/CD Concepts

| Concept | What It Means |
|---------|--------------|
| **Pipeline as Code** | CI/CD config lives in repo (`.github/workflows/`, `Jenkinsfile`) |
| **Artifact** | Build output (JAR, Docker image, binary) |
| **Artifact Registry** | Storage for artifacts (Nexus, Artifactory, ECR, Docker Hub) |
| **Environment** | Where code runs (dev, staging, production) |
| **Secret Management** | Never hardcode secrets. Use GitHub Secrets, Vault, AWS SSM |
| **Infrastructure as Code** | Define infra in code (Terraform, CloudFormation, Pulumi) |
| **GitOps** | Git as single source of truth. Push to Git → auto-deploy (ArgoCD) |
| **Feature Flags** | Toggle features on/off without deploying (LaunchDarkly) |
| **Trunk-Based Dev** | Short-lived branches, merge to main often |
| **Branch Protection** | Require PR reviews, passing CI before merge |

---

## 🔥 Top 10 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | CI vs CD? | CI = build + test on every push. CD = auto deploy (delivery = manual gate, deployment = fully auto). |
| 2 | Why CI/CD? | Catch bugs early, consistent builds, faster releases, less risky deployments. |
| 3 | Blue-Green vs Canary? | Blue-Green = full switch between environments. Canary = gradual traffic shift to new version. |
| 4 | What is GitOps? | Git is the source of truth. Push to Git → auto-deploy. ArgoCD watches Git repo. |
| 5 | How to handle secrets? | Never in code. Use secret managers (Vault, GitHub Secrets, AWS SSM). |
| 6 | What is Pipeline as Code? | CI/CD configuration stored in the repository alongside application code. |
| 7 | How to rollback? | Blue-Green: switch back. Canary: route traffic to old. K8s: `kubectl rollout undo`. |
| 8 | Jenkins vs GitHub Actions? | Jenkins = self-hosted, highly customizable. GH Actions = cloud, simpler, GitHub-native. |
| 9 | What is a build artifact? | Output of the build process — JAR, Docker image, binary. Stored in artifact registry. |
| 10 | How to test in CI? | Unit tests (every push), integration tests (PR), E2E tests (staging deploy). |

---

## Quick Reference

```
CI  = Build + Test (every push)
CD  = Deploy automatically (with or without manual gate)
Pipeline = Series of automated steps (build → test → deploy)
Artifact = Build output (JAR, Docker image)
GitOps = Git is source of truth for deployments
Blue-Green = Two environments, instant switch
Canary = Gradual traffic shift to new version
```

