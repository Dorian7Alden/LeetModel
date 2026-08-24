<template>
  <div class="about-page">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">关于 LeetModel</h1>
        <p class="hero-subtitle">
          以模型会友，以算法相知。LeetModel 是一个专注于数学建模训练与竞技的在线平台，
          致力于为建模爱好者提供从题目、组队、限时提交到 AI 评审的完整实训体验。
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
  { title: '限时实训', desc: '以公开赛题为练习内容，通过题目规定时长还原完整建模节奏。', icon: 'DataAnalysis', bg: '#f0fdf4' },
  { title: '协作共赢', desc: '围绕具体题目组建队伍、分配职责并共享提交与评审结果。', icon: 'Connection', bg: '#fffbeb' },
]

const milestones = [
  { time: '2025年9月', title: '项目立项', desc: '核心团队组建，确定技术选型与产品方向。' },
  { time: '2025年11月', title: 'Alpha 版本', desc: '完成用户系统、题库模块、作品提交核心功能开发。' },
  { time: '2026年1月', title: 'Beta 公测', desc: '上线题目组队与限时练习主流程，开放首批种子用户测试。' },
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
@import './style.css';
</style>
