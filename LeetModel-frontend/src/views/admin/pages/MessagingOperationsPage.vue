<template>
  <div class="messaging-operations">
    <div class="section-toolbar">
      <div>
        <h3>可靠消息运维</h3>
        <p>统一查看 Outbox、Inbox、领域积压和消费器状态；所有重放均需人工确认。</p>
      </div>
      <el-button :loading="loading" @click="loadOverview"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <el-alert v-if="unavailable.length" type="warning" :closable="false" show-icon
      :title="`部分服务暂不可用：${unavailable.join('、')}`" />
    <el-alert v-if="alertingServices.length" type="error" :closable="false" show-icon
      :title="`消息水位告警：${alertingServices.join('、')}（Outbox 最老待投递超过 30 秒或存在阻塞）`" />

    <div class="messaging-service-grid" v-loading="loading">
      <button v-for="item in services" :key="item.service" type="button"
        class="messaging-service-card" :class="[{ active: selectedService === item.service }, alertLevel(item)]"
        @click="selectService(item.service)">
        <span>{{ serviceLabel(item.service) }}</span>
        <strong>{{ pendingCount(item) }}</strong>
        <small>待投递 · 最老 {{ item.oldestPendingSeconds }} 秒</small>
        <em v-if="Number(item.outbox?.BLOCKED || 0) > 0">{{ item.outbox.BLOCKED }} 条阻塞</em>
      </button>
    </div>

    <section v-if="current" class="messaging-panel">
      <div class="section-toolbar compact">
        <div><h3>消费器与领域任务</h3><p>{{ serviceLabel(current.service) }}</p></div>
      </div>
      <el-table :data="current.consumers || []" size="small" empty-text="当前服务无消费器">
        <el-table-column prop="consumerGroup" label="Consumer Group" min-width="240" />
        <el-table-column prop="topic" label="Topic" min-width="190" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="row.paused ? 'warning' : 'success'">{{ row.paused ? "已暂停" : "消费中" }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }"><el-button link :type="row.paused ? 'success' : 'warning'" @click="toggleConsumer(row)">{{ row.paused ? "恢复" : "暂停" }}</el-button></template>
        </el-table-column>
      </el-table>
      <div class="domain-backlog-list">
        <span v-for="(count, key) in current.domainBacklog" :key="key"><small>{{ key }}</small><strong>{{ count }}</strong></span>
      </div>
    </section>

    <section class="messaging-panel">
      <div class="section-toolbar compact">
        <div><h3>Outbox 事件</h3><p>不展示 payload、幂等键或连接信息</p></div>
        <div class="messaging-filters">
          <el-select v-model="outboxStatus" clearable placeholder="全部状态" style="width: 130px" @change="loadOutbox">
            <el-option v-for="status in ['PENDING','SENDING','PUBLISHED','BLOCKED']" :key="status" :label="status" :value="status" />
          </el-select>
          <el-button :disabled="!selectedService" :loading="eventsLoading" @click="loadOutbox">查询</el-button>
        </div>
      </div>
      <el-table :data="outbox" size="small" v-loading="eventsLoading" empty-text="暂无事件">
        <el-table-column prop="eventId" label="Event ID" min-width="280" />
        <el-table-column prop="eventType" label="事件类型" min-width="190" />
        <el-table-column prop="aggregateId" label="聚合 ID" min-width="120" />
        <el-table-column prop="traceId" label="Trace ID" min-width="190" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button v-if="['PUBLISHED','BLOCKED'].includes(row.status)" link type="warning" @click="replay(row)">补发</el-button></template>
        </el-table-column>
      </el-table>
    </section>

    <section class="messaging-panel">
      <div class="section-toolbar compact">
        <div><h3>Inbox 消费事实</h3><p>重复投递由 consumerGroup + eventId 唯一键拦截</p></div>
        <el-button :disabled="!selectedService" :loading="eventsLoading" @click="loadEvents">刷新</el-button>
      </div>
      <el-table :data="inbox" size="small" v-loading="eventsLoading" empty-text="暂无消费事实">
        <el-table-column prop="eventId" label="Event ID" min-width="280" />
        <el-table-column prop="eventType" label="事件类型" min-width="190" />
        <el-table-column prop="consumerGroup" label="Consumer Group" min-width="240" />
        <el-table-column prop="traceId" label="Trace ID" min-width="190" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="consumedAt" label="消费时间" min-width="170" />
      </el-table>
    </section>

    <section class="messaging-panel">
      <div class="section-toolbar compact">
        <div><h3>Broker DLQ</h3><p>死信永不自动回灌；恢复前按 eventId 在 Broker 中核验</p></div>
      </div>
      <el-table :data="deadLetters" size="small" empty-text="当前服务无消费组">
        <el-table-column prop="consumerGroup" label="Consumer Group" min-width="250" />
        <el-table-column prop="topic" label="DLQ Topic" min-width="250" />
        <el-table-column prop="messageCount" label="死信数" width="100" />
        <el-table-column prop="oldestMessageAt" label="最早时间" min-width="170" />
        <el-table-column label="查询状态" width="110">
          <template #default="{ row }"><el-tag :type="row.available ? (row.messageCount ? 'danger' : 'success') : 'info'">{{ row.available ? (row.messageCount ? '有死信' : '空') : 'Broker 不可用' }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div class="dlq-replay-form">
        <el-select v-model="dlqConsumerGroup" placeholder="选择 Consumer Group" style="width: 320px">
          <el-option v-for="item in deadLetters" :key="item.consumerGroup" :label="item.consumerGroup" :value="item.consumerGroup" />
        </el-select>
        <el-input v-model="dlqEventIds" placeholder="eventId，多个用逗号分隔（最多 20 个）" />
        <el-button type="danger" plain :disabled="!dlqConsumerGroup || !dlqEventIds.trim()" @click="replayDlq">核验并重放</el-button>
      </div>
    </section>

    <section class="messaging-panel">
      <div class="section-toolbar compact">
        <div><h3>Trace 关联查询</h3><p>关联消息事件、消费事实与 AI Call ID</p></div>
        <div class="messaging-filters">
          <el-input v-model="traceId" placeholder="输入 traceId" clearable style="width: 300px" @keyup.enter="loadTrace" />
          <el-button type="primary" :loading="traceLoading" @click="loadTrace">追踪</el-button>
        </div>
      </div>
      <div v-if="traceResult" class="trace-summary">
        <span><strong>{{ traceResult.producedEvents?.length || 0 }}</strong><small>生产事件</small></span>
        <span><strong>{{ traceResult.consumedEvents?.length || 0 }}</strong><small>消费事实</small></span>
        <span><strong>{{ traceResult.aiCalls?.length || 0 }}</strong><small>AI 调用</small></span>
      </div>
      <el-table v-if="traceResult" :data="traceResult.aiCalls || []" size="small" empty-text="此链路没有 AI 调用">
        <el-table-column prop="callId" label="AI Call ID" min-width="280" />
        <el-table-column prop="callerService" label="调用服务" min-width="170" />
        <el-table-column prop="featureCode" label="特征" min-width="150" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="totalMs" label="耗时(ms)" width="110" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getMessagingOverview, getMessagingTrace, listMessagingOutbox, listMessagingInbox, listMessagingDeadLetters, replayMessagingOutbox, replayMessagingDeadLetters, setMessagingConsumerPaused } from "@/api/admin-messaging";

const loading = ref(false);
const eventsLoading = ref(false);
const traceLoading = ref(false);
const services = ref([]);
const unavailable = ref([]);
const selectedService = ref("");
const outbox = ref([]);
const inbox = ref([]);
const deadLetters = ref([]);
const dlqConsumerGroup = ref("");
const dlqEventIds = ref("");
const outboxStatus = ref("");
const traceId = ref("");
const traceResult = ref(null);
const current = computed(() => services.value.find((item) => item.service === selectedService.value));
const alertingServices = computed(() => services.value.filter((item) => alertLevel(item) !== "normal").map((item) => serviceLabel(item.service)));
const labels = { "submission-service": "提交派发", "ai-review-service": "AI 评审", "ranking-service": "排行榜", "ai-suggestion-service": "AI 建议", "ai-evaluation-service": "批量评价" };

function serviceLabel(value) { return labels[value] || value; }
function pendingCount(item) { return Number(item.outbox?.PENDING || 0) + Number(item.outbox?.SENDING || 0); }
function alertLevel(item) {
  if (Number(item.outbox?.BLOCKED || 0) > 0 || Number(item.oldestPendingSeconds || 0) >= 300 || pendingCount(item) >= 1000) return "critical";
  if (Number(item.oldestPendingSeconds || 0) >= 30 || pendingCount(item) >= 200) return "warning";
  return "normal";
}
async function loadOverview() {
  loading.value = true;
  try {
    const { data } = await getMessagingOverview();
    services.value = data?.services || [];
    unavailable.value = data?.unavailableServices || [];
    if (!services.value.some((item) => item.service === selectedService.value)) selectedService.value = services.value[0]?.service || "";
    await loadEvents();
  } finally { loading.value = false; }
}
async function selectService(service) { selectedService.value = service; dlqConsumerGroup.value = ""; await loadEvents(); }
async function loadEvents() { await Promise.all([loadOutbox(), loadInbox(), loadDeadLetters()]); }
async function loadOutbox() {
  if (!selectedService.value) { outbox.value = []; return; }
  eventsLoading.value = true;
  try { outbox.value = (await listMessagingOutbox(selectedService.value, { status: outboxStatus.value || undefined, limit: 50 })).data || []; }
  finally { eventsLoading.value = false; }
}
async function loadInbox() {
  if (!selectedService.value) { inbox.value = []; return; }
  inbox.value = (await listMessagingInbox(selectedService.value, { limit: 50 })).data || [];
}
async function loadDeadLetters() {
  if (!selectedService.value) { deadLetters.value = []; return; }
  deadLetters.value = (await listMessagingDeadLetters(selectedService.value)).data || [];
  if (!deadLetters.value.some((item) => item.consumerGroup === dlqConsumerGroup.value)) {
    dlqConsumerGroup.value = deadLetters.value[0]?.consumerGroup || "";
  }
}
async function toggleConsumer(row) {
  const action = row.paused ? "恢复" : "暂停";
  await ElMessageBox.confirm(`${action} ${row.consumerGroup}？`, "消费器控制", { type: "warning", confirmButtonText: action });
  await setMessagingConsumerPaused(selectedService.value, row.consumerGroup, !row.paused);
  ElMessage.success(`消费器已${action}`);
  await loadOverview();
}
async function replay(row) {
  const { value } = await ElMessageBox.prompt("请输入补发原因（至少 3 个字符）", "受控消息补发", { inputValidator: (value) => (value?.trim().length >= 3) || "补发原因至少 3 个字符", confirmButtonText: "确认补发", type: "warning" });
  await replayMessagingOutbox(selectedService.value, [row.eventId], value.trim());
  ElMessage.success("事件已重新进入 Outbox 待投递队列");
  await loadOverview();
}
async function replayDlq() {
  const eventIds = [...new Set(dlqEventIds.value.split(/[,\s]+/).map((value) => value.trim()).filter(Boolean))];
  if (!eventIds.length || eventIds.length > 20) return ElMessage.warning("请输入 1–20 个 eventId");
  const { value } = await ElMessageBox.prompt("请输入死信恢复原因（至少 3 个字符）", "受控 DLQ 重放", { inputValidator: (text) => (text?.trim().length >= 3) || "恢复原因至少 3 个字符", confirmButtonText: "核验并重放", type: "warning" });
  await replayMessagingDeadLetters(selectedService.value, dlqConsumerGroup.value, eventIds, value.trim());
  ElMessage.success("DLQ 已核验，原事件已进入源服务 Outbox");
  dlqEventIds.value = "";
  await loadOverview();
}
async function loadTrace() {
  if (!traceId.value.trim()) return ElMessage.warning("请输入 traceId");
  traceLoading.value = true;
  try { traceResult.value = (await getMessagingTrace(traceId.value.trim())).data; }
  finally { traceLoading.value = false; }
}
onMounted(loadOverview);
</script>

<style scoped>
@import '../style.css';
.messaging-operations { display: grid; gap: 18px; }
.messaging-service-grid { display: grid; grid-template-columns: repeat(5, minmax(150px, 1fr)); gap: 12px; }
.messaging-service-card { position: relative; padding: 16px; text-align: left; border: 1px solid var(--el-border-color-light); border-radius: 12px; background: var(--el-bg-color); cursor: pointer; color: inherit; }
.messaging-service-card.active { border-color: #8b5cf6; box-shadow: 0 0 0 2px rgba(139,92,246,.12); }
.messaging-service-card.warning { border-color: #f59e0b; }.messaging-service-card.critical { border-color: #dc2626; background: rgba(220,38,38,.04); }
.messaging-service-card span, .messaging-service-card small { display: block; }
.messaging-service-card strong { display: block; margin: 8px 0 2px; font-size: 28px; }
.messaging-service-card em { display: inline-block; margin-top: 8px; color: #dc2626; font-style: normal; font-size: 12px; }
.messaging-panel { padding: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 14px; background: var(--el-bg-color); }
.section-toolbar, .messaging-filters, .trace-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-toolbar h3 { margin: 0; }.section-toolbar p { margin: 5px 0 0; color: var(--el-text-color-secondary); }.compact { margin-bottom: 14px; }
.domain-backlog-list, .trace-summary { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 14px; }
.dlq-replay-form { display: grid; grid-template-columns: 320px minmax(260px, 1fr) auto; gap: 12px; margin-top: 14px; }
.domain-backlog-list span, .trace-summary span { min-width: 130px; padding: 10px 14px; border-radius: 10px; background: var(--el-fill-color-light); }
.domain-backlog-list small, .trace-summary small { display: block; color: var(--el-text-color-secondary); }.domain-backlog-list strong, .trace-summary strong { display: block; font-size: 20px; }
@media (max-width: 1100px) { .messaging-service-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 900px) { .dlq-replay-form { grid-template-columns: 1fr; } }
</style>
