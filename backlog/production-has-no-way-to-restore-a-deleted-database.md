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

## 2026-10-06: it ran for the first time, and failed in 22 seconds

Both of the owner's two items were done — the workflow reached `main` with PR #8 and both secrets
exist — so the schedule fired at 08:53 UTC and the job went red on its fourth step:

```
pg_dump: error: aborting because of server version mismatch
pg_dump: detail: server version: 18.6 (4e955f5); pg_dump version: 16.15 (Ubuntu 16.15-1.pgdg24.04+2)
```

Two separate faults, and the second is the one worth remembering:

1. **The client version was named, not derived.** The step was called "Install the Postgres client
   matching the server" and its comment claimed the versions "can never drift apart" — then it
   installed `postgresql-client-17` against a Neon server on **18**. A hard-coded number cannot
   match anything; it can only be right for a while. The step now asks the server
   (`SELECT current_setting('server_version_num')::int / 10000`, through `psql`, which tolerates a
   mismatch precisely where `pg_dump` does not) and installs that major.
2. **The installed client was not the one that ran.** `postgresql-client-17` installed cleanly —
   the step was green — and `pg_dump` still executed **16.15**. The log does not say why, and the
   obvious explanation is wrong: the first guess was that Debian's `pg_wrapper` simply ignores a
   newer client, but a clean `ubuntu:24.04` was checked (2026-10-06) and it does not — install
   client 18 beside the image's 16 there and `/usr/bin/pg_dump` reports **18.6**. So something
   particular to the runner image, most likely its own PGDG client 16 sitting earlier on `PATH`,
   beats the newer package. The fix does not need the answer: the workflow prepends
   `/usr/lib/postgresql/<major>/bin` through `GITHUB_PATH`, which goes ahead of anything the image
   set up, asserts the binary exists in the install step, and prints `command -v pg_dump` and
   `pg_dump --version` above the dump — so the next wrong binary is named in the log instead of
   surfacing three steps later as a version error.

**Why the local rehearsal could not have caught either.** It ran against the Docker Postgres, which
is **16.14**, with the container's own client — the one configuration where the versions are
guaranteed to agree. Production is two majors ahead. The rehearsal was still worth its three
defects; this is simply the class it could not reach, and the lesson is that a backup is only
proven by a real scheduled run against the real server.

Still the owner's to do, unchanged: once this reaches `main`, run it by hand, download the
artifact, and rehearse the restore into a scratch Neon branch.
