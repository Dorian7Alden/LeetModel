<template>
  <div class="team-page">
    <PageHeader title="组队广场" description="寻找志同道合的队友，组建你的建模团队">
      <template #actions>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索队伍名称..."
          class="search-input"
          clearable
          :prefix-icon="Search"
        />
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          创建队伍
        </el-button>
      </template>
    </PageHeader>

    <div class="teams-grid">
      <TeamCard v-for="team in filteredTeams" :key="team.teamId" :team="team" />
    </div>

    <div v-if="filteredTeams.length === 0" class="empty-state">
      <el-empty description="暂无匹配的队伍" />
    </div>

    <!-- 创建队伍弹窗 -->
    <el-dialog v-model="showCreateDialog" title="创建队伍" width="520px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="队伍名称" required>
          <el-input v-model="createForm.name" placeholder="给你的队伍起个名字" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="队伍简介">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="介绍一下队伍的方向和目标" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="最大人数">
          <el-input-number v-model="createForm.maxMembers" :min="3" :max="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { mockTeams } from '@/mock/data.js'
import PageHeader from '@/components/common/PageHeader.vue'
import TeamCard from './components/TeamCard.vue'

const searchKeyword = ref('')
const showCreateDialog = ref(false)

const teams = ref([...mockTeams])

const filteredTeams = computed(() => {
  if (!searchKeyword.value) return teams.value
  const keyword = searchKeyword.value.toLowerCase()
  return teams.value.filter(
    (t) =>
      t.name.toLowerCase().includes(keyword) ||
      t.description.toLowerCase().includes(keyword)
  )
})

const createForm = ref({
  name: '',
  description: '',
  maxMembers: 3,
})

function handleCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入队伍名称')
    return
  }
  const newTeam = {
    teamId: Date.now(),
    name: createForm.value.name.trim(),
    description: createForm.value.description.trim(),
    memberCount: 1,
    maxMembers: createForm.value.maxMembers,
    members: ['我'],
    missingRoles: ['建模', '编程', '写作'].slice(0, createForm.value.maxMembers - 1),
    createTime: new Date().toISOString().split('T')[0],
  }
  teams.value.unshift(newTeam)
  showCreateDialog.value = false
  createForm.value = { name: '', description: '', maxMembers: 3 }
  ElMessage.success('队伍创建成功')
}
</script>

<style scoped>
@import './style.css';
</style>
