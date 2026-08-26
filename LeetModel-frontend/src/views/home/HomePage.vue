<template>
  <div class="home-page">
    <!-- 未登录 -->
    <template v-if="!userStore.isLogin">
      <section class="hero">
        <div class="hero-copy">
          <el-tag type="primary" effect="light">数学建模限时实训</el-tag>
          <h1>让每一篇建模论文都被认真评审</h1>
          <p>
            LeetModel 提供真实的题目、组队、限时提交与 AI 自动评审闭环，
            用可量化的评分与改进建议帮助你打磨建模作品。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" @click="$router.push('/problem')">浏览题库</el-button>
            <el-button size="large" @click="$router.push('/register')">免费注册</el-button>
            <el-button text size="large" @click="$router.push('/login')">已有账号，去登录</el-button>
          </div>
        </div>
        <div class="hero-visual">
          <div class="visual-card">
            <span class="visual-kicker">评分概览</span>
            <div class="score-ring"><strong>88</strong><span>/100</span></div>
            <ul class="dimension-row">
              <li>假设合理性</li><li>建模创造性</li><li>结果正确性</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="flow">
        <h2>练习流程</h2>
        <div class="flow-grid">
          <div v-for="(step, index) in steps" :key="step.title" class="flow-card">
            <span class="step-index">{{ index + 1 }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
          </div>
        </div>
      </section>
    </template>

    <!-- 已登录 -->
    <template v-else>
      <section class="welcome">
        <div class="welcome-copy">
          <p class="eyebrow">{{ greeting }}</p>
          <h1>{{ userStore.nickname || userStore.username }}，继续你的建模练习</h1>
          <p>这里是你的练习概览，快速回到进行中的队伍、提交与评审。</p>
        </div>
        <el-tag :type="roleTagType" effect="light" size="large">{{ userStore.roleLabel }}</el-tag>
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
              <span v-if="team.problemId">题目 #{{ team.problemId }}</span>
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
    </template>
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

const steps = [
  { title: "选择题目", description: "按赛事、年份、语言和难度找到练习题目。" },
  { title: "组建队伍", description: "创建题目队伍，添加成员并分配建模、编程和论文职责。" },
  { title: "限时提交", description: "开始倒计时，在截止前提交可追溯的 PDF 论文版本。" },
  { title: "查看评审", description: "等待异步 AI 评审完成，与队员共享结果并获取改进建议。" },
];

const quickLinks = computed(() => {
  const links = [
    { path: "/problem", title: "题库", icon: "Document", color: "#2563eb", bgColor: "#eff6ff" },
    { path: "/team/square", title: "队伍广场", icon: "Team", color: "#0891b2", bgColor: "#ecfeff" },
    { path: "/team", title: "我的队伍", icon: "User", color: "#16a34a", bgColor: "#f0fdf4" },
    { path: "/ranking", title: "排行榜", icon: "Trophy", color: "#d97706", bgColor: "#fffbeb" },
    { path: "/suggestion", title: "论文建议", icon: "ChatDotRound", color: "#db2777", bgColor: "#fdf2f8" },
    { path: "/assistant", title: "AI 客服", icon: "Collection", color: "#0d9488", bgColor: "#f0fdfa" },
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
.home-page { max-width: 1120px; margin: 0 auto; }
.hero { display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 40px; align-items: center; padding: 56px 40px; background: linear-gradient(135deg, #eff6ff, #f8fafc); border: 1px solid #dbeafe; border-radius: 20px; }
.hero-copy h1 { margin: 18px 0 14px; font-size: clamp(30px, 4.5vw, 48px); line-height: 1.2; color: var(--lm-text-primary); }
.hero-copy p { max-width: 560px; margin: 0; color: var(--lm-text-secondary); font-size: 16px; line-height: 1.8; }
.hero-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 28px; }
.hero-visual { display: flex; justify-content: center; }
.visual-card { width: 260px; padding: 28px; background: #fff; border: 1px solid var(--lm-border); border-radius: 16px; box-shadow: var(--lm-shadow-lg); }
.visual-kicker { font-size: 12px; color: var(--lm-text-muted); }
.score-ring { display: flex; align-items: baseline; margin: 14px 0; }
.score-ring strong { font-size: 56px; line-height: 1; color: var(--lm-primary); }
.score-ring span { color: var(--lm-text-muted); }
.dimension-row { display: flex; flex-wrap: wrap; gap: 8px; padding: 0; margin: 0; list-style: none; }
.dimension-row li { padding: 5px 10px; border-radius: 999px; background: var(--lm-bg-secondary); color: var(--lm-text-secondary); font-size: 12px; }
.flow { margin-top: 56px; }
.flow h2, .section-title { margin: 0 0 20px; color: var(--lm-text-primary); font-size: 20px; }
.flow-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.flow-card { padding: 24px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 14px; }
.step-index { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 50%; background: var(--lm-primary); color: #fff; font-weight: 700; }
.flow-card h3 { margin: 16px 0 8px; color: var(--lm-text-primary); }
.flow-card p { margin: 0; color: var(--lm-text-secondary); font-size: 14px; line-height: 1.7; }
.welcome { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 32px; }
.eyebrow { margin: 0 0 6px; color: var(--lm-primary); font-size: 13px; font-weight: 600; }
.welcome h1 { margin: 0; font-size: clamp(24px, 3.5vw, 34px); color: var(--lm-text-primary); }
.welcome p { margin: 8px 0 0; color: var(--lm-text-secondary); }
.quick-section { margin-bottom: 36px; }
.quick-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.quick-link { display: flex; align-items: center; gap: 12px; padding: 16px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; text-decoration: none; transition: box-shadow var(--lm-transition), transform var(--lm-transition); }
.quick-link:hover { box-shadow: var(--lm-shadow); transform: translateY(-2px); }
.quick-icon { display: flex; width: 40px; height: 40px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 10px; }
.quick-label { color: var(--lm-text-secondary); font-size: 15px; font-weight: 600; }
.overview-alert { margin-bottom: 20px; }
.overview-section { min-height: 120px; }
.overview-heading { display: flex; align-items: baseline; justify-content: space-between; }
.view-all { color: var(--lm-primary); font-size: 14px; }
.team-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-bottom: 32px; }
.team-card { padding: 18px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 12px; text-decoration: none; }
.team-card-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.team-card-head strong { color: var(--lm-text-primary); }
.team-card-meta { display: flex; gap: 14px; margin-top: 8px; color: var(--lm-text-muted); font-size: 13px; }
.recent-heading { margin-top: 8px; }
.submission-list { display: flex; flex-direction: column; gap: 10px; }
.submission-row { display: flex; align-items: center; gap: 14px; padding: 12px 16px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 10px; }
.submission-version { display: inline-flex; width: 52px; height: 42px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 8px; background: var(--lm-primary-bg); color: var(--lm-primary); font-weight: 700; }
.submission-info { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 2px; }
.submission-info strong { overflow: hidden; color: var(--lm-text-primary); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.submission-info span { color: var(--lm-text-muted); font-size: 12px; }
@media (max-width: 900px) { .hero { grid-template-columns: 1fr; padding: 40px 24px; } .quick-grid { grid-template-columns: repeat(2, 1fr); } .team-grid { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .flow-grid { grid-template-columns: repeat(2, 1fr); } .hero-visual { display: none; } }
@media (max-width: 520px) { .flow-grid { grid-template-columns: 1fr; } .quick-grid { grid-template-columns: 1fr; } }
</style>
