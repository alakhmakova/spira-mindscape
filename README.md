# Spira Mindscape

Spira Mindscape is a goal-planning application with a React/Vite frontend and a Spring Boot GraphQL backend. The frontend is a standard Vite SPA (no SSR, no Cloudflare Workers). The backend exposes a GraphQL API backed by PostgreSQL.

## Current Status

- Frontend: React 19, Vite 7, TanStack Router, Zustand persistence, Tailwind CSS 4.
- Backend: Spring Boot 3, Maven, Spring GraphQL, JPA, Flyway, PostgreSQL.
- Authentication/authorization: intentionally not implemented yet.
- Frontend/backend connection: the backend GraphQL API is available at `http://localhost:8080/graphql`, but the frontend is not wired to call it yet.

## Prerequisites

- Node.js 20+ and npm
- Java 17+
- Docker Desktop (for PostgreSQL)

On Windows, run commands in PowerShell.

## Project Structure

```text
.
├── index.html                   # SPA entry point
├── src/                         # Frontend application
│   ├── main.tsx                 # React root mount
│   ├── router.tsx               # TanStack Router setup
│   ├── routes/                  # File-based routes
│   ├── components/              # UI components
│   ├── lib/spira/types.ts       # Domain model
│   └── lib/spira/store.ts       # Zustand store (localStorage)
├── backend/                     # Spring Boot backend
│   ├── docker-compose.yml       # Local PostgreSQL
│   ├── src/main/java/...        # JPA entities, GraphQL resolvers
│   └── src/main/resources/
│       ├── db/migration/        # Flyway migrations
│       └── graphql/             # GraphQL schema
└── specs/                       # Implementation specs
```

## Running The Full Local Stack

Use three terminals.

### 1. Start PostgreSQL

```powershell
cd backend
docker compose up -d postgres
docker compose ps
```

Expected: `spira-mindscape-postgres` is `healthy`.

### 2. Start The Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

- GraphQL endpoint: `http://localhost:8080/graphql`
- GraphiQL UI: `http://localhost:8080/graphiql`

### 3. Start The Frontend

```powershell
npm install   # first time only
npm run dev
```

- Frontend: `http://localhost:5173`
- GraphQL requests from the frontend are proxied to `http://localhost:8080/graphql`

## Stopping

- Frontend: `Ctrl+C` in the `npm run dev` terminal
- Backend: `Ctrl+C` in the `mvnw` terminal
- PostgreSQL (keep data): `cd backend && docker compose down`
- PostgreSQL (delete data): `cd backend && docker compose down -v`

## Useful Commands

```powershell
# Frontend
npm run dev       # Start dev server on http://localhost:5173
npm run build     # Production build
npm run lint      # ESLint
npm run format    # Prettier

# Backend
cd backend
.\mvnw.cmd spring-boot:run   # Run from source
.\mvnw.cmd test              # Run tests
.\mvnw.cmd package           # Build jar

# Docker
cd backend
docker compose up -d postgres
docker compose down
docker compose down -v
```

## Backend Configuration

`backend/src/main/resources/application.properties`:

```properties
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/spira
spring.datasource.username=spira
spring.datasource.password=spira
spring.graphql.path=/graphql
spring.graphql.graphiql.enabled=true
```

Override with environment variables:

```powershell
$env:PORT = "8080"
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/spira"
$env:DATABASE_USERNAME = "spira"
$env:DATABASE_PASSWORD = "spira"
```

## GraphQL Smoke Test

Open `http://localhost:8080/graphiql` and run:

```graphql
mutation {
  createGoal(input: { title: "Test Goal", description: "Test", confidence: 7 }) {
    id
    title
    createdAt
  }
}
```

## What Is Not Implemented Yet

- Frontend-to-backend GraphQL integration
- Authentication and authorization
- Multi-user support
- CI/CD

See `specs/2026-05-06-production-backend-foundation/` for details.
