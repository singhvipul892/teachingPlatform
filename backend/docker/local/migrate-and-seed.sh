#!/bin/sh
# LOCAL STACK ONLY (docker-compose.local.yml). Never point this at production.
#
# 1. Applies backend/docker/migrations/*.sql in filename order, once each, using
#    the same schema_migrations ledger as scripts/run-migrations.sh. Each file
#    runs in one transaction with its ledger row, so nothing is half-applied.
#    On a fresh database 000_consolidated.sql already contains everything; the
#    migrations are re-runnable, so they apply cleanly and just get recorded.
# 2. Loads seed.sql, which does nothing if the demo data is already there.
set -eu

echo "Waiting for the database..."
until pg_isready -q; do sleep 1; done

psql -v ON_ERROR_STOP=1 -q <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
    filename   TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SQL

applied="$(psql -tAc 'SELECT filename FROM schema_migrations')"
ran=0
for file in /migrations/*.sql; do
  [ -e "$file" ] || continue
  name="$(basename "$file")"
  if printf '%s\n' "$applied" | grep -qxF "$name"; then
    continue
  fi
  echo "Applying migration: $name"
  {
    cat "$file"
    printf "\nINSERT INTO schema_migrations (filename) VALUES ('%s');\n" "$name"
  } | psql -1 -v ON_ERROR_STOP=1 -q
  ran=$((ran + 1))
done
echo "Migrations applied this run: $ran"

psql -v ON_ERROR_STOP=1 -q -f /local/seed.sql
echo "Local database ready."
