<template>
  <div class="suggestion-page">
    <PageHeader title="论文改进建议" description="基于最终提交与评审结果生成可追溯的结构化建议">
    </PageHeader>

    <div class="toolbar">
      <el-select
        v-model="selectedTeamId"
        filterable
        placeholder="请选择我的队伍"
        :loading="loadingTeams"
        class="team-select"
        @change="loadTeamData"
      >
        <el-option
          v-for="team in teams"
          :key="team.id"
          :label="`${team.name}（#${team.id}）`"
          :value="team.id"
        />
      </el-select>
      <el-button type="primary" :loading="creating" :disabled="!finalSubmission" @click="generateSuggestion">
        为最终版本生成建议
      </el-button>
    </div>

    <el-alert
      v-if="selectedTeamId && !finalSubmission"
      title="当前队伍还没有确定的最终提交，无法生成论文建议"
      type="info"
      :closable="false"
      show-icon
      class="hint-alert"
    />

    <div v-loading="loading" class="suggestion-body">
      <template v-if="suggestions.length">
        <div v-for="item in suggestions" :key="item.taskId" class="suggestion-card">
          <div class="suggestion-head">
            <el-tag :type="statusType(item.status)" effect="light">{{ statusLabel(item.status) }}</el-tag>
            <span class="suggestion-version">提交 #{{ item.submissionId }} · 评审版本 {{ item.reviewWorkflowVersion || item.workflowVersion }}</span>
            <span v-if="item.finishedAt" class="suggestion-time">{{ formatDate(item.finishedAt) }}</span>
          </div>

          <el-alert
            v-if="item.status === 'FAILED'"
            :title="item.errorMessage || '建议生成失败'"
            type="error"
            :closable="false"
            show-icon
          >
            <template #default>
              <el-button type="primary" link @click="retry(item.taskId)">重新生成</el-button>
            </template>
          </el-alert>

          <template v-if="item.status === 'COMPLETED' && suggestionResult(item)">
            <p class="suggestion-summary">{{ suggestionResult(item).summary }}</p>
            <div class="suggestion-items">
              <div v-for="(s, index) in suggestionResult(item).items" :key="index" class="suggestion-item">
                <div class="item-head">
                  <el-tag size="small" effect="plain">{{ item.categoryLabel(s.category) }}</el-tag>
                  <el-tag size="small" type="warning" effect="light">{{ s.priority }}</el-tag>
                  <strong>{{ s.title }}</strong>
                </div>
                <p class="item-action">{{ s.action }}</p>
                <p v-if="s.evidence" class="item-evidence">依据：{{ s.evidence }}</p>
                <span v-if="s.page" class="item-page">第 {{ s.page }} 页</span>
              </div>
            </div>
          </template>
          <el-empty v-else-if="item.status !== 'FAILED'" description="建议生成中，请稍后刷新" :image-size="60" />
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无论文建议记录" :image-size="90" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/common/PageHeader.vue";
import { getAllMyTeams } from "@/api/team";
import { getTeamSubmissionHistory } from "@/api/submission";
import { listTeamSuggestions, createSuggestion, retrySuggestion } from "@/api/suggestion";

const loadingTeams = ref(false);
const loading = ref(false);
const creating = ref(false);
const teams = ref([]);
const selectedTeamId = ref(null);
const finalSubmission = ref(null);
const suggestions = ref([]);

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

function suggestionResult(item) {
  if (!item.result) return null;
  if (typeof item.result === "string") {
    try { return JSON.parse(item.result); } catch { return null; }
  }
  return item.result;
}

async function loadTeams() {
  loadingTeams.value = true;
  try {
    const { rows } = await getAllMyTeams({ page: 1, pageSize: 100 });
    teams.value = rows;
  } catch (error) {
    ElMessage.error(error.message || "我的队伍加载失败");
  } finally {
    loadingTeams.value = false;
  }
}

async function loadTeamData() {
  if (!selectedTeamId.value) return;
  loading.value = true;
  try {
    const [historyRes, suggestRes] = await Promise.all([
      getTeamSubmissionHistory(selectedTeamId.value),
      listTeamSuggestions(selectedTeamId.value),
    ]);
    const submissions = historyRes.data || [];
    finalSubmission.value = submissions.find((item) => item.finalVersion) || null;
    suggestions.value = suggestRes.data || [];
  } catch (error) {
    ElMessage.error(error.message || "队伍数据加载失败");
  } finally {
    loading.value = false;
  }
}

async function generateSuggestion() {
  if (!finalSubmission.value) return;
  creating.value = true;
  try {
    const res = await createSuggestion(finalSubmission.value.id);
    ElMessage.success("论文建议已创建");
    await loadTeamData();
  } catch (error) {
    ElMessage.error(error.message || "生成论文建议失败");
  } finally {
    creating.value = false;
  }
}

async function retry(taskId) {
  try {
    await retrySuggestion(taskId);
    ElMessage.success("已重新排队生成建议");
    await loadTeamData();
  } catch (error) {
    ElMessage.error(error.message || "重试失败");
  }
}

onMounted(loadTeams);
</script>

<style scoped>
.suggestion-page { max-width: 1120px; margin: 0 auto; padding: 20px 0; }
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; }
.team-select { width: 340px; }
.hint-alert { margin-bottom: 20px; }
.suggestion-body { min-height: 240px; display: flex; flex-direction: column; gap: 16px; }
.suggestion-card { padding: 20px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; }
.suggestion-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.suggestion-version { font-size: 13px; color: var(--lm-text-secondary); }
.suggestion-time { margin-left: auto; font-size: 12px; color: var(--lm-text-muted); }
.suggestion-summary { margin: 0 0 16px; color: var(--lm-text-secondary); line-height: 1.7; }
.suggestion-items { display: flex; flex-direction: column; gap: 12px; }
.suggestion-item { padding: 14px; border: 1px solid var(--lm-border-light); border-radius: 10px; background: var(--lm-bg-secondary); }
.item-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.item-head strong { color: var(--lm-text-primary); }
.item-action { margin: 8px 0 0; color: var(--lm-text-secondary); line-height: 1.7; }
.item-evidence { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; line-height: 1.6; }
.item-page { margin-top: 6px; display: inline-block; font-size: 12px; color: var(--lm-info); }
@media (max-width: 720px) { .toolbar { flex-direction: column; } .team-select { width: 100%; } }
</style>
