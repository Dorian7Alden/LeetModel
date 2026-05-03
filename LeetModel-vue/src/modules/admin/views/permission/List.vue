<template>
  <div class="permission-list-page">
    <el-card shadow="never">
      <div class="action-bar">
        <el-input
          v-model="keyword"
          placeholder="按权限名搜索"
          clearable
          style="width: 260px"
        />
        <div class="actions-right">
          <el-button @click="fetchPermissions" :loading="loading">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增权限
          </el-button>
        </div>
      </div>

      <el-table :data="filteredPermissions" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="permissionId" label="ID" width="90" />
        <el-table-column prop="name" label="权限名称" min-width="160" />
        <el-table-column prop="code" label="编码" width="200" />
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
          <el-empty description="暂无权限数据" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增权限' : '编辑权限'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="80px">
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="formModel.name" maxlength="100" show-word-limit placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="formModel.code" maxlength="100" show-word-limit placeholder="请输入权限编码" />
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
import { getPermissionList, createPermission, updatePermission, deletePermission } from "@/api/permission";

const loading = ref(false);
const submitLoading = ref(false);
const permissions = ref([]);
const keyword = ref("");

const dialogVisible = ref(false);
const dialogMode = ref("create");
const formRef = ref();
const formModel = reactive({
  permissionId: null,
  name: "",
  code: "",
  status: true,
});

const formRules = {
  name: [{ required: true, message: "请输入权限名称", trigger: "blur" }],
  code: [{ required: true, message: "请输入权限编码", trigger: "blur" }],
};

const filteredPermissions = computed(() => {
  const key = keyword.value.trim().toLowerCase();
  if (!key) return permissions.value;
  return permissions.value.filter((item) => (item.name || "").toLowerCase().includes(key));
});

const fetchPermissions = async () => {
  loading.value = true;
  try {
    const res = await getPermissionList();
    permissions.value = Array.isArray(res.data) ? res.data : [];
  } catch (error) {
    console.error("加载权限失败", error);
    ElMessage.error("加载权限失败");
  } finally {
    loading.value = false;
  }
};

const resetFormModel = () => {
  formModel.permissionId = null;
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
  formModel.permissionId = row.permissionId;
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
      await createPermission(payload);
      ElMessage.success("权限创建成功");
    } else {
      await updatePermission(formModel.permissionId, payload);
      ElMessage.success("权限更新成功");
    }

    dialogVisible.value = false;
    await fetchPermissions();
  } catch (error) {
    console.error("保存权限失败", error);
    ElMessage.error("保存失败，请检查后重试");
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除权限"${row.name}"吗？`, "删除确认", {
      type: "warning",
      confirmButtonText: "确认",
      cancelButtonText: "取消",
    });
    await deletePermission(row.permissionId);
    ElMessage.success("删除成功");
    await fetchPermissions();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除权限失败", error);
      ElMessage.error("删除失败");
    }
  }
};

onMounted(() => {
  fetchPermissions();
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
