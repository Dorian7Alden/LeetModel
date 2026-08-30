<template>
  <div class="production-page" v-loading="loading">
    <div class="page-heading">
      <div>
        <p class="eyebrow">AI 客服治理</p>
        <h1>生产工作流版本</h1>
        <p class="page-description">实验候选用于隔离评价，只有当前指针引用的配置才是生产生效版本。</p>
      </div>
      <el-button :loading="loading" @click="loadAll">刷新事实</el-button>
    </div>

    <el-alert
      v-if="lastResult"
      :title="resultTitle"
      :type="lastResult.status === 'APPLIED' ? 'success' : 'warning'"
      :description="`${lastResult.message || ''} 当前 revision：${lastResult.current?.revision ?? '-'}`"
      show-icon
      closable
      @close="lastResult = null"
    />

    <section class="current-card" v-if="current">
      <div class="current-header">
        <div>
          <span class="status-kicker">生产生效</span>
          <h2>{{ current.workflowName }}</h2>
          <code>{{ current.productionConfigVersion }}</code>
        </div>
        <el-tag type="success" effect="dark">revision {{ current.revision }}</el-tag>
      </div>
      <div class="fact-grid">
        <div><span>工作流</span><strong>{{ current.workflowVersion }}</strong></div>
        <div><span>Prompt</span><strong>{{ current.promptVersion }}</strong></div>
        <div><span>模型配置</span><strong>{{ current.modelExecutionConfigVersion }}</strong></div>
        <div><span>RAG 模式</span><strong>{{ current.ragMode }}</strong></div>
        <div><span>物理索引</span><strong>{{ current.ragIndexVersion || "不适用" }}</strong></div>
        <div><span>生效操作者</span><strong>{{ current.activatedBy ?? "系统" }}</strong></div>
      </div>
      <div class="observation-line">
        <span>生效时间：{{ formatTime(current.activatedAt) }}</span>
        <span>观察至：{{ formatTime(current.observationUntil) }}</span>
      </div>
    </section>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-heading">
          <div>
            <h2>实验候选工作流</h2>
            <p>候选身份不代表生产资格；激活前仍由业务服务重检模型配置与固定索引。</p>
          </div>
        </div>
      </template>
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
    </el-card>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-heading">
          <div>
            <h2>历史生产配置</h2>
            <p>回滚只选择曾经生效的不可变配置，并走与激活相同的预览与确认协议。</p>
          </div>
        </div>
      </template>
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
    </el-card>

    <el-card shadow="never" class="section-card">
      <template #header><div class="section-heading"><h2>成功变更审计</h2></div></template>
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
    </el-card>

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
import {
  applyAssistantProductionChange,
  getAssistantProductionCurrent,
  listAssistantProductionAudits,
  listAssistantProductionConfigs,
  listAssistantProductionWorkflows,
  previewAssistantProductionChange,
} from "@/api/admin-ai";

const loading = ref(false);
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
.production-page { display: flex; flex-direction: column; gap: 20px; max-width: 1500px; margin: 0 auto; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.page-heading h1 { margin: 2px 0 8px; color: var(--lm-text-primary); font-size: 28px; }
.eyebrow { margin: 0; color: var(--lm-primary); font-size: 12px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
.page-description, .section-heading p { margin: 0; color: var(--lm-text-secondary); }
.current-card { padding: 24px; color: #eff6ff; background: linear-gradient(125deg, #172554, #1d4ed8 62%, #0f766e); border-radius: 16px; box-shadow: 0 14px 30px rgba(30, 64, 175, .18); }
.current-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.current-header h2 { margin: 7px 0 5px; font-size: 24px; }
.current-header code { color: #bfdbfe; }
.status-kicker { font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.fact-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 24px; }
.fact-grid div { display: flex; flex-direction: column; gap: 5px; min-width: 0; padding: 12px; background: rgba(255,255,255,.09); border: 1px solid rgba(255,255,255,.12); border-radius: 10px; }
.fact-grid span, .observation-line { color: #bfdbfe; font-size: 12px; }
.fact-grid strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.observation-line { display: flex; gap: 28px; margin-top: 14px; }
.section-card { border-radius: 14px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; }
.section-heading h2 { margin: 0 0 5px; font-size: 18px; }
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
code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
@media (max-width: 900px) {
  .fact-grid { grid-template-columns: 1fr; }
  .page-heading, .observation-line { flex-direction: column; gap: 10px; }
  .confirm-route { grid-template-columns: 1fr; }
  .route-arrow { transform: rotate(90deg); justify-self: center; }
}
</style>
