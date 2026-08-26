<template>
  <div class="tag-list-page">
    <el-card shadow="never">
      <div class="toolbar">
        <h2 class="panel-title">标签管理</h2>
        <el-button type="primary" @click="openCreate">新增标签</el-button>
      </div>
      <el-table :data="tags" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="130" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="type" label="类型" min-width="180" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无标签" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="editingId ? '编辑标签' : '新增标签'" width="440px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="背景领域" value="BACKGROUND_DOMAIN" />
            <el-option label="题目类型" value="PROBLEM_TYPE" />
            <el-option label="模型算法" value="MODEL_ALGORITHM" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getAdminContentTags, createAdminContentTag, updateAdminContentTag, deleteAdminContentTag } from "@/api/problem";

const tags = ref([]);
const loading = ref(false);
const saving = ref(false);
const visible = ref(false);
const editingId = ref(null);
const form = reactive({ name: "", type: "MODEL_ALGORITHM" });

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function load() {
  loading.value = true;
  try {
    tags.value = (await getAdminContentTags()).data || [];
  } catch (error) {
    ElMessage.error(error.message || "标签列表加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, { name: "", type: "MODEL_ALGORITHM" });
  visible.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, { name: row.name, type: row.type });
  visible.value = true;
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning("请输入标签名称");
  saving.value = true;
  try {
    if (editingId.value) await updateAdminContentTag(editingId.value, { name: form.name.trim(), type: form.type });
    else await createAdminContentTag({ name: form.name.trim(), type: form.type });
    visible.value = false;
    await load();
    ElMessage.success("标签已保存");
  } catch (error) {
    ElMessage.error(error.message || "标签保存失败");
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除标签「${row.name}」吗？`, "确认删除", { type: "warning" });
    await deleteAdminContentTag(row.id);
    ElMessage.success("删除成功");
    await load();
  } catch (error) {
    if (error !== "cancel") ElMessage.error(error.message || "删除失败");
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
</style>
