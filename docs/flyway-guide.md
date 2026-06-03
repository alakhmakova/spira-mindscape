# Flyway Guide for Beginners

> ## ⚠️ Quick fix: "Found more than one migration with version N" (after switching branches)
>
> ```
> Caused by: org.flywaydb.core.api.FlywayException: Found more than one migration with version 7
> Offenders:
>  ...
> ```
>
> **What it means:** two migration files share the same version number (e.g. two `V7__…`).
> Almost always this is **not** a problem in your source files — it's **stale compiled
> migrations left in the build output from another branch.**
>
> **Why it happens on branch switch:** Flyway scans the *classpath*, i.e.
> `backend/target/classes/db/migration/` — not your `src/` folder. When you switch
> branches, Maven does **not** delete files that no longer exist on the new branch.
> So if branch A had `V7__ai_schema.sql` and branch B has `V7__app_user.sql`, after
> switching to B the `target/classes` folder still holds A's `V7__ai_schema.sql`
> **plus** B's `V7__app_user.sql` → two V7 files → Flyway refuses to start.
>
> **The fix — wipe the stale build output and rebuild:**
>
> ```powershell
> # Windows (from the backend/ folder)
> cd backend
> .\mvnw.cmd clean
> .\mvnw.cmd spring-boot:run
> ```
>
> ```bash
> # macOS / Linux
> cd backend
> ./mvnw clean
> ./mvnw spring-boot:run
> ```
>
> `clean` deletes the whole `target/` directory, so the next build copies **only** the
> migrations that exist on the current branch. Re-run the app and the error is gone.
>
> **How to confirm the cause yourself** (the source is fine, the build output is dirty):
>
> ```powershell
> # current branch's real migrations
> ls backend\src\main\resources\db\migration\
> # what Flyway actually sees (the offenders live here)
> ls backend\target\classes\db\migration\
> ```
>
> If `target\classes\…` lists more `V7__`/`V8__` files than `src\…`, that's the leftover
> from the other branch. `mvnw clean` fixes it. (This is purely a local build-cache issue —
> your committed migrations are untouched.)

---

> ## ⚠️ Quick fix: "Migration checksum mismatch for migration version N" (after switching branches)
>
> ```
> Caused by: org.flywaydb.core.api.exception.FlywayValidateException: Validate failed:
> Migration checksum mismatch for migration version 7
>  -> Applied to database : 1848789104
>  -> Resolved locally    : -640535428
> Either revert the changes to the migration, or run repair to update the schema history.
> ```
>
> **What it means:** the database already ran a migration under version 7, but the
> `V7__…` file on the **current branch is different** from the one that was applied.
> Flyway stores a checksum of every applied migration in its `flyway_schema_history`
> table and refuses to start when the local file no longer matches.
>
> **Why it happens on branch switch:** two branches reused the **same version number for
> different migrations**. Example in this project: `feature/ai` has `V7__ai_schema.sql`
> + `V8__resource_label_length_200.sql`, while `feature/e2e-and-auth` has
> `V7__app_user.sql` + `V8__goal_owner.sql`. If you ran the app on `feature/ai`, the DB
> recorded *its* V7/V8. Switch to the auth branch and its V7/V8 files don't match what's
> recorded → checksum mismatch.
>
> **⛔ Do NOT run `flyway repair` here.** Flyway's message suggests it, but repair only
> rewrites the stored checksums to match your local files — it does **not** create the
> tables those files describe. You'd end up with a history that claims `app_user`/`goal_owner`
> ran while the database physically still holds the AI schema. That is a silent, corrupt
> mismatch. `repair` is the right tool only when *you legitimately edited an existing
> migration file* — never for "a different branch reused the same version number".
>
> **The fix (local dev) — reset the database so migrations re-apply from scratch.**
> Safe here because the local DB is throwaway: the `V2` seed is empty, so the only thing
> lost is test data you created by hand.
>
> ```powershell
> # Windows — full reset of the Docker volume (from the backend/ folder)
> cd backend
> docker compose down -v
> docker compose up -d postgres
> .\mvnw.cmd spring-boot:run     # Flyway now applies V1..VN fresh from THIS branch
> ```
>
> Faster alternative that keeps the container running (same data loss):
>
> ```powershell
> docker compose exec postgres psql -U spira -d spira -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
> .\mvnw.cmd spring-boot:run
> ```
>
> **Inspect what the DB actually recorded** (to confirm the cause):
>
> ```powershell
> docker compose exec postgres psql -U spira -d spira -c "SELECT version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;"
> ```
>
> If the `description` for V7/V8 names the *other* branch's migration (e.g. "ai schema"
> while you're on the auth branch), that's the mismatch.
>
> **Stop it from recurring:** don't let two branches use the same version number for
> different changes. Options: renumber one branch's migrations so they never collide,
> use a separate database per branch (switch `DATABASE_URL` with the branch), or merge
> the branches into one line so the migrations become a single ordered sequence.

---

Flyway is a database migration tool. It tracks and applies changes to your database schema in a
controlled, versioned way — so every developer, every test environment, and every production
server always has exactly the same database structure.

---

## The problem Flyway solves

Imagine you add a new column to a table. You write the `ALTER TABLE` SQL and run it on your
laptop. Now your teammate pulls the code — their database doesn't have the column. The app
crashes. Someone deploys to production and forgets to run the script. Things break in different
ways in different places.

Flyway solves this by treating database changes the same way Git treats code changes: every
change is a versioned file that is applied exactly once, in order, automatically.

---

## How it works

Flyway looks for SQL files in a specific folder:

```
backend/src/main/resources/db/migration/
```

Each file follows a strict naming convention:

```
V{version}__{description}.sql
```

Examples:

```
V1__init_database.sql
V2__seed_data.sql
V3__timestamps_to_timestamptz.sql
```

Rules:
- `V` is always uppercase
- The version number must be unique and increasing
- There are **two underscores** between the version and the description
- The description uses underscores instead of spaces

When the application starts, Flyway:
1. Connects to the database
2. Checks a table called `flyway_schema_history` (Flyway creates this automatically)
3. Compares which migrations have already been applied
4. Runs any new migrations in version order
5. Records each applied migration in `flyway_schema_history`

---

## Flyway in this project

Flyway is already configured and runs automatically on every application start. You do not
need to run any separate commands.

Current migrations:

| File | What it does |
|---|---|
| `V1__init_database.sql` | Creates all tables, indexes, constraints, triggers |
| `V2__seed_data.sql` | Inserts initial test data |
| `V3__timestamps_to_timestamptz.sql` | Converts deadline/achieved_at from VARCHAR to TIMESTAMPTZ |

---

## How to add a new migration

When you need to change the database schema — add a column, create a table, add an index — you
create a new migration file. Never edit an existing migration file that has already been applied.

**Step 1.** Find the current highest version number in the migration folder.

**Step 2.** Create a new file with the next version number:

```
V4__add_priority_to_goal.sql
```

**Step 3.** Write your SQL:

```sql
ALTER TABLE goal ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;
```

**Step 4.** Restart the backend. Flyway will detect the new file and apply it automatically.

You will see this in the logs:

```
Flyway: Migrating schema "public" to version 4 - add priority to goal
Flyway: Successfully applied 1 migration to schema "public"
```

---

## Flyway configuration in this project

Flyway is configured in `application.properties`:

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

`spring.jpa.hibernate.ddl-auto=validate` means Hibernate will **validate** that the entity
classes match the database schema on startup — but it will never create or alter tables itself.
Flyway owns the schema; Hibernate only checks it.

---

## The flyway_schema_history table

Flyway creates this table automatically in your database. You can inspect it with any database
client (e.g. psql, TablePlus, DBeaver):

```sql
SELECT * FROM flyway_schema_history;
```

Example output:

```
version | description                      | state   | installed_on
--------|----------------------------------|---------|-------------
1       | init database                    | Success | 2026-05-01
2       | seed data                        | Success | 2026-05-01
3       | timestamps to timestamptz        | Success | 2026-05-07
```

---

## What NOT to do

**Never edit a migration file that has already been applied.**

If `V2__seed_data.sql` has already run and you change it, Flyway will detect a checksum mismatch
on the next startup and refuse to start the application:

```
FlywayException: Validate failed: Migration checksum mismatch for migration version 2
```

If you need to change something that a previous migration did — always create a new migration
that undoes or adjusts it.

**Never delete a migration file that has already been applied.**

Same problem — Flyway will fail on startup because it expects the file to still exist.

---

## Resetting the database locally (when needed)

During early development it is sometimes useful to wipe the database and start fresh. This will
delete all data and let Flyway re-apply all migrations from scratch:

```powershell
cd backend
docker compose down -v        # deletes the Docker volume (all data)
docker compose up -d postgres # starts a fresh empty database
.\mvnw.cmd spring-boot:run    # Flyway applies all migrations from V1
```

Only do this in local development. Never on a production database.

---

## Comparison with the alternative (no Flyway)

| Without Flyway | With Flyway |
|---|---|
| You run SQL scripts manually | Scripts run automatically on startup |
| Easy to forget a script on a server | Every environment gets every migration |
| No record of what ran | `flyway_schema_history` tracks everything |
| Teammates sync schemas by hand | Pull the code and restart — schema is synced |
| Hard to know what changed when | Version numbers and descriptions show history |

---

## Quick reference

| Task | How |
|---|---|
| Add a schema change | Create `V{next}__description.sql` in `db/migration/` |
| Apply migrations | Restart the backend — Flyway runs automatically |
| Check what ran | `SELECT * FROM flyway_schema_history;` in psql or a DB client |
| Reset local database | `docker compose down -v` then restart |
| See Flyway logs | Watch the backend startup logs for `Flyway:` lines |
