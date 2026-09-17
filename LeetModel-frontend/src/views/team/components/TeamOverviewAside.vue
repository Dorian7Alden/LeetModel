<template>
  <aside class="team-overview-aside">
    <template v-if="team">
      <div class="aside-eyebrow">当前选择</div>
      <div class="aside-title-row">
        <h2>{{ team.name }}</h2>
        <span :class="statusClass">{{ statusLabel }}</span>
      </div>
      <p class="aside-description">{{ team.description || '队伍暂未填写简介。' }}</p>

      <div class="aside-problem">
        <span>练习题目</span>
        <strong>题号 {{ team.problemCode ?? '待同步' }}</strong>
        <p>{{ team.problemTitle || '题目标题加载中' }}</p>
      </div>

      <div class="aside-stats" aria-label="队伍统计">
        <div><strong>{{ team.memberCount || 0 }}</strong><span>当前成员</span></div>
        <div><strong>{{ coveredRoleCount }}</strong><span>职责覆盖</span></div>
        <div><strong>{{ remainingSlots }}</strong><span>剩余名额</span></div>
      </div>

      <div class="aside-section">
        <div class="aside-section-title">职责分工</div>
        <div class="aside-role-list">
          <div v-for="role in roles" :key="role.key" :class="{ covered: role.members.length }">
            <component :is="role.icon" :size="15" />
            <span>{{ role.label }}</span>
            <strong>{{ role.members.length ? role.members.join('、') : '待补充' }}</strong>
          </div>
        </div>
      </div>

      <div class="aside-section">
        <div class="aside-section-title">成员</div>
        <div class="aside-members">
          <div v-for="member in team.members || []" :key="member.userId">
            <span class="aside-avatar">
              <img v-if="member.avatarUrl" :src="member.avatarUrl" alt="" />
              <template v-else>{{ avatarText(member) }}</template>
            </span>
            <span>{{ member.nickname || '未命名成员' }}</span>
            <small>{{ member.role === 'leader' ? '队长' : '成员' }}</small>
          </div>
        </div>
      </div>

      <button type="button" class="enter-team-button" @click="goDetail">
        <span>{{ actionLabel }}</span>
        <ArrowUpRight :size="16" />
      </button>
      <div class="aside-time"><CalendarDays :size="13" />创建于 {{ formatDate(team.createTime) }}</div>
    </template>

    <div v-else class="aside-empty">
      <UsersRound :size="28" :stroke-width="1.5" />
      <strong>暂无可预览队伍</strong>
      <p>{{ emptyDescription }}</p>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, CalendarDays, ChartSpline, Code2, PenLine, UsersRound } from '@lucide/vue'

const props = defineProps({
  team: { type: Object, default: null },
  status: { type: String, required: true },
})
const route = useRoute()
const router = useRouter()
const routeNameByStatus = { PREPARING: 'TeamPreparing', IN_PROGRESS: 'TeamPracticing', ENDED: 'TeamEnded' }

const statusLabel = computed(() => ({ PREPARING: '正在组建', IN_PROGRESS: '练习中', ENDED: '已结束' })[props.status] || '队伍')
const statusClass = computed(() => `aside-status status-${props.status.toLowerCase()}`)
const remainingSlots = computed(() => Math.max(0, Number(props.team?.maxMembers || 3) - Number(props.team?.memberCount || 0)))
const roles = computed(() => [
  { key: 'modeler', label: '建模', icon: ChartSpline, members: roleMembers('modeler') },
  { key: 'programmer', label: '编程', icon: Code2, members: roleMembers('programmer') },
  { key: 'writer', label: '论文', icon: PenLine, members: roleMembers('writer') },
])
const coveredRoleCount = computed(() => roles.value.filter(role => role.members.length).length)
const actionLabel = computed(() => ({ PREPARING: '管理组建', IN_PROGRESS: '进入练习', ENDED: '查看复盘' })[props.status] || '进入队伍')
const emptyDescription = computed(() => ({
  PREPARING: '创建一支队伍，或前往广场寻找正在招募的队伍。',
  IN_PROGRESS: '队伍开始练习后，会在这里集中显示。',
  ENDED: '完成一次实训后，训练记录会保留在这里。',
})[props.status] || '当前分区还没有队伍。')

function roleMembers(key) {
  return (props.team?.members || []).filter(member => member[key]).map(member => member.nickname || '未命名成员')
}

function avatarText(member) {
  return String(member.nickname || member.userId || '?').charAt(0)
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '时间待同步'
}

function goDetail() {
  const routeName = routeNameByStatus[props.status]
  const isCurrentTeam = route.name === routeName && String(route.params.teamId || '') === String(props.team.id)
  if (isCurrentTeam) {
    document.querySelector('.team-primary-content')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  router.push({ name: routeName, params: { teamId: String(props.team.id) } })
}
</script>

<style scoped>
.team-overview-aside {
  position: sticky;
  top: 80px;
  min-height: 360px;
  padding: 22px;
  border: 1px solid var(--lm-border);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.035);
}

.aside-eyebrow,
.aside-section-title,
.aside-problem > span {
  color: var(--lm-text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.aside-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-top: 7px;
}

.aside-title-row h2 {
  margin: 0;
  color: var(--lm-text-primary);
  font-size: 18px;
  line-height: 1.35;
}

.aside-status {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 10px;
  font-weight: 700;
}

.aside-status.status-in_progress {
  background: #fff7ed;
  color: #b45309;
}

.aside-status.status-ended {
  background: #f0fdf4;
  color: #15803d;
}

.aside-description {
  margin: 12px 0 18px;
  color: var(--lm-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.aside-problem {
  padding: 14px;
  border-radius: 8px;
  background: #f7f7f8;
}

.aside-problem strong {
  display: block;
  margin: 6px 0 4px;
  color: var(--lm-text-primary);
  font-family: var(--lm-code-font-family);
  font-size: 12px;
}

.aside-problem p {
  margin: 0;
  color: var(--lm-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.aside-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 18px 0;
  border-top: 1px solid var(--lm-border-light);
  border-bottom: 1px solid var(--lm-border-light);
}

.aside-stats div {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 3px;
  padding: 13px 4px;
}

.aside-stats div + div {
  border-left: 1px solid var(--lm-border-light);
}

.aside-stats strong {
  color: var(--lm-text-primary);
  font-family: var(--lm-code-font-family);
  font-size: 18px;
}

.aside-stats span {
  color: var(--lm-text-muted);
  font-size: 9px;
}

.aside-section {
  margin-top: 18px;
}

.aside-role-list,
.aside-members {
  display: grid;
  gap: 7px;
  margin-top: 9px;
}

.aside-role-list > div {
  display: grid;
  grid-template-columns: 18px 38px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  color: #a1a1aa;
  font-size: 11px;
}

.aside-role-list > div.covered {
  color: #287448;
}

.aside-role-list strong {
  overflow: hidden;
  color: var(--lm-text-secondary);
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aside-members > div {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  color: var(--lm-text-secondary);
  font-size: 11px;
}

.aside-members small {
  color: var(--lm-text-muted);
  font-size: 9px;
}

.aside-avatar {
  display: flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: #27272a;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}

.aside-avatar img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.enter-team-button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin-top: 22px;
  padding: 10px 12px;
  border: 0;
  border-radius: 7px;
  background: #18181b;
  color: #fff;
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.aside-time {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: 11px;
  color: var(--lm-text-muted);
  font-size: 9px;
}

.aside-empty {
  display: flex;
  min-height: 310px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #a1a1aa;
  text-align: center;
}

.aside-empty strong {
  margin-top: 13px;
  color: var(--lm-text-primary);
  font-size: 14px;
}

.aside-empty p {
  max-width: 210px;
  margin: 7px 0 0;
  color: var(--lm-text-muted);
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 1240px) {
  .team-overview-aside {
    position: static;
  }
}
</style>
