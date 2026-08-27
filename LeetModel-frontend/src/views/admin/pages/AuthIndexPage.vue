<template>
  <div class="auth-page">
    <el-card shadow="never" v-loading="loading">
      <div class="auth-toolbar">
        <span class="label">选择角色：</span>
        <el-select v-model="selectedRoleId" placeholder="请选择角色" clearable style="width: 280px" @change="selectRole">
          <el-option v-for="role in allRoles" :key="role.id" :label="`${role.name} [${role.code}]`" :value="role.id" />
        </el-select>
        <el-tag v-if="selectedRole" type="info" effect="plain">{{ selectedRole.description || '角色权限分配' }}</el-tag>
      </div>

      <template v-if="selectedRoleId">
        <el-divider />
        <div v-for="group in permissionGroups" :key="group.label" class="perm-group">
          <div class="perm-group-title">{{ group.label }}</div>
          <el-checkbox-group v-model="checkedPermissions" class="checkbox-list">
            <el-checkbox v-for="perm in group.permissions" :key="perm.id" :value="perm.id">
              <span class="check-label">{{ perm.name }}</span>
              <span class="check-desc">{{ perm.code }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>
        <div class="save-bar">
          <el-button type="primary" :loading="saving" @click="saveRolePermissions">保存角色权限</el-button>
        </div>
      </template>
      <el-empty v-else description="请先选择角色" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getRoleList, getRolePermissions, assignRolePermissions } from "@/api/role";
import { getPermissionList } from "@/api/permission";

const loading = ref(true);
const saving = ref(false);
const allRoles = ref([]);
const allPermissions = ref([]);
const selectedRoleId = ref(null);
const checkedPermissions = ref([]);

const selectedRole = computed(() => allRoles.value.find((role) => role.id === selectedRoleId.value));

const permissionGroups = computed(() => {
  const groups = [
    { label: "全局", prefix: "GLOBAL" },
    { label: "用户管理", prefix: "USER" },
    { label: "题目管理", prefix: "PROBLEM" },
    { label: "作品管理", prefix: "SUBMISSION" },
    { label: "标签管理", prefix: "TAG" },
    { label: "赛事管理", prefix: "CONTEST" },
    { label: "角色管理", prefix: "ROLE" },
    { label: "权限管理", prefix: "PERMISSION" },
    { label: "授权管理", prefix: "AUTH" },
    { label: "文件上传", prefix: "FILE" },
  ];
  return groups
    .map((g) => ({ label: g.label, permissions: allPermissions.value.filter((p) => (p.code || "").startsWith(g.prefix)) }))
    .filter((g) => g.permissions.length > 0);
});

const selectRole = async (roleId) => {
  if (!roleId) {
    checkedPermissions.value = [];
    return;
  }
  try {
    const res = await getRolePermissions(roleId);
    checkedPermissions.value = (res.data || []).map((p) => p.id);
  } catch (error) {
    ElMessage.error(error.message || "角色权限加载失败");
    checkedPermissions.value = [];
  }
};

const saveRolePermissions = async () => {
  if (!selectedRoleId.value) return;
  saving.value = true;
  try {
    await assignRolePermissions(selectedRoleId.value, checkedPermissions.value);
    ElMessage.success("角色权限保存成功");
  } catch (error) {
    ElMessage.error(error.message || "保存失败");
  } finally {
    saving.value = false;
  }
};

onMounted(async () => {
  try {
    const [roleRes, permRes] = await Promise.all([getRoleList(), getPermissionList()]);
    allRoles.value = roleRes.data || [];
    allPermissions.value = permRes.data || [];
  } catch (error) {
    ElMessage.error(error.message || "加载数据失败");
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.auth-toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.auth-toolbar .label { color: var(--lm-text-secondary); font-size: 14px; font-weight: 600; }
.perm-group { margin-bottom: 16px; }
.perm-group-title { margin-bottom: 8px; padding-bottom: 4px; color: var(--lm-text-secondary); font-size: 13px; font-weight: 600; border-bottom: 1px dashed var(--lm-border); }
.checkbox-list { display: flex; flex-wrap: wrap; gap: 14px 24px; }
.check-label { margin-right: 8px; font-weight: 500; }
.check-desc { color: var(--lm-text-muted); font-size: 12px; }
.save-bar { margin-top: 20px; }
</style>
