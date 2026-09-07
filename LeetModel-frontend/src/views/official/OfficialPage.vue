<template>
  <div class="official-page">
    <main class="official-main">
      <section class="hero">
        <div class="hero-copy">
          <img src="@/assets/images/logo-with-en.png" alt="LeetModel" class="hero-logo" />
          <el-tag type="primary" effect="light">数学建模限时实训</el-tag>
          <h1>让每一篇建模论文都被认真评审</h1>
          <p>
            LeetModel 提供真实的题目、组队、限时提交与 AI 自动评审闭环，
            用可量化的评分与改进建议帮助你打磨建模作品。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" @click="$router.push('/problem')">浏览题库</el-button>
            <el-button v-if="userStore.isLogin" size="large" @click="$router.push('/home')">进入首页</el-button>
            <template v-else>
              <el-button size="large" @click="$router.push('/register')">免费注册</el-button>
              <el-button text size="large" @click="$router.push('/login')">已有账号，去登录</el-button>
            </template>
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
    </main>

    <footer class="official-footer">
      <span>&copy; 2026 数学建模在线评测系统. All rights reserved.</span>
    </footer>
  </div>
</template>

<script setup>
import { useUserStore } from "@/store/user";

const userStore = useUserStore();

const steps = [
  { title: "选择题目", description: "按赛事、年份、语言和难度找到练习题目。" },
  { title: "组建队伍", description: "创建题目队伍，添加成员并分配建模、编程和论文职责。" },
  { title: "限时提交", description: "开始倒计时，在截止前提交可追溯的 PDF 论文版本。" },
  { title: "查看评审", description: "等待异步 AI 评审完成，与队员共享结果并获取改进建议。" },
];
</script>

<style scoped>
@import "./style.css";
</style>
