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
4. Write the date here: _(last rehearsed: never — pending the secrets being added)_
