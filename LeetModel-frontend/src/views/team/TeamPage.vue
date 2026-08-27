<template>
  <div class="team-page">
    <PageHeader title="我的队伍" description="管理我创建和加入的队伍">
      <template #actions><el-button type="primary" @click="showCreateDialog = true"><el-icon><Plus /></el-icon>创建队伍</el-button></template>
    </PageHeader>

    <div v-loading="loading" class="team-groups">
      <section v-for="group in teamGroups" :key="group.status" class="team-group">
        <div class="group-heading">
          <h2>{{ group.title }}</h2>
          <span>{{ group.total }} 支队伍</span>
        </div>
        <div v-if="group.teams.length" class="teams-grid">
          <TeamCard v-for="team in group.teams" :key="team.id" :team="team" />
        </div>
        <el-empty v-else-if="!loading" :description="group.emptyText" :image-size="72" />
        <el-pagination
          v-if="group.total > group.pageSize"
          background
          layout="prev, pager, next"
          :current-page="group.page"
          :page-size="group.pageSize"
          :total="group.total"
          class="group-pagination"
          @current-change="page => changeGroupPage(group.status, page)"
        />
      </section>
    </div>

    <CreateTeamDialog v-model="showCreateDialog" @created="loadMyTeams" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getMyTeams } from '@/api/team'
import PageHeader from '@/components/common/PageHeader.vue'
import CreateTeamDialog from './components/CreateTeamDialog.vue'
import TeamCard from './components/TeamCard.vue'

const loading = ref(false)
const showCreateDialog = ref(false)
const groupDefinitions = [
  { status: 'IN_PROGRESS', title: '正在练习', emptyText: '暂无正在练习的队伍' },
  { status: 'PREPARING', title: '组建中', emptyText: '暂无正在组建的队伍' },
  { status: 'ENDED', title: '练习结束', emptyText: '暂无已结束练习的队伍' },
]
const groupPages = reactive(Object.fromEntries(groupDefinitions.map(group => [group.status, {
  rows: [], total: 0, page: 1, pageSize: 6,
}])))
const teamGroups = computed(() => groupDefinitions.map(group => ({
  ...group,
  teams: groupPages[group.status].rows,
  total: groupPages[group.status].total,
  page: groupPages[group.status].page,
  pageSize: groupPages[group.status].pageSize,
})))

async function loadMyTeams() {
  loading.value = true
  try {
    await Promise.all(groupDefinitions.map(group => loadGroup(group.status)))
  }
  catch (error) { ElMessage.error(error.message || '我的队伍加载失败') }
  finally { loading.value = false }
}

async function loadGroup(status) {
  const state = groupPages[status]
  const page = (await getMyTeams({ practiceStatus: status, page: state.page, pageSize: state.pageSize })).data
  state.rows = page?.rows || []
  state.total = page?.total || 0
  if (state.page > 1 && state.rows.length === 0) {
    state.page -= 1
    return loadGroup(status)
  }
}

async function changeGroupPage(status, page) {
  groupPages[status].page = page
  try { await loadGroup(status) }
  catch (error) { ElMessage.error(error.message || '我的队伍加载失败') }
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
.group-pagination { justify-content: center; margin-top: 18px; }
</style>
