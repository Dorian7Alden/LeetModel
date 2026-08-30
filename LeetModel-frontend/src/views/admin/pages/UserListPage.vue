<template>
  <div class="user-list-page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名 / 昵称 / 邮箱"
          clearable
          style="width: 300px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-select v-model="status" placeholder="账号状态" clearable style="width: 140px" @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="users" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="130" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles || []" :key="role.id" size="small" effect="plain" class="role-tag">{{ role.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRoles(row)">分配角色</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无用户" /></template>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="load"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="roleDialogVisible" title="分配角色" width="420px">
      <el-checkbox-group v-if="selectedUser" v-model="checkedRoleIds" class="role-checkboxes">
        <el-checkbox v-for="role in allRoles" :key="role.id" :value="role.id">
          {{ role.name }}（{{ role.code }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { adminPageUsers, adminUpdateUserStatus, adminUpdateUserRoles } from "@/api/user";
import { getRoleList } from "@/api/role";

const users = ref([]);
const allRoles = ref([]);
const loading = ref(false);
const savingRoles = ref(false);
const keyword = ref("");
const status = ref(null);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const roleDialogVisible = ref(false);
const selectedUser = ref(null);
const checkedRoleIds = ref([]);

function formatTime(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 16);
}

async function load() {
  loading.value = true;
  const params = { page: page.value, pageSize: pageSize.value };
  if (keyword.value.trim()) params.keyword = keyword.value.trim();
  if (status.value !== null && status.value !== "") params.status = status.value;
  try {
    const res = await adminPageUsers(params);
    users.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  } catch (error) {
    ElMessage.error(error.message || "用户列表加载失败");
  } finally {
    loading.value = false;
  }
}

function handleSizeChange() {
  page.value = 1;
  load();
}

async function openRoles(row) {
  selectedUser.value = row;
  checkedRoleIds.value = (row.roles || []).map((role) => role.id);
  if (!allRoles.value.length) {
    try {
      allRoles.value = (await getRoleList()).data || [];
    } catch (error) {
      ElMessage.error(error.message || "角色列表加载失败");
    }
  }
  roleDialogVisible.value = true;
}

async function saveRoles() {
  if (!selectedUser.value) return;
  savingRoles.value = true;
  try {
    await adminUpdateUserRoles(selectedUser.value.id, checkedRoleIds.value);
    ElMessage.success("角色分配已更新");
    roleDialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error.message || "角色分配失败");
  } finally {
    savingRoles.value = false;
  }
}

async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1;
  const action = target === 1 ? "启用" : "禁用";
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.username}」吗？`, "账号状态", { type: "warning" });
    await adminUpdateUserStatus(row.id, target);
    ElMessage.success(`${action}成功`);
    await load();
  } catch (error) {
    if (error !== "cancel") ElMessage.error(error.message || `${action}失败`);
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; margin-bottom: 18px; }
.role-tag { margin-right: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.role-checkboxes { display: flex; flex-direction: column; gap: 10px; }
@media (max-width: 720px) { .toolbar { flex-wrap: wrap; } }
</style>
