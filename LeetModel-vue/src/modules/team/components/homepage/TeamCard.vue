<template>
  <div class="team-card" @click="goDetail">
    <div class="card-header">
      <h3 class="team-name">{{ team.name }}</h3>
      <span class="member-count">{{ team.memberCount }} / {{ team.maxMembers }}</span>
    </div>

    <p class="team-desc">{{ team.description }}</p>

    <!-- 成员头像 -->
    <div class="member-avatars">
      <div
        v-for="(member, idx) in team.members"
        :key="idx"
        class="avatar-circle"
        :style="{ backgroundColor: avatarColors[idx % avatarColors.length] }"
        :title="member"
      >
        {{ member.charAt(0) }}
      </div>
      <!-- 空位 -->
      <div
        v-for="n in emptySlots"
        :key="'empty-' + n"
        class="avatar-circle empty-slot"
      >
        <el-icon><User /></el-icon>
      </div>
    </div>

    <!-- 缺失角色标签 -->
    <div class="missing-roles" v-if="team.missingRoles.length > 0">
      <span class="missing-label">急招：</span>
      <span v-for="role in team.missingRoles" :key="role" class="role-tag">
        {{ role }}
      </span>
    </div>
    <div class="missing-roles full-team" v-else>
      <el-icon><CircleCheck /></el-icon>
      <span>队伍已满员</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { User, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  team: { type: Object, required: true },
})

const router = useRouter()

const avatarColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#8b5cf6', '#0891b2']

const emptySlots = computed(() => {
  return Math.max(0, props.team.maxMembers - props.team.memberCount)
})

function goDetail() {
  router.push({ name: 'TeamDetail', params: { id: props.team.teamId } })
}
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

.member-avatars {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.avatar-circle {
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

.empty-slot {
  background: var(--lm-bg, #f0f2f5) !important;
  color: var(--lm-text-muted, #bbb);
  border: 2px dashed var(--lm-border, #d9dce1);
  box-shadow: none;
}

.missing-roles {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}

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
</style>
