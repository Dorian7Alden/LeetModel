#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_ID="$(date +%Y%m%d%H%M%S)-$$"
MYSQL_CONTAINER="leetmodel-audit-mysql-${RUN_ID}"
MYSQL_PORT="${AUDIT_MYSQL_PORT:-33316}"
SERVICE_PORT="${AUDIT_SERVICE_PORT:-18097}"
ROOT_PASSWORD="Root1!$(openssl rand -hex 18)"
MIGRATOR_PASSWORD="Migrate1!$(openssl rand -hex 18)"
APP_PASSWORD="App1!$(openssl rand -hex 18)"
APP_USER="lm_audit_app"
MIGRATOR_USER="lm_audit_migrator"
SERVICE_PID=""
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime/audit-database/${RUN_ID}"

cleanup() {
  local status=$?
  if [[ -n "${SERVICE_PID}" ]] && kill -0 "${SERVICE_PID}" 2>/dev/null; then
    kill "${SERVICE_PID}" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "${SERVICE_PID}" 2>/dev/null || break
      sleep 1
    done
  fi
  if (( status != 0 )); then
    [[ -f "${RUNTIME_DIR}/audit-service.log" ]] && tail -n 80 "${RUNTIME_DIR}/audit-service.log" >&2 || true
    echo "审计数据库验证失败，诊断目录：${RUNTIME_DIR}" >&2
  fi
  docker rm -f "${MYSQL_CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mkdir -p "${RUNTIME_DIR}"
chmod 700 "${RUNTIME_DIR}"

if ss -ltn "sport = :${MYSQL_PORT}" | tail -n +2 | grep -q .; then
  echo "审计数据库验证端口 ${MYSQL_PORT} 已被占用。" >&2
  exit 1
fi
if ss -ltn "sport = :${SERVICE_PORT}" | tail -n +2 | grep -q .; then
  echo "审计服务验证端口 ${SERVICE_PORT} 已被占用。" >&2
  exit 1
fi

cd "${BACKEND_DIR}"
mvn -pl audit-service -am package

docker run -d --name "${MYSQL_CONTAINER}" \
  -e "MYSQL_ROOT_PASSWORD=${ROOT_PASSWORD}" \
  -e TZ=Asia/Shanghai \
  -p "127.0.0.1:${MYSQL_PORT}:3306" \
  mysql:8.0.33 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci \
  --default-time-zone=+08:00 >/dev/null

for _ in {1..60}; do
  if docker exec "${MYSQL_CONTAINER}" mysqladmin ping -h 127.0.0.1 -uroot \
      -p"${ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker exec "${MYSQL_CONTAINER}" mysqladmin ping -h 127.0.0.1 -uroot \
  -p"${ROOT_PASSWORD}" --silent >/dev/null

MYSQL_ADMIN_CONTAINER="${MYSQL_CONTAINER}" \
MYSQL_ADMIN_PASSWORD="${ROOT_PASSWORD}" \
AUDIT_DB_MIGRATOR_USERNAME="${MIGRATOR_USER}" \
AUDIT_DB_MIGRATOR_PASSWORD="${MIGRATOR_PASSWORD}" \
AUDIT_DB_APP_USERNAME="${APP_USER}" \
AUDIT_DB_APP_PASSWORD="${APP_PASSWORD}" \
  "${SCRIPT_DIR}/init-audit-database.sh" bootstrap

artifact="${BACKEND_DIR}/audit-service/target/audit-service-0.0.1-SNAPSHOT.jar"
[[ -f "${artifact}" ]] || { echo "缺少 audit-service 可执行包。" >&2; exit 1; }

AUDIT_DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/lm_audit?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai" \
AUDIT_DB_APP_USERNAME="${APP_USER}" \
AUDIT_DB_APP_PASSWORD="${APP_PASSWORD}" \
AUDIT_DB_MIGRATOR_USERNAME="${MIGRATOR_USER}" \
AUDIT_DB_MIGRATOR_PASSWORD="${MIGRATOR_PASSWORD}" \
NACOS_ADDR=127.0.0.1:1 \
SPRING_CLOUD_NACOS_DISCOVERY_ENABLED=false \
ROCKETMQ_NAME_SERVER=127.0.0.1:1 \
AUDIT_CONSUMER_ENABLED=false \
SERVER_PORT="${SERVICE_PORT}" \
  java -jar "${artifact}" >"${RUNTIME_DIR}/audit-service.log" 2>&1 &
SERVICE_PID=$!

for _ in {1..90}; do
  if ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
    echo "audit-service 未能启动。" >&2
    exit 1
  fi
  if curl -fsS "http://127.0.0.1:${SERVICE_PORT}/actuator/health/readiness" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -fsS "http://127.0.0.1:${SERVICE_PORT}/actuator/health/liveness" >/dev/null
curl -fsS "http://127.0.0.1:${SERVICE_PORT}/actuator/health/readiness" >/dev/null
curl -fsS "http://127.0.0.1:${SERVICE_PORT}/actuator/prometheus" | grep '^jvm_info' >/dev/null

MYSQL_ADMIN_CONTAINER="${MYSQL_CONTAINER}" \
MYSQL_ADMIN_PASSWORD="${ROOT_PASSWORD}" \
AUDIT_DB_MIGRATOR_USERNAME="${MIGRATOR_USER}" \
AUDIT_DB_MIGRATOR_PASSWORD="${MIGRATOR_PASSWORD}" \
AUDIT_DB_APP_USERNAME="${APP_USER}" \
AUDIT_DB_APP_PASSWORD="${APP_PASSWORD}" \
  "${SCRIPT_DIR}/init-audit-database.sh" finalize

mysql_app() {
  docker exec -i -e "MYSQL_PWD=${APP_PASSWORD}" "${MYSQL_CONTAINER}" mysql \
    --protocol=TCP -h 127.0.0.1 -u "${APP_USER}" --database=lm_audit --batch --skip-column-names "$@"
}
mysql_admin() {
  docker exec -i -e "MYSQL_PWD=${ROOT_PASSWORD}" "${MYSQL_CONTAINER}" mysql \
    --protocol=TCP -h 127.0.0.1 -u root --batch --skip-column-names "$@"
}

mysql_admin -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lm_audit' AND table_name IN ('message_inbox','operation_audit_event','flyway_schema_history')" | grep -qx '3'
mysql_admin -e "SELECT version FROM lm_audit.flyway_schema_history WHERE version='1'" | grep -qx '1'

mysql_app <<'SQL'
INSERT INTO operation_audit_event (
  audit_event_id, audit_schema_version, operation_id, phase, occurred_at,
  source_service, service_version, category, operation_code, risk_level, outcome,
  reason, failure_code, actor_type, actor_id, actor_roles_json, target_type, target_id,
  target_version, before_summary_json, after_summary_json, trace_id, sw_trace_id,
  request_id, domain_task_id, related_event_id, client_ip_hash, user_agent_hash
) VALUES (
  '00000000-0000-4000-8000-000000000101', 1, 'operation-db-1', 'COMPLETED', CURRENT_TIMESTAMP(3),
  'user-service', '1.0.0', 'USER_RBAC', 'USER.ROLE_CHANGE', 'HIGH', 'SUCCEEDED',
  'database-contract', NULL, 'ADMIN', 'admin-db-1', JSON_ARRAY('ROLE_ADMIN'), 'USER', 'user-db-1',
  'version-1', JSON_OBJECT('roleCount','1'), JSON_OBJECT('roleCount','2'), 'trace-db-1', NULL,
  NULL, NULL, NULL, NULL, NULL
);
SELECT COUNT(*) FROM operation_audit_event WHERE audit_event_id='00000000-0000-4000-8000-000000000101';
INSERT INTO message_inbox (
  consumer_group, event_id, event_type, source_service, trace_id, status,
  occurred_at, create_time, update_time
) VALUES (
  'lm-audit%cg-audit-archive-v1', '00000000-0000-4000-8000-000000000101',
  'OPERATION_AUDIT_RECORDED', 'user-service', 'trace-db-1', 'PROCESSING',
  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
);
UPDATE message_inbox SET status='CONSUMED', consumed_at=CURRENT_TIMESTAMP(3), update_time=CURRENT_TIMESTAMP(3)
 WHERE event_id='00000000-0000-4000-8000-000000000101';
SELECT status FROM message_inbox WHERE event_id='00000000-0000-4000-8000-000000000101';
SQL

if mysql_app -e "UPDATE operation_audit_event SET reason='tampered' WHERE operation_id='operation-db-1'" >/dev/null 2>&1; then
  echo "审计归档表意外可更新。" >&2
  exit 1
fi
if mysql_app -e "DELETE FROM operation_audit_event WHERE operation_id='operation-db-1'" >/dev/null 2>&1; then
  echo "审计归档表意外可删除。" >&2
  exit 1
fi
if mysql_app -e "DELETE FROM message_inbox WHERE event_id='00000000-0000-4000-8000-000000000101'" >/dev/null 2>&1; then
  echo "Inbox 意外可删除。" >&2
  exit 1
fi

if mysql_app -e "INSERT INTO operation_audit_event (audit_event_id,audit_schema_version,operation_id,phase,occurred_at,source_service,service_version,category,operation_code,risk_level,outcome,actor_type,target_type) VALUES ('00000000-0000-4000-8000-000000000101',1,'operation-db-duplicate','COMPLETED',CURRENT_TIMESTAMP(3),'user-service','1.0.0','USER_RBAC','USER.ROLE_CHANGE','HIGH','SUCCEEDED','SYSTEM','USER')" >/dev/null 2>&1; then
  echo "审计事件主键未拒绝重复写入。" >&2
  exit 1
fi

mysql_admin -e "SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema='lm_audit' AND table_name='operation_audit_event' AND index_name IN ('idx_audit_operation_timeline','idx_audit_actor_timeline','idx_audit_target_timeline','idx_audit_trace','idx_audit_sw_trace')" \
  | sort | diff -u <(printf '%s\n' idx_audit_actor_timeline idx_audit_operation_timeline idx_audit_sw_trace idx_audit_target_timeline idx_audit_trace) -

grants="$(mysql_admin -e "SHOW GRANTS FOR '${APP_USER}'@'%'" | tr -d '\r')"
if grep -Eq 'ON `lm_audit`\.\* TO|GRANT .*DELETE|GRANT .*UPDATE.*`lm_audit`\.\*' <<<"${grants}"; then
  echo "审计应用账号权限超出表级 Inbox/INSERT/SELECT 契约。" >&2
  exit 1
fi

echo "审计数据库验证通过：专用 schema、Flyway V1、追加式归档与 Inbox 最小权限均已验证。"
