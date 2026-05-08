# Flyway Guide for Beginners

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
