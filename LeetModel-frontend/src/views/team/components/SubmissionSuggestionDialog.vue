<template>
  <el-dialog
    :model-value="modelValue"
    title="论文改进建议"
    width="680px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
    @closed="onClosed"
  >
    <div v-if="submission" class="suggestion-dialog">
      <div class="dialog-subhead">
        <span>V{{ submission.version }} · {{ submission.originalFilename || '未命名 PDF' }}</span>
        <el-tag v-if="submission.finalVersion" type="success" size="small">最终版</el-tag>
      </div>

      <div v-loading="loading" class="dialog-body">
        <template v-if="task">
          <div class="task-head">
            <el-tag :type="statusType(task.status)" effect="light">{{ statusLabel(task.status) }}</el-tag>
            <span class="task-version">评审版本 {{ task.reviewWorkflowVersion || task.workflowVersion }}</span>
            <span v-if="task.finishedAt" class="task-time">{{ formatDate(task.finishedAt) }}</span>
          </div>

          <el-alert
            v-if="task.status === 'FAILED'"
            :title="task.errorMessage || '建议生成失败'"
            type="error"
            :closable="false"
            show-icon
          >
            <template #default><el-button type="primary" link @click="retry">重新生成</el-button></template>
          </el-alert>

          <template v-if="task.status === 'COMPLETED' && result">
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

          <el-empty v-else-if="task.status !== 'FAILED'" description="建议生成中，请稍候" :image-size="60" />
        </template>

        <div v-else class="no-task">
          <el-empty description="该版本尚未生成论文改进建议" :image-size="80">
            <el-button type="primary" :loading="creating" @click="create">生成建议</el-button>
          </el-empty>
        </div>
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
const emit = defineEmits(["update:modelValue"]);

const loading = ref(false);
const creating = ref(false);
const task = ref(null);
let timer = null;

const result = computed(() => {
  if (!task.value?.result) return null;
  if (typeof task.value.result === "string") {
    try { return JSON.parse(task.value.result); } catch { return null; }
  }
  return task.value.result;
});

function formatDate(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}
function statusLabel(status) {
  return ({ WAITING: "等待生成", RUNNING: "生成中", COMPLETED: "已完成", FAILED: "生成失败" })[status] || status;
}
function statusType(status) {
  return ({ COMPLETED: "success", FAILED: "danger", RUNNING: "warning" })[status] || "info";
}
function categoryLabel(category) {
  return ({ model: "建模", code: "编程", writing: "写作", result: "结果", structure: "结构" })[category] || category;
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
    task.value = (await getSuggestionBySubmission(props.submission.id)).data;
  } catch (error) {
    task.value = null;
    if (error.code !== 40804) ElMessage.error(error.message || "建议加载失败");
  } finally {
    loading.value = false;
    startPolling();
  }
}

async function create() {
  if (!props.submission) return;
  creating.value = true;
  try {
    task.value = (await createSuggestion(props.submission.id)).data;
    ElMessage.success("论文建议已创建");
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
    ElMessage.success("已重新排队生成建议");
    startPolling();
  } catch (error) {
    ElMessage.error(error.message || "重试失败");
  }
}

function onOpen() {
  task.value = null;
  load();
}

function onClosed() {
  stopPolling();
}

watch(() => props.modelValue, (value) => {
  if (!value) stopPolling();
});

onBeforeUnmount(stopPolling);
</script>

<style scoped>
.suggestion-dialog { min-height: 240px; }
.dialog-subhead { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; padding-bottom: 10px; color: var(--lm-text-secondary); font-size: 13px; border-bottom: 1px solid var(--lm-border-light); flex-wrap: wrap; }
.dialog-body { min-height: 180px; }
.task-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.task-version { color: var(--lm-text-secondary); font-size: 13px; }
.task-time { margin-left: auto; color: var(--lm-text-muted); font-size: 12px; }
.summary { margin: 0 0 16px; color: var(--lm-text-secondary); line-height: 1.7; }
.items { display: flex; flex-direction: column; gap: 12px; }
.item { padding: 14px; border: 1px solid var(--lm-border-light); border-radius: 10px; background: var(--lm-bg-secondary); }
.item-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.item-head strong { color: var(--lm-text-primary); }
.action { margin: 8px 0 0; color: var(--lm-text-secondary); line-height: 1.7; }
.evidence { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; line-height: 1.6; }
.page { margin-top: 6px; display: inline-block; color: var(--lm-info); font-size: 12px; }
.no-task { min-height: 180px; display: flex; align-items: center; justify-content: center; }
</style>
