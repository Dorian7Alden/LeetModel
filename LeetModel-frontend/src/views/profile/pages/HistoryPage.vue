<template>
  <div class="history-page">
    <div class="page-header-row">
      <div>
        <h2 class="page-title">提交历史</h2>
        <p class="page-subtitle">汇总你在各支队伍中的论文提交与评审状态</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      class="load-alert"
    >
      <template #default><el-button type="primary" link @click="load">重新加载</el-button></template>
    </el-alert>

    <div v-loading="loading" class="card-grid">
      <router-link
        v-for="item in visibleList"
        :key="`${item.teamId}-${item.id}`"
        :to="`/team/${item.teamId}`"
        class="submission-card"
      >
        <div class="card-top">
          <span class="team-name">{{ item.teamName || `队伍 #${item.teamId}` }}</span>
          <el-tag :type="statusType(item.reviewStatus)" size="small" effect="light">{{ statusLabel(item.reviewStatus) }}</el-tag>
        </div>
        <h3 class="file-name">{{ item.originalFilename || '未命名 PDF' }}</h3>
        <div class="card-meta">
          <span>题号 {{ item.problemCode || item.problemId }}</span>
          <span>V{{ item.version }}</span>
          <el-tag v-if="item.finalVersion" type="success" size="small">最终版</el-tag>
        </div>
        <div class="card-footer">
          <span class="card-time">{{ formatTime(item.createTime) }}</span>
          <span v-if="item.score != null" class="card-score" :class="scoreClass(item.score)">{{ item.score }} 分</span>
          <span v-else class="card-score evaluating">-- 分</span>
        </div>
      </router-link>
    </div>

    <el-empty v-if="!loading && !loadError && visibleList.length === 0" description="暂无提交记录" :image-size="90" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAllMyTeams } from "@/api/team";
import { getTeamSubmissionHistory } from "@/api/submission";
import { getTeamReviews } from "@/api/review";

const loading = ref(false);
const loadError = ref("");
const items = ref([]);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

function statusLabel(status) {
  return ({ WAITING: "等待评审", RUNNING: "评审中", COMPLETED: "已完成", FAILED: "评审失败", NONE: "未评审" })[status] || status || "未评审";
}

function statusType(status) {
  return ({ COMPLETED: "success", FAILED: "danger", RUNNING: "warning" })[status] || "info";
}

function scoreClass(score) {
  if (score >= 90) return "score-high";
  if (score >= 80) return "score-mid";
  return "score-low";
}

const visibleList = computed(() => items.value);

async function load() {
  loading.value = true;
  loadError.value = "";
  try {
    const { rows: teams } = await getAllMyTeams({ page: 1, pageSize: 50 });
    const results = [];
    for (const team of teams) {
      try {
        const [subRes, reviewRes] = await Promise.all([
          getTeamSubmissionHistory(team.id),
          getTeamReviews(team.id),
        ]);
        const reviews = reviewRes.data || [];
        const reviewBySubmission = new Map(reviews.map((r) => [String(r.submissionId), r]));
        for (const sub of subRes.data || []) {
          const review = reviewBySubmission.get(String(sub.id));
          results.push({
            teamId: team.id,
            teamName: team.name,
            id: sub.id,
            problemId: sub.problemId,
            version: sub.version,
            originalFilename: sub.originalFilename,
            finalVersion: sub.finalVersion,
            createTime: sub.createTime,
            reviewStatus: review?.status || "NONE",
            score: review?.score ?? null,
          });
        }
      } catch {
        // 单个队伍加载失败不影响整体
      }
    }
    results.sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)));
    items.value = results;
  } catch (error) {
    loadError.value = error.message || "提交历史加载失败";
    items.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.history-page { padding: 24px; }
.page-header-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; margin-bottom: 24px; }
.page-title { margin: 0; font-size: 22px; color: var(--lm-text-primary); }
.page-subtitle { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; }
.load-alert { margin-bottom: 20px; }
.card-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.submission-card { display: flex; flex-direction: column; gap: 10px; padding: 16px 20px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: var(--lm-radius-lg); text-decoration: none; transition: transform var(--lm-transition), box-shadow var(--lm-transition); }
.submission-card:hover { transform: translateY(-2px); box-shadow: var(--lm-shadow-lg); }
.card-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.team-name { overflow: hidden; color: var(--lm-primary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.file-name { margin: 0; color: var(--lm-text-primary); font-size: 15px; line-height: 1.4; }
.card-meta { display: flex; align-items: center; gap: 10px; color: var(--lm-text-muted); font-size: 12px; }
.card-footer { display: flex; align-items: center; justify-content: space-between; margin-top: auto; padding-top: 12px; border-top: 1px solid var(--lm-border-light); }
.card-time { color: var(--lm-text-muted); font-size: 12px; }
.card-score { font-size: 15px; font-weight: 700; }
.score-high { color: #16a34a; }
.score-mid { color: var(--lm-primary); }
.score-low { color: #dc2626; }
.evaluating { color: var(--lm-text-muted); font-size: 12px; font-weight: 500; }
@media (max-width: 1400px) { .card-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 1000px) { .card-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 720px) { .history-page { padding: 16px; } .card-grid { grid-template-columns: 1fr; } .page-header-row { flex-direction: column; } }
</style>
