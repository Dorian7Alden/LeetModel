<template>
  <div class="team-workbench-shell">
    <TeamSidebar
      :active-section="activeSection"
      :active-team-id="activeTeamId"
      :counts="navigationCounts"
      :teams="navigationTeams"
      :loading="countsLoading"
      @select="selectSection"
      @select-team="selectTeam"
      @create="openCreateDialog"
    />

    <main class="team-workbench-main">
      <router-view v-slot="{ Component }">
        <transition name="team-view-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <CreateTeamDialog v-model="showCreateDialog" @created="handleTeamCreated" />
  </div>
</template>

<script setup>
import { computed, onMounted, provide, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMyTeams, getPublicTeams } from '@/api/team'
import CreateTeamDialog from './components/CreateTeamDialog.vue'
import TeamSidebar from './components/TeamSidebar.vue'

const route = useRoute()
const router = useRouter()
const showCreateDialog = ref(false)
const countsLoading = ref(false)
const navigationLoaded = ref(false)
const refreshVersion = ref(0)
const validStatuses = new Set(['PREPARING', 'IN_PROGRESS', 'ENDED'])
const routeNameByStatus = {
  PREPARING: 'TeamPreparing',
  IN_PROGRESS: 'TeamPracticing',
  ENDED: 'TeamEnded',
}
const navigationCounts = reactive({
  PREPARING: null,
  IN_PROGRESS: null,
  ENDED: null,
  SQUARE: null,
})
const navigationTeams = reactive({
  PREPARING: [],
  IN_PROGRESS: [],
  ENDED: [],
})

const activeSection = computed(() => {
  if (route.path.startsWith('/team/square')) return 'SQUARE'
  if (route.name === 'TeamDetail') {
    const sourceStatus = String(route.query.fromStatus || '')
    return validStatuses.has(sourceStatus) ? sourceStatus : 'PREPARING'
  }
  const status = String(route.meta.teamStatus || 'PREPARING')
  return validStatuses.has(status) ? status : 'PREPARING'
})
const activeTeamId = computed(() => String(route.params.teamId || route.params.id || ''))

async function loadNavigationCounts() {
  countsLoading.value = true
  const statuses = ['PREPARING', 'IN_PROGRESS', 'ENDED']
  const requests = [
    ...statuses.map(status => [status, loadAllTeams(status)]),
    ['SQUARE', getPublicTeams({ page: 1, pageSize: 1, availableOnly: true, recruitingOnly: true, excludeJoined: true })],
  ]

  const results = await Promise.allSettled(requests.map(([, request]) => request))
  results.forEach((result, index) => {
    const key = requests[index][0]
    if (key === 'SQUARE') {
      navigationCounts[key] = result.status === 'fulfilled' ? Number(result.value.data?.total || 0) : null
      return
    }
    navigationCounts[key] = result.status === 'fulfilled' ? result.value.total : null
    navigationTeams[key] = result.status === 'fulfilled' ? result.value.rows : []
  })
  countsLoading.value = false
  navigationLoaded.value = true
}

async function loadAllTeams(status) {
  const pageSize = 100
  const firstResult = await getMyTeams({ practiceStatus: status, page: 1, pageSize })
  const firstPage = firstResult.data || {}
  const rows = [...(firstPage.rows || [])]
  const total = Number(firstPage.total || rows.length)
  const pageCount = Math.ceil(total / pageSize)

  for (let page = 2; page <= pageCount; page += 1) {
    const result = await getMyTeams({ practiceStatus: status, page, pageSize })
    rows.push(...(result.data?.rows || []))
  }
  return { rows, total }
}

function selectSection(section) {
  if (section === 'SQUARE') {
    router.push({ name: 'TeamSquare' })
    return
  }
  router.push({ name: routeNameByStatus[section] })
}

function selectTeam({ status, team }) {
  router.push({ name: routeNameByStatus[status], params: { teamId: String(team.id) } })
}

function openCreateDialog() {
  showCreateDialog.value = true
}

async function handleTeamCreated(team) {
  refreshVersion.value += 1
  await loadNavigationCounts()
  await router.push({ name: 'TeamPreparing', params: { teamId: String(team.id) } })
}

provide('teamWorkbench', {
  refreshVersion,
  navigationTeams,
  navigationLoaded,
  countsLoading,
  refreshCounts: loadNavigationCounts,
  openCreateDialog,
})

onMounted(loadNavigationCounts)
</script>

<style scoped>
.team-workbench-shell {
  display: flex;
  align-items: flex-start;
  width: 100%;
  min-height: calc(100vh - 56px);
  background: #fff;
}

.team-workbench-main {
  min-width: 0;
  flex: 1;
}

.team-view-fade-enter-active,
.team-view-fade-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.team-view-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.team-view-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 860px) {
  .team-workbench-shell {
    flex-direction: column;
  }

  .team-workbench-main {
    width: 100%;
  }
}
</style>
