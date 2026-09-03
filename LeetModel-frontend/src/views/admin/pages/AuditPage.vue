<template>
  <div class="domain-page audit-page">
    <section class="domain-hero domain-hero-slate">
      <div class="domain-hero-copy">
        <span class="domain-eyebrow">OPERATION AUDIT</span>
        <h2>把每一次高风险操作还原成可核验的时间线</h2>
        <p>审计页只读中央归档，展示阶段、目标和白名单摘要；它不直接修改业务，也不复制领域数据库。</p>
      </div>
      <div class="domain-actions">
        <el-button class="hero-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新审计</el-button>
      </div>
    </section>

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

    <section v-if="unavailable" class="audit-unavailable">
      <el-icon><WarningFilled /></el-icon><div><strong>中央审计暂不可用</strong><p>{{ unavailable }}</p><small>页面不会用空集合伪装成功；请按 Runbook 检查 audit-service、Broker 和本地 Outbox。</small></div>
    </section>

    <section class="domain-section audit-table-section" v-loading="loading">
      <div class="section-heading"><div><span class="section-kicker">IMMUTABLE ARCHIVE</span><h3>操作事件</h3></div><span class="result-count">本页 {{ events.length }} 条<span v-if="page?.hasMore"> · 还有更多</span></span></div>
      <el-table v-if="events.length" :data="events" row-key="auditEventId" @row-click="openDetail">
        <el-table-column label="发生时间" min-width="170"><template #default="{ row }">{{ formatTime(row.occurredAt) }}</template></el-table-column>
        <el-table-column label="操作 / 阶段" min-width="220"><template #default="{ row }"><strong>{{ row.operationCode }}</strong><br /><el-tag size="small" effect="plain">{{ row.phase }} · {{ row.outcome }}</el-tag></template></el-table-column>
        <el-table-column prop="sourceService" label="来源服务" min-width="150" />
        <el-table-column label="目标" min-width="180"><template #default="{ row }">{{ row.targetType }}<br /><code>{{ row.targetId || '—' }}</code></template></el-table-column>
        <el-table-column label="操作者" min-width="150"><template #default="{ row }">{{ row.actorId || '—' }}<br /><small>{{ row.actorType || '—' }}</small></template></el-table-column>
        <el-table-column label="关联" min-width="150"><template #default="{ row }"><el-button v-if="row.traceId || row.swTraceId" link type="primary" @click.stop="copyTrace(row)">{{ row.swTraceId ? '复制 SkyWalking Trace' : '复制 Trace' }}</el-button><span v-else>—</span></template></el-table-column>
      </el-table>
      <el-empty v-else description="暂无符合条件的审计事件" />
    </section>

    <el-drawer v-model="detailVisible" title="操作时间线" size="560px">
      <template v-if="selected">
        <div class="audit-detail-head"><el-tag :type="selected.outcome === 'FAILED' ? 'danger' : 'success'">{{ selected.outcome }}</el-tag><strong>{{ selected.operationCode }}</strong><span>{{ selected.sourceService }}</span></div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="操作 ID"><code>{{ selected.operationId }}</code></el-descriptions-item>
          <el-descriptions-item label="目标">{{ selected.targetType }} / {{ selected.targetId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="操作者">{{ selected.actorId || '—' }}（{{ selected.actorType || '—' }}）</el-descriptions-item>
          <el-descriptions-item label="原因">{{ selected.reason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="失败码">{{ selected.failureCode || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h4>白名单摘要差异</h4><div class="summary-diff"><pre>{{ JSON.stringify({ before: selected.beforeSummary, after: selected.afterSummary }, null, 2) }}</pre></div>
        <div class="detail-links"><el-button v-if="selected.traceId" link type="primary" @click="copyText(selected.traceId)">复制 Trace ID</el-button><el-button v-if="selected.swTraceId" link type="primary" @click="copyText(selected.swTraceId)">复制 SkyWalking ID</el-button></div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { searchAdminAudit } from "@/api/admin-audit";

const loading = ref(false);
const unavailable = ref("");
const events = ref([]);
const page = ref(null);
const selected = ref(null);
const detailVisible = ref(false);
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
    events.value = [];
    page.value = null;
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

function openDetail(row) {
  selected.value = row;
  detailVisible.value = true;
}

async function copyText(value) {
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success("已复制关联 ID");
  } catch {
    ElMessage.warning("浏览器未允许复制，请手动查看");
  }
}

function copyTrace(row) {
  copyText(row.swTraceId || row.traceId);
}

onMounted(load);
</script>

<style scoped>
@import '../style.css';
.audit-filters { padding: 18px 24px 8px; }
.audit-filter-form { width: 100%; }
.filters-row { display: flex; flex-wrap: wrap; align-items: center; }
.filters-main { gap: 2px 8px; }
.filters-advanced { gap: 2px 8px; margin-top: 10px; padding-top: 14px; border-top: 1px dashed #e2e8f0; }
.opt-label { float: left; }
.opt-service { float: right; color: #94a3b8; font-size: 11px; margin-left: 12px; }
.toggle-adv-btn { margin-left: 6px; font-size: 13px; }
.audit-table-section { margin-top: 18px; }
.section-heading { display: flex; justify-content: space-between; align-items: end; margin-bottom: 16px; }
.section-heading h3 { margin: 4px 0 0; }
.section-kicker { color: #64748b; font-size: 11px; letter-spacing: .12em; }
.result-count { color: #64748b; font-size: 13px; }
.audit-unavailable { display: flex; gap: 14px; align-items: flex-start; margin: 18px 0; padding: 16px 20px; border: 1px solid #fed7aa; border-radius: 14px; background: #fff7ed; color: #9a3412; }
.audit-unavailable .el-icon { font-size: 22px; margin-top: 2px; }.audit-unavailable p { margin: 4px 0; }.audit-unavailable small { color: #c2410c; }
.audit-detail-head { display: flex; gap: 10px; align-items: center; margin-bottom: 18px; }.audit-detail-head span { color: #64748b; margin-left: auto; }
.summary-diff { border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; padding: 10px; overflow: auto; }.summary-diff pre { margin: 0; font-size: 12px; }
.detail-links { margin-top: 18px; }
</style>
