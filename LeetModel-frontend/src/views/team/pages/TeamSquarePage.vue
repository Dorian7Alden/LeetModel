<template>
  <div class="team-page">
    <PageHeader title="队伍广场" description="浏览正在公开招募的队伍" />

    <div class="team-filters">
      <el-input v-model="query.keyword" placeholder="搜索名称或简介" clearable :prefix-icon="Search" @keyup.enter="loadPublicTeams" />
      <el-checkbox v-model="query.availableOnly">只看未满员</el-checkbox>
      <el-checkbox v-model="query.recruitingOnly">只看招募中</el-checkbox>
      <el-checkbox v-model="query.needModeler">建模手</el-checkbox>
      <el-checkbox v-model="query.needProgrammer">编程手</el-checkbox>
      <el-checkbox v-model="query.needWriter">论文手</el-checkbox>
      <el-select v-model="query.sortBy">
        <el-option label="最新创建" value="createTime" />
        <el-option label="剩余名额" value="remainingSlots" />
      </el-select>
      <el-button type="primary" @click="handleSearch">筛选</el-button>
    </div>

    <div v-loading="loading" class="teams-grid">
      <TeamCard v-for="team in teams" :key="team.id" :team="team" detail-route-name="TeamSquareDetail" />
    </div>
    <el-empty v-if="!loading && teams.length === 0" description="暂无符合条件的队伍" />
    <el-pagination
      v-if="total > query.pageSize"
      v-model:current-page="query.page"
      :page-size="query.pageSize"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="loadPublicTeams"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getPublicTeams } from '@/api/team'
import PageHeader from '@/components/common/PageHeader.vue'
import TeamCard from '../components/TeamCard.vue'

const teams = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  pageSize: 9,
  keyword: '',
  availableOnly: false,
  recruitingOnly: false,
  needModeler: false,
  needProgrammer: false,
  needWriter: false,
  sortBy: 'createTime',
})

async function loadPublicTeams() {
  loading.value = true
  try {
    const res = await getPublicTeams(query)
    teams.value = res.data.rows || []
    total.value = res.data.total || 0
  } catch (error) {
    ElMessage.error(error.message || '公共队伍加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadPublicTeams()
}

onMounted(loadPublicTeams)
</script>

<style scoped>
@import '../style.css';
</style>
