<template>
  <div class="home-page">
    <section class="welcome">
      <div class="welcome-copy">
        <p class="eyebrow">{{ greeting }}</p>
        <h1 v-if="userStore.isLogin">{{ userStore.nickname || userStore.username }}，继续你的建模练习</h1>
        <h1 v-else>欢迎来到 LeetModel，开启你的建模练习</h1>
        <p v-if="userStore.isLogin">这里是你的练习概览，快速回到进行中的队伍、提交与评审。</p>
        <p v-else>登录后可追踪队伍进度、论文提交历史与 AI 评审反馈。</p>
      </div>
      <el-tag v-if="userStore.isLogin" :type="roleTagType" effect="light" size="large">{{ userStore.roleLabel }}</el-tag>
    </section>

    <section class="quick-section">
      <h2 class="section-title">快捷入口</h2>
      <div class="quick-grid">
        <router-link v-for="item in quickLinks" :key="item.path" :to="item.path" class="quick-link">
          <span class="quick-icon" :style="{ background: item.bgColor, color: item.color }">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </span>
          <span class="quick-label">{{ item.title }}</span>
        </router-link>
      </div>
    </section>

    <el-alert
      v-if="overviewError"
      :title="overviewError"
      type="error"
      :closable="false"
      show-icon
      class="overview-alert"
    />

    <div v-loading="loading" class="overview-section">
      <div class="overview-heading">
        <h2 class="section-title">进行中的练习</h2>
        <router-link v-if="activeTeams.length" to="/team" class="view-all">查看我的队伍</router-link>
      </div>
      <div v-if="activeTeams.length" class="team-grid">
        <router-link v-for="team in activeTeams" :key="team.id" :to="`/team/${team.id}`" class="team-card">
          <div class="team-card-head">
            <strong>{{ team.name }}</strong>
            <el-tag :type="teamStatusType(team.practiceStatus)" size="small" effect="light">{{ teamStatusLabel(team.practiceStatus) }}</el-tag>
          </div>
          <div class="team-card-meta">
            <span v-if="team.problemId">题号 {{ team.problemCode || team.problemId }}</span>
            <span>{{ team.memberCount ?? '成员' }}</span>
          </div>
        </router-link>
      </div>
      <el-empty v-else-if="!loading && !overviewError" description="暂无进行中的队伍，去队伍广场看看吧" :image-size="72">
        <el-button type="primary" @click="$router.push('/team/square')">前往队伍广场</el-button>
      </el-empty>

      <template v-if="recentSubmissions.length">
        <div class="overview-heading recent-heading">
          <h2 class="section-title">最近提交</h2>
        </div>
        <div class="submission-list">
          <div v-for="item in recentSubmissions" :key="item.id" class="submission-row">
            <div class="submission-version">V{{ item.version }}</div>
            <div class="submission-info">
              <strong>{{ item.originalFilename || '未命名 PDF' }}</strong>
              <span>{{ formatDate(item.createTime) }}</span>
            </div>
            <el-tag v-if="item.finalVersion" type="success" size="small">最终版</el-tag>
            <el-tag v-else type="info" size="small">草稿版本</el-tag>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useUserStore } from "@/store/user";
import { getMyTeams } from "@/api/team";
import { getTeamSubmissionHistory } from "@/api/submission";

const userStore = useUserStore();
const loading = ref(false);
const overviewError = ref("");
const activeTeams = ref([]);
const recentSubmissions = ref([]);

const quickLinks = computed(() => {
  const links = [
    { path: "/problem", title: "题库", icon: "Document", color: "#2563eb", bgColor: "#eff6ff" },
    { path: "/team/square", title: "队伍广场", icon: "Team", color: "#0891b2", bgColor: "#ecfeff" },
    { path: "/team", title: "我的队伍", icon: "User", color: "#16a34a", bgColor: "#f0fdf4" },
    { path: "/ranking", title: "排行榜", icon: "Trophy", color: "#d97706", bgColor: "#fffbeb" },
    { path: "/profile/settings", title: "个人设置", icon: "Setting", color: "#475569", bgColor: "#f8fafc" },
  ];
  if (userStore.isAdmin) {
    links.push({ path: "/admin/dashboard", title: "管理后台", icon: "DataAnalysis", color: "#dc2626", bgColor: "#fef2f2" });
  }
  return links;
});

const roleTagType = computed(() => {
  if (userStore.primaryRole === "admin") return "danger";
  if (userStore.primaryRole === "vip") return "warning";
  return "info";
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return "早上好";
  if (hour < 18) return "下午好";
  return "晚上好";
});

function formatDate(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

function teamStatusLabel(status) {
  return ({ PREPARING: "组建中", IN_PROGRESS: "练习中", ENDED: "已结束" })[status] || status;
}

function teamStatusType(status) {
  return ({ PREPARING: "info", IN_PROGRESS: "warning", ENDED: "success" })[status] || "info";
}

async function loadOverview() {
  loading.value = true;
  overviewError.value = "";
  try {
    const [preparing, active] = await Promise.all([
      getMyTeams({ practiceStatus: "PREPARING", page: 1, pageSize: 4 }),
      getMyTeams({ practiceStatus: "IN_PROGRESS", page: 1, pageSize: 4 }),
    ]);
    activeTeams.value = [...(preparing.data?.rows || []), ...(active.data?.rows || [])];

    const submissionTeams = activeTeams.value.slice(0, 6);
    const items = [];
    for (const team of submissionTeams) {
      try {
        const res = await getTeamSubmissionHistory(team.id);
        const rows = res.data || [];
        if (rows.length) items.push({ teamId: team.id, latest: rows[rows.length - 1] });
      } catch {
        // 单个队伍提交失败不阻断整页
      }
    }
    recentSubmissions.value = items.map(({ teamId, latest }) => ({ ...latest, teamId }));
  } catch (error) {
    overviewError.value = error.message || "练习概览加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (userStore.isLogin) loadOverview();
});
</script>

<style scoped>
@import "./style.css";
</style>
