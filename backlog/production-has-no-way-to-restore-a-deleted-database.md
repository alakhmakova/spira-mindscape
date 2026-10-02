# BUG-092 — Production has no way to restore a deleted database

**Status:** 🔧 In progress
**Area:** Backend / Operations
**Severity:** Critical — one accidental or hostile delete ends the product's data

## Summary

There is **no backup of the production database and no restore procedure**. If a user's goals — or
every user's goals — are deleted, whether by an accident, a bug, a bad migration or somebody with
the connection string, nothing brings them back. `deleteGoal` is a hard delete and the rows cascade
to resources, targets, options, reality items and chat transcripts.

This is not hypothetical. On 2026-09-24 exactly that happened to the **local** database (BUG-091):
a test suite deleted every goal on the dev account, and the only reason anything came back is that
Postgres's write-ahead log happened to still hold the last ~11 MB of writes, which had to be
decompressed by hand. Production has neither that luck nor that window — Neon's free tier keeps a
short point-in-time window and nothing is exported anywhere the project controls.

The owner asked for this to be implemented, not merely recorded (2026-09-24).

## Steps to reproduce

1. Delete a goal in production (or run any statement that removes rows).
2. There is no dump, no export, no snapshot under the project's control to restore from — only
   whatever window the hosting provider happens to keep.

## Root cause

The database is **Neon** (`docs/deploy-gcp-cloud-run.md` → "Create the Neon database"). The project
has never taken a backup of its own: no scheduled `pg_dump`, no export to storage the project
owns, and no written restore path. The deploy guide covers creating the database and nothing about
losing it.

## Fix

Two halves, because a backup nobody has ever restored is not a backup:

1. **A scheduled dump the project owns.** `.github/workflows/db-backup.yml` runs `pg_dump` against
   production nightly (and on demand), encrypts the dump with `age`/GPG using a repo secret, and
   keeps it as a workflow artifact with a long retention. It fails loudly — a backup job that goes
   quietly red is the same as no backup.
2. **A restore that has been rehearsed.** `scripts/db-restore.sh` takes a dump and a target URL and
   puts it back, and `docs/database-backup-and-restore.md` writes down the whole path, including
   Neon's own branch/point-in-time restore, which is faster when it is available.

## How to verify fixed

1. Run the workflow by hand (`workflow_dispatch`) and confirm an artifact appears.
2. Restore that artifact into a scratch Neon branch and check the row counts match production's.
3. Delete something in the scratch branch and restore over it — the rehearsal is the test.

## Resolution

Implemented 2026-09-24 (workflow + scripts + guide). **The owner must still add the repository
secrets** (`PROD_DATABASE_URL`, `BACKUP_PASSPHRASE`) and run the workflow once by hand; until that
is done the job cannot run and the bug is not closed.
