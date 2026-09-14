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
  </div>
</template>

<script setup>
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
const activeSection = ref("candidates");
const productionViews = [
  { value: "candidates", label: "候选工作流", icon: "SetUp" },
  { value: "history", label: "历史配置", icon: "Clock" },
  { value: "audits", label: "变更审计", icon: "DocumentChecked" },
];
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

async function loadAll() {
  loading.value = true;
  loadError.value = false;
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
</style>
