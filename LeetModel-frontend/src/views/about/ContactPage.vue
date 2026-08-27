<template>
  <div class="contact-page">
    <PageHeader title="支持渠道" description="只展示当前真实可用的帮助与反馈方式" />
    <el-alert title="当前版本尚未接入站内反馈表单" type="info" :closable="false" show-icon>
      <template #default>我们不会模拟“发送成功”。登录用户可先使用右下角 AI 客服查询平台操作；缺陷与建议请通过项目 GitHub 提交。</template>
    </el-alert>
    <div class="info-cards support-grid">
      <div class="info-card">
        <div class="info-icon"><el-icon :size="24"><ChatDotRound /></el-icon></div>
        <div class="info-body"><h4>AI 客服</h4><p>登录后从页面右下角打开</p><span class="info-hint">用于平台操作说明与受控选题辅助</span></div>
      </div>
      <div class="info-card github-card">
        <div class="info-icon"><el-icon :size="24"><Link /></el-icon></div>
        <div class="info-body"><h4>GitHub Issues</h4><p><a href="https://github.com/Dorian7Alden/LeetModel/issues" target="_blank" rel="noopener noreferrer">提交缺陷或建议</a></p><span class="info-hint">请勿在公开 Issue 中提交密码、密钥或个人隐私</span></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ChatDotRound, Link } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
</script>

<style scoped>
@import './style.css';
.support-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; margin-top: 24px; }
@media (max-width: 720px) { .support-grid { grid-template-columns: 1fr; } }
</style>
