#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME=false
management_curl_args=()

if [[ -n "${MANAGEMENT_TOKEN:-}" ]]; then
  management_curl_args=(-H "X-LeetModel-Management-Token: ${MANAGEMENT_TOKEN}")
fi

if [[ "${1:-}" == "--runtime" ]]; then
  RUNTIME=true
elif [[ $# -gt 0 ]]; then
  echo "用法: $0 [--runtime]" >&2
  exit 2
fi

services=(
  user-service problem-service team-service submission-service ai-review-service
  ranking-service ai-suggestion-service ai-assistant-service knowledge-retrieval-service
  ai-evaluation-service gateway-service admin-service ai-gateway-service
)
ports=(8081 8083 8082 8092 8086 8087 8088 8089 8093 8091 8080 8084 8090)

for index in "${!services[@]}"; do
  service="${services[$index]}"
  pom="${BACKEND_DIR}/${service}/pom.xml"
  application="${BACKEND_DIR}/${service}/src/main/resources/application.yml"

  if ! rg -q '<artifactId>spring-boot-starter-actuator</artifactId>' "${pom}"; then
    echo "${service} 缺少 Actuator 显式依赖。" >&2
    exit 1
  fi
  if ! rg -q '<artifactId>micrometer-registry-prometheus</artifactId>' "${pom}"; then
    echo "${service} 缺少 Prometheus Registry 显式依赖。" >&2
    exit 1
  fi
  if ! rg -q 'classpath:leetmodel-observability.yml' "${application}"; then
    echo "${service} 未导入公共健康契约。" >&2
    exit 1
  fi

  artifact="${BACKEND_DIR}/${service}/target/${service}-0.0.1-SNAPSHOT.jar"
  if [[ -f "${artifact}" ]] \
      && ! jar tf "${artifact}" | rg 'BOOT-INF/lib/micrometer-registry-prometheus-.*\.jar' >/dev/null; then
    echo "${service} 可执行包未携带 Prometheus Registry。" >&2
    exit 1
  fi

  if [[ "${RUNTIME}" == "true" ]]; then
    port="${ports[$index]}"
    base_url="http://127.0.0.1:${port}/actuator"
    curl -fsS "${base_url}/health/liveness" >/dev/null
    curl -fsS "${base_url}/health/readiness" >/dev/null
    if ! curl -fsS "${management_curl_args[@]}" "${base_url}/prometheus" \
        | grep '^jvm_info' >/dev/null; then
      echo "${service} Prometheus 端点缺少 jvm_info。" >&2
      exit 1
    fi
  fi
done

echo "Actuator 契约验证通过：${#services[@]} 个服务均具备显式依赖、公共配置与独立探针契约。"
