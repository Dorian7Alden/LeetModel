#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
api_base="${LEETMODEL_API_BASE:-http://localhost:8080}"
admin_username="${LEETMODEL_ADMIN_USERNAME:-admin}"
admin_password="${LEETMODEL_ADMIN_PASSWORD:-123456}"
contest_id="${LEETMODEL_TEST_CONTEST_ID:-3}"

for command_name in curl jq; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少依赖命令: $command_name" >&2
    exit 1
  fi
done

login_response="$(curl --fail-with-body --silent --show-error \
  --request POST "$api_base/api/auth/login" \
  --header 'Content-Type: application/json' \
  --data "$(jq -n --arg username "$admin_username" --arg password "$admin_password" \
    '{username: $username, password: $password}')")"
token="$(jq -er '.data.token' <<<"$login_response")"

import_problem() {
  local problem_file="$1"
  local tag_ids_json="${2:-[]}"
  local title
  title="$(sed -n 's/^# //p' "$problem_file" | head -n 1)"
  if [[ -z "$title" ]]; then
    echo "题目缺少 Markdown 一级标题: $problem_file" >&2
    return 1
  fi

  local query_response existing_id
  query_response="$(curl --fail-with-body --silent --show-error --get \
    "$api_base/api/public/problems" \
    --data-urlencode "keyword=$title" \
    --data-urlencode 'page=1' \
    --data-urlencode 'pageSize=20')"
  existing_id="$(jq -r --arg title "$title" \
    '.data.rows[]? | select(.title == $title) | .id' <<<"$query_response" | head -n 1)"
  if [[ -n "$existing_id" ]]; then
    echo "已存在，跳过: $existing_id $title"
    return 0
  fi

  local payload create_response created_id
  payload="$(jq -n --rawfile content "$problem_file" \
    --arg title "$title" \
    --argjson contestId "$contest_id" \
    --argjson tagIds "$tag_ids_json" \
    '{title: $title, contentMarkdown: $content, contestId: $contestId,
      year: 2025, statementLanguage: "ZH", durationMinutes: 4320,
      difficulty: 3, status: 1, tagIds: $tagIds}')"
  create_response="$(curl --fail-with-body --silent --show-error \
    --request POST "$api_base/api/problems" \
    --header 'Content-Type: application/json' \
    --header "satoken: $token" \
    --data "$payload")"
  created_id="$(jq -er '.data.id' <<<"$create_response")"
  echo "导入成功: $created_id $title"
}

# 导入测试题目并赋予多题目类型标签样例：
# problem-01：楼梯持续磨损（MCM A题） -> 环境生态(6001) + 预测(6101) + 评价(6102) + 回归分析(6201)
import_problem "$script_dir/problem-01/problem.md" '[6001, 6101, 6102, 6201]'
# problem-02：可持续旅游业管理（MCM B题） -> 环境生态(6001) + 预测(6101) + 评价(6102) + 优化(6103) + 线性规划(6203)
import_problem "$script_dir/problem-02/problem.md" '[6001, 6101, 6102, 6103, 6203]'

echo "公开接口复核："
for problem_file in "$script_dir/problem-01/problem.md" "$script_dir/problem-02/problem.md"; do
  title="$(sed -n 's/^# //p' "$problem_file" | head -n 1)"
  problem_id="$(curl --fail-with-body --silent --show-error --get \
    "$api_base/api/public/problems" \
    --data-urlencode "keyword=$title" \
    --data-urlencode 'page=1' \
    --data-urlencode 'pageSize=20' \
    | jq -er --arg title "$title" \
      '.data.rows[] | select(.title == $title) | .id')"
  curl --fail-with-body --silent --show-error \
    "$api_base/api/public/problems/$problem_id" \
    | jq -er --arg title "$title" \
      '.data | select(.title == $title and (.contentMarkdown | length) > 0)
        | {id, title, tagNames, markdownLength: (.contentMarkdown | length)}'
done
