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
      <el-form :model="filters" inline @submit.prevent="load">
        <el-form-item label="时间起点"><el-date-picker v-model="filters.from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss[Z]" placeholder="不限" /></el-form-item>
        <el-form-item label="服务"><el-input v-model="filters.sourceService" clearable placeholder="如 ai-gateway-service" /></el-form-item>
        <el-form-item label="操作"><el-input v-model="filters.operationCode" clearable placeholder="如 AI_QUEUE.CANCEL" /></el-form-item>
        <el-form-item label="风险"><el-select v-model="filters.riskLevel" clearable placeholder="全部"><el-option label="高" value="HIGH" /><el-option label="中" value="MEDIUM" /><el-option label="低" value="LOW" /></el-select></el-form-item>
        <el-form-item label="结果"><el-select v-model="filters.outcome" clearable placeholder="全部"><el-option label="成功" value="SUCCEEDED" /><el-option label="失败" value="FAILED" /><el-option label="处理中" value="PENDING" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit" :loading="loading">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
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
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { searchAdminAudit } from "@/api/admin-audit";

const loading = ref(false);
const unavailable = ref("");
const events = ref([]);
const page = ref(null);
const selected = ref(null);
const detailVisible = ref(false);
const filters = reactive({ from: "", sourceService: "", operationCode: "", riskLevel: "", outcome: "" });
function formatTime(value) { return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—"; }
function query() { return Object.fromEntries(Object.entries({ ...filters, limit: 50 }).filter(([, value]) => value !== "" && value != null)); }
async function load() {
  loading.value = true; unavailable.value = "";
  try { const result = await searchAdminAudit(query()); page.value = result.data; events.value = result.data?.events || []; }
  catch (error) { events.value = []; page.value = null; unavailable.value = error.message || "audit-service 查询失败"; }
  finally { loading.value = false; }
}
function reset() { Object.keys(filters).forEach((key) => { filters[key] = ""; }); load(); }
function openDetail(row) { selected.value = row; detailVisible.value = true; }
async function copyText(value) { try { await navigator.clipboard.writeText(value); ElMessage.success("已复制关联 ID"); } catch { ElMessage.warning("浏览器未允许复制，请手动查看"); } }
function copyTrace(row) { copyText(row.swTraceId || row.traceId); }
onMounted(load);
</script>

<style scoped>
@import '../style.css';
.audit-filters { padding: 20px 24px 4px; }
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
