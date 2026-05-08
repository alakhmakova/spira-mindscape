# Plan: Production Backend Foundation

---

## Phase 2 Overview

This plan implements the production backend foundation as described in `specs/roadmap.md#phase-2-production-backend-foundation`.

**Goal:** Add the production backend foundation without changing the product model.

## 2026-05-07 Task Scope Update

This task narrows the original Phase 2 plan to produce a compiling, runnable backend foundation before adding security and broader production behavior.

What is being done now:

1. Replace the incomplete Gradle setup with Maven because the backend had Gradle metadata but no runnable wrapper, and this task explicitly requested Maven.
2. Add a lightweight Maven wrapper so the backend can build without a machine-level Maven install.
3. Use PostgreSQL through `backend/docker-compose.yml` for local development and validation.
4. Keep authentication and authorization out of this task by request. The backend is single-user/no-auth for now, and GraphQL does not expose register/login/refresh/me operations.
5. Align Java entities, Flyway migrations, and GraphQL schema with the current frontend model in `src/lib/spira/types.ts`.
6. Use concrete tables with lowercase `type` strings and nullable variant fields instead of JPA inheritance, because the previous scaffold mixed single-table entities with joined subtype migrations.
7. Let Spring Boot auto-configure Flyway so migrations run before Hibernate validation.

Deferred but still needed later:

1. Authentication, authorization, user/account schema, and user-scoped data isolation.
2. AI action and chat message persistence.
3. File/object storage; `dataUrl` is preserved for frontend compatibility in this pass.
4. Data import from localStorage, including `contact` to `email` resource migration.
5. Rich GraphQL validation/error mapping and broader CRUD tests.
6. DataLoader/N+1 optimization.
7. CI/CD, coverage gates, and deployment automation.

---

## Group 1: Project Setup and Configuration

1. Create Spring Boot project structure with Maven
2. Configure Java 17 with Spring Boot 3.x
3. Set up PostgreSQL as the database
4. Configure Maven build with necessary plugins:
   - Spring Boot
   - Flyway for database migrations
   - Spring Boot GraphQL starter
   - Lombok
   - JUnit 5 test support
5. Configure CI/CD pipeline structure later

---

## Group 2: Domain Model Implementation

6. Implement core domain entities matching frontend types:
   - `Goal` entity with nested Reality, Options, Resources, and Targets
   - `RealityItem` rows grouped into actions and obstacles
   - `Resource` entity with lowercase type discriminator: note, link, file, email
   - `Option` entity
   - `Target` entity with lowercase type discriminator: numeric, binary, checklist
   - `ChecklistItem` entity
7. Create JPA repositories:
   - `GoalRepository`
   - `RealityRepository`
   - `OptionRepository`
   - `ResourceRepository`
   - `TargetRepository`
   - `ChecklistItemRepository`
8. Implement progress calculation using the same algorithm as frontend.

---

## Group 3: GraphQL API Schema and Resolvers

9. Design GraphQL schema (`schema.graphqls`) around the current frontend model.
10. Implement Spring GraphQL controller methods for:
    - Goal queries and mutations
    - Resource queries and mutations
    - Target queries and mutations
    - Option queries and mutations
    - Reality item CRUD operations
11. Defer DataLoader optimization until the API surface stabilizes.

---

## Group 4: User Authentication and Authorization

Deferred for this task by request.

Later work should implement:

1. User registration/login or another chosen auth flow
2. Password hashing
3. Token/session handling
4. User-scoped data ownership
5. Authorization tests preventing cross-user reads/writes

---

## Group 5: Database Migrations

12. Create initial Flyway migration:
    - Goal table
    - Reality item table
    - Option table
    - Target table with variant fields
    - Checklist item table
    - Resource table with variant fields
13. Keep seed migration intentionally empty while frontend local data remains authoritative.
14. Validate migrations against Docker PostgreSQL.

---

## Group 6: Tests

15. Current task: make `./mvnw test` pass and smoke-test app startup against Docker PostgreSQL.
16. Later work:
    - Domain entity tests
    - Progress calculation tests
    - Repository tests
    - GraphQL happy-path and error-path tests
    - Migration tests from an empty database

---

## Group 7: CI/CD Pipeline

Deferred for this task. Add Maven build/test, Docker-backed integration tests, quality checks, and deployment automation later.

---

## Group 8: Documentation

17. Update this spec with current scope, rationale, and deferred work.
18. Later, create backend README and GraphQL API usage documentation.

---

## Implementation Notes

- The backend remains in `backend/`.
- Use Spring Boot 3.x with Spring Framework 6.
- Use Spring Boot GraphQL annotations for this Maven backend.
- Use PostgreSQL 15+; local development uses Docker Compose with PostgreSQL 16.
- Authentication/authorization is intentionally absent in this pass.
- Follow the frontend model first; introduce richer backend abstractions only when they remove proven complexity.
