#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-bootstrap}"
MYSQL_ADMIN_CONTAINER_VALUE="${MYSQL_ADMIN_CONTAINER:-leetmodel-mysql}"
MYSQL_ADMIN_USER_VALUE="${MYSQL_ADMIN_USER:-root}"
MYSQL_ADMIN_PASSWORD_VALUE="${MYSQL_ADMIN_PASSWORD:?MYSQL_ADMIN_PASSWORD is required}"
AUDIT_DB_SCHEMA_VALUE="${AUDIT_DB_SCHEMA:-lm_audit}"
AUDIT_DB_MIGRATOR_USERNAME_VALUE="${AUDIT_DB_MIGRATOR_USERNAME:-lm_audit_migrator}"
AUDIT_DB_MIGRATOR_PASSWORD_VALUE="${AUDIT_DB_MIGRATOR_PASSWORD:?AUDIT_DB_MIGRATOR_PASSWORD is required}"
AUDIT_DB_APP_USERNAME_VALUE="${AUDIT_DB_APP_USERNAME:-lm_audit_app}"
AUDIT_DB_APP_PASSWORD_VALUE="${AUDIT_DB_APP_PASSWORD:?AUDIT_DB_APP_PASSWORD is required}"

if [[ "${MODE}" != "bootstrap" && "${MODE}" != "finalize" ]]; then
  echo "用法: $0 [bootstrap|finalize]" >&2
  exit 2
fi
for identifier in "${AUDIT_DB_SCHEMA_VALUE}" "${AUDIT_DB_MIGRATOR_USERNAME_VALUE}" \
    "${AUDIT_DB_APP_USERNAME_VALUE}"; do
  if [[ ! "${identifier}" =~ ^[A-Za-z0-9_]{3,64}$ ]]; then
    echo "审计数据库 schema 或用户名格式非法。" >&2
    exit 2
  fi
done
for password in "${AUDIT_DB_MIGRATOR_PASSWORD_VALUE}" "${AUDIT_DB_APP_PASSWORD_VALUE}"; do
  if (( ${#password} < 16 || ${#password} > 128 )) \
      || [[ ! "${password}" =~ ^[A-Za-z0-9._!@#%+=:-]+$ ]]; then
    echo "审计数据库密码必须为 16-128 位安全字符。" >&2
    exit 2
  fi
done

mysql_admin() {
  docker exec -i -e "MYSQL_PWD=${MYSQL_ADMIN_PASSWORD_VALUE}" \
    "${MYSQL_ADMIN_CONTAINER_VALUE}" mysql --protocol=TCP -h 127.0.0.1 \
    -u "${MYSQL_ADMIN_USER_VALUE}" --batch --skip-column-names "$@"
}

if [[ "${MODE}" == "bootstrap" ]]; then
  mysql_admin <<SQL
CREATE DATABASE IF NOT EXISTS \`${AUDIT_DB_SCHEMA_VALUE}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${AUDIT_DB_MIGRATOR_USERNAME_VALUE}'@'%'
  IDENTIFIED BY '${AUDIT_DB_MIGRATOR_PASSWORD_VALUE}';
ALTER USER '${AUDIT_DB_MIGRATOR_USERNAME_VALUE}'@'%'
  IDENTIFIED BY '${AUDIT_DB_MIGRATOR_PASSWORD_VALUE}';
CREATE USER IF NOT EXISTS '${AUDIT_DB_APP_USERNAME_VALUE}'@'%'
  IDENTIFIED BY '${AUDIT_DB_APP_PASSWORD_VALUE}';
ALTER USER '${AUDIT_DB_APP_USERNAME_VALUE}'@'%'
  IDENTIFIED BY '${AUDIT_DB_APP_PASSWORD_VALUE}';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${AUDIT_DB_MIGRATOR_USERNAME_VALUE}'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${AUDIT_DB_APP_USERNAME_VALUE}'@'%';
GRANT ALL PRIVILEGES ON \`${AUDIT_DB_SCHEMA_VALUE}\`.*
  TO '${AUDIT_DB_MIGRATOR_USERNAME_VALUE}'@'%';
GRANT SELECT, INSERT ON \`${AUDIT_DB_SCHEMA_VALUE}\`.*
  TO '${AUDIT_DB_APP_USERNAME_VALUE}'@'%';
FLUSH PRIVILEGES;
SQL
  echo "审计 schema 与独立迁移/应用账号已初始化（凭据未输出）。"
  exit 0
fi

table_exists="$(mysql_admin -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${AUDIT_DB_SCHEMA_VALUE}' AND table_name='message_inbox'")"
if [[ "${table_exists}" != "1" ]]; then
  echo "message_inbox 尚未由 Flyway 创建，不能完成最小权限授权。" >&2
  exit 1
fi
mysql_admin <<SQL
REVOKE SELECT, INSERT ON \`${AUDIT_DB_SCHEMA_VALUE}\`.*
  FROM '${AUDIT_DB_APP_USERNAME_VALUE}'@'%';
GRANT SELECT, INSERT ON \`${AUDIT_DB_SCHEMA_VALUE}\`.\`message_inbox\`
  TO '${AUDIT_DB_APP_USERNAME_VALUE}'@'%';
GRANT SELECT, INSERT ON \`${AUDIT_DB_SCHEMA_VALUE}\`.\`operation_audit_event\`
  TO '${AUDIT_DB_APP_USERNAME_VALUE}'@'%';
GRANT UPDATE ON \`${AUDIT_DB_SCHEMA_VALUE}\`.\`message_inbox\`
  TO '${AUDIT_DB_APP_USERNAME_VALUE}'@'%';
FLUSH PRIVILEGES;
SQL
echo "审计应用账号权限已收敛：归档表仅 SELECT/INSERT，Inbox 另允许 UPDATE，无 DELETE。"
