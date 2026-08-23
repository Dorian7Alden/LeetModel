<template>
  <div class="user-card">
    <div class="user-avatar" @click="triggerUpload">
      <img v-if="profile.avatarUrl" :src="profile.avatarUrl" class="avatar-img" />
      <span v-else>{{ (profile.username || '').charAt(0) || '?' }}</span>
      <div class="avatar-overlay">
        <el-icon :size="16"><Camera /></el-icon>
      </div>
      <input ref="fileInput" type="file" accept="image/*" class="file-hidden" @change="handleFileChange" />
    </div>

    <div class="user-info">
      <div class="info-top">
        <h3 class="username">{{ profile.nickname || profile.username || '未设置昵称' }}</h3>
        <el-tag :type="roleTagType" size="small" effect="light">
          {{ userStore.roleLabel }}
        </el-tag>
      </div>
      <div class="info-details">
        <div class="detail-item">
          <el-icon :size="15" class="detail-icon"><Message /></el-icon>
          <span class="detail-label">邮箱</span>
          <span class="detail-value">{{ profile.email || '未填写' }}</span>
        </div>
        <div class="detail-item">
          <el-icon :size="15" class="detail-icon"><School /></el-icon>
          <span class="detail-label">学校</span>
          <span class="detail-value">{{ profile.school || '未填写' }}</span>
        </div>
        <div class="detail-item">
          <el-icon :size="15" class="detail-icon"><Phone /></el-icon>
          <span class="detail-label">手机</span>
          <span class="detail-value">{{ maskPhone(profile.phone) }}</span>
        </div>
        <div class="detail-item">
          <el-icon :size="15" class="detail-icon"><Clock /></el-icon>
          <span class="detail-label">注册于</span>
          <span class="detail-value">{{ profile.createTime || '未知' }}</span>
        </div>
      </div>
    </div>

    <el-button class="edit-btn" @click="$router.push('/profile/settings')" round>
      <el-icon><EditPen /></el-icon>
      编辑资料
    </el-button>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Message, School, Phone, Clock, Camera } from '@element-plus/icons-vue'
import { getCurrentUser, uploadCurrentAvatar } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const roleTagType = computed(() => {
  if (userStore.primaryRole === 'admin') return 'danger'
  if (userStore.primaryRole === 'vip') return 'warning'
  return 'info'
})

const fileInput = ref(null)

const profile = ref({
  username: '',
  nickname: '',
  email: '',
  school: '',
  phone: '',
  createTime: '',
  avatarUrl: '',
})

async function loadProfile() {
  try {
    const res = await getCurrentUser()
    const user = res.data
    profile.value.username = user.username ?? ''
    profile.value.nickname = user.nickname ?? ''
    profile.value.email = user.email ?? ''
    profile.value.school = user.school ?? ''
    profile.value.phone = user.phone ?? ''
    profile.value.createTime = user.createTime ?? ''
    profile.value.avatarUrl = user.avatarUrl ?? ''
  } catch {
    // keep defaults on error
  }
}

function maskPhone(phone) {
  if (!phone) return '未填写'
  return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

function triggerUpload() {
  fileInput.value?.click()
}

async function handleFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadCurrentAvatar(file)
    profile.value.avatarUrl = res.data.avatarUrl
    userStore.updateProfile({ avatarUrl: res.data.avatarUrl })
    ElMessage.success('头像更新成功')
  } catch {
    ElMessage.error('头像上传失败')
  } finally {
    e.target.value = ''
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.user-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  padding: 20px 28px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.user-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  font-size: 26px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.user-avatar:hover .avatar-overlay {
  opacity: 1;
}

.file-hidden {
  display: none;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.info-top {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.username {
  font-size: 20px;
  font-weight: 700;
  color: var(--lm-text-primary);
  margin: 0;
}

.info-details {
  display: flex;
  flex-wrap: wrap;
  gap: 0 24px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 0;
}

.detail-icon {
  color: var(--lm-text-muted);
  flex-shrink: 0;
}

.detail-label {
  font-size: 12px;
  color: var(--lm-text-muted);
  flex-shrink: 0;
}

.detail-label::after {
  content: ':';
  margin-left: 1px;
}

.detail-value {
  font-size: 13px;
  color: var(--lm-text-secondary);
  margin-left: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.edit-btn {
  flex-shrink: 0;
  font-weight: 500;
}
</style>
