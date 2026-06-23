#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SQL_FILE="${1:-"$REPO_ROOT/../sgsmineralscapability.sql"}"
CONTAINER="${MSSQL_CONTAINER:-capability-mssql}"
DATABASE="${MSSQL_DATABASE:-sgsmineralscapability}"
: "${MSSQL_SA_PASSWORD:?Set MSSQL_SA_PASSWORD before importing}"
PASSWORD="$MSSQL_SA_PASSWORD"

if [[ ! -f "$SQL_FILE" ]]; then
  echo "SQL file not found: $SQL_FILE" >&2
  exit 1
fi

sqlcmd() {
  if docker exec "$CONTAINER" test -x /opt/mssql-tools18/bin/sqlcmd >/dev/null 2>&1; then
    docker exec "$CONTAINER" /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$PASSWORD" -C "$@"
  else
    docker exec "$CONTAINER" /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "$PASSWORD" "$@"
  fi
}

echo "Waiting for SQL Server in container: $CONTAINER"
for _ in {1..90}; do
  if sqlcmd -Q "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
sqlcmd -Q "SELECT 1" >/dev/null

echo "Creating database if missing: $DATABASE"
if [[ "${MSSQL_RESET_DATABASE:-0}" == "1" ]]; then
  echo "Resetting database: $DATABASE"
  sqlcmd -Q "IF DB_ID(N'$DATABASE') IS NOT NULL BEGIN ALTER DATABASE [$DATABASE] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$DATABASE]; END"
fi
sqlcmd -Q "IF DB_ID(N'$DATABASE') IS NULL CREATE DATABASE [$DATABASE];"

echo "Copying SQL file into container..."
docker cp "$SQL_FILE" "$CONTAINER:/tmp/sgsmineralscapability.sql"

echo "Importing production SQL into [$DATABASE]. This can take a while."
if docker exec "$CONTAINER" test -x /opt/mssql-tools18/bin/sqlcmd >/dev/null 2>&1; then
  docker exec "$CONTAINER" /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P "$PASSWORD" -C -d "$DATABASE" -b -x -i /tmp/sgsmineralscapability.sql
else
  docker exec "$CONTAINER" /opt/mssql-tools/bin/sqlcmd \
    -S localhost -U sa -P "$PASSWORD" -d "$DATABASE" -b -x -i /tmp/sgsmineralscapability.sql
fi

echo "Verifying core tables..."
sqlcmd -d "$DATABASE" -Q "SELECT 'MineralAbilityTable' AS TableName, COUNT(*) AS Total FROM dbo.MineralAbilityTable UNION ALL SELECT 'SgsUsers', COUNT(*) FROM dbo.SgsUsers UNION ALL SELECT 'SgsOrganizationUnits', COUNT(*) FROM dbo.SgsOrganizationUnits UNION ALL SELECT 'SgsEntityChanges', COUNT(*) FROM dbo.SgsEntityChanges;"
