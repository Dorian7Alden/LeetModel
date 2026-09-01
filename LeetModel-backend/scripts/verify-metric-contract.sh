#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "metric contract verification failed: $*" >&2
  exit 1
}

OBSERVABILITY_CONFIG="common/common-core/src/main/resources/leetmodel-observability.yml"
rg -q 'http\.server\.requests: true' "$OBSERVABILITY_CONFIG" \
  || fail "HTTP server histogram baseline is missing"

MAIN_SOURCES=$(find . -type f -path '*/src/main/java/*' -name '*.java' -print)
METRIC_SOURCES=$(printf '%s\n' "$MAIN_SOURCES" | xargs rg -l \
  'MeterRegistry|Counter\.builder|Gauge\.builder|Timer\.builder|MultiGauge\.builder' || true)
[[ -n "$METRIC_SOURCES" ]] || fail "no metric sources found"

FORBIDDEN_TAG_PATTERN='(\.tag|\.tags|Tags\.of|\.counter\()[^\n]*(user_?id|team_?id|submission_?id|trace_?id|sw_?(trace|span)_?id|operation_?id|event_?id|task_?id|attempt_?(id|no)|ai_?call_?id|call_?id|message_?id|provider_?response_?id)'
if printf '%s\n' "$METRIC_SOURCES" | xargs rg -n -i "$FORBIDDEN_TAG_PATTERN"; then
  fail "forbidden high-cardinality metric tag found"
fi

required_markers=(
  'leetmodel.messaging.outbox.records'
  'leetmodel.messaging.consumer.backlog'
  'leetmodel.messaging.dlq.records'
  'claim_type'
  'leetmodel.ai.queue.tasks'
  'leetmodel.ai.queue.duration'
  'leetmodel.ai.execution.duration'
  'leetmodel.ai.end_to_end.duration'
  'leetmodel.ai.tokens'
  'leetmodel.ai.cost'
  'upstream_result_unknown'
)
for marker in "${required_markers[@]}"; do
  printf '%s\n' "$MAIN_SOURCES" | xargs rg -q --fixed-strings "$marker" \
    || fail "required metric marker is missing: $marker"
done

echo "metric contract verification passed"
