<template>
  <div class="team-page">
    <PageHeader title="我的队伍" description="管理我创建和加入的队伍">
      <template #actions><el-button type="primary" @click="showCreateDialog = true"><el-icon><Plus /></el-icon>创建队伍</el-button></template>
    </PageHeader>

    <div v-loading="loading" class="team-groups">
      <section v-for="group in teamGroups" :key="group.status" class="team-group">
        <div class="group-heading">
          <h2>{{ group.title }}</h2>
          <span>{{ group.teams.length }} 支队伍</span>
        </div>
        <div v-if="group.teams.length" class="teams-grid">
          <TeamCard v-for="team in group.teams" :key="team.id" :team="team" />
        </div>
        <el-empty v-else-if="!loading" :description="group.emptyText" :image-size="72" />
      </section>
    </div>

    <CreateTeamDialog v-model="showCreateDialog" @created="loadMyTeams" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getMyTeams } from '@/api/team'
import PageHeader from '@/components/common/PageHeader.vue'
import CreateTeamDialog from './components/CreateTeamDialog.vue'
import TeamCard from './components/TeamCard.vue'

const teams = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)
const groupDefinitions = [
  { status: 'IN_PROGRESS', title: '正在练习', emptyText: '暂无正在练习的队伍' },
  { status: 'PREPARING', title: '组建中', emptyText: '暂无正在组建的队伍' },
  { status: 'ENDED', title: '练习结束', emptyText: '暂无已结束练习的队伍' },
]
const teamGroups = computed(() => groupDefinitions.map(group => ({
  ...group,
  teams: teams.value.filter(team => team.practiceStatus === group.status),
})))

async function loadMyTeams() {
  loading.value = true
  try { teams.value = (await getMyTeams()).data || [] }
  catch (error) { ElMessage.error(error.message || '我的队伍加载失败') }
  finally { loading.value = false }
}

onMounted(loadMyTeams)
</script>

<style scoped>
@import './style.css';
.team-groups { display: flex; min-height: 320px; flex-direction: column; gap: 28px; }
.team-group { min-height: 150px; }
.group-heading { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; border-bottom: 1px solid var(--lm-border); }
.group-heading h2 { margin: 0 0 10px; color: var(--lm-text-primary); font-size: 18px; }
.group-heading span { color: var(--lm-text-muted); font-size: 12px; }
</style>
