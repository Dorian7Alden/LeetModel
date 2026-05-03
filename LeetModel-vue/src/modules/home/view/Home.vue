<template>
  <div class="home-page">
    <!-- Hero Banner -->
    <section class="hero-banner">
      <div class="hero-content">
        <h1 class="hero-title">数学建模在线评测系统</h1>
        <p class="hero-subtitle">学习 · 训练 · 竞赛 · 交流 — 一站式数学建模实践平台</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" round @click="$router.push('/problem')">
            <el-icon><Search /></el-icon>
            开始刷题
          </el-button>
          <el-button size="large" round @click="$router.push('/contest')">
            <el-icon><Trophy /></el-icon>
            查看赛事
          </el-button>
        </div>
        <div class="hero-stats">
          <div class="hero-stat" v-for="s in heroStats" :key="s.label">
            <span class="hero-stat-value">{{ s.value }}</span>
            <span class="hero-stat-label">{{ s.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Main Content -->
    <div class="home-container">
      <div class="home-content">
        <div class="home-layout">
          <div class="main-content">
            <ContestSection />
            <communitySection />
          </div>
          <div class="sidebar">
            <CalendarSection />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Search, Trophy } from '@element-plus/icons-vue'
import ContestSection from "../components/homepage/ContestSection.vue";
import communitySection from "../components/homepage/communitySection.vue";
import CalendarSection from "../components/homepage/CalendarSection.vue";

const heroStats = [
  { value: '120+', label: '精品题目' },
  { value: '8,900+', label: '注册用户' },
  { value: '15+', label: '年度赛事' },
  { value: '50K+', label: '提交评测' },
]
</script>

<style scoped>
.home-page {
  background: var(--lm-bg);
}

/* Hero */
.hero-banner {
  background: linear-gradient(135deg, #1e3a5f 0%, #2563eb 40%, #3b82f6 70%, #60a5fa 100%);
  padding: 60px 40px;
  position: relative;
  overflow: hidden;
}

.hero-banner::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 500px;
  height: 500px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 50%;
}

.hero-banner::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: 5%;
  width: 300px;
  height: 300px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 50%;
}

.hero-content {
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 40px;
  font-weight: 800;
  color: #ffffff;
  margin: 0 0 12px;
  letter-spacing: -0.5px;
}

.hero-subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.85);
  margin: 0 0 32px;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 40px;
}

.hero-actions .el-button--primary {
  background: #ffffff;
  color: #2563eb;
  border-color: #ffffff;
  font-weight: 600;
}

.hero-actions .el-button--primary:hover {
  background: #f0f5ff;
  color: #1d4ed8;
}

.hero-actions .el-button:not(.el-button--primary) {
  background: rgba(255, 255, 255, 0.15);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
}

.hero-actions .el-button:not(.el-button--primary):hover {
  background: rgba(255, 255, 255, 0.25);
}

.hero-stats {
  display: flex;
  justify-content: center;
  gap: 48px;
}

.hero-stat {
  text-align: center;
}

.hero-stat-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.2;
}

.hero-stat-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 4px;
}

/* Main Layout */
.home-container {
  max-width: 1200px;
  margin: 0 auto;
}

.home-layout {
  display: flex;
  gap: 30px;
  padding: 30px 40px;
}

.main-content {
  flex: 6;
  display: flex;
  flex-direction: column;
  gap: 30px;
  min-width: 0;
}

.sidebar {
  flex: 4;
  max-width: 380px;
}

@media (max-width: 1024px) {
  .home-layout {
    flex-direction: column;
    padding: 20px;
  }
  .sidebar {
    max-width: none;
  }
  .hero-title {
    font-size: 28px;
  }
  .hero-stats {
    gap: 24px;
  }
}

@media (max-width: 768px) {
  .hero-banner {
    padding: 40px 20px;
  }
  .hero-stats {
    flex-wrap: wrap;
    gap: 16px 24px;
  }
}
</style>
