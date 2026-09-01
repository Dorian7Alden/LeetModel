#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.mvp-runtime"

usage() {
  cat <<'EOF'
用法：drill-messaging-failures.sh <命令> [参数]

只执行一个明确故障动作，不自动串行破坏环境：
  status                         查看 RocketMQ、MySQL 与 MVP 进程状态
  broker-restart                 重启 Broker 并验证持久化探针
  broker-pause | broker-resume   暂停/恢复 Broker 容器，模拟网络中断
  db-pause | db-resume           暂停/恢复 MySQL 容器，模拟数据库短故障
  app-kill <服务名>              SIGKILL 一个由 start-mvp.sh 启动的消息服务
  duplicate-probe                运行真实 Broker 重复消息协议测试

app-kill 仅允许 submission-service、ai-review-service、ranking-service、
ai-suggestion-service、ai-evaluation-service，且会校验 PID 命令行。
EOF
}

require_exact_service() {
  case "${1:-}" in
    submission-service|ai-review-service|ranking-service|ai-suggestion-service|ai-evaluation-service) ;;
    *) echo "服务名不在允许的消息服务列表。" >&2; exit 2 ;;
  esac
}

cd "${BACKEND_DIR}"
command="${1:-}"
case "${command}" in
  status)
    docker compose ps rocketmq-namesrv rocketmq-broker mysql
    for pid_file in "${RUNTIME_DIR}"/*.pid; do
      [[ -e "${pid_file}" ]] || continue
      service="$(basename "${pid_file}" .pid)"
      pid="$(<"${pid_file}")"
      if [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null; then
        printf '%-32s running pid=%s\n' "${service}" "${pid}"
      fi
    done
    ;;
  broker-restart)
    ROCKETMQ_VERIFY_RESTART=true "${SCRIPT_DIR}/verify-rocketmq.sh"
    ;;
  broker-pause)
    docker compose pause rocketmq-broker
    echo "Broker 已暂停；观察 Outbox PENDING、最老等待时间和在线降级。完成后必须执行 broker-resume。"
    ;;
  broker-resume)
    docker compose unpause rocketmq-broker
    docker compose up -d --wait rocketmq-broker
    "${SCRIPT_DIR}/init-rocketmq.sh"
    ;;
  db-pause)
    docker compose pause mysql
    echo "MySQL 已暂停；观察消费事务失败且 Inbox 不落脏记录。完成后必须执行 db-resume。"
    ;;
  db-resume)
    docker compose unpause mysql
    docker compose up -d --wait mysql
    ;;
  app-kill)
    service="${2:-}"
    require_exact_service "${service}"
    pid_file="${RUNTIME_DIR}/${service}.pid"
    [[ -f "${pid_file}" ]] || { echo "找不到 ${pid_file}，服务不是由 start-mvp.sh 启动。" >&2; exit 1; }
    pid="$(<"${pid_file}")"
    [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null || { echo "PID 不在运行。" >&2; exit 1; }
    command_line="$(tr '\0' ' ' <"/proc/${pid}/cmdline")"
    [[ "${command_line}" == *"/${service}-0.0.1-SNAPSHOT.jar"* ]] || { echo "PID 命令行校验失败。" >&2; exit 1; }
    kill -9 "${pid}"
    echo "已终止 ${service} (PID ${pid})；重启后验证过期租约恢复、Inbox 幂等和领域任务收敛。"
    ;;
  duplicate-probe)
    RUN_ROCKETMQ_INTEGRATION=true \
      mvn -pl common/common-messaging -am -Dtest=RocketMqProtocolIntegrationTest \
      -Dsurefire.failIfNoSpecifiedTests=false test
    ;;
  *) usage; exit 2 ;;
esac
