<template>
  <div class="team-card" :class="`team-card--${displayMode}`" @click="goDetail">
    <div v-if="displayMode === 'roles'" class="role-focus">
      <span class="focus-label">开放职位</span>
      <div class="role-list">
        <div v-for="item in openRecruitments" :key="item.id" class="role-item">
          <strong>{{ recruitmentText(item) }}</strong>
          <small>发布于 {{ formatDate(item.createTime) }}</small>
        </div>
      </div>
    </div>

    <div v-if="displayMode === 'problems'" class="problem-focus">
      <span>当前题目</span>
      <strong>{{ team.problemTitle || `题目 ${team.problemCode || team.problemId}` }}</strong>
      <small>题目 {{ team.problemCode || team.problemId }}</small>
    </div>

    <div class="card-header">
      <h3 class="team-name">{{ team.name }}</h3>
      <span class="member-count">{{ team.memberCount }} / {{ team.maxMembers }}</span>
    </div>

    <p class="team-desc">{{ team.description }}</p>
    <div v-if="displayMode !== 'problems'" class="team-context">
      <el-tag size="small" type="info">题号 {{ team.problemCode || team.problemId }} · {{ team.problemTitle || '标题加载中' }}</el-tag>
      <el-tag size="small" :type="practiceType">{{ practiceLabel }}</el-tag>
    </div>

    <!-- 成员头像 -->
    <div class="member-avatars">
      <button
        v-for="(member, idx) in team.members"
        :key="member.userId || idx"
        type="button"
        class="avatar-circle avatar-button"
        :style="{ backgroundColor: avatarColors[idx % avatarColors.length] }"
        :title="member.nickname || `用户 ${member.userId}`"
        :aria-label="`查看${member.nickname || `用户 ${member.userId}`}的个人名片`"
        @click.stop="emit('show-user', member)"
      >
        <img v-if="member.avatarUrl" :src="member.avatarUrl" class="avatar-image" />
        <template v-else>{{ (member.nickname || String(member.userId)).charAt(0) }}</template>
      </button>
      <!-- 空位 -->
      <div
        v-for="n in emptySlots"
        :key="'empty-' + n"
        class="avatar-circle empty-slot"
      >
        <el-icon><User /></el-icon>
      </div>
    </div>

    <div class="team-times"><span>成立于 {{ formatDate(team.createTime) }}</span></div>

    <div v-if="displayMode === 'problems' && team.practiceStatus === 'PREPARING' && openRecruitments.length > 0" class="missing-roles">
      <span class="missing-label">招募：</span>
      <span v-for="item in openRecruitments" :key="item.id" class="role-tag">
        {{ recruitmentText(item) }} · {{ formatDate(item.createTime) }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { User } from '@element-plus/icons-vue'

const props = defineProps({
  team: { type: Object, required: true },
  detailRouteName: { type: String, default: 'TeamDetail' },
  displayMode: { type: String, default: 'teams', validator: value => ['teams', 'problems', 'roles'].includes(value) },
})
const emit = defineEmits(['show-user'])

const router = useRouter()

const avatarColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#8b5cf6', '#0891b2']

const emptySlots = computed(() => {
  return props.team.remainingSlots
})

const openRecruitments = computed(() => (props.team.recruitments || []).filter(item => item.status === 'OPEN'))
const practiceLabel = computed(() => ({ PREPARING: '组建中', IN_PROGRESS: '练习中', ENDED: '已结束' })[props.team.practiceStatus] || props.team.practiceStatus || '未知状态')
const practiceType = computed(() => ({ PREPARING: 'info', IN_PROGRESS: 'warning', ENDED: 'success' })[props.team.practiceStatus] || 'info')

function goDetail() {
  router.push({ name: props.detailRouteName, params: { id: props.team.id } })
}
function recruitmentText(item) { return [item.needModeler && '建模手', item.needProgrammer && '编程手', item.needWriter && '论文手'].filter(Boolean).join('、') }
function formatDate(value) { return value ? value.replace('T', ' ').slice(0, 16) : '未知时间' }
</script>

<style scoped>
.team-card {
  background: var(--lm-surface, #fff);
  border: 1px solid var(--lm-border, #e8ecf1);
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.team-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
  border-color: var(--lm-primary, #409eff);
}

.team-card--roles { border-top: 3px solid #f97316; }
.team-card--problems { border-top: 3px solid #3b82f6; }
.role-focus { padding: 14px; border-radius: 10px; background: #fff7ed; }
.focus-label,.problem-focus span { display: block; margin-bottom: 9px; color: #c2410c; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.role-list { display: grid; gap: 8px; }
.role-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.role-item strong { color: #9a3412; font-size: 16px; }
.role-item small { color: #a16207; font-size: 12px; white-space: nowrap; }
.problem-focus { display: grid; gap: 4px; padding: 14px; border-radius: 10px; background: #eff6ff; }
.problem-focus span { margin: 0; color: #2563eb; }
.problem-focus strong { color: #1e3a8a; font-size: 16px; line-height: 1.45; }
.problem-focus small { color: #64748b; }

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.team-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--lm-text-primary, #1a1a2e);
  margin: 0;
  line-height: 1.3;
}

.member-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--lm-text-secondary, #666);
  background: var(--lm-bg, #f5f7fa);
  padding: 4px 10px;
  border-radius: 20px;
  white-space: nowrap;
}

.team-desc {
  font-size: 13px;
  color: var(--lm-text-muted, #999);
  margin: 0;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.team-context { display: flex; gap: 8px; flex-wrap: wrap; }

.member-avatars {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.avatar-circle {
  padding: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  border: 2px solid #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}
.avatar-button { cursor: pointer; transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease; }
.avatar-button:hover,.avatar-button:focus-visible { outline: none; border-color: #2563eb; box-shadow: 0 0 0 4px rgba(37,99,235,.18); transform: translateY(-2px); }

.empty-slot {
  background: var(--lm-bg, #f0f2f5) !important;
  color: var(--lm-text-muted, #bbb);
  border: 2px dashed var(--lm-border, #d9dce1);
  box-shadow: none;
}

.avatar-image { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }

.missing-roles {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}
.team-times { color: var(--lm-text-muted,#94a3b8); font-size: 12px; }

.missing-label {
  color: var(--lm-text-muted, #999);
  font-size: 12px;
}

.role-tag {
  background: #fef0f0;
  color: #f56c6c;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid #fbc4c4;
}

.full-team {
  color: #67c23a;
  font-size: 13px;
  gap: 4px;
}

@media (max-width: 480px) {
  .role-item { align-items: flex-start; flex-direction: column; gap: 3px; }
}
</style>
