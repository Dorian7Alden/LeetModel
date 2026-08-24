<template>
  <div class="home-page">
    <section class="hero">
      <el-tag type="primary" effect="light">数学建模限时实训</el-tag>
      <h1>从选择题目，到获得 AI 评审结果</h1>
      <p>浏览公开赛题，组建三人以内的练习队伍，在规定时间内提交 PDF 论文并查看共享评审结果。</p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="$router.push('/problem')">浏览题库</el-button>
        <el-button size="large" @click="$router.push('/team')">我的队伍</el-button>
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
  </div>
</template>

<script setup>
const steps = [
  { title: '选择题目', description: '按赛事、年份、语言和难度找到练习题目。' },
  { title: '组建队伍', description: '创建题目队伍，添加成员并分配建模、编程和论文职责。' },
  { title: '限时提交', description: '开始倒计时，在截止前提交可追溯的 PDF 论文版本。' },
  { title: '查看评审', description: '等待异步 AI 评审完成，与本次练习队员共享结果。' },
]
</script>

<style scoped>
.home-page { max-width: 1120px; margin: 0 auto; padding: 72px 24px; }
.hero { padding: 64px; text-align: center; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: var(--lm-radius-lg); }
.hero h1 { margin: 20px 0 16px; font-size: clamp(32px, 5vw, 52px); color: var(--lm-text-primary); }
.hero p { max-width: 720px; margin: 0 auto; line-height: 1.8; color: var(--lm-text-secondary); }
.hero-actions { display: flex; justify-content: center; gap: 12px; margin-top: 32px; }
.flow { margin-top: 48px; }
.flow h2 { margin-bottom: 20px; color: var(--lm-text-primary); }
.flow-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.flow-card { padding: 24px; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: var(--lm-radius); }
.step-index { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 50%; background: var(--lm-primary); color: white; font-weight: 700; }
.flow-card h3 { margin: 16px 0 8px; color: var(--lm-text-primary); }
.flow-card p { margin: 0; font-size: 14px; line-height: 1.7; color: var(--lm-text-secondary); }
@media (max-width: 800px) { .hero { padding: 40px 24px; } .flow-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 520px) { .flow-grid { grid-template-columns: 1fr; } }
</style>
