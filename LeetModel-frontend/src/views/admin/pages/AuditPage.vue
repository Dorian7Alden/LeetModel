<template>
  <div class="domain-page audit-page">
    <section class="domain-section audit-filters">
      <el-form :model="filters" inline @submit.prevent="load" class="audit-filter-form">
        <div class="filters-row filters-main">
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="起始时间"
              end-placeholder="截止时间"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
              :shortcuts="pickerShortcuts"
              clearable
              @change="onTimeRangeChange"
              style="width: 330px"
            />
          </el-form-item>

          <el-form-item label="服务">
            <el-select
              v-model="filters.sourceService"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="全部服务 / 可选或输入"
              style="width: 210px"
            >
              <el-option
                v-for="item in serviceOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="操作">
            <el-select
              v-model="filters.operationCode"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="全部操作 / 可选或输入"
              style="width: 260px"
            >
              <el-option
                v-for="item in filteredOperationOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
                <span class="opt-label">{{ item.label }}</span>
                <span v-if="item.service" class="opt-service">{{ item.service }}</span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="风险">
            <el-select
              v-model="filters.riskLevel"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="全部"
              style="width: 120px"
            >
              <el-option label="高 (HIGH)" value="HIGH" />
              <el-option label="中 (MEDIUM)" value="MEDIUM" />
              <el-option label="低 (LOW)" value="LOW" />
            </el-select>
          </el-form-item>

          <el-form-item label="结果">
            <el-select
              v-model="filters.outcome"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="全部"
              style="width: 120px"
            >
              <el-option label="成功 (SUCCEEDED)" value="SUCCEEDED" />
              <el-option label="失败 (FAILED)" value="FAILED" />
              <el-option label="处理中 (PENDING)" value="PENDING" />
            </el-select>
          </el-form-item>

          <el-form-item class="filter-actions">
            <el-button type="primary" native-type="submit" :loading="loading">
              <el-icon><Search /></el-icon>查询
            </el-button>
            <el-button @click="reset">重置</el-button>
            <el-button link type="primary" class="toggle-adv-btn" @click="showAdvanced = !showAdvanced">
              {{ showAdvanced ? '收起精确条件' : '精确标识筛选' }}
              <el-icon><ArrowUp v-if="showAdvanced" /><ArrowDown v-else /></el-icon>
            </el-button>
          </el-form-item>
        </div>

        <transition name="el-zoom-in-top">
          <div v-show="showAdvanced" class="filters-row filters-advanced">
            <el-form-item label="操作者">
              <el-input v-model="filters.actorId" clearable placeholder="如 1001 / admin" style="width: 150px" />
            </el-form-item>

            <el-form-item label="目标类型">
              <el-select
                v-model="filters.targetType"
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="全部类型 / 可选或输入"
                style="width: 190px"
              >
                <el-option v-for="item in targetTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="目标 ID">
              <el-input v-model="filters.targetId" clearable placeholder="实体业务 ID" style="width: 150px" />
            </el-form-item>

            <el-form-item label="操作 ID">
              <el-input v-model="filters.operationId" clearable placeholder="精确 operationId" style="width: 170px" />
            </el-form-item>

            <el-form-item label="Trace ID">
              <el-input v-model="filters.traceId" clearable placeholder="精确 traceId / 链路标识" style="width: 200px" />
            </el-form-item>
          </div>
        </transition>
      </el-form>
    </section>

    <AdminStatePanel v-if="unavailable" type="error" title="中央审计暂不可用" action-label="重新查询" @action="load">{{ unavailable }}</AdminStatePanel>

    <section class="domain-section audit-table-section" v-loading="loading">
      <div class="result-bar"><span>最近 {{ events.length }} 条</span><strong v-if="page?.hasMore">仍有更早记录，可缩小筛选范围</strong></div>
      <el-table v-if="events.length" :data="events" row-key="auditEventId" @row-click="openDetail">
        <el-table-column label="发生时间" min-width="170"><template #default="{ row }">{{ formatTime(row.occurredAt) }}</template></el-table-column>
        <el-table-column label="操作" min-width="240"><template #default="{ row }"><strong class="primary-cell">{{ row.operationCode }}</strong><span class="secondary-cell">{{ row.sourceService }} · {{ row.phase }}</span></template></el-table-column>
        <el-table-column label="结果" width="106"><template #default="{ row }"><AdminStatusBadge :status="row.outcome || 'UNKNOWN'" :label="outcomeLabel(row.outcome)" /></template></el-table-column>
        <el-table-column label="目标" min-width="190"><template #default="{ row }"><strong class="primary-cell">{{ row.targetType || '—' }}</strong><span class="secondary-cell">{{ row.targetId || '—' }}</span></template></el-table-column>
        <el-table-column label="操作者" min-width="150"><template #default="{ row }"><strong class="primary-cell">{{ row.actorId || '—' }}</strong><span class="secondary-cell">{{ row.actorType || '—' }}</span></template></el-table-column>
        <el-table-column label="" width="62" align="right"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <AdminStatePanel v-else :type="hasFilters ? 'filtered' : 'empty'" :title="hasFilters ? '没有符合条件的审计事件' : '暂无审计事件'" />
    </section>

    <el-drawer v-model="detailVisible" title="操作时间线" size="560px">
      <template v-if="selected">
        <div class="audit-detail-head"><AdminStatusBadge :status="selected.outcome || 'UNKNOWN'" :label="outcomeLabel(selected.outcome)" /><strong>{{ selected.operationCode }}</strong><span>{{ selected.sourceService }}</span></div>
        <el-alert v-if="detailError" :title="detailError" type="warning" :closable="false" show-icon />
        <el-timeline v-if="detailEvents.length > 1" v-loading="detailLoading" class="operation-timeline">
          <el-timeline-item v-for="event in detailEvents" :key="event.auditEventId" :timestamp="formatTime(event.occurredAt)" placement="top" :type="event.outcome === 'FAILED' ? 'danger' : event.outcome === 'SUCCEEDED' ? 'success' : 'primary'">
            <button type="button" :class="{ active: event.auditEventId === selected.auditEventId }" @click="selected = event"><strong>{{ event.phase }}</strong><span>{{ outcomeLabel(event.outcome) }}</span></button>
          </el-timeline-item>
        </el-timeline>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="操作 ID"><code>{{ selected.operationId }}</code></el-descriptions-item>
          <el-descriptions-item label="目标">{{ selected.targetType }} / {{ selected.targetId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="操作者">{{ selected.actorId || '—' }}（{{ selected.actorType || '—' }}）</el-descriptions-item>
          <el-descriptions-item label="原因">{{ selected.reason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="失败码">{{ selected.failureCode || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h4>摘要差异</h4>
        <div v-if="summaryRows.length" class="summary-diff">
          <div class="diff-head"><span>字段</span><span>变更前</span><span>变更后</span></div>
          <div v-for="row in summaryRows" :key="row.key" class="diff-row" :class="{ changed: row.before !== row.after }"><strong>{{ row.key }}</strong><span>{{ row.before }}</span><span>{{ row.after }}</span></div>
        </div>
        <AdminStatePanel v-else type="empty" title="此阶段没有摘要字段" />
        <div class="detail-links"><el-button v-if="selected.traceId" @click="copyText(selected.traceId)">复制 Trace ID</el-button><el-button v-if="selected.swTraceId" @click="copyText(selected.swTraceId)">复制 SkyWalking ID</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { searchAdminAudit } from "@/api/admin-audit";

const loading = ref(false);
const unavailable = ref("");
const events = ref([]);
const page = ref(null);
const selected = ref(null);
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref("");
const detailEvents = ref([]);
const showAdvanced = ref(false);

const timeRange = ref([]);

const pickerShortcuts = [
  {
    text: "最近 1 小时",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setTime(start.getTime() - 3600 * 1000);
      return [start, end];
    }
  },
  {
    text: "最近 24 小时",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setTime(start.getTime() - 24 * 3600 * 1000);
      return [start, end];
    }
  },
  {
    text: "最近 7 天",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setTime(start.getTime() - 7 * 24 * 3600 * 1000);
      return [start, end];
    }
  }
];

const serviceOptions = ref([
  { label: "user-service (用户服务)", value: "user-service" },
  { label: "problem-service (题目服务)", value: "problem-service" },
  { label: "team-service (团队服务)", value: "team-service" },
  { label: "submission-service (提交服务)", value: "submission-service" },
  { label: "ranking-service (排行服务)", value: "ranking-service" },
  { label: "ai-gateway-service (AI网关服务)", value: "ai-gateway-service" },
  { label: "ai-review-service (AI评审服务)", value: "ai-review-service" },
  { label: "ai-suggestion-service (AI建议服务)", value: "ai-suggestion-service" },
  { label: "ai-assistant-service (AI客服服务)", value: "ai-assistant-service" },
  { label: "ai-evaluation-service (AI评价服务)", value: "ai-evaluation-service" },
  { label: "audit-service (审计服务)", value: "audit-service" },
  { label: "admin-service (管理服务)", value: "admin-service" }
]);

const operationOptions = ref([
  { label: "AUTH.LOGIN_SUCCESS (登录成功)", value: "AUTH.LOGIN_SUCCESS", service: "user-service" },
  { label: "AUTH.LOGIN_FAILED (登录失败)", value: "AUTH.LOGIN_FAILED", service: "user-service" },
  { label: "USER.PASSWORD_CHANGE (密码修改)", value: "USER.PASSWORD_CHANGE", service: "user-service" },
  { label: "USER.STATUS_CHANGE (状态变更)", value: "USER.STATUS_CHANGE", service: "user-service" },
  { label: "USER.ROLE_CHANGE (角色变更)", value: "USER.ROLE_CHANGE", service: "user-service" },
  { label: "ROLE.PERMISSION_CHANGE (权限变更)", value: "ROLE.PERMISSION_CHANGE", service: "user-service" },
  { label: "PROBLEM.CREATE (题目创建)", value: "PROBLEM.CREATE", service: "problem-service" },
  { label: "PROBLEM.UPDATE (题目更新)", value: "PROBLEM.UPDATE", service: "problem-service" },
  { label: "PROBLEM.DELETE (题目删除)", value: "PROBLEM.DELETE", service: "problem-service" },
  { label: "PROBLEM.ATTACHMENT_DELETE (附件删除)", value: "PROBLEM.ATTACHMENT_DELETE", service: "problem-service" },
  { label: "CONTEST.UPDATE (比赛更新)", value: "CONTEST.UPDATE", service: "problem-service" },
  { label: "SUBMISSION.FINALIZE (提交终态)", value: "SUBMISSION.FINALIZE", service: "submission-service" },
  { label: "AI_QUEUE.CANCEL (AI队列取消)", value: "AI_QUEUE.CANCEL", service: "ai-gateway-service" },
  { label: "EVALUATION.PAUSE (评价暂停)", value: "EVALUATION.PAUSE", service: "ai-evaluation-service" },
  { label: "EVALUATION.RESUME (评价恢复)", value: "EVALUATION.RESUME", service: "ai-evaluation-service" },
  { label: "EVALUATION.CANCEL (评价取消)", value: "EVALUATION.CANCEL", service: "ai-evaluation-service" },
  { label: "EVALUATION.RETRY (评价重试)", value: "EVALUATION.RETRY", service: "ai-evaluation-service" },
  { label: "ASSISTANT_CONFIG.ACTIVATE (客服配置激活)", value: "ASSISTANT_CONFIG.ACTIVATE", service: "ai-assistant-service" },
  { label: "ASSISTANT_CONFIG.ROLLBACK (客服配置回滚)", value: "ASSISTANT_CONFIG.ROLLBACK", service: "ai-assistant-service" },
  { label: "WEIGHT_SCHEME.DEACTIVATE (权重方案停用)", value: "WEIGHT_SCHEME.DEACTIVATE", service: "ai-evaluation-service" },
  { label: "CONSUMER.PAUSE (消费暂停)", value: "CONSUMER.PAUSE" },
  { label: "CONSUMER.RESUME (消费恢复)", value: "CONSUMER.RESUME" },
  { label: "OUTBOX.REPLAY (Outbox重放)", value: "OUTBOX.REPLAY" },
  { label: "DLQ.REPLAY (死信重放)", value: "DLQ.REPLAY" },
  { label: "RANKING.REBUILD (排行重建)", value: "RANKING.REBUILD", service: "ranking-service" },
  { label: "AUDIT.SEARCH_EXPORT (审计导出)", value: "AUDIT.SEARCH_EXPORT", service: "audit-service" }
]);

const targetTypeOptions = [
  { label: "USER (用户)", value: "USER" },
  { label: "ROLE (角色)", value: "ROLE" },
  { label: "PROBLEM (题目)", value: "PROBLEM" },
  { label: "ATTACHMENT (附件)", value: "ATTACHMENT" },
  { label: "CONTEST (比赛)", value: "CONTEST" },
  { label: "SUBMISSION (提交)", value: "SUBMISSION" },
  { label: "AI_CALL_TASK (AI调用任务)", value: "AI_CALL_TASK" },
  { label: "EVALUATION_TASK (评价任务)", value: "EVALUATION_TASK" },
  { label: "ASSISTANT_CONFIG (客服配置)", value: "ASSISTANT_CONFIG" },
  { label: "WEIGHT_SCHEME (权重方案)", value: "WEIGHT_SCHEME" },
  { label: "MESSAGE_CONSUMER (消息消费组)", value: "MESSAGE_CONSUMER" },
  { label: "MESSAGE_OUTBOX (消息本地表)", value: "MESSAGE_OUTBOX" },
  { label: "MESSAGE_DLQ (死信队列)", value: "MESSAGE_DLQ" },
  { label: "RANKING_SCOPE (排行范围)", value: "RANKING_SCOPE" },
  { label: "AUDIT_EXPORT (审计导出)", value: "AUDIT_EXPORT" }
];

const filters = reactive({
  from: "",
  to: "",
  sourceService: "",
  operationCode: "",
  riskLevel: "",
  outcome: "",
  actorId: "",
  targetType: "",
  targetId: "",
  operationId: "",
  traceId: ""
});

const filteredOperationOptions = computed(() => {
  if (!filters.sourceService) {
    return operationOptions.value;
  }
  const matched = [];
  const others = [];
  for (const op of operationOptions.value) {
    if (op.service === filters.sourceService) {
      matched.push(op);
    } else {
      others.push(op);
    }
  }
  return [...matched, ...others];
});
const hasFilters = computed(() => Object.values(filters).some(Boolean));
const summaryRows = computed(() => {
  const before = selected.value?.beforeSummary || {};
  const after = selected.value?.afterSummary || {};
  return [...new Set([...Object.keys(before), ...Object.keys(after)])].sort().map(key => ({
    key,
    before: displaySummaryValue(before[key]),
    after: displaySummaryValue(after[key]),
  }));
});

function onTimeRangeChange(val) {
  if (val && val.length === 2) {
    filters.from = val[0];
    filters.to = val[1];
  } else {
    filters.from = "";
    filters.to = "";
  }
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";
}

function outcomeLabel(value) {
  return ({ SUCCEEDED: "成功", FAILED: "失败", PENDING: "处理中", REJECTED: "已拒绝" })[value] || "未知";
}

function displaySummaryValue(value) {
  if (value == null || value === "") return "—";
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}

function query() {
  return Object.fromEntries(
    Object.entries({ ...filters, limit: 50 }).filter(([, value]) => value !== "" && value != null)
  );
}

function enrichOptionsFromEvents(list) {
  if (!Array.isArray(list)) return;
  const existingServices = new Set(serviceOptions.value.map(s => s.value));
  const existingOps = new Set(operationOptions.value.map(o => o.value));

  for (const item of list) {
    if (item.sourceService && !existingServices.has(item.sourceService)) {
      existingServices.add(item.sourceService);
      serviceOptions.value.push({ label: item.sourceService, value: item.sourceService });
    }
    if (item.operationCode && !existingOps.has(item.operationCode)) {
      existingOps.add(item.operationCode);
      operationOptions.value.push({
        label: item.operationCode,
        value: item.operationCode,
        service: item.sourceService
      });
    }
  }
}

async function load() {
  loading.value = true;
  unavailable.value = "";
  try {
    const result = await searchAdminAudit(query());
    page.value = result.data;
    const eventList = result.data?.events || [];
    events.value = eventList;
    enrichOptionsFromEvents(eventList);
  } catch (error) {
    unavailable.value = error.message || "audit-service 查询失败";
  } finally {
    loading.value = false;
  }
}

function reset() {
  timeRange.value = [];
  Object.keys(filters).forEach((key) => {
    filters[key] = "";
  });
  load();
}

async function openDetail(row) {
  selected.value = row;
  detailEvents.value = [row];
  detailError.value = "";
  detailVisible.value = true;
  if (!row.operationId) return;
  detailLoading.value = true;
  try {
    const result = await searchAdminAudit({ operationId: row.operationId, limit: 100 });
    const stages = result.data?.events || [];
    detailEvents.value = stages.sort((left, right) => new Date(left.occurredAt) - new Date(right.occurredAt));
    selected.value = detailEvents.value.find(event => event.auditEventId === row.auditEventId) || row;
  } catch (error) {
    detailError.value = error.message || "操作阶段加载失败";
  } finally {
    detailLoading.value = false;
  }
}

async function copyText(value) {
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success("已复制关联 ID");
  } catch {
    ElMessage.warning("浏览器未允许复制，请手动查看");
  }
}

onMounted(load);
</script>

<style scoped>
@import '../style.css';
.audit-page { display: flex; min-width: 0; flex-direction: column; gap: 12px; }
.audit-filters { padding: 12px 12px 0; }
.audit-filter-form { width: 100%; }
.filters-row { display: flex; flex-wrap: wrap; align-items: center; }
.filters-main { gap: 2px 8px; }
.filters-advanced { gap: 2px 8px; margin-top: 2px; padding-top: 10px; border-top: 1px solid var(--lm-admin-border); }
.audit-filter-form :deep(.el-form-item) { margin-right: 0; margin-bottom: 10px; }
.opt-label { float: left; }
.opt-service { float: right; color: #94a3b8; font-size: 11px; margin-left: 12px; }
.toggle-adv-btn { margin-left: 6px; font-size: 13px; }
.audit-table-section { min-width: 0; overflow: hidden; }
.result-bar { display: flex; min-height: 34px; align-items: center; justify-content: space-between; gap: 12px; padding: 6px 12px; color: var(--lm-admin-text-muted); background: var(--lm-admin-surface-subtle); border-bottom: 1px solid var(--lm-admin-border); font-size: 11px; }
.result-bar strong { color: var(--lm-admin-warning); font-weight: 600; }
.audit-table-section :deep(.el-table__row) { cursor: pointer; }
.primary-cell, .secondary-cell { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.primary-cell { color: var(--lm-admin-text-strong); font-size: 12px; }.secondary-cell { margin-top: 2px; color: var(--lm-admin-text-muted); font-size: 10px; }
.audit-detail-head { display: flex; gap: 10px; align-items: center; margin-bottom: 18px; }.audit-detail-head span { color: #64748b; margin-left: auto; }
.operation-timeline { margin: 16px 0 4px; padding-left: 6px; }
.operation-timeline button { display: flex; width: 100%; align-items: center; justify-content: space-between; padding: 8px 10px; color: var(--lm-admin-text-default); background: var(--lm-admin-surface-subtle); border: 1px solid transparent; border-radius: var(--lm-admin-radius-control); cursor: pointer; text-align: left; }
.operation-timeline button.active { color: var(--lm-admin-primary); background: #eff6ff; border-color: #bfdbfe; }.operation-timeline button span { color: var(--lm-admin-text-muted); font-size: 11px; }
.summary-diff { overflow: hidden; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.diff-head, .diff-row { display: grid; grid-template-columns: minmax(90px, .7fr) minmax(0, 1fr) minmax(0, 1fr); }
.diff-head { color: var(--lm-admin-text-muted); background: var(--lm-admin-surface-subtle); font-size: 10px; }
.diff-head span, .diff-row > * { min-width: 0; padding: 8px 10px; border-right: 1px solid var(--lm-admin-border); }.diff-head span:last-child, .diff-row > *:last-child { border-right: 0; }
.diff-row { border-top: 1px solid var(--lm-admin-border); font-size: 11px; }.diff-row > span { overflow-wrap: anywhere; }.diff-row.changed { background: #fffbeb; }.diff-row strong { color: var(--lm-admin-text-strong); }
.detail-links { margin-top: 18px; }
@media (max-width: 1100px) { .filter-actions { width: 100%; }.result-bar { align-items: flex-start; flex-direction: column; } }
</style>
