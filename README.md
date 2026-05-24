# Spira

A full-stack goal-tracking web application built as a personal learning project. Spira helps users set, structure, and track long-term personal goals using the GROW coaching model — Goal, Reality, Options, Will.

Detailed project documentation is in the repository root folders:

- `docs/` — practical guides and testing/linting docs
- `specs/` — mission, roadmap, architecture and implementation specs

## Why This Project Exists

I graduated from Jensen Yrkeshögskola in May 2026, specialising in systems development with a focus on testing. This project is how I learn best — by building something real, making mistakes, and figuring out why things work the way they do.

The idea came during my web application development course, when I wanted to understand exactly how a backend and frontend connect and communicate. I rebuilt this project several times as my understanding improved.

At some point, I learned that clear specs and tests are the best form of documentation:

- they verify behavior
- they catch regressions
- they make intent explicit for collaborators and AI tools

## The Problem I Was Solving

In summer 2023 I moved to Sweden with one large goal: to build a life here. In practice that meant many parallel goals — language, education, work, social integration. I needed one place to structure all of it and track visible progress.

I first tried plain notes in Notion, then traditional task-management apps, and then goal-tracking products. Most tools were still focused on work-style processes (OKRs, team performance, reporting), while I needed a personal long-term goal system.

So I built Spira.

## What the Application Does

Spira is a structured goal-setting workspace. For each goal, a user can manage:

- **Goal** — title, description, confidence (1..10), deadline
- **Reality** — actions already taken and current obstacles
- **Options** — possible strategies and selected option
- **Will / Targets** — measurable execution using:
  - binary target (done / not done)
  - numeric target (e.g. current/total)
  - checklist target (sub-items)
- **Resources** — notes, links, files, email/contact resources

Goal progress is calculated from targets and exposed both in frontend and backend.

---

## Tech Stack

### Backend

- Language: **Java 17** (`backend/pom.xml`)
- Framework: **Spring Boot 3.4.5**
- API layer: **GraphQL** (`spring-boot-starter-graphql`)
- Database: **PostgreSQL 16** (Docker) + **Flyway** migrations
- ORM: **Spring Data JPA / Hibernate**
- Build tool: **Maven Wrapper** (`backend/mvnw`)
- Tests: **Spring Boot Test + Spring GraphQL Test + H2 (test profile)**

### Frontend

- **React 19 + TypeScript + Vite 7**
- **TanStack Router** (file-based routes)
- **Zustand** (app state)
- **Tailwind CSS 4** + **Radix UI** components
- **TipTap** (rich text editor)
- **Vitest** (tests), **ESLint**, **Prettier**

---

## Full Local Run (Frontend + Backend + DB)

### 1) Prerequisites

- Node.js 20+
- npm
- Java 17+
- Docker (for PostgreSQL)

### 2) Start PostgreSQL

```bash
cd backend
docker compose up -d postgres
docker compose ps
```

Expected DB settings (`backend/docker-compose.yml`):

- DB: `spira`
- user: `spira`
- password: `spira`
- port: `5432`

### 3) Start backend

```bash
cd backend
sh ./mvnw spring-boot:run
```

Backend URLs:

- GraphQL endpoint: `http://localhost:8080/graphql`
- GraphiQL UI: `http://localhost:8080/graphiql.html`

### 4) Start frontend

```bash
cd .
npm install
npm run dev
```

Frontend URL:

- `http://localhost:5173`

### 5) Stop everything

- Stop frontend/backend terminals with `Ctrl+C`
- Stop DB:

```bash
cd backend
docker compose down
```
#### If the terminals were closed but the servers are still running, find the processes by port and stop them:

```powershell
# Backend on port 8080
netstat -ano | Select-String ":8080"
Stop-Process -Id <PID_FROM_LISTENING_LINE> -Force

# Frontend on port 5173
netstat -ano | Select-String ":5173"
Stop-Process -Id <PID_FROM_LISTENING_LINE> -Force
Stop-Process -Id 26928 -Force

```

### Windows equivalents

Backend run:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend run:

```powershell
npm install
npm run dev
```

---

## How Frontend and Backend Are Connected (with code references)

Connection flow in this project:

1. **Frontend sends GraphQL HTTP POST** to `/graphql` from `src/lib/spira/api.ts`.
2. **Vite dev proxy** forwards `/graphql` to backend `http://localhost:8080` (`vite.config.ts`).
3. **Spring GraphQL controller** handles queries/mutations in `backend/src/main/java/com/spiramindscape/backend/graphql/SpiraGraphqlController.java`.
4. **Services + JPA** save/fetch data (`goal`, `target`, `resource`, `reality`).
5. **PostgreSQL schema/migrations** are in `backend/src/main/resources/db/migration/`.

Concrete frontend trigger example:

- `AppShell` calls `loadGoals()` on mount (`src/components/shell/AppShell.tsx`)
- store method calls `spiraApi.fetchGoals()` (`src/lib/spira/store.ts`)
- API executes GraphQL `query Goals { goals { ... } }` (`src/lib/spira/api.ts`)

---

## Why GraphQL Instead of REST (project example)

### Real GraphQL shape used in Spira

From this project, a single query can request only needed nested fields:

```graphql
query {
  goalById(id: "42") {
    id
    title
    confidence
    progress
    targets {
      id
      title
      type
      progress
    }
    resources {
      id
      type
      title
    }
  }
}
```

Related files:

- schema: `backend/src/main/resources/graphql/schema.graphqls`
- resolver/controller: `backend/src/main/java/com/spiramindscape/backend/graphql/SpiraGraphqlController.java`
- frontend client query patterns: `src/lib/spira/api.ts`

### How this would look in REST

To get equivalent data, REST usually needs several endpoints/requests, for example:

- `GET /goals/42`
- `GET /goals/42/targets`
- `GET /goals/42/resources`
- optionally `GET /goals/42/reality`, `GET /goals/42/options`

Or one oversized endpoint returning more fields than this page needs.

That is the practical reason GraphQL fits Spira’s goal workspace screens.

### GraphQL Smoke Test

Open `http://localhost:8080/graphiql.html` and run:

```graphql
mutation {

  createGoal(input: { title: "Test Goal", description: "Test", confidence: 7 }) {

    id

    title

    createdAt

  }

}

```

Then open `http://localhost:5173` and confirm the goal appears in the frontend.

---

## Frontend Guide 

### What the frontend consists of

- `src/main.tsx` — app entry point
- `src/router.tsx` + `src/routeTree.gen.ts` — routing setup
- `src/routes/` — pages:
  - `index.tsx` — goals overview
  - `goals.$goalId.tsx` — goal workspace
  - `calendar.tsx` — calendar view
- `src/components/spira/` — product-specific components (GoalCard, Targets, Resources, etc.)
- `src/components/ui/` — reusable UI primitives
- `src/lib/spira/` — domain logic:
  - `types.ts` — core types
  - `progress.ts` — progress calculation
  - `store.ts` — Zustand state + optimistic sync
  - `api.ts` — GraphQL client

### How to find the component you need

1. Start from route file in `src/routes/`.
2. See which components it imports from `src/components/spira/`.
3. If component behavior updates backend data, check `src/lib/spira/store.ts` for action.
4. Then inspect matching GraphQL operation in `src/lib/spira/api.ts`.

---

## Backend Guide 

### What the backend consists of

- `backend/src/main/java/.../BackendApplication.java` — Spring Boot entry
- `.../graphql/SpiraGraphqlController.java` — GraphQL queries + mutations + batch resolvers
- `.../goal`, `.../target`, `.../resource` packages:
  - entities (`Goal`, `Target`, `Resource`, etc.)
  - services (`GoalService`, `TargetService`, `ResourceService`, `RealityService`)
  - repositories (`JpaRepository` interfaces)
- GraphQL schema: `backend/src/main/resources/graphql/schema.graphqls`
- Config: `backend/src/main/resources/application.properties`

### Request lifecycle (simple mental model)

GraphQL request -> controller method -> service business logic -> repository -> PostgreSQL -> response mapped back to frontend.

---

## Database Guide 

### Where DB is defined

- Docker PostgreSQL: `backend/docker-compose.yml`
- App connection config: `backend/src/main/resources/application.properties`
- Migrations: `backend/src/main/resources/db/migration/`

### Existing migrations

- `V1__init_database.sql` — core tables/indexes/triggers
- `V2__seed_data.sql` — intentionally empty seed
- `V3__timestamps_to_timestamptz.sql` — deadlines/achieved timestamps to `TIMESTAMPTZ`
- `V4__option_position.sql` — option ordering (`position`)
- `V5__resource_label_length.sql` — resource title/name constraints
- `V6__confidence_history.sql` — confidence history table

Flyway runs these in order on backend startup.

---

## Tests: how to run and what exists

### Run tests locally

From repository root (frontend):

```bash
npm test
npm run build
```

From `backend/` (backend tests):

```bash
cd backend
sh ./mvnw test
```

Windows equivalents:

```powershell
npm.cmd test
npm.cmd run build
cd backend
.\mvnw.cmd test
```

### Frontend test files (current)

- `src/lib/spira/api.contract.test.ts`
- `src/lib/spira/api.test.ts`
- `src/lib/spira/progress.test.ts`
- `src/lib/spira/store.test.ts`

### Backend unit/service-level test files (current)

- `backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/target/TargetServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/resource/ResourceServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/GoalServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/RealityServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/EntityTimestampTest.java`

### Backend GraphQL integration/contract test files (current)

- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalCreationIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalWorkspaceIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalConfidenceIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/RealityIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/OptionIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/TargetIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/ResourceIntegrationTest.java`

### Test structure and conventions 

What is correct:

- Most integration test classes are organized by domain area (goals/reality/options/targets/resources/confidence).
- Integration tests run with Spring Boot + GraphQL tester and test the full flow (GraphQL -> service -> persistence -> response).
- Many integration tests use AAA comments (`Arrange / Act / Assert`) and `@DisplayName`.
- Many error-path tests verify both message and error classification (for example `ValidationError` or `NOT_FOUND`).

How conventions are applied in practice:

- `@DisplayName` and parameterized tests are both first-class patterns in this suite.
  - Parameterized tests (`@ParameterizedTest`) are used when one behavior must be validated across multiple inputs without duplicating test code.
  - This keeps tests shorter, keeps scenarios aligned, and improves failure diagnostics by showing exactly which input case failed.
  - Examples:
    - `backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java` (`goalValidationCases`)
    - `backend/src/test/java/com/spiramindscape/backend/graphql/GoalCreationIntegrationTest.java` (deadline/title boundary matrices)
    - `backend/src/test/java/com/spiramindscape/backend/graphql/ResourceIntegrationTest.java` (resource-type validation matrices)
    - `backend/src/test/java/com/spiramindscape/backend/graphql/TargetIntegrationTest.java` (invalid deadline matrices)
  - Result of review: no additional immediate refactor to parameterized tests is required right now; current parameterization already covers repeated input matrices where it gives clear value.

- AAA comments (`Arrange / Act / Assert`) are used where they materially improve readability (especially multi-step integration flows with setup + mutation/query + grouped assertions).
  - In very short or fluent-chain tests, AAA headers are intentionally omitted to avoid visual noise when the structure is already obvious from the code.

- Cleanup strategy (`@BeforeEach` vs `@AfterEach`) differs intentionally by class setup style:
  - `@BeforeEach` cleanup is used when each test starts from a guaranteed empty state before building its own fixtures.
  - `@AfterEach` cleanup is used when tests rely on shared setup created in `@BeforeEach` and need guaranteed teardown after execution.
  - Some classes use both (`@BeforeEach` for deterministic fixture creation, `@AfterEach` for teardown), which is expected for integration tests touching persistence.

- Error assertions now follow the same principle: validate both classification and message fragment when checking expected failure paths.
  - In particular, confidence validation tests in `GoalConfidenceIntegrationTest` now assert `ValidationError` plus a relevant message fragment, not only the presence of any error.

### Testing stack

- JUnit 5
- Spring Boot Test (`@SpringBootTest`)
- Spring GraphQL Test (`GraphQlTester`, `@AutoConfigureGraphQlTester`)
- AssertJ
- Jakarta Bean Validation (`jakarta.validation`)
- Mockito (for unit/service tests, e.g. `TargetServiceTest`)
- Vitest (frontend tests)

### CI (GitHub Actions) for tests

Workflow file:

- `.github/workflows/ci.yml`

When it runs:

- on every `push`
- on every `pull_request`
- manually via `workflow_dispatch`
- every night by schedule (`0 3 * * *`, UTC)

What it runs:

1. **Frontend tests and build**
   - `npm ci`
   - `npm test`
   - `npm run build`
2. **Backend tests**
   - `cd backend && sh ./mvnw test`
3. **Artifacts**
   - `backend-surefire-reports` (raw Maven test reports)
   - `backend-allure-results` (raw Allure input files)
   - `allure-report` (generated HTML Allure report)

Allure generation:

- Uses `simple-elf/allure-report-action` pinned to commit `53ebb757a2097edc77c53ecef4d454fc2f2f774c` (`v1.13`).
- Backend tests write Allure results to `backend/target/allure-results`.

How to know tests passed even if Allure HTML report is unavailable:

1. Open the workflow run and check job conclusions:
   - `Frontend tests and build` must be **success**
   - `Backend tests` must be **success**
2. Open backend job steps and verify `Run backend tests` is **success**.
3. Download `backend-surefire-reports` artifact and inspect XML results (`failures=\"0\"`, `errors=\"0\"`).

If Allure report job fails but frontend/backend jobs are green, test execution still passed; only report generation failed.

### Where to find the report in GitHub UI

1. Open repository -> **Actions** -> choose the latest **CI** run.
2. Open run page and go to **Artifacts**.
3. Download `allure-report`.
4. Unzip locally and open `index.html` from the extracted report folder.

### Other ways to check test status (without Allure report)

- **PR checks:** in the pull request, check that `Frontend tests and build` and `Backend tests` are green.
- **Workflow jobs:** in Actions run details, confirm `Run frontend tests` and `Run backend tests` steps are green.
- **Surefire artifact:** download `backend-surefire-reports` and verify XML counters (`failures=\"0\"`, `errors=\"0\"`, `skipped` as expected).
- **Raw backend logs:** backend job logs include Maven summary (`Tests run`, `Failures`, `Errors`, `Skipped`).

---

## Documentation

### `docs/` currently contains

- `docs/testing-guide.md`
- `docs/unit-vs-integration-tests.md`
- `docs/flyway-guide.md`
- `docs/graphiql-guide.md`
- `docs/test-coverage-report.md`
- `docs/linting-guide.md`

### `specs/` currently contains

- `specs/mission.md`
- `specs/tech-stack.md`
- `specs/roadmap.md`
- `specs/2026-05-04-stabilize-frontend-mvp/{requirements.md,plan.md,validation.md}`
- `specs/2026-05-06-production-backend-foundation/{requirements.md,plan.md,validation.md}`
- `specs/2026-05-08-backend-test-coverage/{requirements.md,plan.md,validation.md}`
- `specs/2026-05-08-frontend-backend-integration/{requirements.md,plan.md,validation.md}`

Also, GROW source documents are in `grow/`:

- `grow/Coaching for Performance.docx`
- `grow/Coach the Person.docx`
