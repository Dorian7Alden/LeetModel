<template>
  <div class="messaging-operations">
    <!-- 1. 顶部可靠消息集群总体态势条 -->
    <section class="messaging-fleet-strip">
      <div class="fleet-item">
        <span class="fleet-label">集群服务节点</span>
        <strong class="fleet-val text-primary">{{ services.length }}</strong>
      </div>
      <div class="fleet-divider" />
      <div class="fleet-item">
        <span class="fleet-label">全集群待投递</span>
        <strong class="fleet-val" :class="fleetPendingCount > 0 ? 'text-warning' : 'text-success'">
          {{ fleetPendingCount }}
        </strong>
      </div>
      <div class="fleet-divider" />
      <div class="fleet-item">
        <span class="fleet-label">全集群阻塞事件</span>
        <strong class="fleet-val" :class="fleetBlockedCount > 0 ? 'text-danger' : 'text-muted'">
          {{ fleetBlockedCount }}
        </strong>
      </div>
      <div class="fleet-divider" />
      <div class="fleet-item">
        <span class="fleet-label">在线消费组</span>
        <strong class="fleet-val">{{ fleetConsumerCount }}</strong>
      </div>
      <div class="fleet-divider" />
      <div class="fleet-item">
        <span class="fleet-label">集群健康状态</span>
        <div class="fleet-status-wrap">
          <el-tag v-if="unavailable.length" size="small" type="danger" effect="plain">
            {{ unavailable.length }} 个节点离线
          </el-tag>
          <el-tag v-else-if="alertingServices.length" size="small" type="warning" effect="plain">
            {{ alertingServices.length }} 个节点积压
          </el-tag>
          <el-tag v-else size="small" type="success" effect="plain">
            全集群运行正常
          </el-tag>
        </div>
      </div>
      <div class="fleet-action-right">
        <el-button link type="primary" size="small" :loading="loading" @click="loadOverview">
          <el-icon><Refresh /></el-icon>刷新全景
        </el-button>
      </div>
    </section>

    <!-- 2. 服务选择与单节点事实栏 -->
    <div class="service-control-card">
      <div class="service-select-group">
        <span class="select-label">当前服务域:</span>
        <el-select v-model="selectedService" placeholder="选择受控服务" class="service-select" @change="selectService">
          <el-option v-for="item in services" :key="item.service" :label="`${serviceLabel(item.service)} (${item.service})`" :value="item.service">
            <span class="opt-title">{{ serviceLabel(item.service) }}</span>
            <span class="opt-code">{{ item.service }}</span>
            <small :class="['opt-badge', alertLevel(item)]">{{ pendingCount(item) }} 待投递</small>
          </el-option>
        </el-select>
      </div>

      <div v-if="current" class="service-facts">
        <div class="fact-cell">
          <small>Outbox 待投递</small>
          <strong :class="{ 'text-warning': pendingCount(current) > 0 }">{{ pendingCount(current) }}</strong>
        </div>
        <div class="fact-cell">
          <small>最老待投递时延</small>
          <strong :class="{ 'text-warning': Number(current.oldestPendingSeconds || 0) >= 30, 'text-danger': Number(current.oldestPendingSeconds || 0) >= 300 }">
            {{ current.oldestPendingSeconds || 0 }} 秒
          </strong>
        </div>
        <div class="fact-cell">
          <small>投递阻塞</small>
          <strong :class="{ danger: Number(current.outbox?.BLOCKED || 0) > 0, 'text-muted': !Number(current.outbox?.BLOCKED || 0) }">
            {{ current.outbox?.BLOCKED || 0 }}
          </strong>
        </div>
        <div class="fact-cell">
          <small>Inbox 已消费</small>
          <strong>{{ current.inboxConsumed || 0 }}</strong>
        </div>
        <div class="fact-cell">
          <small>消费器组</small>
          <strong>{{ current.consumers?.length || 0 }}</strong>
        </div>
        <div class="fact-cell">
          <small>重放模式</small>
          <el-tag size="small" type="info" effect="plain">{{ current.replayMode || 'STANDARD' }}</el-tag>
        </div>
      </div>
    </div>

    <!-- 告警与不可用状态横幅 -->
    <el-alert v-if="loadError" title="消息运维数据加载失败，已保留最近结果" type="warning" :closable="false" show-icon />
    <el-alert v-if="unavailable.length" type="warning" :closable="false" show-icon
      :title="`部分服务节点不可用：${unavailable.join('、')}（可能尚未启动或链路探测超时）`" />
    <el-alert v-if="alertingServices.length" type="error" :closable="false" show-icon
      :title="`消息水位高水位预警：${alertingServices.join('、')}（Outbox 存在最老待投递超过 30 秒或存在阻塞事件）`" />

    <!-- 3. 工作面子导航 -->
    <div class="subnav-container">
      <AdminSubnav :model-value="activeResource" :items="resourceViews" aria-label="消息运维资源" @update:model-value="selectResource" />
    </div>
    <el-alert v-if="resourceError" :title="resourceError" type="warning" :closable="false" show-icon />

    <!-- 4.1 消费器 (Consumers) -->
    <section v-if="activeResource === 'consumers' && current" class="messaging-panel">
      <el-table :data="current.consumers || []" stripe size="small" empty-text="当前服务未注册 RocketMQ 消费器">
        <el-table-column prop="consumerGroup" label="Consumer Group (消费组)" min-width="260">
          <template #default="{ row }">
            <strong class="code-font text-strong">{{ row.consumerGroup }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="topic" label="订阅 Topic" min-width="220">
          <template #default="{ row }">
            <span class="code-font text-muted">{{ row.topic }}</span>
          </template>
        </el-table-column>
        <el-table-column label="运行状态" width="120">
          <template #default="{ row }">
            <AdminStatusBadge :status="row.paused ? 'WAITING' : 'RUNNING'" :label="row.paused ? '已暂停消费' : '正常消费中'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link :type="row.paused ? 'success' : 'warning'" @click="toggleConsumer(row)">
              {{ row.paused ? "恢复消费" : "暂停消费" }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="current.domainBacklog && Object.keys(current.domainBacklog).length" class="domain-backlog-wrap">
        <div class="backlog-title">业务领域积压维度指标：</div>
        <div class="domain-backlog-list">
          <div v-for="(count, key) in current.domainBacklog" :key="key" class="backlog-item">
            <small>{{ key }}</small>
            <strong class="code-font">{{ count }}</strong>
          </div>
        </div>
      </div>
    </section>

    <!-- 4.2 本地消息表 (Outbox) -->
    <section v-else-if="activeResource === 'outbox'" class="messaging-panel">
      <div class="section-toolbar compact">
        <div class="left-facts text-muted font-12">
          <span>展示当前服务 Outbox 本地可靠事件表（最多显示最近 50 条）</span>
        </div>
        <div class="messaging-filters">
          <el-select v-model="outboxStatus" clearable placeholder="全部状态" style="width: 130px" @change="loadResource">
            <el-option v-for="status in ['PENDING','SENDING','PUBLISHED','BLOCKED']" :key="status" :label="status" :value="status" />
          </el-select>
          <el-button :disabled="!selectedService" :loading="eventsLoading" @click="loadResource">
            <el-icon><Search /></el-icon>查询
          </el-button>
        </div>
      </div>
      <el-table :data="outbox" stripe size="small" v-loading="eventsLoading" empty-text="当前无匹配的 Outbox 事件">
        <el-table-column prop="eventType" label="事件类型" min-width="200">
          <template #default="{ row }">
            <strong class="code-font text-strong">{{ row.eventType }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <AdminStatusBadge :status="statusForOutbox(row.status)" :label="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="80" align="center">
          <template #default="{ row }">
            <span class="code-font" :class="{ 'text-danger': row.retryCount >= 3 }">{{ row.retryCount }} 次</span>
          </template>
        </el-table-column>
        <el-table-column prop="eventId" label="Event ID" min-width="240">
          <template #default="{ row }">
            <div class="identifier-cell">
              <span class="code-font text-ellipsis" :title="row.eventId">{{ row.eventId }}</span>
              <el-button link type="primary" size="small" @click="copyText(row.eventId)">复制</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="Trace ID" min-width="220">
          <template #default="{ row }">
            <div class="identifier-cell">
              <a href="javascript:void(0)" class="code-font text-ellipsis link-text" :title="row.traceId" @click="jumpTrace(row.traceId)">{{ row.traceId }}</a>
              <el-button link type="primary" size="small" @click="copyText(row.traceId)">复制</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="right">
          <template #default="{ row }">
            <el-button v-if="['PUBLISHED','BLOCKED'].includes(row.status)" link type="warning" @click="replay(row)">
              补发重投
            </el-button>
            <span v-else class="text-muted font-11">—</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 4.3 幂等消费表 (Inbox) -->
    <section v-else-if="activeResource === 'inbox'" class="messaging-panel">
      <div class="section-toolbar compact">
        <div class="left-facts text-muted font-12">
          <span>记录消费者接收、校验与幂等去重的历史记录（最多显示最近 50 条）</span>
        </div>
        <el-button :disabled="!selectedService" :loading="eventsLoading" size="small" @click="loadResource">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
      <el-table :data="inbox" stripe size="small" v-loading="eventsLoading" empty-text="当前无匹配的 Inbox 消费记录">
        <el-table-column prop="eventType" label="事件类型" min-width="190">
          <template #default="{ row }">
            <strong class="code-font text-strong">{{ row.eventType }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="consumerGroup" label="Consumer Group" min-width="220">
          <template #default="{ row }">
            <span class="code-font">{{ row.consumerGroup }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="eventId" label="Event ID" min-width="230">
          <template #default="{ row }">
            <div class="identifier-cell">
              <span class="code-font text-ellipsis">{{ row.eventId }}</span>
              <el-button link type="primary" size="small" @click="copyText(row.eventId)">复制</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="Trace ID" min-width="200">
          <template #default="{ row }">
            <div class="identifier-cell">
              <a href="javascript:void(0)" class="code-font text-ellipsis link-text" @click="jumpTrace(row.traceId)">{{ row.traceId }}</a>
              <el-button link type="primary" size="small" @click="copyText(row.traceId)">复制</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="消费状态" width="105">
          <template #default="{ row }">
            <AdminStatusBadge :status="statusForInbox(row.status)" :label="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="consumedAt" label="消费时间" min-width="160">
          <template #default="{ row }">
            <span class="tabular-nums font-11">{{ formatAdminTime(row.consumedAt) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 4.4 死信队列治理 (DLQ) -->
    <section v-else-if="activeResource === 'dlq'" class="messaging-panel">
      <el-table :data="deadLetters" stripe size="small" empty-text="当前服务暂无死信队列或消费组">
        <el-table-column prop="consumerGroup" label="Consumer Group (死信消费组)" min-width="250">
          <template #default="{ row }">
            <strong class="code-font text-strong">{{ row.consumerGroup }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="topic" label="DLQ Topic" min-width="240">
          <template #default="{ row }">
            <span class="code-font text-muted">{{ row.topic }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="messageCount" label="死信消息数" width="110" align="center">
          <template #default="{ row }">
            <strong class="code-font" :class="row.messageCount > 0 ? 'text-danger' : 'text-success'">
              {{ row.messageCount ?? 0 }}
            </strong>
          </template>
        </el-table-column>
        <el-table-column prop="oldestMessageAt" label="最早死信时间" min-width="165">
          <template #default="{ row }">
            <span class="tabular-nums font-11">{{ formatAdminTime(row.oldestMessageAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Broker 探测状态" width="130">
          <template #default="{ row }">
            <AdminStatusBadge
              :status="row.available ? (row.messageCount ? 'FAILED' : 'COMPLETED') : 'UNKNOWN'"
              :label="row.available ? (row.messageCount ? '存在死信' : '队列正常') : 'Broker 不可用'"
            />
          </template>
        </el-table-column>
      </el-table>

      <!-- DLQ 受控重放表单 -->
      <div class="dlq-replay-section">
        <div class="dlq-replay-header">
          <span class="header-tag">受控死信重放工作台</span>
          <span class="text-muted font-11">将死信队列中校验失败或重试耗尽的消息重新置入源服务 Outbox，以供重新派发与消费。</span>
        </div>
        <div class="dlq-replay-form">
          <el-select v-model="dlqConsumerGroup" placeholder="选择死信消费组 (Consumer Group)" style="width: 320px">
            <el-option v-for="item in deadLetters" :key="item.consumerGroup" :label="item.consumerGroup" :value="item.consumerGroup" />
          </el-select>
          <el-input v-model="dlqEventIds" placeholder="输入 eventId，多个用逗号分隔（最多 20 个）" clearable />
          <el-button type="danger" plain :disabled="!dlqConsumerGroup || !dlqEventIds.trim()" @click="replayDlq">
            核验并重放
          </el-button>
        </div>
      </div>
    </section>

    <!-- 4.5 链路追踪与因果图谱 (Trace) -->
    <section v-else class="messaging-panel">
      <div class="section-toolbar compact">
        <div class="messaging-filters w-full-between">
          <div class="text-muted font-12">
            <span>输入全局业务 traceId 查看由消息驱动的端到端调用流转图谱</span>
          </div>
          <div class="flex-row-gap">
            <el-input v-model="traceId" placeholder="输入 32-100 位业务 traceId" clearable style="width: 320px" @keyup.enter="loadTrace" />
            <el-button type="primary" :loading="traceLoading" @click="loadTrace">
              <el-icon><Connection /></el-icon>追踪全链路
            </el-button>
          </div>
        </div>
      </div>

      <!-- 链路聚合事实卡片 -->
      <div v-if="traceResult" class="trace-summary-strip">
        <div class="summary-cell">
          <span class="cell-label">生产事件 (Outbox)</span>
          <strong class="cell-val text-primary">{{ traceResult.producedEvents?.length || 0 }}</strong>
        </div>
        <div class="cell-divider" />
        <div class="summary-cell">
          <span class="cell-label">消费事实 (Inbox)</span>
          <strong class="cell-val text-success">{{ traceResult.consumedEvents?.length || 0 }}</strong>
        </div>
        <div class="cell-divider" />
        <div class="summary-cell">
          <span class="cell-label">下游 AI 调用</span>
          <strong class="cell-val text-warning">{{ traceResult.aiCalls?.length || 0 }}</strong>
        </div>
      </div>

      <!-- 链路详情：生产/消费/AI调用三维列表 -->
      <div v-if="traceResult" class="trace-details-wrap">
        <!-- 生产事件 -->
        <div class="trace-block">
          <h5 class="block-title">1. 生产事件 (Produced Events)</h5>
          <el-table :data="traceResult.producedEvents || []" stripe size="small" empty-text="当前链路无 Outbox 生产记录">
            <el-table-column prop="sourceService" label="来源服务" min-width="140">
              <template #default="{ row }"><span class="code-font font-11">{{ row.sourceService }}</span></template>
            </el-table-column>
            <el-table-column prop="eventType" label="事件类型" min-width="180">
              <template #default="{ row }"><strong class="code-font text-strong font-11">{{ row.eventType }}</strong></template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }"><AdminStatusBadge :status="statusForOutbox(row.status)" :label="row.status" /></template>
            </el-table-column>
            <el-table-column prop="eventId" label="Event ID" min-width="220">
              <template #default="{ row }"><span class="code-font font-11 text-ellipsis">{{ row.eventId }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 消费事实 -->
        <div class="trace-block">
          <h5 class="block-title">2. 消费事实 (Consumed Inboxes)</h5>
          <el-table :data="traceResult.consumedEvents || []" stripe size="small" empty-text="当前链路无 Inbox 消费记录">
            <el-table-column prop="consumerGroup" label="消费组" min-width="200">
              <template #default="{ row }"><span class="code-font font-11">{{ row.consumerGroup }}</span></template>
            </el-table-column>
            <el-table-column prop="eventType" label="事件类型" min-width="170">
              <template #default="{ row }"><strong class="code-font text-strong font-11">{{ row.eventType }}</strong></template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }"><AdminStatusBadge :status="statusForInbox(row.status)" :label="row.status" /></template>
            </el-table-column>
            <el-table-column prop="consumedAt" label="消费时间" min-width="150">
              <template #default="{ row }"><span class="tabular-nums font-11">{{ formatAdminTime(row.consumedAt) }}</span></template>
            </el-table-column>
          </el-table>
        </div>

        <!-- AI 调用关联 -->
        <div class="trace-block">
          <h5 class="block-title">3. 关联 AI 智能调用 (AI Calls)</h5>
          <el-table :data="traceResult.aiCalls || []" stripe size="small" empty-text="此消息驱动链路上未产生下游 AI 调用">
            <el-table-column prop="callId" label="AI Call ID" min-width="220">
              <template #default="{ row }"><span class="code-font font-11 text-ellipsis">{{ row.callId }}</span></template>
            </el-table-column>
            <el-table-column prop="callerService" label="调用服务" min-width="150">
              <template #default="{ row }"><span class="code-font font-11">{{ row.callerService }}</span></template>
            </el-table-column>
            <el-table-column prop="featureCode" label="业务特征" min-width="140">
              <template #default="{ row }"><strong class="font-11">{{ row.featureCode }}</strong></template>
            </el-table-column>
            <el-table-column prop="status" label="调用状态" width="95">
              <template #default="{ row }"><AdminStatusBadge :status="row.status === 'SUCCESS' ? 'COMPLETED' : 'FAILED'" :label="row.status" /></template>
            </el-table-column>
            <el-table-column prop="totalMs" label="耗时" width="95" align="right">
              <template #default="{ row }"><span class="tabular-nums font-11">{{ row.totalMs }} ms</span></template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <AdminStatePanel v-else-if="!traceLoading" type="empty" title="输入 Trace ID 即可全景检索生产、消费与 AI 调用链路事实" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AdminSubnav from "../components/AdminSubnav.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import {
  getMessagingOverview,
  getMessagingTrace,
  listMessagingOutbox,
  listMessagingInbox,
  listMessagingDeadLetters,
  replayMessagingOutbox,
  replayMessagingDeadLetters,
  setMessagingConsumerPaused,
} from "@/api/admin-messaging";

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const eventsLoading = ref(false);
const traceLoading = ref(false);
const loadError = ref(false);
const resourceError = ref("");

const activeResource = ref("consumers");
const resourceViews = [
  { value: "consumers", label: "消费器治理", icon: "Operation" },
  { value: "outbox", label: "本地消息表 (Outbox)", icon: "Upload" },
  { value: "inbox", label: "幂等消费记录 (Inbox)", icon: "Download" },
  { value: "dlq", label: "死信队列 (DLQ)", icon: "Warning" },
  { value: "trace", label: "链路因果追踪 (Trace)", icon: "Connection" },
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
const alertingServices = computed(() =>
  services.value.filter((item) => alertLevel(item) !== "normal").map((item) => serviceLabel(item.service))
);

// 集群聚合计算
const fleetPendingCount = computed(() =>
  services.value.reduce((acc, cur) => acc + pendingCount(cur), 0)
);
const fleetBlockedCount = computed(() =>
  services.value.reduce((acc, cur) => acc + Number(cur.outbox?.BLOCKED || 0), 0)
);
const fleetConsumerCount = computed(() =>
  services.value.reduce((acc, cur) => acc + (cur.consumers?.length || 0), 0)
);

const labels = {
  "submission-service": "提交派发服务",
  "ai-review-service": "AI 评审服务",
  "ranking-service": "排行榜服务",
  "ai-suggestion-service": "AI 建议服务",
  "ai-evaluation-service": "批量评测服务",
};

function serviceLabel(value) {
  return labels[value] || value;
}

function pendingCount(item) {
  return Number(item?.outbox?.PENDING || 0) + Number(item?.outbox?.SENDING || 0);
}

function alertLevel(item) {
  if (
    Number(item?.outbox?.BLOCKED || 0) > 0 ||
    Number(item?.oldestPendingSeconds || 0) >= 300 ||
    pendingCount(item) >= 1000
  )
    return "critical";
  if (Number(item?.oldestPendingSeconds || 0) >= 30 || pendingCount(item) >= 200) return "warning";
  return "normal";
}

function statusForOutbox(status) {
  return ({
    PUBLISHED: "COMPLETED",
    SENDING: "RUNNING",
    PENDING: "WAITING",
    BLOCKED: "FAILED",
  })[status] || "UNKNOWN";
}

function statusForInbox(status) {
  return ({
    CONSUMED: "COMPLETED",
    PROCESSING: "RUNNING",
    FAILED: "FAILED",
  })[status] || "UNKNOWN";
}

function formatAdminTime(val) {
  return val ? new Date(val).toLocaleString("zh-CN", { hour12: false }) : "—";
}

async function loadOverview() {
  loading.value = true;
  loadError.value = false;
  try {
    const { data } = await getMessagingOverview();
    services.value = data?.services || [];
    unavailable.value = data?.unavailableServices || [];
    if (!services.value.some((item) => item.service === selectedService.value)) {
      selectedService.value = services.value[0]?.service || "";
    }
    await loadResource();
  } catch (error) {
    loadError.value = true;
    ElMessage.error(error.message || "消息运维数据加载失败");
  } finally {
    loading.value = false;
  }
}

async function selectService(service) {
  selectedService.value = service;
  dlqConsumerGroup.value = "";
  await loadResource();
}

async function selectResource(resource) {
  activeResource.value = resource;
  const query = { ...route.query, view: resource };
  if (resource === "consumers") delete query.view;
  router.replace({ query });
  await loadResource();
}

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
  if (!selectedService.value) {
    outbox.value = [];
    return;
  }
  outbox.value =
    (
      await listMessagingOutbox(selectedService.value, {
        status: outboxStatus.value || undefined,
        limit: 50,
      })
    ).data || [];
}

async function loadInbox() {
  if (!selectedService.value) {
    inbox.value = [];
    return;
  }
  inbox.value = (await listMessagingInbox(selectedService.value, { limit: 50 })).data || [];
}

async function loadDeadLetters() {
  if (!selectedService.value) {
    deadLetters.value = [];
    return;
  }
  deadLetters.value = (await listMessagingDeadLetters(selectedService.value)).data || [];
  if (!deadLetters.value.some((item) => item.consumerGroup === dlqConsumerGroup.value)) {
    dlqConsumerGroup.value = deadLetters.value[0]?.consumerGroup || "";
  }
}

async function toggleConsumer(row) {
  const action = row.paused ? "恢复" : "暂停";
  try {
    await ElMessageBox.confirm(
      `确定对消费组「${row.consumerGroup}」执行${action}操作吗？`,
      "消费器治理控制",
      {
        type: "warning",
        confirmButtonText: action,
        cancelButtonText: "取消",
      }
    );
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
    const { value } = await ElMessageBox.prompt(
      "请输入补发原因（至少 3 个字符）",
      "受控消息补发",
      {
        inputValidator: (val) => (val?.trim().length >= 3) || "补发原因至少 3 个字符",
        confirmButtonText: "确认补发",
        type: "warning",
      }
    );
    await replayMessagingOutbox(selectedService.value, [row.eventId], value.trim());
    ElMessage.success("事件已重新进入 Outbox 待投递队列");
    await loadOverview();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || "Outbox 补发失败");
  }
}

async function replayDlq() {
  const eventIds = [...new Set(dlqEventIds.value.split(/[,\s]+/).map((v) => v.trim()).filter(Boolean))];
  if (!eventIds.length || eventIds.length > 20) return ElMessage.warning("请输入 1–20 个 eventId");
  try {
    const { value } = await ElMessageBox.prompt(
      "请输入死信恢复原因（至少 3 个字符）",
      "受控 DLQ 重放",
      {
        inputValidator: (text) => (text?.trim().length >= 3) || "恢复原因至少 3 个字符",
        confirmButtonText: "核验并重放",
        type: "warning",
      }
    );
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
  try {
    traceResult.value = (await getMessagingTrace(traceId.value.trim())).data;
  } catch (error) {
    resourceError.value = error.message || "Trace 关联查询失败";
  } finally {
    traceLoading.value = false;
  }
}

function jumpTrace(tid) {
  traceId.value = tid;
  activeResource.value = "trace";
  const query = { ...route.query, view: "trace", traceId: tid };
  router.replace({ query });
  loadTrace();
}

async function copyText(value) {
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success("标识已复制");
  } catch {
    ElMessage.warning("复制失败，请手动查看");
  }
}

onMounted(() => {
  if (route.query.view && resourceViews.some((v) => v.value === route.query.view)) {
    activeResource.value = route.query.view;
  }
  if (route.query.traceId) {
    traceId.value = route.query.traceId;
    loadTrace();
  }
  loadOverview();
});
</script>

<style scoped>
.messaging-operations {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

/* 1. 集群态势条 */
.messaging-fleet-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 18px;
  padding: 10px 14px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  font-size: 12px;
}

.fleet-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.fleet-label {
  color: var(--lm-admin-text-muted);
  font-size: 11px;
}

.fleet-val {
  color: var(--lm-admin-text-strong);
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.fleet-divider {
  width: 1px;
  height: 14px;
  background: var(--lm-admin-border);
}

.fleet-status-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}

.fleet-action-right {
  margin-left: auto;
}

/* 2. 单服务控制卡片 */
.service-control-card {
  display: flex;
  align-items: stretch;
  gap: 12px;
  padding: 10px 14px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.service-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 340px;
}

.select-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-admin-text-strong);
  white-space: nowrap;
}

.service-select {
  flex: 1;
}

.opt-title { float: left; font-weight: 600; }
.opt-code { float: left; margin-left: 8px; color: #94a3b8; font-size: 11px; font-family: monospace; }
.opt-badge { float: right; font-size: 11px; margin-left: 12px; }
.opt-badge.critical { color: #dc2626; font-weight: 600; }
.opt-badge.warning { color: #d97706; }
.opt-badge.normal { color: #16a34a; }

.service-facts {
  display: flex;
  min-width: 0;
  flex: 1;
  overflow-x: auto;
  align-items: center;
  gap: 14px;
  padding: 0 10px;
  border-left: 1px solid var(--lm-admin-border);
}

.fact-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 90px;
}

.fact-cell small {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
  white-space: nowrap;
}

.fact-cell strong {
  color: var(--lm-admin-text-strong);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.fact-cell strong.danger { color: #dc2626; font-weight: bold; }

/* 3. 子导航 */
.subnav-container {
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  padding: 0 8px;
}

/* 4. 面板容器 */
.messaging-panel {
  min-width: 0;
  overflow: hidden;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.section-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.compact {
  padding: 8px 12px;
  border-bottom: 1px solid var(--lm-admin-border);
}

.messaging-filters {
  display: flex;
  align-items: center;
  gap: 10px;
}

.w-full-between {
  width: 100%;
  justify-content: space-between;
}

.flex-row-gap {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 消费器领域积压 */
.domain-backlog-wrap {
  padding: 12px;
  border-top: 1px solid var(--lm-admin-border);
  background: var(--lm-admin-surface-subtle);
}

.backlog-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-admin-text-strong);
  margin-bottom: 8px;
}

.domain-backlog-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.backlog-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 130px;
  padding: 6px 10px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.backlog-item small {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.backlog-item strong {
  font-size: 14px;
  color: var(--lm-admin-text-strong);
}

/* 单元格辅助 */
.identifier-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.code-font {
  font-family: var(--lm-code-font-family, monospace);
}

.text-strong {
  color: var(--lm-admin-text-strong);
}

.text-muted {
  color: var(--lm-admin-text-muted);
}

.text-primary { color: #2563eb; }
.text-success { color: #16a34a; }
.text-warning { color: #d97706; }
.text-danger { color: #dc2626; }
.font-11 { font-size: 11px; }
.font-12 { font-size: 12px; }
.tabular-nums { font-variant-numeric: tabular-nums; }
.text-ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link-text { color: var(--lm-admin-primary, #2563eb); text-decoration: none; cursor: pointer; }
.link-text:hover { text-decoration: underline; }

/* DLQ 受控重放 */
.dlq-replay-section {
  padding: 12px 14px;
  border-top: 1px solid var(--lm-admin-border);
  background: var(--lm-admin-surface-subtle);
}

.dlq-replay-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.header-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-admin-text-strong);
}

.dlq-replay-form {
  display: grid;
  grid-template-columns: 300px minmax(260px, 1fr) auto;
  gap: 10px;
}

/* 链路因果图谱 */
.trace-summary-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  background: var(--lm-admin-surface-subtle);
  border-bottom: 1px solid var(--lm-admin-border);
}

.summary-cell {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.cell-label {
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}

.cell-val {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.cell-divider {
  width: 1px;
  height: 14px;
  background: var(--lm-admin-border);
}

.trace-details-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 14px;
}

.trace-block {
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  overflow: hidden;
}

.block-title {
  margin: 0;
  padding: 8px 12px;
  background: var(--lm-admin-surface-subtle);
  border-bottom: 1px solid var(--lm-admin-border);
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-admin-text-strong);
}

@media (max-width: 1100px) {
  .service-control-card { flex-wrap: wrap; }
  .service-select-group { flex: 1 1 100%; }
  .service-facts { border-left: 0; border-top: 1px solid var(--lm-admin-border); padding-top: 10px; width: 100%; }
  .w-full-between { flex-direction: column; align-items: flex-start; gap: 8px; }
  .dlq-replay-form { grid-template-columns: 1fr; }
}
</style>
