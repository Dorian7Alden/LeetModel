<template>
  <div class="about-page">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">关于 LeetModel</h1>
        <p class="hero-subtitle">
          以模型会友，以算法相知。LeetModel 是一个专注于数学建模训练与竞技的在线平台，
          致力于为建模爱好者提供最优质的训练、竞赛与协作体验。
        </p>
      </div>
      <div class="hero-stats">
        <div class="stat-item">
          <span class="stat-num">5,000+</span>
          <span class="stat-text">注册用户</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">124</span>
          <span class="stat-text">精品题目</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">890+</span>
          <span class="stat-text">提交作品</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">6</span>
          <span class="stat-text">年度赛事</span>
        </div>
      </div>
    </section>

    <!-- 使命与愿景 -->
    <section class="section">
      <h2 class="section-title">我们的使命</h2>
      <el-row :gutter="24">
        <el-col :md="8" :sm="24" v-for="item in missions" :key="item.title">
          <div class="mission-card">
            <div class="mission-icon" :style="{ background: item.bg }">
              <el-icon :size="28"><component :is="item.icon" /></el-icon>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- 项目里程碑时间线 -->
    <section class="section">
      <h2 class="section-title">项目里程碑</h2>
      <div class="timeline">
        <div class="timeline-item" v-for="(milestone, idx) in milestones" :key="idx">
          <div class="timeline-dot"></div>
          <div class="timeline-content">
            <span class="time">{{ milestone.time }}</span>
            <h4>{{ milestone.title }}</h4>
            <p>{{ milestone.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 技术栈 -->
    <section class="section">
      <h2 class="section-title">技术栈</h2>
      <div class="tech-grid">
        <div class="tech-category" v-for="cat in techStack" :key="cat.name">
          <h4 class="tech-cat-name">{{ cat.name }}</h4>
          <div class="tech-badges">
            <span class="tech-badge" v-for="tech in cat.items" :key="tech">{{ tech }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { Aim, DataAnalysis, Connection, Promotion } from '@element-plus/icons-vue'

const missions = [
  { title: '精准训练', desc: '覆盖模型设计、算法实现、数据处理、结果分析、论文写作五大维度，提供全方位建模训练。', icon: 'Aim', bg: '#eff6ff' },
  { title: '以赛促学', desc: '对接国赛、美赛、统计建模等主流赛事，提供赛前训练、赛中交流、赛后复盘的全流程支持。', icon: 'DataAnalysis', bg: '#f0fdf4' },
  { title: '协作共赢', desc: '打通组队、讨论、分享等协作场景，让建模不再是一个人的战斗。', icon: 'Connection', bg: '#fffbeb' },
]

const milestones = [
  { time: '2025年9月', title: '项目立项', desc: '核心团队组建，确定技术选型与产品方向。' },
  { time: '2025年11月', title: 'Alpha 版本', desc: '完成用户系统、题库模块、作品提交核心功能开发。' },
  { time: '2026年1月', title: 'Beta 公测', desc: '上线社区模块、赛事模块，开放首批种子用户测试。' },
  { time: '2026年3月', title: 'v1.0 正式发布', desc: '完整功能上线，包括 AI 评审引擎和 RBAC 权限体系。' },
  { time: '2026年5月', title: 'v1.1 持续迭代', desc: '优化用户体验，接入更多竞赛数据源，完善训练体系。' },
]

const techStack = [
  { name: '前端', items: ['Vue 3', 'Element Plus', 'ECharts', 'Pinia', 'Vue Router', 'Vite'] },
  { name: '后端', items: ['Spring Boot 3', 'MyBatis Plus', 'MySQL', 'Redis', 'OSS', 'JWT'] },
  { name: 'AI', items: ['Python', 'Scikit-learn', 'Auto-Evaluation', 'NLP Feedback'] },
  { name: '部署', items: ['Docker', 'Nginx', 'GitHub Actions', 'Linux'] },
]
</script>

<style scoped>
.about-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px 20px 60px;
}

/* Hero */
.hero-section {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  border-radius: 20px;
  padding: 56px 48px;
  text-align: center;
  color: #fff;
  margin-bottom: 40px;
}

.hero-title {
  font-size: 38px;
  font-weight: 800;
  margin: 0 0 16px;
  letter-spacing: 1px;
}

.hero-subtitle {
  font-size: 16px;
  opacity: 0.8;
  max-width: 650px;
  margin: 0 auto;
  line-height: 1.8;
}

.hero-stats {
  display: flex;
  justify-content: center;
  gap: 48px;
  margin-top: 36px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-num {
  font-size: 28px;
  font-weight: 800;
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-text {
  font-size: 13px;
  opacity: 0.65;
}

/* Sections */
.section {
  margin-bottom: 44px;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 24px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--lm-primary, #409eff);
  display: inline-block;
}

/* Mission cards */
.mission-card {
  text-align: center;
  padding: 30px 20px;
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  transition: all 0.25s;
  height: 100%;
}

.mission-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.mission-icon {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  color: var(--lm-primary, #409eff);
}

.mission-card h3 {
  font-size: 17px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 10px;
}

.mission-card p {
  font-size: 14px;
  color: var(--lm-text-secondary, #666);
  margin: 0;
  line-height: 1.7;
}

/* Timeline */
.timeline {
  position: relative;
  padding-left: 28px;
}

.timeline::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: var(--lm-border, #d9dce1);
}

.timeline-item {
  position: relative;
  padding-bottom: 28px;
}

.timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: -23px;
  top: 6px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--lm-primary, #409eff);
  border: 3px solid #fff;
  box-shadow: 0 0 0 3px var(--lm-primary, #409eff);
}

.timeline-content {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 10px;
  padding: 18px 22px;
}

.timeline-content .time {
  font-size: 12px;
  color: var(--lm-primary, #409eff);
  font-weight: 600;
}

.timeline-content h4 {
  font-size: 16px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 6px 0 4px;
}

.timeline-content p {
  font-size: 13px;
  color: var(--lm-text-secondary, #666);
  margin: 0;
  line-height: 1.6;
}

/* Tech Stack */
.tech-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tech-category {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.tech-cat-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  min-width: 60px;
  margin: 0;
  padding-top: 6px;
}

.tech-badges {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tech-badge {
  padding: 6px 16px;
  background: var(--lm-bg, #f5f7fa);
  color: var(--lm-text-secondary, #555);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid var(--lm-border, #e8ecf1);
  transition: all 0.2s;
}

.tech-badge:hover {
  color: var(--lm-primary, #409eff);
  border-color: var(--lm-primary, #409eff);
  background: #f0f7ff;
}

@media (max-width: 768px) {
  .hero-section {
    padding: 36px 24px;
  }
  .hero-title {
    font-size: 28px;
  }
  .hero-stats {
    gap: 24px;
  }
  .stat-num {
    font-size: 22px;
  }
}
</style>
