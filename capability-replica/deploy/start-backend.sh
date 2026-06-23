#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/capability-replica}"
JAVA_BIN="${JAVA_BIN:-java}"
JAR_FILE="$APP_HOME/backend/capability-replica-0.1.0-SNAPSHOT.jar"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$APP_HOME/backend/capability.pid"
ENV_FILE="$APP_HOME/backend/capability.env"

mkdir -p "$LOG_DIR"

if [[ -f "$PID_FILE" ]]; then
  OLD_PID="$(cat "$PID_FILE")"
  if [[ -n "$OLD_PID" ]] && kill -0 "$OLD_PID" >/dev/null 2>&1; then
    kill "$OLD_PID"
    for _ in {1..30}; do
      if ! kill -0 "$OLD_PID" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    if kill -0 "$OLD_PID" >/dev/null 2>&1; then
      kill -9 "$OLD_PID" || true
    fi
  fi
fi

cd "$APP_HOME/backend"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:sqlserver://127.0.0.1:14333;databaseName=sgsmineralscapability;encrypt=false;trustServerCertificate=true}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-sa}"
: "${SPRING_DATASOURCE_PASSWORD:?Set SPRING_DATASOURCE_PASSWORD in $ENV_FILE}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx3584m}"

nohup "$JAVA_BIN" $JAVA_OPTS \
  -Dserver.port=9901 \
  -Dreplica.store.mode=database \
  -Dapp.server-root-address=http://203.110.232.128:8102/ \
  -jar "$JAR_FILE" > "$LOG_DIR/backend.log" 2>&1 &

echo $! > "$PID_FILE"
echo "Started capability backend: $(cat "$PID_FILE")"
