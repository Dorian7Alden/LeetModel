#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime"
AGENT_VERSION="9.7.0"
AGENT_IMAGE="apache/skywalking-java-agent:${AGENT_VERSION}-alpine"
ARCHIVE_NAME="apache-skywalking-java-agent-${AGENT_VERSION}.tgz"
ARCHIVE_URL="https://archive.apache.org/dist/skywalking/java-agent/${AGENT_VERSION}/${ARCHIVE_NAME}"
ARCHIVE_SHA512="4b2f8ec00604ed067c44ccadf8800852ecf3d33f4da16ed494a05b4a5ffb45b078cf48e25ebadfda6d1197611fe8a1cb49bf426256c5e194299a1ddd631484ac"
AGENT_HOME="${RUNTIME_DIR}/skywalking-agent-${AGENT_VERSION}"
AGENT_JAR="${AGENT_HOME}/skywalking-agent.jar"

activate_optional_plugins() {
  local agent_home="$1"
  local plugin_pattern
  local plugin_path
  local optional_plugin_patterns=(
    'apm-spring-cloud-gateway-4.x-plugin-*.jar'
    'apm-springmvc-annotation-6.x-plugin-*.jar'
    'apm-spring-webflux-6.x-plugin-*.jar'
    'apm-resttemplate-6.x-plugin-*.jar'
  )
  for plugin_pattern in "${optional_plugin_patterns[@]}"; do
    plugin_path="$(find "${agent_home}/optional-plugins" -maxdepth 1 -type f -name "${plugin_pattern}" -print -quit)"
    if [[ -z "${plugin_path}" ]]; then
      echo "未找到 SkyWalking 可选插件：${plugin_pattern}" >&2
      exit 1
    fi
    cp "${plugin_path}" "${agent_home}/plugins/"
  done
}

if [[ -f "${AGENT_JAR}" ]]; then
  activate_optional_plugins "${AGENT_HOME}"
  printf '%s\n' "${AGENT_JAR}"
  exit 0
fi

mkdir -p "${RUNTIME_DIR}"
archive_path="${RUNTIME_DIR}/${ARCHIVE_NAME}"
extract_dir="${RUNTIME_DIR}/skywalking-agent-${AGENT_VERSION}.extracting"
rm -rf "${extract_dir}"
mkdir -p "${extract_dir}"

if command -v docker >/dev/null 2>&1; then
  container_id="$(docker create "${AGENT_IMAGE}")"
  cleanup_container() {
    docker rm -f "${container_id}" >/dev/null 2>&1 || true
  }
  trap cleanup_container EXIT
  docker cp "${container_id}:/skywalking/agent" "${extract_dir}/skywalking-agent"
  cleanup_container
  trap - EXIT
else
  if [[ -f "${archive_path}" ]]; then
    actual_sha512="$(sha512sum "${archive_path}" | awk '{print $1}')"
    if [[ "${actual_sha512}" != "${ARCHIVE_SHA512}" ]]; then
      rm -f "${archive_path}"
    fi
  fi

  if [[ ! -f "${archive_path}" ]]; then
    curl --fail --location --retry 3 --output "${archive_path}" "${ARCHIVE_URL}"
  fi

  actual_sha512="$(sha512sum "${archive_path}" | awk '{print $1}')"
  if [[ "${actual_sha512}" != "${ARCHIVE_SHA512}" ]]; then
    echo "SkyWalking Agent SHA-512 校验失败。" >&2
    exit 1
  fi
  tar -xzf "${archive_path}" -C "${extract_dir}"
fi

extracted_home="${extract_dir}/skywalking-agent"
if [[ ! -f "${extracted_home}/skywalking-agent.jar" ]]; then
  echo "SkyWalking Agent 压缩包结构不符合预期。" >&2
  exit 1
fi

activate_optional_plugins "${extracted_home}"

mv "${extracted_home}" "${AGENT_HOME}"
rm -rf "${extract_dir}"
printf '%s\n' "${AGENT_JAR}"
