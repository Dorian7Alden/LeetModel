<template>
  <div class="role-list-page">
    <el-card shadow="never">
      <div class="action-bar">
        <el-input v-model="keyword" placeholder="按角色名搜索" clearable style="width: 260px" />
        <div class="actions-right">
          <el-button @click="fetchRoles" :loading="loading">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog"><el-icon><Plus /></el-icon>新增角色</el-button>
        </div>
      </div>

      <el-table :data="filteredRoles" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="130" />
        <el-table-column prop="name" label="角色名称" min-width="160" />
        <el-table-column prop="code" label="编码" width="180" />
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无角色数据" /></template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增角色' : '编辑角色'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="80px">
        <el-form-item label="角色名称" prop="name"><el-input v-model="formModel.name" maxlength="50" show-word-limit placeholder="请输入角色名称" /></el-form-item>
        <el-form-item label="编码" prop="code"><el-input v-model="formModel.code" maxlength="50" show-word-limit placeholder="请输入角色编码" /></el-form-item>
        <el-form-item label="描述" prop="description"><el-input v-model="formModel.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ dialogMode === 'create' ? '创建' : '保存' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { getRoleList, createRole, updateRole, deleteRole } from "@/api/role";

const loading = ref(false);
const submitLoading = ref(false);
const roles = ref([]);
const keyword = ref("");
const dialogVisible = ref(false);
const dialogMode = ref("create");
const formRef = ref();
const formModel = reactive({ id: null, name: "", code: "", description: "" });

const formRules = {
  name: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
  code: [{ required: true, message: "请输入角色编码", trigger: "blur" }],
};

const filteredRoles = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  if (!key) return roles.value;
  return roles.value.filter((item) => (item.name || "").toLowerCase().includes(key));
});

const fetchRoles = async () => {
  loading.value = true;
  try {
    roles.value = (await getRoleList()).data || [];
  } catch (error) {
    ElMessage.error(error.message || "加载角色失败");
  } finally {
    loading.value = false;
  }
};

const openCreateDialog = () => {
  dialogMode.value = "create";
  Object.assign(formModel, { id: null, name: "", code: "", description: "" });
  dialogVisible.value = true;
};

const openEditDialog = (row) => {
  dialogMode.value = "edit";
  Object.assign(formModel, { id: row.id, name: row.name, code: row.code, description: row.description || "" });
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  submitLoading.value = true;
  try {
    const payload = { name: formModel.name.trim(), code: formModel.code.trim().toUpperCase(), description: formModel.description.trim() };
    if (dialogMode.value === "create") await createRole(payload);
    else await updateRole(formModel.id, payload);
    ElMessage.success(dialogMode.value === "create" ? "角色创建成功" : "角色更新成功");
    dialogVisible.value = false;
    await fetchRoles();
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除角色"${row.name}"吗？`, "删除确认", { type: "warning" });
    await deleteRole(row.id);
    ElMessage.success("删除成功");
    await fetchRoles();
  } catch (error) {
    if (error !== "cancel") ElMessage.error(error.message || "删除失败");
  }
};

onMounted(fetchRoles);
</script>

<style scoped>
.action-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; }
.actions-right { margin-left: auto; display: flex; gap: 8px; }
</style>
