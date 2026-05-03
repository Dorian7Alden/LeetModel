<template>
  <div class="auth-page">
    <el-card shadow="never" v-loading="loading">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- ==================== 用户授权 ==================== -->
        <el-tab-pane label="用户授权" name="user">
          <div class="auth-layout">
            <div class="auth-left">
              <div class="panel-header">
                <span>用户列表</span>
                <el-input v-model="userKeyword" placeholder="搜索用户" clearable style="width: 200px" />
              </div>
              <el-table
                :data="filteredUsers"
                stripe
                highlight-current-row
                @row-click="selectUser"
                :row-class-name="userRowClass"
                max-height="420"
              >
                <el-table-column prop="userId" label="ID" width="70" />
                <el-table-column prop="username" label="用户名" min-width="120" />
                <el-table-column prop="email" label="邮箱" min-width="180" />
              </el-table>
            </div>

            <div class="auth-right">
              <div class="panel-header">
                <span>角色分配</span>
                <el-tag v-if="selectedUser" type="success" size="small">
                  {{ selectedUser.username }}
                </el-tag>
              </div>
              <template v-if="selectedUser">
                <el-checkbox-group v-model="checkedRoles" class="checkbox-list">
                  <el-checkbox v-for="role in allRoles" :key="role.roleId" :label="role.roleId">
                    <span class="check-label">{{ role.name }}</span>
                    <span class="check-desc">{{ role.code }}</span>
                  </el-checkbox>
                </el-checkbox-group>
                <div class="save-bar">
                  <el-button type="primary" :loading="userSaving" @click="saveUserRoles">
                    保存
                  </el-button>
                </div>
              </template>
              <el-empty v-else description="请选择左侧用户" />
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== 角色授权 ==================== -->
        <el-tab-pane label="角色授权" name="role">
          <div class="role-auth-top">
            <span class="label">选择角色：</span>
            <el-select
              v-model="selectedRoleId"
              placeholder="请选择角色"
              clearable
              style="width: 260px"
              @change="selectRole"
            >
              <el-option
                v-for="role in allRoles"
                :key="role.roleId"
                :label="`${role.name} [${role.code}]`"
                :value="role.roleId"
              />
            </el-select>
          </div>

          <template v-if="selectedRoleId">
            <el-divider />
            <div v-for="group in permissionGroups" :key="group.label" class="perm-group">
              <div class="perm-group-title">{{ group.label }}</div>
              <el-checkbox-group v-model="checkedPermissions" class="checkbox-list">
                <el-checkbox v-for="perm in group.permissions" :key="perm.permissionId" :label="perm.permissionId">
                  <span class="check-label">{{ perm.name }}</span>
                  <span class="check-desc">{{ perm.code }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </div>
            <div class="save-bar">
              <el-button type="primary" :loading="roleSaving" @click="saveRolePermissions">
                保存
              </el-button>
            </div>
          </template>
          <el-empty v-else description="请先选择角色" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAllUsers, getUserRoles, assignUserRoles } from "@/api/user";
import { getRoleList, getRolePermissions, assignRolePermissions } from "@/api/role";
import { getPermissionList } from "@/api/permission";

const activeTab = ref("user");
const loading = ref(true);

// ================== 用户授权 ==================
const users = ref([]);
const allRoles = ref([]);
const selectedUser = ref(null);
const checkedRoles = ref([]);
const userKeyword = ref("");
const userSaving = ref(false);

const filteredUsers = computed(() => {
  const key = userKeyword.value.trim().toLowerCase();
  if (!key) return users.value;
  return users.value.filter(
    (u) =>
      (u.username || "").toLowerCase().includes(key) ||
      (u.email || "").toLowerCase().includes(key)
  );
});

const selectUser = async (row) => {
  selectedUser.value = row;
  try {
    const res = await getUserRoles(row.userId);
    checkedRoles.value = (Array.isArray(res.data) ? res.data : []).map((r) => r.roleId);
  } catch {
    checkedRoles.value = [];
  }
};

const userRowClass = ({ row }) => {
  return selectedUser.value && selectedUser.value.userId === row.userId ? "current-row" : "";
};

const saveUserRoles = async () => {
  if (!selectedUser.value) return;
  userSaving.value = true;
  try {
    await assignUserRoles(selectedUser.value.userId, checkedRoles.value);
    ElMessage.success("用户角色保存成功");
  } catch {
    ElMessage.error("保存失败");
  } finally {
    userSaving.value = false;
  }
};

// ================== 角色授权 ==================
const allPermissions = ref([]);
const selectedRoleId = ref(null);
const checkedPermissions = ref([]);
const roleSaving = ref(false);

const permissionGroups = computed(() => {
  const groups = [
    { label: '首页', prefix: 'DASHBOARD' },
    { label: '用户管理', prefix: 'USER' },
    { label: '题目管理', prefix: 'PROBLEM' },
    { label: '作品管理', prefix: 'SUBMISSION' },
    { label: '标签管理', prefix: 'TAG' },
    { label: '帖子管理', prefix: 'POST' },
    { label: '赛事管理', prefix: 'CONTEST' },
    { label: '角色管理', prefix: 'ROLE' },
    { label: '权限管理', prefix: 'PERMISSION' },
    { label: '授权管理', prefix: 'AUTH' },
    { label: '文件上传', prefix: 'FILE' },
  ];
  return groups.map(g => ({
    label: g.label,
    permissions: allPermissions.value.filter(p => (p.code || '').startsWith(g.prefix))
  })).filter(g => g.permissions.length > 0);
});

const selectRole = async (roleId) => {
  if (!roleId) {
    checkedPermissions.value = [];
    return;
  }
  try {
    const res = await getRolePermissions(roleId);
    checkedPermissions.value = (Array.isArray(res.data) ? res.data : []).map((p) => p.permissionId);
  } catch {
    checkedPermissions.value = [];
  }
};

const saveRolePermissions = async () => {
  if (!selectedRoleId.value) return;
  roleSaving.value = true;
  try {
    await assignRolePermissions(selectedRoleId.value, checkedPermissions.value);
    ElMessage.success("角色权限保存成功");
  } catch {
    ElMessage.error("保存失败");
  } finally {
    roleSaving.value = false;
  }
};

// ================== 初始化 ==================
const handleTabChange = (tab) => {
  if (tab === 'user') {
    selectedRoleId.value = null;
    checkedPermissions.value = [];
  } else {
    selectedUser.value = null;
    checkedRoles.value = [];
  }
};

onMounted(async () => {
  try {
    const [userRes, roleRes, permRes] = await Promise.all([
      getAllUsers(),
      getRoleList(),
      getPermissionList(),
    ]);
    users.value = Array.isArray(userRes.data) ? userRes.data : [];
    allRoles.value = Array.isArray(roleRes.data) ? roleRes.data : [];
    allPermissions.value = Array.isArray(permRes.data) ? permRes.data : [];
  } catch {
    ElMessage.error("加载数据失败");
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.auth-layout {
  display: flex;
  gap: 20px;
  min-height: 420px;
}
.auth-left {
  flex: 1;
  min-width: 0;
}
.auth-right {
  flex: 1;
  min-width: 0;
  border-left: 1px solid #eee;
  padding-left: 20px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 600;
  font-size: 14px;
  color: #333;
}
.checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.check-label {
  font-weight: 500;
  margin-right: 8px;
}
.check-desc {
  font-size: 12px;
  color: #999;
}
.save-bar {
  margin-top: 20px;
}
.role-auth-top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.role-auth-top .label {
  font-weight: 600;
  font-size: 14px;
  color: #333;
}
.perm-group {
  margin-bottom: 16px;
}
.perm-group-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed #ebeef5;
}
:deep(.current-row) {
  background-color: #ecf5ff !important;
}
@media (max-width: 900px) {
  .auth-layout {
    flex-direction: column;
  }
  .auth-right {
    border-left: none;
    border-top: 1px solid #eee;
    padding-left: 0;
    padding-top: 20px;
  }
}
</style>
