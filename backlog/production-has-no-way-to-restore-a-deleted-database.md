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

## 2026-10-05: rehearsed locally, and three defects it found

The whole path — dump, sanity-check, manifest, encrypt, decrypt, restore, compare — was run end to
end against the Docker Postgres (`docs/database-backup-and-restore.md` has the commands). The
restored row counts matched `MANIFEST.txt` table for table, the guard against restoring over a
non-empty database refused as it should (exit 1), and a restore into an empty one returned 0.

**Reading the YAML would not have found any of this:**

| What | Why it mattered |
|---|---|
| The artifact name was `spira-db-${{ github.run_started_at }}` | that is an ISO-8601 timestamp, and `upload-artifact@v4` **rejects a name containing a colon**. The first run would have dumped, checked, encrypted — and then failed on the last step, keeping nothing |
| `MANIFEST.txt` counted **COPY blocks**, which is one per table whatever the data | so "compare the counts with the MANIFEST", the final instruction of both halves, could not be carried out: the dump reported 1 and the restore reported 4,000. It now counts rows per table, straight out of the dump, in the same shape the restore prints |
| `scripts/db-restore.sh` required a local `psql` | the machine most likely to rehearse it develops against Docker and has no client at all, so the half that must work on the worst day could not be run by its owner. `PSQL=...` now takes any client that reads SQL on stdin |

Two smaller things came with them: `BACKUP_PASSPHRASE` makes the decrypt work without a terminal
(gpg otherwise waits on a pinentry that a script has not got), and the queries that are not the
restore itself now read from `/dev/null` — a client that forwards stdin, like `docker exec -i`, was
swallowing the confirmation the script asks the operator to type.

## Resolution

Implemented 2026-09-24 (workflow + scripts + guide) and rehearsed locally 2026-10-05, which is what
turned up the three defects above. **It still cannot run**, and the two things left are the owner's:

1. **The workflow has to reach the default branch.** A `schedule:` trigger only fires from the
   repository's default branch — GitHub answers `workflow db-backup.yml not found on the default
   branch` while it lives in a feature branch, and a scheduled job that does not exist cannot go
   red to tell you so.
2. **The two secrets have to exist**: `PROD_DATABASE_URL` and `BACKUP_PASSPHRASE`. `gh secret list`
   is empty today, so even on the default branch the first run would stop at its own preflight.

Then: run it once by hand, download the artifact, and rehearse the restore into a scratch branch —
at which point the date in the guide stops saying "never".
