# Spira Mindscape

  

Spira Mindscape is a goal-planning application with a React/Vite frontend and a Spring Boot GraphQL backend. The backend persists goals, reality items, options, resources, targets, and checklist items in PostgreSQL.

  

## Current Status

  

- Frontend: React 19, Vite 7, TanStack Router, Zustand UI state, Tailwind CSS 4.

- Backend: Spring Boot 3, Maven, Spring GraphQL, JPA, Flyway, PostgreSQL.

- Frontend/backend connection: the frontend now loads and writes Spira goal data through the backend GraphQL API.

- Authentication/authorization: intentionally not implemented yet.

- AI chat persistence: intentionally not implemented yet; AI chat remains local UI state.

  

## Prerequisites

  

- Node.js 20+ and npm

- Java 17+

- Docker Desktop for PostgreSQL

  

On Windows, run commands in PowerShell.

  

## Project Structure

  

```text

.

├── index.html

├── public/

│   └── favicon.svg

├── src/

│   ├── main.tsx

│   ├── router.tsx

│   ├── routes/

│   ├── components/

│   └── lib/spira/

│       ├── api.ts

│       ├── progress.ts

│       ├── store.ts

│       └── types.ts

├── backend/

│   ├── docker-compose.yml

│   ├── pom.xml

│   └── src/main/

│       ├── java/

│       └── resources/

│           ├── db/migration/

│           └── graphql/schema.graphqls

└── specs/

```

  

## How The Frontend Talks To The Backend

  

The frontend uses `src/lib/spira/api.ts` as a small typed GraphQL client. It sends GraphQL requests to:

  

```text

/graphql

```

  

During local development, Vite proxies `/graphql` to the backend at:

  

```text

http://localhost:8080/graphql

```

  

The proxy is configured in `vite.config.ts`.

  

For environments where the frontend is served separately from the backend, set:

  

```powershell

$env:VITE_GRAPHQL_ENDPOINT = "http://localhost:8080/graphql"

```

  

The Zustand store in `src/lib/spira/store.ts` holds the UI copy of backend data. Goals are loaded from the backend when the app shell mounts. Mutations are optimistic where the UI needs immediate feedback, then written through to GraphQL.

  

## Running The Full Local Stack

  

Use three terminals.

  

### 1. Start PostgreSQL

  

```powershell

cd backend

docker compose up -d postgres

docker compose ps

```

  

Expected: `spira-mindscape-postgres` is healthy.

  

If the backend later fails with `Connection refused` on `localhost:5432`, PostgreSQL is not running. Start it again with the same `docker compose up -d postgres` command above.

  

### 2. Start The Backend

  

```powershell

cd backend

.\mvnw.cmd spring-boot:run

```

  

- GraphQL endpoint: `http://localhost:8080/graphql`

- GraphiQL UI: `http://localhost:8080/graphiql.html`

  

### 3. Start The Frontend

  

```powershell

npm install

npm run dev

```

  

- Frontend: `http://localhost:5173`

- Frontend GraphQL calls are proxied to `http://localhost:8080/graphql`.

  

## Stopping The Local Stack

  

If the backend and frontend terminals are still open, press `Ctrl+C` in each terminal.

  

If the terminals were closed but the servers are still running, find the processes by port and stop them:

  

```powershell

# Backend on port 8080
netstat -ano | Select-String ":8080"
Stop-Process -Id <PID_FROM_LISTENING_LINE> -Force

# Frontend on port 5173
netstat -ano | Select-String ":5173"
Stop-Process -Id <PID_FROM_LISTENING_LINE> -Force
Stop-Process -Id 26928 -Force

```

  

If Vite started on a different frontend port, such as `5174`, use that port instead of `5173`.

  

PostgreSQL keeps running in Docker until you stop it:

  

```powershell

cd backend
docker compose down

```

  

## Useful Commands

  

```powershell

# Frontend

npm run dev

npm run build

npm test

npm run lint

npm run format

  

# Backend

cd backend

.\mvnw.cmd spring-boot:run

.\mvnw.cmd test

.\mvnw.cmd package

  

# Database

cd backend

docker compose up -d postgres

docker compose down

docker compose down -v

```

  

## GraphQL Smoke Test

  

Open `http://localhost:8080/graphiql.html` and run:

  

```graphql

mutation {

  createGoal(input: { title: "Test Goal", description: "Test", confidence: 7 }) {

    id

    title

    createdAt

  }

}

```

  

Then open `http://localhost:5173` and confirm the goal appears in the frontend.

  

## Known Limitations

  

- No authentication or user isolation yet.

- AI chat remains local only.

- Option drag-and-drop reorder is local only until the backend adds persistent ordering.

- Some nullable field clearing is limited by the current backend update semantics.

- Frontend bundle size can be improved with deeper code splitting.

  

## Specs

  

- Backend foundation: `specs/2026-05-06-production-backend-foundation/`

- Frontend/backend integration: `specs/2026-05-08-frontend-backend-integration/`
