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
command -v psql >/dev/null || { echo "psql is not installed" >&2; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

plain="$work/dump.sql"
case "$DUMP" in
  *.gpg)
    command -v gpg >/dev/null || { echo "gpg is not installed" >&2; exit 1; }
    echo "Decrypting $DUMP (the passphrase is BACKUP_PASSPHRASE)..."
    gpg --quiet --decrypt --output "$plain" "$DUMP"
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

existing=$(psql "$TARGET" -tAc \
  "select coalesce(sum(n_live_tup), 0) from pg_stat_user_tables" 2>/dev/null || echo 0)
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
psql --set ON_ERROR_STOP=on --quiet "$TARGET" < "$plain"

echo
echo "Row counts after the restore:"
psql "$TARGET" -c "select relname, n_live_tup from pg_stat_user_tables
                   where n_live_tup > 0 order by n_live_tup desc"
echo "Done. Check the counts against the backup's MANIFEST.txt."
