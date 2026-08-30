<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div>
        <h2 class="panel-title">赛事基础数据</h2>
        <p class="panel-subtitle">赛事是题目的来源语境，修改后会同步影响题目归属展示</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-table :data="contests" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="id" label="赛事 ID" width="180" />
      <el-table-column prop="code" label="编码" width="180" />
      <el-table-column prop="name" label="名称" min-width="260" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }"><el-button type="primary" link @click="openEdit(row)">编辑</el-button></template>
      </el-table-column>
      <template #empty><el-empty description="暂无赛事数据" /></template>
    </el-table>

    <el-dialog v-model="editVisible" title="编辑赛事基础数据" width="480px" destroy-on-close>
      <el-alert title="赛事编码会用于筛选和接口表达，建议保持稳定。" type="info" :closable="false" show-icon class="edit-alert" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="赛事编码" prop="code"><el-input v-model="form.code" maxlength="32" placeholder="例如 MCM_ICM" /></el-form-item>
        <el-form-item label="赛事名称" prop="name"><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存修改</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminContentContests, updateAdminContentContest } from "@/api/problem";

const contests = ref([]);
const loading = ref(false);
const saving = ref(false);
const editVisible = ref(false);
const editingId = ref(null);
const formRef = ref();
const form = reactive({ code: "", name: "" });
const rules = {
  code: [
    { required: true, message: "请输入赛事编码", trigger: "blur" },
    { pattern: /^[A-Za-z0-9_-]+$/, message: "只能使用字母、数字、下划线和短横线", trigger: "blur" },
  ],
  name: [{ required: true, message: "请输入赛事名称", trigger: "blur" }],
};

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function load() {
  loading.value = true;
  try {
    contests.value = (await getAdminContentContests()).data || [];
  } catch (error) {
    ElMessage.error(error.message || "赛事数据加载失败");
  } finally {
    loading.value = false;
  }
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, { code: row.code || "", name: row.name || "" });
  editVisible.value = true;
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || !editingId.value) return;
  saving.value = true;
  try {
    await updateAdminContentContest(editingId.value, { code: form.code.trim(), name: form.name.trim() });
    ElMessage.success("赛事基础数据已更新");
    editVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error.message || "赛事更新失败");
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.panel-title { margin: 0; font-size: 18px; }
.panel-subtitle { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; }
.edit-alert { margin-bottom: 18px; }
</style>
