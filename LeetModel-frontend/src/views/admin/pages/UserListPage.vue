<template>
  <div class="user-console">
    <div class="view-toolbar">
      <div class="filter-controls">
        <el-input
          v-model="keyword"
          placeholder="用户名 / 昵称 / 邮箱"
          clearable
          class="keyword-filter"
          @keyup.enter="applyFilters"
          @clear="applyFilters"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="status" placeholder="账号状态" clearable class="select-filter" @change="applyFilters">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-select
          v-model="roleId"
          placeholder="全部角色"
          clearable
          class="select-filter"
          :loading="rolesLoading"
          :disabled="!roleCatalogAvailable"
          @change="applyFilters"
        >
          <el-option v-for="role in allRoles" :key="role.id" :label="role.name" :value="role.id" />
        </el-select>
        <el-button type="primary" @click="applyFilters">查询</el-button>
      </div>
      <el-button :loading="loading" @click="loadUsers"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <div v-if="activeFilters.length" class="active-filters" aria-label="已选筛选条件">
      <el-tag
        v-for="filter in activeFilters"
        :key="filter.key"
        closable
        effect="plain"
        @close="clearFilter(filter.key)"
      >
        {{ filter.label }}
      </el-tag>
      <button type="button" @click="clearAllFilters">清除全部</button>
    </div>

    <AdminStatePanel
      v-if="listError && !users.length"
      type="error"
      title="用户列表加载失败"
      action-label="重新加载"
      @action="loadUsers"
    />

    <template v-else>
      <div v-if="listError" class="inline-warning" role="alert">
        <el-icon><WarningFilled /></el-icon>
        刷新失败，当前保留上次取得的数据
      </div>

      <div class="table-scroll">
        <el-table :data="users" v-loading="loading" row-key="id" table-layout="fixed">
          <el-table-column label="用户" min-width="210">
            <template #default="{ row }">
              <div class="user-identity">
                <el-avatar :size="30" :src="row.avatarUrl || undefined">{{ avatarText(row) }}</el-avatar>
                <span>
                  <strong>{{ row.username || '—' }}</strong>
                  <small>{{ row.nickname || '未设置昵称' }}</small>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">{{ row.email || '—' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="190">
            <template #default="{ row }">
              <div v-if="row.roles?.length" class="role-tags">
                <el-tag v-for="role in row.roles" :key="role.id" size="small" effect="plain">{{ role.name }}</el-tag>
              </div>
              <span v-else class="muted-value">未分配</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <AdminStatusBadge
                :status="row.status === 1 ? 'ENABLED' : 'DISABLED'"
                :label="row.status === 1 ? '启用' : '禁用'"
                :tone="row.status === 1 ? 'success' : 'danger'"
              />
            </template>
          </el-table-column>
          <el-table-column label="注册时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="104" fixed="right" align="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" @click="openDetail(row)">详情</el-button>
                <el-dropdown trigger="click" @command="handleRowCommand($event, row)">
                  <button class="more-button" type="button" :aria-label="`${row.username} 更多操作`">
                    <el-icon><MoreFilled /></el-icon>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="copy-email" :disabled="!row.email">复制邮箱</el-dropdown-item>
                      <el-dropdown-item command="copy-id">复制用户 ID</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">{{ activeFilters.length ? '没有符合条件的用户' : '暂无用户' }}</div>
          </template>
        </el-table>
      </div>

      <div class="pagination-row">
        <span>共 {{ total.toLocaleString('zh-CN') }} 位用户</span>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          layout="sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="loadUsers"
          @size-change="handleSizeChange"
        />
      </div>
    </template>

    <el-drawer
      v-model="detailVisible"
      title="用户详情"
      size="min(520px, 94vw)"
      destroy-on-close
      :before-close="beforeCloseDetail"
    >
      <div v-loading="detailLoading" class="user-detail">
        <AdminStatePanel
          v-if="detailError"
          type="error"
          title="用户详情加载失败"
          action-label="重试"
          @action="loadDetail(selectedUserId)"
        />

        <template v-else-if="selectedUser">
          <div class="detail-identity">
            <el-avatar :size="42" :src="selectedUser.avatarUrl || undefined">{{ avatarText(selectedUser) }}</el-avatar>
            <span>
              <strong>{{ selectedUser.username }}</strong>
              <small>{{ selectedUser.nickname || '未设置昵称' }}</small>
            </span>
            <AdminStatusBadge
              :status="selectedUser.status === 1 ? 'ENABLED' : 'DISABLED'"
              :label="selectedUser.status === 1 ? '启用' : '禁用'"
              :tone="selectedUser.status === 1 ? 'success' : 'danger'"
            />
          </div>

          <dl class="detail-facts">
            <div><dt>邮箱</dt><dd>{{ selectedUser.email || '—' }}</dd></div>
            <div><dt>注册时间</dt><dd>{{ formatTime(selectedUser.createTime) }}</dd></div>
            <div><dt>最近更新</dt><dd>{{ formatTime(selectedUser.updateTime) }}</dd></div>
            <div class="id-fact">
              <dt>内部 ID</dt>
              <dd><code>{{ selectedUser.id }}</code><el-button link type="primary" @click="copyText(selectedUser.id, '用户 ID')">复制</el-button></dd>
            </div>
          </dl>

          <section class="detail-section">
            <div class="detail-section-heading">
              <h3>账号状态</h3>
              <el-button
                :type="selectedUser.status === 1 ? 'danger' : 'success'"
                plain
                :loading="statusSaving"
                :disabled="isCurrentUser"
                @click="changeStatus"
              >
                {{ selectedUser.status === 1 ? '禁用账号' : '启用账号' }}
              </el-button>
            </div>
            <p v-if="isCurrentUser" class="self-protection">当前登录账号不能修改自身状态或角色。</p>
          </section>

          <section class="detail-section">
            <div class="detail-section-heading">
              <h3>角色</h3>
              <span v-if="rolesDirty" class="dirty-label">未保存</span>
            </div>
            <AdminStatePanel
              v-if="!roleCatalogAvailable"
              type="error"
              title="角色目录加载失败"
              action-label="重试"
              @action="loadRoles"
            />
            <el-checkbox-group v-else v-model="checkedRoleIds" class="role-options" :disabled="isCurrentUser">
              <el-checkbox v-for="role in allRoles" :key="role.id" :value="role.id">
                <span><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span>
              </el-checkbox>
            </el-checkbox-group>
            <div class="detail-save-row">
              <el-button :disabled="!rolesDirty" @click="resetRoles">撤销</el-button>
              <el-button type="primary" :loading="rolesSaving" :disabled="!rolesDirty || isCurrentUser" @click="saveRoles">保存角色</el-button>
            </div>
          </section>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "@/store/user";
import { adminPageUsers, adminUpdateUserRoles, adminUpdateUserStatus, adminUserDetail } from "@/api/user";
import { getRoleList } from "@/api/role";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";

const emit = defineEmits(["changed"]);
const userStore = useUserStore();
const users = ref([]);
const allRoles = ref([]);
const loading = ref(false);
const rolesLoading = ref(false);
const roleCatalogAvailable = ref(true);
const listError = ref(false);
const keyword = ref("");
const status = ref(null);
const roleId = ref(null);
const page = ref(1);
const pageSize = ref(10);
const total = ref(0);
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref(false);
const selectedUser = ref(null);
const selectedUserId = ref("");
const checkedRoleIds = ref([]);
const savedRoleIds = ref([]);
const rolesSaving = ref(false);
const statusSaving = ref(false);

const activeFilters = computed(() => {
  const filters = [];
  if (keyword.value.trim()) filters.push({ key: "keyword", label: `关键词：${keyword.value.trim()}` });
  if (status.value !== null && status.value !== "") {
    filters.push({ key: "status", label: `状态：${status.value === 1 ? "启用" : "禁用"}` });
  }
  const role = allRoles.value.find((item) => item.id === roleId.value);
  if (roleId.value) filters.push({ key: "roleId", label: `角色：${role?.name || roleId.value}` });
  return filters;
});
const rolesDirty = computed(() => normalizeIds(checkedRoleIds.value) !== normalizeIds(savedRoleIds.value));
const isCurrentUser = computed(() => String(selectedUser.value?.id || "") === String(userStore.userId || ""));

function normalizeIds(values) {
  return [...values].map(String).sort().join(",");
}

function avatarText(user) {
  return String(user?.nickname || user?.username || "用").slice(0, 1).toUpperCase();
}

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

function buildQuery() {
  const params = { page: page.value, pageSize: pageSize.value };
  if (keyword.value.trim()) params.keyword = keyword.value.trim();
  if (status.value !== null && status.value !== "") params.status = status.value;
  if (roleId.value) params.roleId = roleId.value;
  return params;
}

async function loadUsers() {
  if (loading.value) return;
  loading.value = true;
  listError.value = false;
  try {
    const response = await adminPageUsers(buildQuery());
    users.value = response.data?.rows || [];
    total.value = Number(response.data?.total || 0);
  } catch (error) {
    listError.value = true;
    if (users.value.length) ElMessage.error(error.message || "用户列表刷新失败");
  } finally {
    loading.value = false;
  }
}

async function loadRoles() {
  rolesLoading.value = true;
  try {
    allRoles.value = (await getRoleList()).data || [];
    roleCatalogAvailable.value = true;
  } catch (error) {
    roleCatalogAvailable.value = false;
    ElMessage.error(error.message || "角色目录加载失败");
  } finally {
    rolesLoading.value = false;
  }
}

function applyFilters() {
  page.value = 1;
  loadUsers();
}

function clearFilter(key) {
  if (key === "keyword") keyword.value = "";
  if (key === "status") status.value = null;
  if (key === "roleId") roleId.value = null;
  applyFilters();
}

function clearAllFilters() {
  keyword.value = "";
  status.value = null;
  roleId.value = null;
  applyFilters();
}

function handleSizeChange() {
  page.value = 1;
  loadUsers();
}

async function openDetail(row) {
  selectedUserId.value = String(row.id);
  detailVisible.value = true;
  await Promise.all([loadDetail(row.id), roleCatalogAvailable.value ? Promise.resolve() : loadRoles()]);
}

async function loadDetail(userId) {
  if (!userId) return;
  detailLoading.value = true;
  detailError.value = false;
  try {
    selectedUser.value = (await adminUserDetail(userId)).data;
    const roleIds = (selectedUser.value?.roles || []).map((role) => role.id);
    checkedRoleIds.value = [...roleIds];
    savedRoleIds.value = [...roleIds];
  } catch (error) {
    detailError.value = true;
    selectedUser.value = null;
  } finally {
    detailLoading.value = false;
  }
}

function resetRoles() {
  checkedRoleIds.value = [...savedRoleIds.value];
}

async function saveRoles() {
  if (!selectedUser.value || !rolesDirty.value || isCurrentUser.value) return;
  rolesSaving.value = true;
  try {
    await adminUpdateUserRoles(selectedUser.value.id, checkedRoleIds.value);
    savedRoleIds.value = [...checkedRoleIds.value];
    ElMessage.success("用户角色已更新");
    await Promise.all([loadUsers(), loadDetail(selectedUser.value.id)]);
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "角色保存失败");
  } finally {
    rolesSaving.value = false;
  }
}

async function changeStatus() {
  if (!selectedUser.value || isCurrentUser.value) return;
  const target = selectedUser.value.status === 1 ? 0 : 1;
  const action = target === 1 ? "启用" : "禁用";
  try {
    await ElMessageBox.confirm(
      `${action}后将${target === 1 ? "恢复" : "阻止"}“${selectedUser.value.username}”登录。`,
      `${action}账号？`,
      { type: "warning", confirmButtonText: action, cancelButtonText: "取消" },
    );
  } catch {
    return;
  }

  statusSaving.value = true;
  try {
    await adminUpdateUserStatus(selectedUser.value.id, target);
    ElMessage.success(`账号已${action}`);
    await Promise.all([loadUsers(), loadDetail(selectedUser.value.id)]);
  } catch (error) {
    ElMessage.error(error.message || `${action}失败`);
  } finally {
    statusSaving.value = false;
  }
}

async function beforeCloseDetail(done) {
  if (!rolesDirty.value) {
    done();
    return;
  }
  try {
    await ElMessageBox.confirm("角色修改尚未保存，关闭后将丢失。", "放弃未保存修改？", {
      type: "warning",
      confirmButtonText: "放弃修改",
      cancelButtonText: "继续编辑",
    });
    done();
  } catch {
    // 用户继续编辑时保持详情面板打开。
  }
}

async function copyText(value, label) {
  try {
    await navigator.clipboard.writeText(String(value || ""));
    ElMessage.success(`${label}已复制`);
  } catch {
    ElMessage.error("复制失败，请手动复制");
  }
}

function handleRowCommand(command, row) {
  if (command === "copy-email") copyText(row.email, "邮箱");
  if (command === "copy-id") copyText(row.id, "用户 ID");
}

onMounted(() => Promise.all([loadUsers(), loadRoles()]));
</script>

<style scoped>
.user-console {
  min-width: 0;
  padding: var(--lm-admin-space-3);
}

.view-toolbar,
.filter-controls,
.active-filters,
.pagination-row,
.row-actions,
.detail-section-heading,
.detail-save-row {
  display: flex;
  align-items: center;
}

.view-toolbar {
  justify-content: space-between;
  gap: var(--lm-admin-space-3);
  margin-bottom: var(--lm-admin-space-3);
}

.filter-controls {
  min-width: 0;
  flex-wrap: wrap;
  gap: var(--lm-admin-space-2);
}

.keyword-filter {
  width: 280px;
}

.select-filter {
  width: 132px;
}

.active-filters {
  flex-wrap: wrap;
  gap: 6px;
  margin: -4px 0 var(--lm-admin-space-3);
}

.active-filters button {
  padding: 4px 6px;
  color: var(--lm-admin-primary);
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 11px;
}

.inline-warning {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--lm-admin-space-2);
  padding: 8px 10px;
  color: var(--lm-admin-warning);
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: var(--lm-admin-radius-control);
  font-size: 11px;
}

.table-scroll {
  min-width: 0;
  overflow-x: auto;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.table-scroll :deep(.el-table) {
  min-width: 946px;
}

.table-scroll :deep(.el-table__header th) {
  height: 38px;
  color: var(--lm-admin-text-muted);
  background: var(--lm-admin-surface-subtle);
  font-size: 11px;
}

.table-scroll :deep(.el-table__cell) {
  padding: 7px 0;
}

.user-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}

.user-identity > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.user-identity strong,
.detail-identity strong {
  overflow: hidden;
  color: var(--lm-admin-text-strong);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-identity small,
.detail-identity small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--lm-admin-text-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.muted-value,
.pagination-row,
.self-protection {
  color: var(--lm-admin-text-muted);
  font-size: 11px;
}

.row-actions {
  justify-content: flex-end;
  gap: 2px;
}

.more-button {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: var(--lm-admin-text-muted);
  background: transparent;
  border: 0;
  border-radius: var(--lm-admin-radius-control);
  cursor: pointer;
}

.more-button:hover,
.more-button:focus-visible {
  color: var(--lm-admin-text-strong);
  background: var(--lm-admin-surface-subtle);
}

.pagination-row {
  justify-content: space-between;
  gap: var(--lm-admin-space-3);
  margin-top: var(--lm-admin-space-3);
}

.table-empty {
  padding: 36px 0;
  color: var(--lm-admin-text-muted);
  font-size: 12px;
}

.user-detail {
  min-height: 240px;
}

.detail-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: var(--lm-admin-space-4);
  border-bottom: 1px solid var(--lm-admin-border);
}

.detail-identity > span:nth-child(2) {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.detail-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  padding: var(--lm-admin-space-3) 0;
  border-bottom: 1px solid var(--lm-admin-border);
}

.detail-facts > div {
  min-width: 0;
  padding: 7px 8px;
}

.detail-facts dt {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.detail-facts dd {
  overflow: hidden;
  margin: 3px 0 0;
  color: var(--lm-admin-text-strong);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.id-fact dd {
  display: flex;
  align-items: center;
  gap: 6px;
}

.id-fact code {
  overflow: hidden;
  color: var(--lm-admin-text-default);
  text-overflow: ellipsis;
}

.detail-section {
  padding-top: var(--lm-admin-space-4);
}

.detail-section-heading {
  min-height: 32px;
  justify-content: space-between;
  gap: var(--lm-admin-space-3);
  margin-bottom: var(--lm-admin-space-2);
}

.detail-section-heading h3 {
  margin: 0;
  color: var(--lm-admin-text-strong);
  font-size: 13px;
}

.dirty-label {
  color: var(--lm-admin-warning);
  font-size: 11px;
}

.self-protection {
  margin: 2px 0 0;
}

.role-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.role-options :deep(.el-checkbox) {
  height: auto;
  margin: 0;
  padding: 9px;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.role-options :deep(.el-checkbox__label) {
  min-width: 0;
}

.role-options span {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.role-options strong {
  color: var(--lm-admin-text-strong);
  font-size: 11px;
}

.role-options small {
  margin-top: 2px;
  color: var(--lm-admin-text-muted);
  font-size: 9px;
}

.detail-save-row {
  justify-content: flex-end;
  gap: var(--lm-admin-space-2);
  margin-top: var(--lm-admin-space-3);
}

@media (max-width: 767px) {
  .user-console {
    padding: var(--lm-admin-space-2);
  }

  .view-toolbar,
  .pagination-row {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-controls {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .keyword-filter {
    width: auto;
    grid-column: 1 / -1;
  }

  .select-filter {
    width: auto;
  }

  .pagination-row :deep(.el-pagination) {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .detail-facts,
  .role-options {
    grid-template-columns: 1fr;
  }
}
</style>
