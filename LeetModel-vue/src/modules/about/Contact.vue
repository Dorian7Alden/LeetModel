<template>
  <div class="contact-page">
    <PageHeader title="联系我们" description="我们非常乐意听到你的声音，无论是问题反馈、建议还是合作洽谈" />

    <el-row :gutter="30">
      <!-- 联系表单 -->
      <el-col :lg="16" :sm="24">
        <div class="form-card">
          <h3 class="card-title">发送消息</h3>
          <el-form :model="form" label-width="80px" class="contact-form">
            <el-form-item label="你的邮箱" required>
              <el-input v-model="form.email" placeholder="请输入你的邮箱地址" />
            </el-form-item>
            <el-form-item label="消息类型">
              <el-select v-model="form.type" placeholder="请选择" style="width: 100%">
                <el-option label="问题反馈" value="bug" />
                <el-option label="功能建议" value="feature" />
                <el-option label="合作洽谈" value="cooperation" />
                <el-option label="其他" value="other" />
              </el-select>
            </el-form-item>
            <el-form-item label="消息标题" required>
              <el-input v-model="form.title" placeholder="请简要描述你的问题或建议" />
            </el-form-item>
            <el-form-item label="详细描述" required>
              <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请详细描述你的问题或建议..." />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" @click="handleSubmit" :loading="submitting">
                发送消息
              </el-button>
              <el-button size="large" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-col>

      <!-- 联系信息卡片 -->
      <el-col :lg="8" :sm="24">
        <div class="info-cards">
          <div class="info-card email-card">
            <div class="info-icon">
              <el-icon :size="24"><Message /></el-icon>
            </div>
            <div class="info-body">
              <h4>电子邮箱</h4>
              <p>support@leetmodel.com</p>
              <span class="info-hint">工作日 24 小时内回复</span>
            </div>
          </div>

          <div class="info-card qq-card">
            <div class="info-icon">
              <el-icon :size="24"><ChatDotRound /></el-icon>
            </div>
            <div class="info-body">
              <h4>QQ 交流群</h4>
              <p>123456789</p>
              <span class="info-hint">5000+ 建模爱好者在线交流</span>
            </div>
          </div>

          <div class="info-card github-card">
            <div class="info-icon">
              <el-icon :size="24"><Link /></el-icon>
            </div>
            <div class="info-body">
              <h4>GitHub</h4>
              <p><a href="https://github.com/leetmodel" target="_blank">github.com/leetmodel</a></p>
              <span class="info-hint">欢迎 Star &amp; PR</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Message, ChatDotRound, Link } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'

const submitting = ref(false)

const form = reactive({
  email: '',
  type: '',
  title: '',
  content: '',
})

function handleSubmit() {
  if (!form.email || !form.title || !form.content) {
    ElMessage.warning('请填写完整的消息内容')
    return
  }
  submitting.value = true
  setTimeout(() => {
    submitting.value = false
    ElMessage.success('消息已发送，我们会尽快回复你！')
    handleReset()
  }, 1200)
}

function handleReset() {
  form.email = ''
  form.type = ''
  form.title = ''
  form.content = ''
}
</script>

<style scoped>
.contact-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px 20px 60px;
}

.form-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  padding: 32px;
}

.card-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 24px;
}

.contact-form {
  margin-top: 8px;
}

/* Info Cards */
.info-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  padding: 22px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  transition: all 0.25s;
}

.info-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}

.info-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.email-card .info-icon {
  background: #eff6ff;
  color: #409eff;
}

.qq-card .info-icon {
  background: #f0fdf4;
  color: #67c23a;
}

.github-card .info-icon {
  background: #f8f9fb;
  color: #1a1a2e;
}

.info-body h4 {
  font-size: 15px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 4px;
}

.info-body p {
  font-size: 14px;
  color: var(--lm-text-secondary, #555);
  margin: 0 0 2px;
  word-break: break-all;
}

.info-body a {
  color: var(--lm-primary, #409eff);
  text-decoration: none;
}

.info-body a:hover {
  text-decoration: underline;
}

.info-hint {
  font-size: 12px;
  color: var(--lm-text-muted, #999);
}

@media (max-width: 992px) {
  .info-cards {
    margin-top: 20px;
  }
}
</style>
