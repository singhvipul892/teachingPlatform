#!/bin/bash
# Apply pending SQL migrations to the running database.
#
# Called by scripts/deploy.sh before the new API image starts, and safe to run
# on its own at any time — it only ever applies files that have not been applied
# yet. Running it twice in a row does nothing the second time.
#
# Why it exists: the API runs with hibernate ddl-auto=validate and refuses to
# boot against a schema older than its entities. Before this, that meant
# remembering to pipe a .sql file through psql by hand before every schema-
# changing deploy, in the right order, or the site went down.
#
# Files are taken from backend/docker/migrations/ in filename order and applied
# once each, tracked in a schema_migrations table. Each file runs in a single
# transaction together with the row that records it (psql -1), so a migration
# cannot end up half-applied or applied twice — Postgres DDL is transactional,
# and either both the change and its bookkeeping land, or neither does.
#
# backend/docker/init/000_consolidated.sql is deliberately NOT run here. Postgres
# runs that itself, and only on a genuinely empty data directory.
#
# Usage:
#   scripts/run-migrations.sh
#   APP_DIR=/somewhere COMPOSE_FILE=path/to/compose.yml scripts/run-migrations.sh
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/teacherplatform}"
COMPOSE_FILE="${COMPOSE_FILE:-backend/docker-compose.prod.yml}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-backend/docker/migrations}"
DB_SERVICE="${DB_SERVICE:-db}"

cd "$APP_DIR"

# Read the same .env compose does, so the two cannot disagree about which
# database to talk to. Defaults match backend/docker-compose.prod.yml.
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi
PG_USER="${POSTGRES_USER:-teacher}"
PG_DB="${POSTGRES_DB:-teacher_videos}"

COMPOSE=(docker compose -f "$COMPOSE_FILE")

psql_db() {
  "${COMPOSE[@]}" exec -T "$DB_SERVICE" psql -U "$PG_USER" -d "$PG_DB" "$@"
}

if [ ! -d "$MIGRATIONS_DIR" ] || ! compgen -G "$MIGRATIONS_DIR/*.sql" > /dev/null; then
  echo "No migrations to apply."
  exit 0
fi

# The db container normally outlives deploys, but bring it up in case this is a
# cold start, and wait until it actually accepts connections.
"${COMPOSE[@]}" up -d "$DB_SERVICE"
for _ in $(seq 1 30); do
  if "${COMPOSE[@]}" exec -T "$DB_SERVICE" pg_isready -U "$PG_USER" -d "$PG_DB" > /dev/null 2>&1; then
    break
  fi
  sleep 2
done
if ! "${COMPOSE[@]}" exec -T "$DB_SERVICE" pg_isready -U "$PG_USER" -d "$PG_DB" > /dev/null 2>&1; then
  echo "ERROR: database is not accepting connections." >&2
  exit 1
fi

psql_db -v ON_ERROR_STOP=1 -q <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
    filename   TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SQL

# Read the whole ledger once rather than querying per file.
applied="$(psql_db -tAc 'SELECT filename FROM schema_migrations')"

ran=0
for file in "$MIGRATIONS_DIR"/*.sql; do
  name="$(basename "$file")"

  if printf '%s\n' "$applied" | grep -qxF "$name"; then
    continue
  fi

  echo "Applying migration: $name"
  # Migrations are also written to be individually re-runnable (ADD COLUMN IF
  # NOT EXISTS), so one already applied by hand records cleanly here instead of
  # failing the deploy.
  if ! {
    cat "$file"
    printf "\nINSERT INTO schema_migrations (filename) VALUES ('%s');\n" "$name"
  } | psql_db -1 -v ON_ERROR_STOP=1 -q; then
    echo "ERROR: migration $name failed; nothing from it was applied." >&2
    exit 1
  fi
  ran=$((ran + 1))
done

if [ "$ran" -eq 0 ]; then
  echo "Schema is up to date; no migrations to apply."
else
  echo "Applied $ran migration(s)."
fi
