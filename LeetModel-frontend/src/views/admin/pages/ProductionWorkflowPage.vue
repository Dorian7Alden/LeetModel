<template>
  <div class="production-page" v-loading="loading">
    <div class="production-toolbar"><el-button :loading="loading" @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button></div>
    <el-alert v-if="loadError" title="生产版本刷新失败，已保留最近结果" type="warning" :closable="false" show-icon />
    <el-alert
      v-if="lastResult"
      :title="resultTitle"
      :type="lastResult.status === 'APPLIED' ? 'success' : 'warning'"
      :description="`${lastResult.message || ''} 当前 revision：${lastResult.current?.revision ?? '-'}`"
      show-icon
      closable
      @close="lastResult = null"
    />

    <section v-if="current" class="current-strip">
      <div class="current-identity">
        <AdminStatusBadge status="HEALTHY" label="生产生效" />
        <strong>{{ current.workflowName }}</strong>
        <code>{{ current.workflowVersion }}</code>
      </div>
      <dl class="current-facts">
        <div><dt>revision</dt><dd>{{ current.revision }}</dd></div>
        <div><dt>Prompt</dt><dd>{{ current.promptVersion }}</dd></div>
        <div><dt>模型配置</dt><dd>{{ current.modelExecutionConfigVersion }}</dd></div>
        <div><dt>RAG</dt><dd>{{ current.ragMode }}</dd></div>
        <div><dt>生效时间</dt><dd>{{ formatTime(current.activatedAt) }}</dd></div>
      </dl>
    </section>

    <AdminSubnav v-model="activeSection" :items="productionViews" aria-label="生产版本资源" />

    <!-- 客服会话与审计事实 -->
    <section v-if="activeSection === 'conversations'" class="production-panel">
      <div class="assistant-metrics-strip">
        <div class="convo-metric-card">
          <span class="m-label">累计咨询会话</span>
          <strong class="m-val">{{ totalConversations }}</strong>
          <span class="m-hint">最近 50 条事实</span>
        </div>
        <div class="convo-metric-card">
          <span class="m-label">活跃会话</span>
          <strong class="m-val success-color">{{ activeConversations }}</strong>
          <span class="m-hint">进行中咨询</span>
        </div>
        <div class="convo-metric-card">
          <span class="m-label">总交互消息轮数</span>
          <strong class="m-val">{{ totalMessages }}</strong>
          <span class="m-hint">平均每会话 {{ avgMessagesPerConvo }} 轮</span>
        </div>
      </div>

      <el-table :data="pagedConversations" stripe v-loading="loadingConversations">
        <el-table-column label="会话标题" min-width="210">
          <template #default="{ row }">
            <strong class="primary-cell">{{ row.title || 'AI 客服咨询' }}</strong>
            <span class="secondary-cell">会话 ID: {{ row.conversationId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户 ID" width="120" align="center">
          <template #default="{ row }">{{ row.userId }}</template>
        </el-table-column>
        <el-table-column label="交互消息数" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.messageCount || 0 }} 轮</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <AdminStatusBadge :status="row.status === 'ACTIVE' ? 'HEALTHY' : 'WAITING'" :label="row.status === 'ACTIVE' ? '活跃' : '已结束'" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConversationDetail(row)">查看对话</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无咨询会话" /></template>
      </el-table>

      <div class="convo-pagination-bar" v-if="conversations.length > 0">
        <el-pagination
          v-model:current-page="convoPage"
          v-model:page-size="convoPageSize"
          :page-sizes="[10, 20, 50]"
          :total="conversations.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </section>

    <section v-if="activeSection === 'candidates'" class="production-panel">
      <el-table :data="workflows" stripe>
        <el-table-column prop="name" label="工作流" min-width="180" />
        <el-table-column prop="workflowVersion" label="版本" min-width="210" />
        <el-table-column label="身份" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.experimentCandidate" type="info">实验候选</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ragMode" label="RAG" width="120" />
        <el-table-column prop="impactScope" label="影响范围" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="row.status !== 'ENABLED' || row.workflowVersion === current?.workflowVersion"
              @click="openActivate(row)"
            >预览激活</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else-if="activeSection === 'history'" class="production-panel">
      <el-table :data="configs" stripe>
        <el-table-column prop="productionConfigVersion" label="配置版本" min-width="230" />
        <el-table-column prop="workflowName" label="工作流" min-width="170" />
        <el-table-column prop="ragIndexVersion" label="固定索引" min-width="210">
          <template #default="{ row }">{{ row.ragIndexVersion || "不适用" }}</template>
        </el-table-column>
        <el-table-column label="生产状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.productionConfigVersion === current?.productionConfigVersion" type="success">生产生效</el-tag>
            <span v-else-if="row.everActive" class="muted">历史已生效</span>
            <el-tag v-else type="info">未生效预览</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="warning"
              link
              :disabled="!row.everActive || row.productionConfigVersion === current?.productionConfigVersion"
              @click="openRollback(row)"
            >预览回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else class="production-panel">
      <el-table :data="audits" stripe>
        <el-table-column label="动作" width="100">
          <template #default="{ row }">
            <el-tag :type="row.action === 'ROLLBACK' ? 'warning' : 'primary'">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配置变化" min-width="330">
          <template #default="{ row }"><code>{{ row.fromProductionConfigVersion }}</code> → <code>{{ row.toProductionConfigVersion }}</code></template>
        </el-table-column>
        <el-table-column label="revision" width="130">
          <template #default="{ row }">{{ row.fromRevision }} → {{ row.toRevision }}</template>
        </el-table-column>
        <el-table-column prop="operatorId" label="操作者" width="110" />
        <el-table-column prop="reason" label="原因" min-width="260" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.changedAt) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="editVisible" :title="changeForm.action === 'ACTIVATE' ? '准备激活生产版本' : '准备回滚生产版本'" width="560px">
      <el-form label-position="top">
        <el-form-item label="目标配置">
          <div class="target-summary">
            <strong>{{ selectedTargetName }}</strong>
            <code>{{ selectedTargetVersion }}</code>
          </div>
        </el-form-item>
        <el-form-item v-if="selectedWorkflow?.ragMode === 'FIXED_INDEX'" label="物理 RAG 索引版本" required>
          <el-input v-model.trim="changeForm.ragIndexVersion" maxlength="128" placeholder="例如 rag-v1-63c42340a72610d1" />
        </el-form-item>
        <el-form-item label="变更原因" required>
          <el-input
            v-model="changeForm.reason"
            type="textarea"
            :rows="4"
            minlength="10"
            maxlength="500"
            show-word-limit
            placeholder="说明本次切换或回滚的依据、预期和观察重点"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="previewing" @click="createPreview">生成服务端预览</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="confirmVisible" title="二次确认生产变更" width="680px" :close-on-click-modal="false">
      <div v-if="preview" class="confirm-content">
        <el-alert title="确认后只影响新创建的客服回复；在途与历史消息继续使用原快照。" type="warning" :closable="false" show-icon />
        <div class="confirm-route">
          <div><span>当前</span><strong>{{ preview.current.workflowName }}</strong><code>{{ preview.current.productionConfigVersion }}</code></div>
          <span class="route-arrow">→</span>
          <div><span>目标</span><strong>{{ preview.target.workflowName }}</strong><code>{{ preview.target.productionConfigVersion }}</code></div>
        </div>
        <div class="confirm-block"><span>服务端差异</span><ul><li v-for="item in preview.differences" :key="item">{{ item }}</li></ul></div>
        <div class="confirm-block"><span>影响范围</span><p>{{ preview.impactScope }}</p></div>
        <div class="confirm-block"><span>变更原因</span><p>{{ preview.reason }}</p></div>
        <div class="confirm-meta">请求将在 {{ formatTime(preview.expiresAt) }} 过期 · expected revision {{ preview.expectedRevision }}</div>
      </div>
      <template #footer>
        <el-button @click="confirmVisible = false">暂不执行</el-button>
        <el-button type="danger" :loading="applying" @click="applyPreview">确认并执行</el-button>
      </template>
    </el-dialog>

    <!-- 对话回溯审计抽屉 -->
    <el-drawer v-model="chatDrawerVisible" title="用户咨询完整对话回溯" size="min(680px, 92vw)">
      <div v-if="selectedConversation" class="chat-drawer-content" v-loading="loadingMessages">
        <div class="chat-meta-bar">
          <div><strong>{{ selectedConversation.title }}</strong><span>用户 ID: {{ selectedConversation.userId }}</span></div>
          <AdminStatusBadge :status="selectedConversation.status === 'ACTIVE' ? 'HEALTHY' : 'WAITING'" :label="selectedConversation.status === 'ACTIVE' ? '活跃' : '已结束'" />
        </div>

        <div v-if="conversationMessages.length" class="chat-messages-container">
          <div v-for="msg in conversationMessages" :key="msg.id" class="chat-bubble" :class="msg.role.toLowerCase()">
            <div class="bubble-header">
              <span class="role-name">{{ msg.role === 'USER' ? '提问用户' : 'LeetModel AI 智能助手' }}</span>
              <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
            </div>
            <div class="bubble-body">
              <pre class="msg-text">{{ msg.content }}</pre>
            </div>
            <div v-if="msg.modelName" class="bubble-footer">
              <span>调用模型: {{ msg.modelName }}</span>
            </div>
          </div>
        </div>
        <el-empty v-else-if="!loadingMessages" description="本会话暂无消息记录" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { getAdminConversations } from "@/api/admin-ops";
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import AdminSubnav from "../components/AdminSubnav.vue";
import {
  applyAssistantProductionChange,
  getAssistantProductionCurrent,
  listAssistantProductionAudits,
  listAssistantProductionConfigs,
  listAssistantProductionWorkflows,
  previewAssistantProductionChange,
} from "@/api/admin-ai";

const loading = ref(false);
const loadError = ref(false);
const activeSection = ref("conversations");
const productionViews = [
  { value: "conversations", label: "客服会话与审计", icon: "ChatDotRound" },
  { value: "candidates", label: "候选工作流", icon: "SetUp" },
  { value: "history", label: "历史配置", icon: "Clock" },
  { value: "audits", label: "变更审计", icon: "DocumentChecked" },
];
const conversations = ref([]);
const loadingConversations = ref(false);
const selectedConversation = ref(null);
const conversationMessages = ref([]);
const loadingMessages = ref(false);
const chatDrawerVisible = ref(false);
const convoPage = ref(1);
const convoPageSize = ref(10);
const pagedConversations = computed(() => {
  const start = (convoPage.value - 1) * convoPageSize.value;
  return conversations.value.slice(start, start + convoPageSize.value);
});

const totalConversations = computed(() => conversations.value.length);
const activeConversations = computed(() => conversations.value.filter(c => c.status === "ACTIVE").length);
const totalMessages = computed(() => conversations.value.reduce((acc, c) => acc + (c.messageCount || 0), 0));
const avgMessagesPerConvo = computed(() => totalConversations.value ? (totalMessages.value / totalConversations.value).toFixed(1) : "0");

const previewing = ref(false);
const applying = ref(false);
const editVisible = ref(false);
const confirmVisible = ref(false);
const current = ref(null);
const workflows = ref([]);
const configs = ref([]);
const audits = ref([]);
const preview = ref(null);
const lastResult = ref(null);
const selectedWorkflow = ref(null);
const selectedConfig = ref(null);
const changeForm = reactive({ action: "ACTIVATE", reason: "", ragIndexVersion: "" });

const selectedTargetName = computed(() => selectedWorkflow.value?.name || selectedConfig.value?.workflowName || "-");
const selectedTargetVersion = computed(() => selectedWorkflow.value?.workflowVersion || selectedConfig.value?.productionConfigVersion || "-");
const resultTitle = computed(() => lastResult.value?.status === "APPLIED" ? "生产配置变更已生效" : `生产变更未生效：${lastResult.value?.status || "UNKNOWN"}`);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 19) : "-";
}


async function loadConversations() {
  loadingConversations.value = true;
  try {
    const res = await getAdminConversations(50);
    const raw = res.data || [];
    conversations.value = raw.map(c => ({
      ...c,
      conversationId: String(c.conversationId)
    }));
    console.log("Loaded conversations:", conversations.value.length);
  } catch (err) {
    console.error("加载客服会话失败", err);
  } finally {
    loadingConversations.value = false;
  }
}

async function openConversationDetail(row) {
  selectedConversation.value = row;
  chatDrawerVisible.value = true;
  loadingMessages.value = true;
  try {
    const { getConversation } = await import("@/api/assistant");
    const res = await getConversation(row.conversationId);
    conversationMessages.value = res.data?.messages || [];
  } catch (err) {
    ElMessage.error(err.message || "会话详情加载失败");
    conversationMessages.value = [];
  } finally {
    loadingMessages.value = false;
  }
}

async function loadAll() {
  loading.value = true;
  loadError.value = false;
    loadConversations();
  try {
    const [workflowResult, currentResult, configResult, auditResult] = await Promise.all([
      listAssistantProductionWorkflows(),
      getAssistantProductionCurrent(),
      listAssistantProductionConfigs(50),
      listAssistantProductionAudits(50),
    ]);
    workflows.value = workflowResult.data || [];
    current.value = currentResult.data;
    configs.value = configResult.data || [];
    audits.value = auditResult.data || [];
  } catch (error) {
    loadError.value = true;
    ElMessage.error(error.message || "生产版本事实加载失败");
  } finally {
    loading.value = false;
  }
}

function resetChange(action) {
  changeForm.action = action;
  changeForm.reason = "";
  changeForm.ragIndexVersion = "";
  preview.value = null;
}

function openActivate(workflow) {
  resetChange("ACTIVATE");
  selectedWorkflow.value = workflow;
  selectedConfig.value = null;
  editVisible.value = true;
}

function openRollback(config) {
  resetChange("ROLLBACK");
  selectedWorkflow.value = null;
  selectedConfig.value = config;
  editVisible.value = true;
}

async function createPreview() {
  const reason = changeForm.reason.trim();
  if (reason.length < 10) {
    ElMessage.warning("变更原因至少需要 10 个字符");
    return;
  }
  if (selectedWorkflow.value?.ragMode === "FIXED_INDEX" && !changeForm.ragIndexVersion) {
    ElMessage.warning("固定 RAG 工作流必须填写物理索引版本");
    return;
  }
  const data = {
    action: changeForm.action,
    expectedRevision: current.value.revision,
    reason,
  };
  if (changeForm.action === "ACTIVATE") {
    data.targetWorkflowVersion = selectedWorkflow.value.workflowVersion;
    if (selectedWorkflow.value.ragMode === "FIXED_INDEX") data.ragIndexVersion = changeForm.ragIndexVersion;
  } else {
    data.targetProductionConfigVersion = selectedConfig.value.productionConfigVersion;
  }
  previewing.value = true;
  try {
    preview.value = (await previewAssistantProductionChange(data)).data;
    editVisible.value = false;
    confirmVisible.value = true;
  } catch (error) {
    ElMessage.error(error.message || "服务端预览失败");
  } finally {
    previewing.value = false;
  }
}

async function applyPreview() {
  if (!preview.value?.changeRequestId) return;
  applying.value = true;
  try {
    lastResult.value = (await applyAssistantProductionChange(preview.value.changeRequestId)).data;
    confirmVisible.value = false;
    ElMessage[lastResult.value.status === "APPLIED" ? "success" : "warning"](lastResult.value.message || lastResult.value.status);
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message || "生产变更确认失败");
  } finally {
    applying.value = false;
  }
}

onMounted(loadAll);
</script>

<style scoped>
.production-page { display: flex; min-width: 0; flex-direction: column; gap: 12px; padding: 12px; }
.production-toolbar { display: flex; justify-content: flex-end; }
.current-strip { display: flex; min-width: 0; align-items: center; gap: 16px; padding: 12px; background: var(--lm-admin-surface-subtle); border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.current-identity { display: flex; min-width: 210px; flex-direction: column; gap: 3px; padding-right: 16px; border-right: 1px solid var(--lm-admin-border); }
.current-identity strong { color: var(--lm-admin-text-strong); font-size: 14px; }.current-identity code { color: var(--lm-admin-text-muted); font-size: 10px; }
.current-facts { display: grid; min-width: 0; flex: 1; grid-template-columns: .6fr 1fr 1.45fr .7fr 1fr; gap: 12px; margin: 0; }
.current-facts > div { min-width: 0; }.current-facts dt { color: var(--lm-admin-text-muted); font-size: 10px; }.current-facts dd { overflow: hidden; margin: 3px 0 0; color: var(--lm-admin-text-strong); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.production-page > :deep(.admin-subnav) { padding: 0 8px; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.production-panel { min-width: 0; overflow: hidden; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.target-summary { display: flex; flex-direction: column; gap: 4px; }
.target-summary code, .confirm-route code { color: var(--lm-text-secondary); }
.confirm-content { display: flex; flex-direction: column; gap: 18px; }
.confirm-route { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 16px; }
.confirm-route > div { display: flex; flex-direction: column; gap: 5px; padding: 16px; background: var(--lm-bg-secondary); border-radius: 10px; }
.confirm-route span, .confirm-block > span { color: var(--lm-text-muted); font-size: 12px; font-weight: 700; }
.route-arrow { font-size: 22px !important; }
.confirm-block ul, .confirm-block p { margin: 8px 0 0; }
.confirm-block li { margin: 5px 0; }
.confirm-meta, .muted { color: var(--lm-text-muted); font-size: 12px; }
code { font-family: var(--lm-code-font-family); }
@media (max-width: 900px) {
  .current-strip { align-items: stretch; flex-direction: column; }
  .current-identity { border-right: 0; border-bottom: 1px solid var(--lm-admin-border); padding: 0 0 10px; }
  .current-facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .confirm-route { grid-template-columns: 1fr; }
  .route-arrow { transform: rotate(90deg); justify-self: center; }
}

.assistant-metrics-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  padding: 12px;
  background: #f8fafc;
  border-bottom: 1px solid var(--lm-admin-border);
}
.convo-metric-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}
.m-label { font-size: 11px; color: var(--lm-admin-text-muted); }
.m-val { font-size: 20px; color: var(--lm-admin-text-strong); font-weight: 700; }
.m-hint { font-size: 11px; color: var(--lm-admin-text-muted); }
.success-color { color: var(--lm-admin-success); }

.chat-drawer-content { display: flex; flex-direction: column; gap: 16px; }
.chat-meta-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px; background: #f8fafc; border: 1px solid var(--lm-admin-border); border-radius: 6px; }
.chat-meta-bar strong { font-size: 14px; color: var(--lm-admin-text-strong); display: block; }
.chat-meta-bar span { font-size: 11px; color: var(--lm-admin-text-muted); }
.chat-messages-container { display: flex; flex-direction: column; gap: 14px; max-height: 70vh; overflow-y: auto; padding: 6px 2px; }
.chat-bubble { display: flex; flex-direction: column; gap: 6px; padding: 12px 14px; border-radius: 8px; font-size: 13px; line-height: 1.5; }
.chat-bubble.user { background: #eff6ff; border: 1px solid #bfdbfe; margin-left: 20px; }
.chat-bubble.assistant { background: #f8fafc; border: 1px solid #e2e8f0; margin-right: 20px; }
.bubble-header { display: flex; align-items: center; justify-content: space-between; font-size: 11px; }
.role-name { font-weight: 600; color: var(--lm-admin-text-strong); }
.msg-time { color: var(--lm-admin-text-muted); }
.msg-text { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: inherit; }
.bubble-footer { font-size: 10px; color: var(--lm-admin-text-muted); border-top: 1px dashed rgba(0,0,0,0.06); padding-top: 4px; }

.convo-pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 12px 14px;
  background: #fff;
  border-top: 1px solid var(--lm-admin-border);
}
</style>
