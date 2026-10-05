#!/usr/bin/env bash
#
# Put a backup back (BUG-092). The other half of `.github/workflows/db-backup.yml` — a backup
# nobody has ever restored is not a backup, so this is meant to be *rehearsed* against a scratch
# database, not read for the first time on the day it is needed.
#
#   scripts/db-restore.sh spira.sql.gpg "postgresql://user:pass@host/db?sslmode=require"
#
# It refuses a target that already holds data unless --force is given, because the usual mistake
# is pointing a restore at production while meaning to point it at a scratch branch.
set -euo pipefail

DUMP=${1:-}
TARGET=${2:-}
FORCE=${3:-}

if [ -z "$DUMP" ] || [ -z "$TARGET" ]; then
  cat >&2 <<'USAGE'
usage: scripts/db-restore.sh <dump.sql[.gpg]> <target-database-url> [--force]

  dump                 the file from the "Database backup" workflow's artifact
  target-database-url  libpq form: postgresql://USER:PASS@HOST/DB?sslmode=require
  --force              restore even though the target already has rows

Full procedure, including Neon's own point-in-time restore: docs/database-backup-and-restore.md
USAGE
  exit 2
fi

[ -f "$DUMP" ] || { echo "no such dump: $DUMP" >&2; exit 1; }
# The client is whatever can reach the target. A machine that develops against the Docker
# Postgres has no local `psql` at all — which would have made this script unrunnable on the one
# machine most likely to rehearse it — so the command is overridable:
#
#   PSQL="docker exec -i spira-mindscape-postgres psql" scripts/db-restore.sh dump.sql.gpg <url>
#
# Anything that takes a connection string and reads SQL on stdin will do.
PSQL=${PSQL:-psql}
if [ "$PSQL" = "psql" ] && ! command -v psql >/dev/null; then
  cat >&2 <<'NOPSQL'
psql is not installed.

Either install the PostgreSQL client, or point this script at one that is already running:

  PSQL="docker exec -i spira-mindscape-postgres psql" scripts/db-restore.sh <dump> <url>

(the target URL is then resolved from inside that container)
NOPSQL
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

plain="$work/dump.sql"
case "$DUMP" in
  *.gpg)
    command -v gpg >/dev/null || { echo "gpg is not installed" >&2; exit 1; }
    echo "Decrypting $DUMP (the passphrase is BACKUP_PASSPHRASE)..."
    # A person at a terminal gets gpg's own prompt, which keeps the passphrase off the command
    # line and out of the shell history. Setting BACKUP_PASSPHRASE takes the prompt away — which
    # is what a rehearsal, or a restore from a script, needs: gpg has no terminal to ask on there
    # and would fail on pinentry instead.
    if [ -n "${BACKUP_PASSPHRASE:-}" ]; then
      printf '%s' "$BACKUP_PASSPHRASE" | gpg --batch --yes --quiet         --passphrase-fd 0 --pinentry-mode loopback         --decrypt --output "$plain" "$DUMP"
    else
      gpg --quiet --decrypt --output "$plain" "$DUMP"
    fi
    ;;
  *) cp "$DUMP" "$plain" ;;
esac

# A dump that is not this application's is the one thing worse than no dump.
grep -q "CREATE TABLE public.goal" "$plain" || {
  echo "This file does not look like a Spira dump — stopping." >&2
  exit 1
}

echo
echo "Dump:   $DUMP  ($(wc -l < "$plain") lines)"
echo "Target: ${TARGET%%\?*}"

# ANALYZE first: `n_live_tup` comes from the statistics collector, and on a database that has
# just been created — or just been restored — it is stale or zero, which would read as "empty"
# and wave the restore through over data that is really there.
$PSQL "$TARGET" -qc "ANALYZE" </dev/null >/dev/null 2>&1 || true
existing=$($PSQL "$TARGET" -tAc \
  "select coalesce(sum(n_live_tup), 0) from pg_stat_user_tables" </dev/null 2>/dev/null || echo 0)
existing=$(printf '%s' "$existing" | tr -d '[:space:]')
existing=${existing:-0}
echo "Target currently holds about $existing row(s)."

if [ "$existing" -gt 0 ] && [ "$FORCE" != "--force" ]; then
  cat >&2 <<EOF

Refusing: the target is not empty, and restoring over it drops what is there.
Restore into a scratch database or branch first, check it, and only then decide.
Pass --force if this really is the database you mean to overwrite.
EOF
  exit 1
fi

read -r -p "Type the database host to confirm: " confirm
host=$(printf '%s' "$TARGET" | sed -E 's|.*@([^/?]+).*|\1|')
[ "$confirm" = "$host" ] || { echo "Host did not match ($host) — stopping." >&2; exit 1; }

echo "Restoring..."
# ON_ERROR_STOP so a half-applied restore is a failure, not a silent partial success.
# `-o /dev/null`: a plain dump ends in a long tail of `setval` results, and scrolling past
# them is exactly how a real error in the middle gets missed. Errors go to stderr and
# ON_ERROR_STOP still aborts, so nothing worth seeing is hidden.
$PSQL --set ON_ERROR_STOP=on --quiet -o /dev/null "$TARGET" < "$plain"

echo
echo "Rows per table after the restore — the same shape as the backup's MANIFEST.txt:"
# ANALYZE again, for the same reason: the numbers below are read from the statistics.
$PSQL "$TARGET" -qc "ANALYZE" </dev/null >/dev/null
$PSQL "$TARGET" -tAc "select '  ' || rpad(relname, 28) || n_live_tup
                      from pg_stat_user_tables where n_live_tup > 0
                      order by relname" </dev/null
echo
echo "Done. Compare the two, table by table."
