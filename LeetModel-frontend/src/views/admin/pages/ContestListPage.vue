<template>
  <el-card shadow="never">
    <div class="action-bar"><h2>赛事基础数据</h2><el-button type="primary" @click="openCreate">新增赛事</el-button></div>
    <el-table :data="contests" v-loading="loading" stripe>
      <el-table-column prop="code" label="编码" width="180" />
      <el-table-column prop="name" label="名称" min-width="260" />
      <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button></template></el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="editingId ? '编辑赛事' : '新增赛事'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="赛事编码" required><el-input v-model="form.code" maxlength="32" placeholder="例如 CUMCM" /></el-form-item>
        <el-form-item label="赛事名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createContest, getContests, updateContest } from '@/api/problem'

const contests = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const editingId = ref(null)
const form = reactive({ code: '', name: '', status: 1 })

async function load() {
  loading.value = true
  try { contests.value = (await getContests()).data || [] }
  catch (error) { ElMessage.error(error.message || '赛事数据加载失败') }
  finally { loading.value = false }
}
function openCreate() { editingId.value = null; Object.assign(form, { code: '', name: '', status: 1 }); visible.value = true }
function openEdit(item) { editingId.value = item.id; Object.assign(form, { code: item.code, name: item.name, status: item.status }); visible.value = true }
async function save() {
  if (!form.code.trim() || !form.name.trim()) return ElMessage.warning('请填写赛事编码和名称')
  saving.value = true
  try {
    const payload = { code: form.code.trim(), name: form.name.trim(), status: form.status }
    if (editingId.value) await updateContest(editingId.value, payload)
    else await createContest(payload)
    visible.value = false
    await load()
    ElMessage.success('赛事数据已保存')
  } catch (error) { ElMessage.error(error.message || '赛事数据保存失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.action-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.action-bar h2 { margin: 0; font-size: 20px; }
</style>
