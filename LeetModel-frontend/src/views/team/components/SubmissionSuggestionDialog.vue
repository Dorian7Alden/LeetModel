<template>
  <el-dialog
    :model-value="modelValue"
    title="有依据的论文改进建议"
    width="820px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
    @closed="onClosed"
  >
    <div v-if="submission" class="suggestion-dialog">
      <div class="dialog-subhead">
        <span>V{{ submission.version }} · {{ submission.originalFilename || '未命名 PDF' }}</span>
        <el-tag v-if="submission.finalVersion" type="success" size="small">最终版</el-tag>
        <el-button
          v-if="submission.review?.status === 'COMPLETED'"
          type="primary"
          size="small"
          :loading="creating"
          @click="create"
        >{{ history.length ? '再次生成' : '生成建议' }}</el-button>
      </div>

      <div v-loading="loading" class="dialog-body">
        <div v-if="history.length" class="history-bar">
          <span>历史报告</span>
          <el-select v-model="selectedTaskId" size="small" @change="selectTask">
            <el-option
              v-for="item in history"
              :key="item.taskId"
              :label="`${formatDate(item.createTime)} · ${statusLabel(item.status)} · ${item.workflowVersion}`"
              :value="String(item.taskId)"
            />
          </el-select>
        </div>

        <template v-if="task">
          <div class="task-head">
            <el-tag :type="statusType(task.status)" effect="light">{{ statusLabel(task.status) }}</el-tag>
            <span class="task-version">{{ task.workflowVersion }}</span>
            <span v-if="task.currentStage && task.status !== 'COMPLETED'" class="task-stage">
              {{ stageLabel(task.currentStage) }}
            </span>
            <span v-if="task.finishedAt" class="task-time">{{ formatDate(task.finishedAt) }}</span>
          </div>

          <el-alert
            v-if="task.status === 'FAILED'"
            :title="task.errorMessage || '建议生成失败'"
            type="error"
            :closable="false"
            show-icon
          >
            <template #default><el-button type="primary" link @click="retry">按原版本重试</el-button></template>
          </el-alert>

          <template v-if="task.status === 'COMPLETED' && result">
            <template v-if="isV2">
              <el-alert
                title="每项建议均经过论文页码、评审发现和知识来源三段依据校验"
                type="success"
                :closable="false"
                show-icon
              />
              <p class="summary">{{ result.overallStrategy }}</p>
              <div v-if="result.topPriorities?.length" class="top-priorities">
                <strong>本轮优先事项</strong>
                <ol><li v-for="item in result.topPriorities" :key="item">{{ item }}</li></ol>
              </div>
              <div class="items">
                <div v-for="item in result.items" :key="item.suggestionId" class="item">
                  <div class="item-head">
                    <el-tag size="small" effect="plain">{{ categoryLabel(item.category) }}</el-tag>
                    <el-tag size="small" type="warning" effect="light">{{ item.priority }}</el-tag>
                    <strong>{{ item.suggestionId }} · {{ item.problem }}</strong>
                  </div>
                  <p class="impact">影响：{{ item.impact }}</p>
                  <div class="detail-section"><b>修改动作</b><ul><li v-for="action in item.actions" :key="action">{{ action }}</li></ul></div>
                  <div class="detail-section"><b>验收标准</b><ul><li v-for="criterion in item.acceptanceCriteria" :key="criterion">{{ criterion }}</li></ul></div>
                  <div class="evidence-chain">
                    <span>论文：第 {{ item.target?.physicalPages?.join('、') }} 页</span>
                    <span>评审：{{ item.reviewFindingIds?.join('、') }}</span>
                    <span>资料：{{ knowledgeLabels(item.knowledgeCitationIds).join('；') }}</span>
                  </div>
                </div>
              </div>
              <div class="version-snapshot">
                <span>评审 {{ task.reviewWorkflowVersion }}</span>
                <span>解析 {{ task.paperParsingWorkflowVersion }}</span>
                <span>检索 {{ task.retrievalWorkflowVersion }}</span>
                <span v-if="task.knowledgeIndexVersion">索引 {{ task.knowledgeIndexVersion }}</span>
                <span v-if="task.knowledgeManifestVersion">目录 {{ task.knowledgeManifestVersion }}</span>
                <span v-if="task.knowledgeSourceVersion">资料 {{ task.knowledgeSourceVersion }}</span>
                <span v-if="task.reviewEvidenceProjectionVersion">投影 {{ task.reviewEvidenceProjectionVersion }}</span>
              </div>
            </template>

            <template v-else>
              <p class="summary">{{ result.summary }}</p>
              <div class="items">
                <div v-for="(item, index) in result.items" :key="index" class="item">
                  <div class="item-head">
                    <el-tag size="small" effect="plain">{{ categoryLabel(item.category) }}</el-tag>
                    <el-tag size="small" type="warning" effect="light">{{ item.priority }}</el-tag>
                    <strong>{{ item.title }}</strong>
                  </div>
                  <p class="action">{{ item.action }}</p>
                  <p v-if="item.evidence" class="evidence">依据：{{ item.evidence }}</p>
                  <span v-if="item.page" class="page">第 {{ item.page }} 页</span>
                </div>
              </div>
            </template>
          </template>

          <el-empty v-else-if="task.status !== 'FAILED'" :description="stageLabel(task.currentStage)" :image-size="60" />
        </template>

        <el-empty v-else description="该论文版本尚未生成建议" :image-size="80">
          <el-button type="primary" :loading="creating" @click="create">生成建议</el-button>
        </el-empty>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { getSuggestionBySubmission, createSuggestion, retrySuggestion } from "@/api/suggestion";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  submission: { type: Object, default: null },
});
defineEmits(["update:modelValue"]);

const loading = ref(false);
const creating = ref(false);
const history = ref([]);
const task = ref(null);
const selectedTaskId = ref("");
let timer = null;

const result = computed(() => task.value?.result || null);
const isV2 = computed(() => task.value?.workflowVersion === "GROUNDED_SUGGESTION_V2");

function formatDate(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}
function statusLabel(status) {
  return ({ WAITING: "等待生成", RUNNING: "生成中", COMPLETED: "已完成", FAILED: "生成失败" })[status] || status;
}
function statusType(status) {
  return ({ COMPLETED: "success", FAILED: "danger", RUNNING: "warning" })[status] || "info";
}
function stageLabel(stage) {
  return ({
    PREPARING: "正在准备输入",
    PARSING: "正在确认 PDF 解析产物",
    PREPARING_REVIEW: "正在准备证据化评审",
    RETRIEVING: "正在检索参考资料",
    GENERATING: "正在综合生成建议",
    VALIDATING: "正在校验依据引用",
  })[stage] || "建议生成中，请稍候";
}
function categoryLabel(category) {
  return ({
    PROBLEM: "题目覆盖", ASSUMPTION: "假设", DATA: "数据", MODEL: "模型",
    SOLUTION: "求解", RESULT: "结果", VALIDATION: "验证", SENSITIVITY: "敏感性",
    WRITING: "写作", FIGURE: "图表", CITATION: "引用", APPENDIX: "附录",
    PRESENTATION: "表达",
  })[category] || category;
}
function knowledgeLabels(ids = []) {
  const citations = new Map((task.value?.knowledgeCitations || []).map(item => [item.citationId, item]));
  return ids.map(id => {
    const citation = citations.get(id);
    return citation ? `${citation.title || id}（${citation.sourcePath || id}）` : id;
  });
}

function selectTask(value) {
  task.value = history.value.find(item => String(item.taskId) === String(value)) || null;
  startPolling();
}

function stopPolling() {
  if (timer) { clearInterval(timer); timer = null; }
}
function startPolling() {
  stopPolling();
  if (task.value && ["WAITING", "RUNNING"].includes(task.value.status)) {
    timer = window.setInterval(load, 4000);
  }
}

async function load() {
  if (!props.submission) return;
  loading.value = true;
  try {
    const previous = selectedTaskId.value;
    history.value = (await getSuggestionBySubmission(props.submission.id)).data || [];
    const next = history.value.find(item => String(item.taskId) === String(previous)) || history.value[0] || null;
    task.value = next;
    selectedTaskId.value = next ? String(next.taskId) : "";
  } catch (error) {
    history.value = [];
    task.value = null;
    if (error.code !== 40804) ElMessage.error(error.message || "建议加载失败");
  } finally {
    loading.value = false;
    startPolling();
  }
}

function requestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `suggestion_${Date.now()}_${Math.random().toString(36).slice(2, 12)}`;
}

async function create() {
  if (!props.submission?.review?.taskId) return ElMessage.warning("请先完成并选择一份评审");
  creating.value = true;
  try {
    task.value = (await createSuggestion({
      submissionId: props.submission.id,
      reviewTaskId: props.submission.review.taskId,
      clientRequestId: requestId(),
      retrievalWorkflowVersion: "VECTOR_RAG_V1",
    })).data;
    history.value = [task.value, ...history.value];
    selectedTaskId.value = String(task.value.taskId);
    ElMessage.success("已创建一份新的建议报告");
    startPolling();
  } catch (error) {
    ElMessage.error(error.message || "生成建议失败");
  } finally {
    creating.value = false;
  }
}

async function retry() {
  try {
    task.value = (await retrySuggestion(task.value.taskId)).data;
    const index = history.value.findIndex(item => String(item.taskId) === String(task.value.taskId));
    if (index >= 0) history.value[index] = task.value;
    ElMessage.success("已按原版本快照重新排队");
    startPolling();
  } catch (error) {
    ElMessage.error(error.message || "重试失败");
  }
}

function onOpen() {
  history.value = [];
  task.value = null;
  selectedTaskId.value = "";
  load();
}
function onClosed() { stopPolling(); }
watch(() => props.modelValue, value => { if (!value) stopPolling(); });
onBeforeUnmount(stopPolling);
</script>

<style scoped>
.suggestion-dialog { min-height: 260px; }
.dialog-subhead { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; padding-bottom: 10px; color: var(--lm-text-secondary); font-size: 13px; border-bottom: 1px solid var(--lm-border-light); flex-wrap: wrap; }
.dialog-subhead .el-button { margin-left: auto; }
.dialog-body { min-height: 200px; }
.history-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; color: var(--lm-text-secondary); font-size: 13px; }
.history-bar .el-select { flex: 1; }
.task-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
.task-version, .task-stage { color: var(--lm-text-secondary); font-size: 13px; }
.task-time { margin-left: auto; color: var(--lm-text-muted); font-size: 12px; }
.summary { margin: 14px 0 16px; color: var(--lm-text-secondary); line-height: 1.7; }
.top-priorities { margin-bottom: 16px; padding: 12px 16px; border-radius: 10px; background: var(--lm-bg-secondary); }
.top-priorities ol { margin: 8px 0 0; padding-left: 22px; line-height: 1.7; }
.items { display: flex; flex-direction: column; gap: 12px; }
.item { padding: 14px; border: 1px solid var(--lm-border-light); border-radius: 10px; background: var(--lm-bg-secondary); }
.item-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.item-head strong { color: var(--lm-text-primary); }
.action, .impact { margin: 8px 0 0; color: var(--lm-text-secondary); line-height: 1.7; }
.detail-section { margin-top: 10px; color: var(--lm-text-secondary); }
.detail-section ul { margin: 6px 0 0; padding-left: 22px; line-height: 1.7; }
.evidence, .page { color: var(--lm-text-muted); font-size: 13px; }
.evidence-chain { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.evidence-chain span, .version-snapshot span { padding: 4px 8px; border-radius: 6px; background: var(--lm-surface); color: var(--lm-text-muted); font-size: 12px; }
.version-snapshot { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--lm-border-light); }
</style>
