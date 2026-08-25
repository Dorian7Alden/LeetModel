<template>
  <div class="team-page">
    <PageHeader title="我的队伍" description="管理我创建和加入的队伍">
      <template #actions><el-button type="primary" @click="showCreateDialog = true"><el-icon><Plus /></el-icon>创建队伍</el-button></template>
    </PageHeader>

    <div v-loading="loading" class="teams-grid">
      <TeamCard v-for="team in teams" :key="team.id" :team="team" />
    </div>
    <el-empty v-if="!loading && teams.length === 0" description="你还没有创建或加入队伍" />

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
import { Plus } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { createTeam, getMyTeams } from '@/api/team'
import { getPublicProblemList } from '@/api/problem'
import PageHeader from '@/components/common/PageHeader.vue'
import TeamCard from './components/TeamCard.vue'

const route = useRoute()
const teams = ref([])
const problemOptions = ref([])
const loading = ref(false)
const showCreateDialog = ref(false)
const createForm = reactive({ problemId: null, name: '', description: '', maxMembers: 3 })

async function loadProblemOptions() {
  try { problemOptions.value = (await getPublicProblemList({ page: 1, pageSize: 100 })).data?.rows || [] }
  catch (error) { ElMessage.error(error.message || '题目列表加载失败') }
}

async function loadMyTeams() {
  loading.value = true
  try { teams.value = (await getMyTeams()).data || [] }
  catch (error) { ElMessage.error(error.message || '我的队伍加载失败') }
  finally { loading.value = false }
}

async function handleCreate() {
  if (!createForm.name.trim()) return ElMessage.warning('请输入队伍名称')
  if (!createForm.problemId) return ElMessage.warning('请选择练习题目')
  try {
    const created = (await createTeam({ ...createForm, name: createForm.name.trim(), description: createForm.description.trim() || null })).data
    Object.assign(createForm, { problemId: null, name: '', description: '', maxMembers: 3 })
    showCreateDialog.value = false
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
