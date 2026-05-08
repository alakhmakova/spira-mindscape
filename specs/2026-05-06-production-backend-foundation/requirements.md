# Requirements: Production Backend Foundation

## Scope

Implement the production backend foundation as the data persistence layer for Spira. This phase establishes the technical foundation for future features while preserving the current frontend model.

## 2026-05-07 Task Scope Override

For this task, authentication and authorization are intentionally excluded. The backend should compile and run first as a no-auth, single-user persistence API. User accounts, login, JWT/refresh tokens, and cross-user access checks should be added later with their own schema and tests.

This task uses Maven instead of Gradle and PostgreSQL via Docker Compose. The backend domain, GraphQL schema, and Flyway migrations should match the current frontend model in `src/lib/spira/types.ts` rather than introducing a new product model.

## Included

- Spring Boot backend with GraphQL API
- Maven build and lightweight Maven wrapper
- PostgreSQL database with Docker Compose and Flyway migrations
- No-auth, single-user persistence
- Complete domain model matching frontend types:
  - Goals with Reality, Options, Resources, and Targets
  - Resources: note, link, file, email
  - Targets: numeric, binary, checklist
  - Checklist items with deadline and achieved date
- Backend progress calculation matching frontend behavior
- Input validation through entity constraints and controller checks
- Logging configuration

## Excluded

- Frontend changes; keep local Zustand persistence for now
- Authentication and authorization for this task, including user registration, login, JWT, refresh tokens, password hashing, and user isolation
- AI Actions and Chat Messages storage for this task
- AI orchestration logic
- File storage integration; `dataUrl` remains in the backend shape for frontend compatibility during this pass
- GROW session implementation
- Mini tool implementation
- Notifications system
- Professional-assisted sharing
- DataLoader optimization
- CI/CD pipeline and coverage enforcement
- LocalStorage import/migration utilities

## Decisions

1. **Domain Model Source of Truth**: The frontend `src/lib/spira/types.ts` is the authoritative source for this task.

2. **Data Migration**: Existing frontend localStorage data may use older resource shapes such as `contact`. Backend import/migration utilities are deferred.

3. **Progress Calculation**: Backend must implement the same progress calculation algorithm as the frontend:
   - Numeric target: use frontend start/current/total behavior, including reverse progress and clamping to `[0,1]`
   - Binary target: 1 if done, 0 if not
   - Checklist target: completed items / total items
   - Goal progress: average of all target progress values

4. **User Isolation**: Deferred for this task. The current backend is no-auth/single-user by request. Later auth work must add user-scoped ownership and authorization tests.

5. **GraphQL Implementation**: Use Spring Boot GraphQL annotations for this Maven/Spring Boot 3 backend.

6. **Database**: PostgreSQL through Docker Compose for local validation. H2/test database work is deferred until integration tests are added.

7. **File Upload**: Preserve the frontend `dataUrl` field for now. Actual object storage and metadata-only file handling should be implemented later.

8. **Persistence Shape**: Use concrete tables with lowercase type discriminators and nullable variant fields. Avoid JPA inheritance until there is a strong reason for it.

## Success Criteria

- [ ] Backend builds successfully with `./mvnw test`
- [ ] Docker PostgreSQL starts with `docker compose -f backend/docker-compose.yml up -d postgres`
- [ ] Backend starts against Docker PostgreSQL and Flyway applies migrations before Hibernate validation
- [ ] GraphQL schema is valid with no unmapped fields
- [ ] New goal can be created via GraphQL and persists to database
- [ ] Goal data loaded via GraphQL matches frontend model
- [ ] Deferred work is documented clearly, especially auth/user isolation

## Testing Requirements

### Current Task Checks

- Maven compile/test phase passes
- Docker Compose config is valid
- Docker PostgreSQL starts and becomes healthy
- Backend jar starts against Docker PostgreSQL
- Flyway validates/applies migrations
- Hibernate validates the schema
- Spring GraphQL schema inspection reports no unmapped fields

### Later Unit Tests

- Domain entity creation and validation
- Progress calculation accuracy
- Input validation
- GraphQL controller behavior

### Later Integration Tests

- Repository operations with real database
- GraphQL resolver happy paths
- GraphQL resolver error cases
- Database migration from scratch
- Authentication, authorization, and user data isolation after auth is reintroduced

### Later CI/CD Checks

- Build compiles without errors
- All tests pass
- Code quality metrics meet threshold
- Security scan passes

## Dependencies

- Spring Boot 3.x
- PostgreSQL 15+
- Maven 3.9+
- Spring Boot GraphQL starter
- Flyway for migrations
- JUnit 5 test support
