# Validation: Production Backend Foundation

## Automated Checks

### Build and Compile

- [ ] `./mvnw test` exits with code 0
- [ ] No compiler errors
- [ ] TypeScript types, if generated from GraphQL later, match schema

### Code Quality

- [ ] Unit tests pass when added
- [ ] Integration tests pass when added
- [ ] Code coverage target is defined when service tests are added
- [ ] Checkstyle/SpotBugs/PMD can be added later if desired

### Database

- [ ] Docker PostgreSQL starts with `docker compose -f backend/docker-compose.yml up -d postgres`
- [ ] Flyway migration runs without errors
- [ ] Migration version is up to date
- [ ] Backend starts against Docker PostgreSQL and logs that Flyway validated/applied migrations before Hibernate validation
- [ ] Hibernate schema validation passes

### Security

- [ ] Authentication/authorization is intentionally absent in this task
- [ ] No hardcoded secrets or API keys are introduced
- [ ] Later auth work must add password hashing, token/session expiration, and user isolation checks
- [ ] SQL injection prevention through JPA/repository APIs

---

## Automated Test Checks

### GraphQL Smoke Queries

Run after starting Docker PostgreSQL and the backend:

```graphql
mutation {
  createGoal(input: {title: "Test Goal", description: "Test", confidence: 7}) {
    id
    title
    createdAt
  }
}
```

```graphql
query {
  goals {
    id
    title
    progress
    reality {
      actions { id text }
      obstacles { id text }
    }
    targets {
      id
      type
      progress
    }
  }
}
```

- [ ] Create Goal mutation works
- [ ] Goals query returns no-auth single-user data
- [ ] Update Goal mutation works
- [ ] Delete Goal mutation works
- [ ] Resources can be added/updated/deleted
- [ ] Targets can be added/updated/deleted
- [ ] Options can be added/updated/deleted
- [ ] Reality actions/obstacles can be modified

### Authentication And Authorization Tests

Deferred for this task by request. Add these when auth is reintroduced:

- [ ] User can register/login or otherwise establish an authenticated session
- [ ] Invalid credentials fail safely
- [ ] User A cannot read or modify User B's goals/resources/targets
- [ ] Each user has independent data

---

## Search Checks

### Backend Code

Run in backend directory:

```bash
rg -n 'Optional.get\(' src/main/java --glob "*.java"
rg -n 'throw new' src/main/java --glob "*.java"
rg -n 'security|Jwt|register|login|refreshToken|PasswordEncoder' src/main/java src/main/resources/graphql
```

Expected:

- No direct `Optional.get()` without an explicit presence check
- Proper exception handling for business rules
- No auth/security classes or GraphQL auth operations in this no-auth task
- No SQL queries in loops beyond known deferred N+1 cleanup

### Database

Run in PostgreSQL:

```sql
SELECT COUNT(*) FROM resource WHERE goal_id NOT IN (SELECT id FROM goal);
SELECT COUNT(*) FROM target WHERE goal_id NOT IN (SELECT id FROM goal);
SELECT COUNT(*) FROM checklist_item WHERE target_id NOT IN (SELECT id FROM target);
SELECT COUNT(*) FROM reality_item WHERE goal_id NOT IN (SELECT id FROM goal);
```

Each query should return 0.

---

## Manual Checks

### API Functionality

1. Start database: `docker compose -f backend/docker-compose.yml up -d postgres`
2. Start backend server: `./mvnw spring-boot:run`
3. Run GraphQL queries in GraphiQL at `/graphiql`
4. Verify response format matches frontend expectations

### Goal Operations

- [ ] Create new goal with title and description
- [ ] Goal appears in `goals` query
- [ ] Update goal title and description
- [ ] Confidence update works
- [ ] Deadline can be set
- [ ] Achieved date can be set
- [ ] Delete goal removes nested data

### Reality Operations

- [ ] Add action to reality
- [ ] Add obstacle to reality
- [ ] Update action text
- [ ] Update obstacle text
- [ ] Remove action
- [ ] Remove obstacle

### Resource Operations

- [ ] Add note resource
- [ ] Add link resource
- [ ] Add file resource with `dataUrl`
- [ ] Add email resource
- [ ] Update resource
- [ ] Delete resource
- [ ] Resource type is stored as lowercase frontend value

### Target Operations

- [ ] Create numeric target
- [ ] Create binary target
- [ ] Create checklist target
- [ ] Update target progress fields
- [ ] Mark checklist item done
- [ ] Remove target

### Progress Calculation

- [ ] Numeric target progress matches frontend calculation
- [ ] Binary target progress is 0 or 1
- [ ] Checklist progress = completed / total
- [ ] Goal progress = average of target progress

---

## Definition of Done

- [ ] Backend builds and tests pass
- [ ] Docker PostgreSQL local path is configured
- [ ] Backend starts against Docker PostgreSQL
- [ ] GraphQL schema maps cleanly
- [ ] Database migrations are in place
- [ ] Authentication and authorization are explicitly documented as deferred
- [ ] Deferred work is documented
