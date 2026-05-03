<template>
  <div class="team-detail-page">
    <!-- Hero 区域 -->
    <div class="hero-section">
      <div class="hero-content">
        <div class="hero-info">
          <h1 class="team-name">{{ team.name }}</h1>
          <p class="team-desc">{{ team.description }}</p>
          <div class="hero-meta">
            <span class="meta-item">
              <el-icon><Calendar /></el-icon>
              {{ team.createTime }} 创建
            </span>
            <span class="meta-item">
              <el-icon><UserFilled /></el-icon>
              {{ team.memberCount }} / {{ team.maxMembers }} 人
            </span>
          </div>
        </div>
        <div class="hero-action">
          <el-button
            v-if="team.missingRoles.length > 0"
            type="primary"
            size="large"
            @click="handleJoinRequest"
          >
            <el-icon><Plus /></el-icon>
            申请加入
          </el-button>
          <el-tag v-else type="success" size="large" class="full-tag">
            <el-icon><CircleCheck /></el-icon>
            队伍已满
          </el-tag>
        </div>
      </div>
    </div>

    <div class="detail-body">
      <!-- 当前成员 -->
      <div class="section">
        <h3 class="section-title">当前成员</h3>
        <div class="member-list">
          <div v-for="(member, idx) in team.members" :key="idx" class="member-item">
            <div
              class="member-avatar"
              :style="{ backgroundColor: avatarColors[idx % avatarColors.length] }"
            >
              {{ member.charAt(0) }}
            </div>
            <div class="member-info">
              <span class="member-name">{{ member }}</span>
              <span class="member-role">{{ getMemberRole(idx) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 急需角色 -->
      <div v-if="team.missingRoles.length > 0" class="section highlight-section">
        <h3 class="section-title">
          <el-icon class="icon-fire"><WarningFilled /></el-icon>
          急招角色
        </h3>
        <div class="missing-cards">
          <div v-for="role in team.missingRoles" :key="role" class="missing-card">
            <el-icon class="role-icon"><Aim /></el-icon>
            <span class="role-name">{{ role }}</span>
            <span class="role-hint">虚位以待</span>
          </div>
        </div>
        <div class="cta-box">
          <p>这个队伍正在寻找像你一样优秀的人才！</p>
          <el-button type="primary" @click="handleJoinRequest">
            <el-icon><Plus /></el-icon>
            立即申请加入
          </el-button>
        </div>
      </div>

      <!-- 队伍统计 -->
      <div class="section">
        <h3 class="section-title">队伍统计</h3>
        <el-row :gutter="20">
          <el-col :span="8" v-for="stat in teamStats" :key="stat.label">
            <div class="stat-card">
              <span class="stat-value">{{ stat.value }}</span>
              <span class="stat-label">{{ stat.label }}</span>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  UserFilled,
  Plus,
  CircleCheck,
  WarningFilled,
  Aim,
} from '@element-plus/icons-vue'
import { mockTeams } from '@/mock/data.js'

const route = useRoute()

const teamId = computed(() => Number(route.params.id))
const team = computed(() => {
  return mockTeams.find((t) => t.teamId === teamId.value) || mockTeams[0]
})

const avatarColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#8b5cf6', '#0891b2']

const defaultRoles = ['建模', '编程', '写作']

function getMemberRole(idx) {
  return defaultRoles[idx % defaultRoles.length]
}

const teamStats = computed(() => [
  { label: '已提交作品', value: team.value.memberCount * 7 || 7 },
  { label: '平均得分', value: (85 + Math.random() * 10).toFixed(1) },
  { label: '参赛次数', value: Math.floor(Math.random() * 8) + 1 },
])

function handleJoinRequest() {
  ElMessage.success('申请已发送，请等待队长审核')
}
</script>

<style scoped>
.team-detail-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px 60px;
}

/* Hero */
.hero-section {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border-radius: 16px;
  padding: 40px 48px;
  margin-top: 20px;
  color: #fff;
}

.hero-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  flex-wrap: wrap;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.team-name {
  font-size: 30px;
  font-weight: 800;
  margin: 0 0 10px;
  letter-spacing: 0.5px;
}

.team-desc {
  font-size: 15px;
  opacity: 0.9;
  margin: 0 0 16px;
  line-height: 1.6;
}

.hero-meta {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  opacity: 0.85;
}

.hero-action {
  flex-shrink: 0;
}

.full-tag {
  font-size: 15px !important;
  padding: 12px 20px !important;
}

/* Body */
.detail-body {
  margin-top: 28px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.section {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  padding: 28px;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0 0 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Members */
.member-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.member-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  background: var(--lm-bg, #f8f9fb);
  border-radius: 10px;
  transition: background 0.2s;
}

.member-item:hover {
  background: #eef2f7;
}

.member-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.member-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.member-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--lm-text-primary, #1a1a2e);
}

.member-role {
  font-size: 12px;
  color: var(--lm-text-muted, #999);
}

/* Missing roles */
.highlight-section {
  border-color: #fbc4c4;
  background: #fffafa;
}

.icon-fire {
  color: #f56c6c;
}

.missing-cards {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.missing-card {
  flex: 1;
  min-width: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  background: #fff;
  border: 2px dashed var(--lm-border, #d9dce1);
  border-radius: 12px;
  transition: all 0.2s;
}

.missing-card:hover {
  border-color: var(--lm-primary, #409eff);
  background: #f0f7ff;
}

.role-icon {
  font-size: 28px;
  color: var(--lm-primary, #409eff);
}

.role-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--lm-text-primary, #1a1a2e);
}

.role-hint {
  font-size: 12px;
  color: var(--lm-text-muted, #999);
}

.cta-box {
  text-align: center;
  padding: 20px;
  background: linear-gradient(135deg, #f0f7ff, #e6f0ff);
  border-radius: 10px;
}

.cta-box p {
  margin: 0 0 12px;
  color: var(--lm-text-secondary, #666);
  font-size: 14px;
  font-weight: 500;
}

/* Stats */
.stat-card {
  text-align: center;
  padding: 20px;
  background: var(--lm-bg, #f8f9fb);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: 800;
  color: var(--lm-primary, #409eff);
}

.stat-label {
  font-size: 13px;
  color: var(--lm-text-muted, #999);
}

@media (max-width: 768px) {
  .hero-section {
    padding: 28px 24px;
  }

  .team-name {
    font-size: 24px;
  }

  .section {
    padding: 20px;
  }
}
</style>
