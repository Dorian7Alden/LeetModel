#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${BACKEND_DIR}/.." && pwd)"
OBSERVABILITY_DIR="${BACKEND_DIR}/docker/observability"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"

for command in docker python3 rg; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少告警门禁命令：${command}" >&2
    exit 1
  fi
done
if ! python3 -c 'import yaml' >/dev/null 2>&1; then
  echo "告警注解门禁需要 Python PyYAML。" >&2
  exit 1
fi

docker compose -f "${COMPOSE_FILE}" config --quiet

docker run --rm --entrypoint=/bin/promtool \
  -v "${OBSERVABILITY_DIR}/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-rules:/etc/prometheus/rules:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-targets:/etc/prometheus/targets:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-targets:/etc/prometheus/runtime-targets:ro" \
  -v "/etc/hostname:/run/secrets/management_token:ro" \
  prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml >/dev/null

docker run --rm --entrypoint=/bin/promtool \
  -v "${OBSERVABILITY_DIR}:/etc/observability:ro" \
  prom/prometheus:v3.14.0 test rules \
  /etc/observability/prometheus-tests/alerts.test.yml >/dev/null

docker run --rm --entrypoint=/bin/amtool \
  -v "${OBSERVABILITY_DIR}/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" \
  prom/alertmanager:v0.34.0 check-config /etc/alertmanager/alertmanager.yml >/dev/null

for route_case in \
  'local-null-warning severity=warning service=user-service alertname=ExampleWarning' \
  'local-null-critical severity=critical service=user-service alertname=ExampleCritical' \
  'local-drill-webhook drill=true severity=warning service=alert-drill alertname=ExampleDrill'; do
  read -r expected_receiver labels <<<"${route_case}"
  docker run --rm --entrypoint=/bin/amtool \
    -v "${OBSERVABILITY_DIR}/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" \
    prom/alertmanager:v0.34.0 config routes test \
    --config.file=/etc/alertmanager/alertmanager.yml \
    --verify.receivers="${expected_receiver}" ${labels} >/dev/null
done

python3 - "${REPO_DIR}" "${OBSERVABILITY_DIR}/prometheus-rules" <<'PY'
import pathlib
import re
import sys
import yaml
from collections import Counter

repo = pathlib.Path(sys.argv[1])
rules_dir = pathlib.Path(sys.argv[2])
required_annotations = {
    "summary", "impact", "current_value", "dashboard",
    "investigation", "runbook", "recovery",
}
forbidden_labels = {
    "userid", "teamid", "submissionid", "traceid", "swtraceid", "swspanid",
    "operationid", "eventid", "taskid", "domaintaskid", "attemptid", "attemptno",
    "aicallid", "callid", "messageid",
}


def anchor_set(markdown: pathlib.Path) -> set[str]:
    anchors = set()
    for line in markdown.read_text(encoding="utf-8").splitlines():
        if not line.startswith("#"):
            continue
        heading = line.lstrip("#").strip().lower()
        anchor = re.sub(r"[^a-z0-9\u4e00-\u9fff _-]", "", heading)
        anchor = re.sub(r"[ _]+", "-", anchor).strip("-")
        anchors.add(anchor)
    return anchors


alerts = []
for path in sorted(rules_dir.glob("*-alerts.yml")):
    document = yaml.safe_load(path.read_text(encoding="utf-8"))
    for group in document.get("groups", []):
        for rule in group.get("rules", []):
            if "alert" in rule:
                alerts.append((path, rule))

if len(alerts) != 22:
    raise SystemExit(f"告警规则数量应为 22，实际为 {len(alerts)}")

for path, rule in alerts:
    name = rule["alert"]
    labels = rule.get("labels", {})
    annotations = rule.get("annotations", {})
    severity = labels.get("severity")
    if severity not in {"warning", "critical"}:
        raise SystemExit(f"{name} 缺少固定 warning/critical severity")
    normalized_keys = {re.sub(r"[^a-z0-9]", "", key.lower()) for key in labels}
    bad_keys = normalized_keys & forbidden_labels
    if bad_keys:
        raise SystemExit(f"{name} 使用禁止的高基数标签：{sorted(bad_keys)}")
    missing = required_annotations - annotations.keys()
    if missing:
        raise SystemExit(f"{name} 缺少告警注解：{sorted(missing)}")
    if any(not str(annotations[key]).strip() for key in required_annotations):
        raise SystemExit(f"{name} 存在空告警注解")
    expression = str(rule.get("expr", ""))
    if "http_server_requests" in expression:
        raise SystemExit(f"{name} 在没有真实基线前设置了 HTTP SLO")
    runbook_ref = str(annotations["runbook"])
    relative, separator, anchor = runbook_ref.partition("#")
    runbook = repo / relative
    if not runbook.is_file():
        raise SystemExit(f"{name} Runbook 不存在：{relative}")
    if not separator or anchor not in anchor_set(runbook):
        raise SystemExit(f"{name} Runbook anchor 不存在：{runbook_ref}")

expected = {
    "LeetModelServiceMetricsUnavailable",
    "LeetModelServiceDiscoveryIncomplete",
    "LeetModelTelemetryComponentUnavailable",
    "LeetModelAlertmanagerDisconnected",
    "LeetModelRuleEvaluationFailure",
    "LeetModelOutboxPublishDelayed",
    "LeetModelOutboxBlocked",
    "LeetModelOnlineConsumerBacklogHigh",
    "LeetModelOnlineConsumerOldestHigh",
    "LeetModelBrokerMetricsUnavailable",
    "LeetModelDeadLetterPresent",
    "LeetModelAiP0QueueWaitHigh",
    "LeetModelAiBackgroundQueueWaitHigh",
    "LeetModelAiQueueCapacityHigh",
    "LeetModelAiUpstreamResultUnknown",
    "LeetModelDomainLeaseExpired",
}
actual = {rule["alert"] for _, rule in alerts}
if actual != expected:
    raise SystemExit(f"告警覆盖集合不匹配：missing={expected-actual}, extra={actual-expected}")

expected_severities = Counter({
    ("LeetModelServiceMetricsUnavailable", "critical"): 1,
    ("LeetModelServiceDiscoveryIncomplete", "critical"): 1,
    ("LeetModelTelemetryComponentUnavailable", "warning"): 1,
    ("LeetModelAlertmanagerDisconnected", "critical"): 1,
    ("LeetModelRuleEvaluationFailure", "warning"): 1,
    ("LeetModelOutboxPublishDelayed", "warning"): 1,
    ("LeetModelOutboxPublishDelayed", "critical"): 1,
    ("LeetModelOutboxBlocked", "critical"): 1,
    ("LeetModelOnlineConsumerBacklogHigh", "warning"): 1,
    ("LeetModelOnlineConsumerBacklogHigh", "critical"): 1,
    ("LeetModelOnlineConsumerOldestHigh", "warning"): 1,
    ("LeetModelOnlineConsumerOldestHigh", "critical"): 1,
    ("LeetModelBrokerMetricsUnavailable", "warning"): 1,
    ("LeetModelDeadLetterPresent", "warning"): 1,
    ("LeetModelDeadLetterPresent", "critical"): 1,
    ("LeetModelAiP0QueueWaitHigh", "warning"): 1,
    ("LeetModelAiP0QueueWaitHigh", "critical"): 1,
    ("LeetModelAiBackgroundQueueWaitHigh", "warning"): 1,
    ("LeetModelAiQueueCapacityHigh", "warning"): 1,
    ("LeetModelAiUpstreamResultUnknown", "critical"): 1,
    ("LeetModelDomainLeaseExpired", "warning"): 1,
    ("LeetModelDomainLeaseExpired", "critical"): 1,
})
actual_severities = Counter(
    (rule["alert"], rule.get("labels", {}).get("severity")) for _, rule in alerts
)
if actual_severities != expected_severities:
    raise SystemExit(
        "告警分级组合不匹配："
        f"missing={expected_severities-actual_severities}, "
        f"extra={actual_severities-expected_severities}"
    )
PY

if ! rg -q 'send_resolved:[[:space:]]+true' "${OBSERVABILITY_DIR}/alertmanager.yml" \
    || ! rg -q 'inhibit_rules:' "${OBSERVABILITY_DIR}/alertmanager.yml" \
    || ! rg -q 'drill="true"' "${OBSERVABILITY_DIR}/alertmanager.yml"; then
  echo "Alertmanager 缺少 resolved、抑制或隔离演练路由。" >&2
  exit 1
fi

echo "MET-04 告警规则、注解、Runbook 与路由静态门禁通过。"
