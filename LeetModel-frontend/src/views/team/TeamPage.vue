<template>
  <div class="team-page">
    <PageHeader title="组队中心" description="浏览公开队伍，或管理我创建和加入的队伍">
      <template #actions><el-button type="primary" @click="showCreateDialog = true"><el-icon><Plus /></el-icon>创建队伍</el-button></template>
    </PageHeader>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="我的队伍" name="mine" />
      <el-tab-pane label="组队广场" name="public">
        <div class="team-filters">
          <el-input v-model="query.keyword" placeholder="搜索名称或简介" clearable :prefix-icon="Search" @keyup.enter="loadPublicTeams" />
          <el-checkbox v-model="query.availableOnly">只看未满员</el-checkbox>
          <el-checkbox v-model="query.recruitingOnly">只看招募中</el-checkbox>
          <el-checkbox v-model="query.needModeler">建模手</el-checkbox>
          <el-checkbox v-model="query.needProgrammer">编程手</el-checkbox>
          <el-checkbox v-model="query.needWriter">论文手</el-checkbox>
          <el-select v-model="query.sortBy"><el-option label="最新创建" value="createTime" /><el-option label="剩余名额" value="remainingSlots" /></el-select>
          <el-button type="primary" @click="handleSearch">筛选</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <div v-loading="loading" class="teams-grid">
      <TeamCard v-for="team in teams" :key="team.id" :team="team" />
    </div>
    <el-empty v-if="!loading && teams.length === 0" description="暂无符合条件的队伍" />
    <el-pagination v-if="activeTab === 'public' && total > query.pageSize" v-model:current-page="query.page" :page-size="query.pageSize" :total="total" layout="prev, pager, next, total" @current-change="loadPublicTeams" />

    <el-dialog v-model="showCreateDialog" title="创建队伍" width="520px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="练习题目" required>
          <el-select v-model="createForm.problemId" filterable placeholder="请选择题目" style="width: 100%">
            <el-option v-for="problem in problemOptions" :key="problem.id" :label="`${problem.year} · ${problem.title}`" :value="problem.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="队伍名称" required><el-input v-model="createForm.name" maxlength="64" show-word-limit /></el-form-item>
        <el-form-item label="队伍简介"><el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="256" show-word-limit /></el-form-item>
        <el-form-item label="最大人数"><el-input-number v-model="createForm.maxMembers" :min="1" :max="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreateDialog = false">取消</el-button><el-button type="primary" @click="handleCreate">确认创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { createTeam, getMyTeams, getPublicTeams } from '@/api/team'
import { getPublicProblemList } from '@/api/problem'
import PageHeader from '@/components/common/PageHeader.vue'
import TeamCard from './components/TeamCard.vue'

const activeTab = ref('mine')
const route = useRoute()
const teams = ref([])
const problemOptions = ref([])
const total = ref(0)
const loading = ref(false)
const showCreateDialog = ref(false)
const query = reactive({ page: 1, pageSize: 9, keyword: '', availableOnly: false, recruitingOnly: false, needModeler: false, needProgrammer: false, needWriter: false, sortBy: 'createTime' })
const createForm = reactive({ problemId: null, name: '', description: '', maxMembers: 3 })

async function loadProblemOptions() {
  try { problemOptions.value = (await getPublicProblemList({ page: 1, pageSize: 100 })).data?.rows || [] }
  catch (error) { ElMessage.error(error.message || '题目列表加载失败') }
}

async function loadPublicTeams() {
  loading.value = true
  try {
    const res = await getPublicTeams(query)
    teams.value = res.data.rows || []
    total.value = res.data.total || 0
  } catch (error) { ElMessage.error(error.message || '公共队伍加载失败') }
  finally { loading.value = false }
}

async function loadMyTeams() {
  loading.value = true
  try { teams.value = (await getMyTeams()).data || [] }
  catch (error) { ElMessage.error(error.message || '我的队伍加载失败') }
  finally { loading.value = false }
}

function handleSearch() { query.page = 1; loadPublicTeams() }
function handleTabChange(name) { name === 'public' ? loadPublicTeams() : loadMyTeams() }

async function handleCreate() {
  if (!createForm.name.trim()) return ElMessage.warning('请输入队伍名称')
  if (!createForm.problemId) return ElMessage.warning('请选择练习题目')
  try {
    const created = (await createTeam({ ...createForm, name: createForm.name.trim(), description: createForm.description.trim() || null })).data
    Object.assign(createForm, { problemId: null, name: '', description: '', maxMembers: 3 })
    showCreateDialog.value = false
    activeTab.value = 'mine'
    await loadMyTeams()
    ElMessage.success(`队伍“${created.name}”创建成功`)
  } catch (error) { ElMessage.error(error.message || '队伍创建失败') }
}

onMounted(async () => {
  await Promise.all([loadMyTeams(), loadProblemOptions()])
  if (route.query.problemId) {
    createForm.problemId = problemOptions.value.find(item => String(item.id) === String(route.query.problemId))?.id || route.query.problemId
    showCreateDialog.value = true
  }
})
</script>

<style scoped>
@import './style.css';
</style>
