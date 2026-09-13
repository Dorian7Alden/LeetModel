<template>
  <div class="team-overview-page">
    <main class="team-primary-content">
      <el-result v-if="loadError" icon="error" title="队伍加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="loadSelectedTeam">重新加载</el-button></template>
      </el-result>
      <div v-else v-loading="loading || !navigationReady" class="team-selected-content">
        <TeamDetailPage
          v-if="selectedTeam && activeStatus === 'PREPARING'"
          embedded
          :team-id="selectedTeam.id"
          @changed="handleManagedTeamChanged"
          @transitioned="handleTeamTransitioned"
          @removed="handleManagedTeamRemoved"
        />
        <TeamSelectedWorkspace v-else-if="selectedTeam" :team="selectedTeam" @transitioned="handleTeamTransitioned" />
        <div v-else-if="navigationReady && !loading" class="team-workspace-empty">
          <component :is="activeDefinition.icon" :size="32" :stroke-width="1.5" />
          <strong>{{ activeDefinition.emptyTitle }}</strong>
          <p>{{ activeDefinition.emptyDescription }}</p>
          <button type="button" @click="handleEmptyAction">{{ activeDefinition.emptyAction }}</button>
        </div>
      </div>
    </main>

    <TeamOverviewAside :team="selectedTeam || selectedSummary" :status="activeStatus" />
  </div>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCheck, Timer, UsersRound } from '@lucide/vue'
import { getTeamDetail } from '@/api/team'
import TeamOverviewAside from './components/TeamOverviewAside.vue'
import TeamSelectedWorkspace from './components/TeamSelectedWorkspace.vue'
import TeamDetailPage from './pages/TeamDetailPage.vue'

const route = useRoute()
const router = useRouter()
const workbench = inject('teamWorkbench')
const props = defineProps({ status: { type: String, required: true } })
const loading = ref(false)
const loadError = ref('')
const selectedTeam = ref(null)
let requestSequence = 0

const definitions = {
  PREPARING: {
    title: '正在组建',
    description: '补齐成员与职责后，即可开始一次完整实训。',
    emptyTitle: '暂无正在组建的队伍',
    emptyDescription: '创建一支队伍，或前往广场寻找正在招募的队伍。',
    emptyAction: '创建队伍',
    icon: UsersRound,
  },
  IN_PROGRESS: {
    title: '练习中',
    description: '关注截止时间、论文版本和当前提交状态。',
    emptyTitle: '暂无练习中的队伍',
    emptyDescription: '队伍完成职责配置并开始练习后，会集中显示在这里。',
    emptyAction: '查看正在组建',
    icon: Timer,
  },
  ENDED: {
    title: '已结束',
    description: '回看已经完成的训练队伍与最终结果。',
    emptyTitle: '还没有完成的实训',
    emptyDescription: '完成一次实训后，训练记录会保留在这里。',
    emptyAction: '查看正在组建',
    icon: CircleCheck,
  },
}

const routeNameByStatus = { PREPARING: 'TeamPreparing', IN_PROGRESS: 'TeamPracticing', ENDED: 'TeamEnded' }
const activeStatus = computed(() => definitions[props.status] ? props.status : 'PREPARING')
const activeDefinition = computed(() => definitions[activeStatus.value])
const routeTeamId = computed(() => String(route.params.teamId || ''))
const navigationReady = computed(() => Boolean(workbench?.navigationLoaded.value))
const teamsInSection = computed(() => workbench?.navigationTeams[activeStatus.value] || [])
const selectedSummary = computed(() => teamsInSection.value.find(team => String(team.id) === routeTeamId.value) || null)

async function loadSelectedTeam() {
  if (!routeTeamId.value) {
    selectedTeam.value = null
    return
  }
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  try {
    const result = await getTeamDetail(routeTeamId.value)
    if (sequence !== requestSequence) return
    selectedTeam.value = result.data || null
  } catch (error) {
    if (sequence === requestSequence) {
      selectedTeam.value = null
      loadError.value = error.message || '请检查网络连接后重试'
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function handleEmptyAction() {
  if (activeStatus.value === 'PREPARING') {
    workbench?.openCreateDialog()
    return
  }
  router.push({ name: 'TeamPreparing' })
}

async function handleTeamTransitioned(team) {
  selectedTeam.value = team
  await workbench?.refreshCounts()
  await router.replace({ name: routeNameByStatus[team.practiceStatus], params: { teamId: String(team.id) } })
}

async function handleManagedTeamChanged(team) {
  selectedTeam.value = team
  await workbench?.refreshCounts()
}

async function handleManagedTeamRemoved() {
  selectedTeam.value = null
  await workbench?.refreshCounts()
  await router.replace({ name: 'TeamPreparing' })
}

watch(
  [activeStatus, navigationReady, teamsInSection, () => workbench?.refreshVersion.value],
  () => {
    if (!navigationReady.value) return
    const matched = teamsInSection.value.find(team => String(team.id) === routeTeamId.value)
    if (matched) return
    const firstTeam = teamsInSection.value[0]
    if (firstTeam) {
      router.replace({ name: routeNameByStatus[activeStatus.value], params: { teamId: String(firstTeam.id) } })
      return
    }
    if (routeTeamId.value) router.replace({ name: routeNameByStatus[activeStatus.value] })
    selectedTeam.value = null
  },
  { immediate: true, deep: true },
)

watch(routeTeamId, () => {
  selectedTeam.value = null
  loadSelectedTeam()
}, { immediate: true })
</script>

<style scoped>
.team-overview-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 264px;
  gap: 16px;
  width: 100%;
  padding: 18px 20px 56px;
  box-sizing: border-box;
}

.team-primary-content {
  min-width: 0;
}

.team-selected-content {
  min-height: 420px;
}

.team-workspace-empty {
  display: flex;
  min-height: 520px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #a1a1aa;
  text-align: center;
}

.team-workspace-empty strong {
  margin-top: 14px;
  color: var(--lm-text-primary);
  font-size: 15px;
}

.team-workspace-empty p {
  max-width: 320px;
  margin: 7px 0 0;
  color: var(--lm-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.team-workspace-empty button {
  margin-top: 17px;
  padding: 9px 16px;
  border: 0;
  border-radius: 7px;
  background: #18181b;
  color: #fff;
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

@media (max-width: 1200px) {
  .team-overview-page {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .team-overview-page {
    padding: 18px 14px 40px;
  }
}

@media (max-width: 560px) {
  .team-overview-page {
    padding-right: 10px;
    padding-left: 10px;
  }
}
</style>
