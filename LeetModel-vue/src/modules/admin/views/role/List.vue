<template>
  <div class="role-list-page">
    <el-card shadow="never">
      <div class="action-bar">
        <el-input
          v-model="keyword"
          placeholder="按角色名搜索"
          clearable
          style="width: 260px"
        />
        <div class="actions-right">
          <el-button @click="fetchRoles" :loading="loading">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增角色
          </el-button>
        </div>
      </div>

      <el-table :data="filteredRoles" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="roleId" label="ID" width="90" />
        <el-table-column prop="name" label="角色名称" min-width="160" />
        <el-table-column prop="code" label="编码" width="180" />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status ? 'success' : 'info'">
              {{ scope.row.status ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="openEditDialog(scope.row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无角色数据" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增角色' : '编辑角色'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="80px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="formModel.name" maxlength="50" show-word-limit placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="formModel.code" maxlength="50" show-word-limit placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="formModel.status" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
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
const formModel = reactive({
  roleId: null,
  name: "",
  code: "",
  status: true,
});

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
    const res = await getRoleList();
    if (res.code === 20000) {
      roles.value = Array.isArray(res.data) ? res.data : [];
    } else {
      ElMessage.error(res.msg || '加载角色失败');
    }
  } catch (error) {
    console.error("加载角色失败", error);
    ElMessage.error("加载角色失败");
  } finally {
    loading.value = false;
  }
};

const resetFormModel = () => {
  formModel.roleId = null;
  formModel.name = "";
  formModel.code = "";
  formModel.status = true;
};

const openCreateDialog = () => {
  dialogMode.value = "create";
  resetFormModel();
  dialogVisible.value = true;
};

const openEditDialog = (row) => {
  dialogMode.value = "edit";
  formModel.roleId = row.roleId;
  formModel.name = row.name;
  formModel.code = row.code;
  formModel.status = row.status;
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    const payload = {
      name: formModel.name.trim(),
      code: formModel.code.trim().toUpperCase(),
      status: formModel.status,
    };

    if (dialogMode.value === "create") {
      await createRole(payload);
      ElMessage.success("角色创建成功");
    } else {
      await updateRole(formModel.roleId, payload);
      ElMessage.success("角色更新成功");
    }

    dialogVisible.value = false;
    await fetchRoles();
  } catch (error) {
    console.error("保存角色失败", error);
    ElMessage.error("保存失败，请检查后重试");
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除角色"${row.name}"吗？`, "删除确认", {
      type: "warning",
      confirmButtonText: "确认",
      cancelButtonText: "取消",
    });
    await deleteRole(row.roleId);
    ElMessage.success("删除成功");
    await fetchRoles();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除角色失败", error);
      ElMessage.error("删除失败");
    }
  }
};

onMounted(() => {
  fetchRoles();
});
</script>

<style scoped>
.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.actions-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
</style>
