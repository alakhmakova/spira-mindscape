# Apollo Kotlin — the Android GraphQL client (guide)

## What Apollo is, and why we use it

The backend exposes a **GraphQL** API (`/graphql`). The web app talks to it with a hand-written
client (`src/lib/spira/api.ts`). The **Android** app instead uses **Apollo Kotlin** — a GraphQL
client that **generates type-safe Kotlin code from the schema and your queries**, so you don't
hand-write request/response parsing or DTOs.

You write:
- the **schema** (a copy of the backend's), and
- your **operations** (`.graphql` queries/mutations),

and Apollo's Gradle plugin generates, at build time, Kotlin classes like `GetGoalsQuery` with
typed `Data`/`Goal`/`Target` models. If a query asks for a field that doesn't exist, or you read
a field you didn't request, it's a **compile error** — not a runtime surprise.

Why this over a hand-rolled client (like the web's): on Android, typed models + null-safety +
generated adapters remove a whole class of parsing bugs, and the Apollo Gradle plugin, OkHttp
integration, and coroutine `suspend` calls are the standard, well-supported path.

## Where things live (`android/app/`)

| Path | Role |
|---|---|
| `src/main/graphql/schema.graphqls` | The GraphQL schema — a **copy** of `backend/src/main/resources/graphql/schema.graphqls` (the single source of truth). |
| `src/main/graphql/*.graphql` | Operations: `GetGoals`, `GetGoal`, `UpdateTarget` (mirroring the fields the web uses). |
| `build.gradle.kts` → `apollo { service("spira") { packageName = "…graphql" } }` | Codegen config. |
| generated `…/graphql/**` | Apollo-generated Kotlin (do **not** edit or hand-test — see `docs/reading-coverage-reports.md`). |
| `data/net/Network.kt` | Builds the `ApolloClient` over the shared OkHttp client (cookie jar + CSRF). |

## The shared contract

The **schema is the contract** between web and app. Both target the same backend; the app's
`schema.graphqls` is a copy of the backend's. When the backend schema changes, update this copy
and the operations, and Apollo regenerates the models. (Keeping a copy is simpler than reaching
across modules; the trade-off is remembering to sync it — noted at the top of the file.)

## Adding or changing an operation

1. Edit the backend schema if the API itself changed; copy it into
   `android/app/src/main/graphql/schema.graphqls`.
2. Add/edit a `.graphql` file under `src/main/graphql/` — e.g.:
   ```graphql
   query GetGoals {
     goals { id title progress confidence deadline }
   }
   ```
3. Build (`.\gradlew.bat :app:assembleDebug` or Android Studio sync). Apollo generates
   `GetGoalsQuery` in `com.spiramindscape.android.graphql`.
4. Call it (suspending):
   ```kotlin
   val response = Network.apollo.query(GetGoalsQuery()).execute()
   val goals = response.data?.goals.orEmpty()
   ```
   Mutations use `Network.apollo.mutation(UpdateTargetMutation(id, input)).execute()`.

## How the client is wired

`Network.apollo` is one `ApolloClient` built on the **shared OkHttp client**, so it reuses the
same cookie jar (the `SESSION` cookie) and the CSRF interceptor. That means an Apollo call is
authenticated exactly like the REST auth calls — the app is signed in once and every GraphQL
request carries the session automatically.

> Note: GraphQL requests (queries **and** mutations) are HTTP **POST**s, which the backend
> CSRF-protects. The `CsrfInterceptor` adds `X-XSRF-TOKEN` from the cookie jar, so this is handled
> centrally — see `docs/security-model.md`.

## Testing

- Generated Apollo code is **not** something we test or count for coverage (it's generated;
  excluded from the curated JaCoCo report).
- Test **our** code that *uses* Apollo (mappers, view models) with fakes, and cover full data
  journeys with **Maestro** (`docs/maestro-e2e-guide.md`).

Official docs: <https://www.apollographql.com/docs/kotlin>
