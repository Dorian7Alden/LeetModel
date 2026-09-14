<template>
  <div class="messaging-operations">
    <div class="messaging-toolbar">
      <el-select v-model="selectedService" placeholder="选择服务" class="service-select" @change="selectService">
        <el-option v-for="item in services" :key="item.service" :label="serviceLabel(item.service)" :value="item.service">
          <span>{{ serviceLabel(item.service) }}</span>
          <small :class="alertLevel(item)">{{ pendingCount(item) }} 待投递</small>
        </el-option>
      </el-select>
      <div v-if="current" class="service-facts">
        <span><small>待投递</small><strong>{{ pendingCount(current) }}</strong></span>
        <span><small>最老积压</small><strong>{{ current.oldestPendingSeconds || 0 }} 秒</strong></span>
        <span><small>阻塞</small><strong :class="{ danger: Number(current.outbox?.BLOCKED || 0) > 0 }">{{ current.outbox?.BLOCKED || 0 }}</strong></span>
        <span><small>消费器</small><strong>{{ current.consumers?.length || 0 }}</strong></span>
      </div>
      <el-button :loading="loading || eventsLoading" @click="loadOverview"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <el-alert v-if="loadError" title="消息运维数据加载失败，已保留最近结果" type="warning" :closable="false" show-icon />
    <el-alert v-if="unavailable.length" type="warning" :closable="false" show-icon
      :title="`部分服务暂不可用：${unavailable.join('、')}`" />
    <el-alert v-if="alertingServices.length" type="error" :closable="false" show-icon
      :title="`消息水位告警：${alertingServices.join('、')}（Outbox 最老待投递超过 30 秒或存在阻塞）`" />

    <AdminSubnav :model-value="activeResource" :items="resourceViews" aria-label="消息运维资源" @update:model-value="selectResource" />
    <el-alert v-if="resourceError" :title="resourceError" type="warning" :closable="false" show-icon />

    <section v-if="activeResource === 'consumers' && current" class="messaging-panel">
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

    <section v-else-if="activeResource === 'outbox'" class="messaging-panel">
      <div class="section-toolbar compact">
        <div class="messaging-filters">
          <el-select v-model="outboxStatus" clearable placeholder="全部状态" style="width: 130px" @change="loadResource">
            <el-option v-for="status in ['PENDING','SENDING','PUBLISHED','BLOCKED']" :key="status" :label="status" :value="status" />
          </el-select>
          <el-button :disabled="!selectedService" :loading="eventsLoading" @click="loadResource">查询</el-button>
        </div>
      </div>
      <el-table :data="outbox" size="small" v-loading="eventsLoading" empty-text="暂无事件">
        <el-table-column prop="eventType" label="事件类型" min-width="190" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column prop="eventId" label="Event ID" min-width="250" show-overflow-tooltip />
        <el-table-column prop="traceId" label="Trace ID" min-width="190" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }"><el-button v-if="['PUBLISHED','BLOCKED'].includes(row.status)" link type="warning" @click="replay(row)">补发</el-button></template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else-if="activeResource === 'inbox'" class="messaging-panel">
      <el-table :data="inbox" size="small" v-loading="eventsLoading" empty-text="暂无消费事实">
        <el-table-column prop="eventType" label="事件类型" min-width="190" />
        <el-table-column prop="consumerGroup" label="Consumer Group" min-width="240" />
        <el-table-column prop="eventId" label="Event ID" min-width="250" show-overflow-tooltip />
        <el-table-column prop="traceId" label="Trace ID" min-width="190" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="consumedAt" label="消费时间" min-width="170" />
      </el-table>
    </section>

    <section v-else-if="activeResource === 'dlq'" class="messaging-panel">
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

    <section v-else class="messaging-panel">
      <div class="section-toolbar compact">
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
import AdminSubnav from "../components/AdminSubnav.vue";
import { getMessagingOverview, getMessagingTrace, listMessagingOutbox, listMessagingInbox, listMessagingDeadLetters, replayMessagingOutbox, replayMessagingDeadLetters, setMessagingConsumerPaused } from "@/api/admin-messaging";

const loading = ref(false);
const eventsLoading = ref(false);
const traceLoading = ref(false);
const loadError = ref(false);
const resourceError = ref("");
const activeResource = ref("consumers");
const resourceViews = [
  { value: "consumers", label: "消费器", icon: "Operation" },
  { value: "outbox", label: "Outbox", icon: "Upload" },
  { value: "inbox", label: "Inbox", icon: "Download" },
  { value: "dlq", label: "死信", icon: "Warning" },
  { value: "trace", label: "Trace", icon: "Connection" },
];
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
  loadError.value = false;
  try {
    const { data } = await getMessagingOverview();
    services.value = data?.services || [];
    unavailable.value = data?.unavailableServices || [];
    if (!services.value.some((item) => item.service === selectedService.value)) selectedService.value = services.value[0]?.service || "";
    await loadResource();
  } catch (error) {
    loadError.value = true;
    ElMessage.error(error.message || "消息运维数据加载失败");
  } finally { loading.value = false; }
}
async function selectService(service) { selectedService.value = service; dlqConsumerGroup.value = ""; await loadResource(); }
async function selectResource(resource) { activeResource.value = resource; await loadResource(); }
async function loadResource() {
  resourceError.value = "";
  const remoteResource = ["outbox", "inbox", "dlq"].includes(activeResource.value);
  if (remoteResource) eventsLoading.value = true;
  try {
    if (activeResource.value === "outbox") await loadOutbox();
    if (activeResource.value === "inbox") await loadInbox();
    if (activeResource.value === "dlq") await loadDeadLetters();
  } catch (error) {
    resourceError.value = error.message || "当前消息资源加载失败";
  } finally {
    if (remoteResource) eventsLoading.value = false;
  }
}
async function loadOutbox() {
  if (!selectedService.value) { outbox.value = []; return; }
  outbox.value = (await listMessagingOutbox(selectedService.value, { status: outboxStatus.value || undefined, limit: 50 })).data || [];
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
  try {
    await ElMessageBox.confirm(`${action} ${row.consumerGroup}？`, "消费器控制", { type: "warning", confirmButtonText: action });
    await setMessagingConsumerPaused(selectedService.value, row.consumerGroup, !row.paused);
    ElMessage.success(`消费器已${action}`);
    await loadOverview();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || `消费器${action}失败`);
  }
}
async function replay(row) {
  try {
    const { value } = await ElMessageBox.prompt("请输入补发原因（至少 3 个字符）", "受控消息补发", { inputValidator: (value) => (value?.trim().length >= 3) || "补发原因至少 3 个字符", confirmButtonText: "确认补发", type: "warning" });
    await replayMessagingOutbox(selectedService.value, [row.eventId], value.trim());
    ElMessage.success("事件已重新进入 Outbox 待投递队列");
    await loadOverview();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || "Outbox 补发失败");
  }
}
async function replayDlq() {
  const eventIds = [...new Set(dlqEventIds.value.split(/[,\s]+/).map((value) => value.trim()).filter(Boolean))];
  if (!eventIds.length || eventIds.length > 20) return ElMessage.warning("请输入 1–20 个 eventId");
  try {
    const { value } = await ElMessageBox.prompt("请输入死信恢复原因（至少 3 个字符）", "受控 DLQ 重放", { inputValidator: (text) => (text?.trim().length >= 3) || "恢复原因至少 3 个字符", confirmButtonText: "核验并重放", type: "warning" });
    await replayMessagingDeadLetters(selectedService.value, dlqConsumerGroup.value, eventIds, value.trim());
    ElMessage.success("DLQ 已核验，原事件已进入源服务 Outbox");
    dlqEventIds.value = "";
    await loadOverview();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || "DLQ 重放失败");
  }
}
async function loadTrace() {
  if (!traceId.value.trim()) return ElMessage.warning("请输入 traceId");
  traceLoading.value = true;
  resourceError.value = "";
  try { traceResult.value = (await getMessagingTrace(traceId.value.trim())).data; }
  catch (error) { resourceError.value = error.message || "Trace 关联查询失败"; }
  finally { traceLoading.value = false; }
}
onMounted(loadOverview);
</script>

<style scoped>
.messaging-operations { display: grid; min-width: 0; gap: 12px; padding: 12px; }
.messaging-toolbar { display: flex; min-width: 0; align-items: stretch; gap: 12px; }
.service-select { width: 220px; flex: 0 0 220px; }
.service-facts { display: flex; min-width: 0; flex: 1; overflow-x: auto; background: var(--lm-admin-surface-subtle); border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.service-facts > span { display: flex; min-width: 116px; align-items: center; gap: 8px; padding: 6px 12px; border-right: 1px solid var(--lm-admin-border); white-space: nowrap; }
.service-facts small { color: var(--lm-admin-text-muted); font-size: 10px; }.service-facts strong { color: var(--lm-admin-text-strong); font-size: 13px; }.service-facts .danger { color: var(--lm-admin-danger); }
.messaging-operations > :deep(.admin-subnav) { padding: 0 8px; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.messaging-panel { min-width: 0; overflow: hidden; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); background: var(--lm-admin-surface); }
.section-toolbar, .messaging-filters, .trace-summary { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.compact { padding: 10px 12px; border-bottom: 1px solid var(--lm-admin-border); }
.domain-backlog-list, .trace-summary { display: flex; flex-wrap: wrap; gap: 8px; padding: 10px 12px; border-top: 1px solid var(--lm-admin-border); }
.dlq-replay-form { display: grid; grid-template-columns: 320px minmax(260px, 1fr) auto; gap: 12px; margin-top: 14px; }
.messaging-panel > .dlq-replay-form { margin: 0; padding: 10px 12px; border-top: 1px solid var(--lm-admin-border); }
.domain-backlog-list span, .trace-summary span { min-width: 122px; padding: 8px 10px; border-radius: var(--lm-admin-radius-control); background: var(--lm-admin-surface-subtle); }
.domain-backlog-list small, .trace-summary small { display: block; color: var(--lm-admin-text-muted); font-size: 10px; }.domain-backlog-list strong, .trace-summary strong { display: block; font-size: 16px; }
@media (max-width: 1100px) { .messaging-toolbar { flex-wrap: wrap; }.service-facts { order: 3; width: 100%; flex-basis: 100%; } }
@media (max-width: 900px) { .dlq-replay-form { grid-template-columns: 1fr; }.service-select { width: min(100%, 260px); } }
</style>
