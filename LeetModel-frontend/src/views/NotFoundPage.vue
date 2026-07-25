<template>
  <div class="notfound-page">
    <div class="notfound-content">
      <div class="illustration">
        <svg viewBox="0 0 400 200" xmlns="http://www.w3.org/2000/svg">
          <text x="200" y="120" text-anchor="middle" font-size="96" font-weight="800" fill="var(--lm-primary, #409eff)" opacity="0.15">404</text>
          <text x="200" y="120" text-anchor="middle" font-size="32" font-weight="700" fill="var(--lm-text-primary, #1a1a2e)">4 0 4</text>
        </svg>
      </div>

      <h1 class="title">页面不见了</h1>
      <p class="description">
        你访问的页面可能已被移除、链接失效，或者你输入了错误的地址。
      </p>

      <div class="actions">
        <el-button type="primary" size="large" @click="goHome">
          <el-icon><HomeFilled /></el-icon>
          返回首页
        </el-button>
        <el-button size="large" @click="goBack">
          <el-icon><Back /></el-icon>
          返回上页
        </el-button>
      </div>

      <div class="tips">
        <h4>你可能想找：</h4>
        <div class="tips-links">
          <router-link to="/problem">题库训练</router-link>
          <router-link to="/contest">赛事一览</router-link>
          <router-link to="/community">社区讨论</router-link>
          <router-link to="/help">使用帮助</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { HomeFilled, Back } from '@element-plus/icons-vue'

const router = useRouter()

function goHome() {
  router.push('/')
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.notfound-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 65vh;
  padding: 40px 20px;
}

.notfound-content {
  text-align: center;
  max-width: 480px;
}

.illustration {
  margin-bottom: 16px;
}

.illustration svg {
  width: 100%;
  max-width: 400px;
  height: auto;
}

.title {
  font-size: 28px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 12px;
}

.description {
  font-size: 15px;
  color: var(--lm-text-secondary, #666);
  margin: 0 0 28px;
  line-height: 1.6;
}

.actions {
  display: flex;
  gap: 14px;
  justify-content: center;
}

.tips {
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid var(--lm-border, #e8ecf1);
}

.tips h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--lm-text-muted, #999);
  margin: 0 0 12px;
}

.tips-links {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
}

.tips-links a {
  font-size: 14px;
  color: var(--lm-primary, #409eff);
  text-decoration: none;
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid var(--lm-border, #e8ecf1);
  transition: all 0.2s;
}

.tips-links a:hover {
  background: var(--lm-primary, #409eff);
  color: #fff;
  border-color: var(--lm-primary, #409eff);
}
</style>
