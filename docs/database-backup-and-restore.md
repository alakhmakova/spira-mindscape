# Backing up and restoring the database

**Why this exists.** Until 2026-09-24 the project held no backup of its own and had no written way
back from a delete (BUG-092). What prompted it was the local database: a test suite deleted every
goal on the dev account (BUG-091) and the only reason anything came back is that Postgres's
write-ahead log still held the last few megabytes of writes, which had to be decompressed by hand.
That is luck, not a procedure, and production has less of it — Neon's free tier keeps a short
point-in-time window that this project does not control.

There are two independent paths back. Use the provider's first when it is available: it is faster
and loses less.

---

## 1. Neon's own point-in-time restore (first choice, minutes old)

Neon keeps a history window and can create a **branch** at a past moment.

1. Neon console → the project → **Branches** → **New branch** → *Time* → pick a point a minute
   before the damage.
2. The branch has its own connection string. Point a local backend at it
   (`DATABASE_URL=...`, `local` profile) and check the data is the data you want.
3. Either copy the missing rows across, or make the branch primary if everything since the
   incident is expendable.

Its limits, and why it is not enough on its own: the window is hours to days depending on the
plan, it is gone if the Neon project itself is deleted, and it does not survive the account.

---

## 2. The project's own dump (always available, at most a day old)

`.github/workflows/db-backup.yml` runs nightly at 02:10 UTC and on demand
(**Actions → Database backup → Run workflow**). It:

- takes a plain-SQL `pg_dump` of production,
- refuses to keep a dump that does not contain `CREATE TABLE public.goal` or is suspiciously
  short — an empty backup with a green tick is the failure this whole thing exists to prevent,
- writes a `MANIFEST.txt` (when it was taken, how large, which tables it holds),
- encrypts it with **AES-256 through gpg** using `BACKUP_PASSPHRASE`, and
- keeps it as a workflow artifact for **90 days**.

### The two secrets it needs

Settings → Secrets and variables → Actions:

| Secret | What it is |
|---|---|
| `PROD_DATABASE_URL` | the **libpq** form, `postgresql://USER:PASSWORD@HOST/DB?sslmode=require` — not the JDBC form the backend uses |
| `BACKUP_PASSPHRASE` | a long random passphrase. **Without it the dumps cannot be read.** Keep it somewhere that is not this repository and not the same account |

The job fails immediately, naming the missing one, if either is absent.

### Why the dump is encrypted

A workflow artifact is readable by anyone with repository access, and this dump is every user's own
writing — their goals, their CVs, their chat with the coach. See BUG-093 for the wider question of
who can read user data; a backup must not be the easiest answer to it.

---

## Restoring

```bash
# 1. Download the artifact from the workflow run, unzip it.
# 2. Rehearse into a scratch Neon branch FIRST, never straight into production.
scripts/db-restore.sh spira.sql.gpg "postgresql://user:pass@scratch-host/db?sslmode=require"
```

The script decrypts, checks the file really is a Spira dump, refuses a target that already holds
rows unless `--force` is passed, asks you to type the target host back, restores with
`ON_ERROR_STOP` and prints the row counts so they can be compared with `MANIFEST.txt`.

On Windows use Git Bash (`bash scripts/db-restore.sh ...`); it needs `psql` and `gpg` on the path.

**Mind the client version, and know which tool cares.** Production is Neon, on **PostgreSQL 18**,
while the local Docker database is **16** — two majors apart, so a client that is fine for one is
not automatically fine for the other.

- **`pg_dump` refuses outright** to dump a server newer than itself: `aborting because of server
  version mismatch`. That is what broke the very first scheduled backup (2026-10-06), and why the
  workflow now asks the server its major version and installs that client rather than naming one.
- **`psql` only warns**, so restoring with an older client usually works — which is also why it is
  the tool the workflow uses to ask the question in the first place.
- A dump taken from 18 may still use syntax a 16 server rejects, so when rehearsing a restore
  locally, restore into a **matching** major (or accept that `ON_ERROR_STOP` may stop on a
  statement the old server has never heard of).

On a Debian/Ubuntu machine, `/usr/bin/pg_dump` is `pg_wrapper` and it does **not** necessarily run
the newest client installed — call `/usr/lib/postgresql/<major>/bin/pg_dump` if you need to be
sure. On this project's own machine the simplest answer is the one the local section below uses:
run `pg_dump` **inside** the container, where client and server are the same build.

---

## The local development database

The same two commands, without the ceremony:

```bash
docker exec spira-mindscape-postgres pg_dump -U spira -d spira > ~/spira-db-backups/spira-$(date +%Y%m%d-%H%M).sql
docker exec -i spira-mindscape-postgres psql -U spira -d spira < ~/spira-db-backups/<file>.sql
```

Take one before anything that writes in bulk — a migration you are unsure of, a bulk edit, and
above all **the full E2E suite**, which creates and deletes goals on the dev account.

---

## Rehearse it

A restore that has never been run is a document, not a recovery. Twice a year, and after any change
to the schema or the hosting:

1. Run the workflow by hand.
2. Restore that artifact into a scratch branch.
3. Compare the row counts with `MANIFEST.txt`.
4. Write the date here: _(last rehearsed against **production**: never — pending the two secrets;
   rehearsed end to end against the **local** database 2026-10-05, see below)_

### Rehearsing locally, which needs no secrets and no production

The whole path — dump, encrypt, decrypt, restore, compare — runs against the Docker Postgres this
project already uses, and that is where to find out whether the procedure works before the day it
matters. It was run this way on **2026-10-05** and the restored row counts matched `MANIFEST.txt`
table for table.

```bash
# 1. Dump from inside the container, so the client version always matches the server
docker exec spira-mindscape-postgres pg_dump --no-owner --no-privileges   --format=plain -U spira spira > /tmp/spira.sql

# 2. Encrypt exactly as the workflow does
printf '%s' "a-throwaway-passphrase" | gpg --batch --yes --quiet   --passphrase-fd 0 --pinentry-mode loopback   --symmetric --cipher-algo AES256 --output /tmp/spira.sql.gpg /tmp/spira.sql

# 3. A scratch database to restore into
docker exec spira-mindscape-postgres psql -U spira -d postgres -c "CREATE DATABASE spira_restore_test"

# 4. Restore. Two environment variables make it runnable without a terminal prompt:
BACKUP_PASSPHRASE="a-throwaway-passphrase" PSQL="docker exec -i spira-mindscape-postgres psql"   scripts/db-restore.sh /tmp/spira.sql.gpg   "postgresql://spira:spira@localhost:5432/spira_restore_test"
# it asks for the host to confirm: type  localhost:5432

# 5. Clean up — the dump is every user's own writing, in the clear
docker exec spira-mindscape-postgres psql -U spira -d postgres -c "DROP DATABASE spira_restore_test"
rm -f /tmp/spira.sql /tmp/spira.sql.gpg
```

Two things that rehearsal is there to find, and did:

- **`PSQL`** exists because a machine that develops against Docker has no local `psql` at all — so
  the restore script, the half that has to work on the worst day, could not run on the owner's own
  computer. It now takes any client that reads SQL on stdin.
- **`BACKUP_PASSPHRASE`** exists because `gpg` otherwise asks on a terminal, and a rehearsal driven
  from a script has none. Left unset, the prompt is still the default — which keeps the passphrase
  out of shell history for a human doing this by hand.
