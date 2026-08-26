<template>
  <div class="submission-detail-page">
    <div v-loading="loading" class="detail-card">
      <template v-if="submission">
        <div class="detail-head">
          <div>
            <span class="eyebrow">提交详情</span>
            <h2 class="detail-title">V{{ submission.version }} · {{ submission.originalFilename || '未命名 PDF' }}</h2>
          </div>
          <el-tag v-if="submission.finalVersion" type="success" effect="light">最终版</el-tag>
        </div>

        <el-descriptions :column="2" border class="detail-descriptions">
          <el-descriptions-item label="队伍 ID">{{ submission.teamId }}</el-descriptions-item>
          <el-descriptions-item label="题目 ID">{{ submission.problemId }}</el-descriptions-item>
          <el-descriptions-item label="提交人 ID">{{ submission.submitterId }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatSize(submission.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(submission.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="评审状态">
            <el-tag :type="statusType(review?.status)" size="small" effect="light">{{ statusLabel(review?.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="review?.score != null" label="得分"><strong class="score">{{ review.score }}</strong> / 100</el-descriptions-item>
          <el-descriptions-item v-if="submission.downloadUrl" label="下载">
            <a :href="submission.downloadUrl" target="_blank" rel="noopener">打开 PDF</a>
          </el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button type="primary" @click="$router.push(`/team/${submission.teamId}`)">返回队伍查看评审</el-button>
          <router-link class="back-link" to="/profile/history">返回提交历史</router-link>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="未找到该提交记录" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { getAllMyTeams } from "@/api/team";
import { getTeamSubmissionHistory } from "@/api/submission";
import { getTeamReviews } from "@/api/review";

const route = useRoute();
const loading = ref(false);
const submission = ref(null);
const review = ref(null);

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

function formatSize(value) {
  if (value == null) return "-";
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function statusLabel(status) {
  return ({ WAITING: "等待评审", RUNNING: "评审中", COMPLETED: "已完成", FAILED: "评审失败" })[status] || status || "未评审";
}

function statusType(status) {
  return ({ COMPLETED: "success", FAILED: "danger", RUNNING: "warning" })[status] || "info";
}

async function load() {
  loading.value = true;
  try {
    const submissionId = String(route.params.id);
    const { rows: myTeams } = await getAllMyTeams({ page: 1, pageSize: 50 });
    for (const team of myTeams) {
      try {
        const [subRes, reviewRes] = await Promise.all([
          getTeamSubmissionHistory(team.id),
          getTeamReviews(team.id),
        ]);
        const found = (subRes.data || []).find((item) => String(item.id) === submissionId);
        if (found) {
          submission.value = found;
          review.value = (reviewRes.data || []).find((r) => String(r.submissionId) === submissionId) || null;
          return;
        }
      } catch {
        // 跳过单个队伍
      }
    }
  } catch (error) {
    ElMessage.error(error.message || "提交详情加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.submission-detail-page { padding: 24px; }
.detail-card { max-width: 820px; margin: 0 auto; padding: 24px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 14px; }
.detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 20px; }
.eyebrow { color: var(--lm-primary); font-size: 12px; font-weight: 600; }
.detail-title { margin: 6px 0 0; color: var(--lm-text-primary); font-size: 22px; }
.detail-descriptions { margin-bottom: 20px; }
.score { color: var(--lm-primary); font-size: 18px; }
.actions { display: flex; align-items: center; gap: 16px; }
.back-link { color: var(--lm-primary); font-size: 14px; }
@media (max-width: 720px) { .submission-detail-page { padding: 16px; } }
</style>
