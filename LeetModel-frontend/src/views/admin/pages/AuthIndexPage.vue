<template>
  <div class="authorization-console">
    <AdminStatePanel
      v-if="catalogError"
      type="error"
      title="授权目录加载失败"
      action-label="重新加载"
      @action="loadCatalog"
    />

    <div v-else class="authorization-workspace" v-loading="loading">
      <aside class="role-rail" aria-label="角色列表">
        <div class="rail-heading">
          <span>角色</span>
          <b>{{ roles.length }}</b>
        </div>
        <button
          v-for="role in roles"
          :key="role.id"
          class="role-option"
          :class="{ active: role.id === selectedRoleId }"
          type="button"
          @click="requestRole(role.id)"
        >
          <span class="role-mark">{{ roleMark(role) }}</span>
          <span class="role-copy"><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span>
          <span class="role-user-count">{{ formatCount(role.userCount) }}</span>
        </button>
      </aside>

      <main v-if="selectedRole" class="policy-panel">
        <div class="policy-header">
          <div class="policy-title">
            <span><strong>{{ selectedRole.name }}</strong><code>{{ selectedRole.code }}</code></span>
            <AdminStatusBadge v-if="selectedRole.system" status="PROTECTED" label="系统角色" />
          </div>
          <div class="policy-count">
            <strong>{{ checkedPermissions.length }} / {{ permissions.length }}</strong>
            <span>已授权</span>
          </div>
        </div>

        <div class="policy-toolbar">
          <span class="sync-state" :class="{ dirty }"><i></i>{{ dirty ? '有未保存修改' : '已同步' }}</span>
          <div>
            <el-button size="small" @click="selectAll">全选</el-button>
            <el-button size="small" @click="clearAll">清空</el-button>
            <el-button size="small" :disabled="!dirty" @click="resetChanges">撤销</el-button>
          </div>
        </div>

        <AdminStatePanel
          v-if="permissionLoadError"
          type="error"
          title="角色权限加载失败"
          action-label="重试"
          @action="loadRolePermissions(selectedRoleId)"
        />

        <div v-else-if="permissionGroups.length" class="permission-groups" v-loading="roleLoading">
          <section v-for="group in permissionGroups" :key="group.key" class="permission-group">
            <div class="group-header">
              <span><strong>{{ group.label }}</strong><small>{{ groupCheckedCount(group) }}/{{ group.permissions.length }}</small></span>
              <el-checkbox
                :model-value="isGroupChecked(group)"
                :indeterminate="isGroupIndeterminate(group)"
                @change="toggleGroup(group, $event)"
              >全组</el-checkbox>
            </div>
            <el-checkbox-group v-model="checkedPermissions" class="permission-options">
              <el-checkbox v-for="permission in group.permissions" :key="permission.id" :value="permission.id">
                <span>
                  <strong>{{ permission.name }}</strong>
                  <code>{{ permission.code }}</code>
                  <small v-if="permission.description">{{ permission.description }}</small>
                </span>
              </el-checkbox>
            </el-checkbox-group>
          </section>
        </div>

        <AdminStatePanel v-else type="empty" title="当前没有可配置权限" />

        <div class="policy-save-bar">
          <span>{{ impactLabel }}</span>
          <el-button type="primary" :loading="saving" :disabled="!dirty || permissionLoadError" @click="savePermissions">
            保存策略
          </el-button>
        </div>
      </main>

      <AdminStatePanel v-else class="role-empty" type="empty" title="暂无可配置角色" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { assignRolePermissions, getRoleList, getRolePermissions } from "@/api/role";
import { getPermissionList } from "@/api/permission";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";

const emit = defineEmits(["dirty-change", "changed"]);
const loading = ref(false);
const roleLoading = ref(false);
const saving = ref(false);
const catalogError = ref(false);
const permissionLoadError = ref(false);
const roles = ref([]);
const permissions = ref([]);
const selectedRoleId = ref(null);
const checkedPermissions = ref([]);
const savedPermissions = ref([]);
const groupDefinitions = [
  { key: "user", label: "用户与账号" },
  { key: "role", label: "角色" },
  { key: "permission", label: "权限" },
  { key: "auth", label: "授权策略" },
  { key: "problem", label: "题目" },
  { key: "tag", label: "标签" },
  { key: "contest", label: "赛事" },
  { key: "submission", label: "提交" },
  { key: "suggestion", label: "建议" },
  { key: "file", label: "文件" },
  { key: "ai", label: "AI" },
  { key: "audit", label: "审计" },
];

const selectedRole = computed(() => roles.value.find((role) => role.id === selectedRoleId.value));
const dirty = computed(() => normalizeIds(checkedPermissions.value) !== normalizeIds(savedPermissions.value));
const permissionGroups = computed(() => {
  const assigned = new Set();
  const groups = groupDefinitions.map((definition) => {
    const groupPermissions = permissions.value.filter((permission) => {
      const domain = permissionDomain(permission.code);
      const matches = domain === definition.key;
      if (matches) assigned.add(permission.id);
      return matches;
    });
    return { ...definition, permissions: groupPermissions };
  }).filter((group) => group.permissions.length);

  const remaining = permissions.value.filter((permission) => !assigned.has(permission.id));
  if (remaining.length) groups.push({ key: "other", label: "其他", permissions: remaining });
  return groups;
});
const impactLabel = computed(() => {
  if (!selectedRole.value) return "";
  const count = selectedRole.value.userCount;
  return Number.isFinite(Number(count))
    ? `保存后影响 ${Number(count).toLocaleString("zh-CN")} 位关联用户`
    : "关联用户影响范围未取得";
});

watch(dirty, (value) => emit("dirty-change", value), { immediate: true });

function normalizeIds(values) {
  return [...values].map(String).sort().join(",");
}

function permissionDomain(code) {
  return String(code || "").toLowerCase().split(/[:_.-]/)[0];
}

function roleMark(role) {
  return String(role?.name || "角").slice(0, 1);
}

function formatCount(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString("zh-CN") : "—";
}

function groupCheckedCount(group) {
  return group.permissions.filter((permission) => checkedPermissions.value.includes(permission.id)).length;
}

function isGroupChecked(group) {
  return group.permissions.length > 0 && groupCheckedCount(group) === group.permissions.length;
}

function isGroupIndeterminate(group) {
  const count = groupCheckedCount(group);
  return count > 0 && count < group.permissions.length;
}

function toggleGroup(group, value) {
  const ids = group.permissions.map((permission) => permission.id);
  if (value) checkedPermissions.value = [...new Set([...checkedPermissions.value, ...ids])];
  else checkedPermissions.value = checkedPermissions.value.filter((id) => !ids.includes(id));
}

function selectAll() {
  checkedPermissions.value = permissions.value.map((permission) => permission.id);
}

function clearAll() {
  checkedPermissions.value = [];
}

function resetChanges() {
  checkedPermissions.value = [...savedPermissions.value];
}

async function confirmDiscard() {
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm(
      "当前角色的权限修改尚未保存，切换后将丢失。",
      "放弃未保存修改？",
      {
        type: "warning",
        confirmButtonText: "放弃修改",
        cancelButtonText: "继续编辑",
      },
    );
    return true;
  } catch {
    return false;
  }
}

async function requestRole(roleId) {
  if (roleId === selectedRoleId.value) return;
  if (!(await confirmDiscard())) return;
  await loadRolePermissions(roleId);
}

async function loadRolePermissions(roleId) {
  if (!roleId) return;
  selectedRoleId.value = roleId;
  roleLoading.value = true;
  permissionLoadError.value = false;
  try {
    const response = await getRolePermissions(roleId);
    const ids = (response.data || []).map((permission) => permission.id);
    savedPermissions.value = [...ids];
    checkedPermissions.value = [...ids];
  } catch (error) {
    permissionLoadError.value = true;
    savedPermissions.value = [];
    checkedPermissions.value = [];
    ElMessage.error(error.message || "角色权限加载失败");
  } finally {
    roleLoading.value = false;
  }
}

async function savePermissions() {
  if (!selectedRoleId.value || !dirty.value || permissionLoadError.value) return;
  saving.value = true;
  try {
    await assignRolePermissions(selectedRoleId.value, checkedPermissions.value);
    savedPermissions.value = [...checkedPermissions.value];
    const role = roles.value.find((item) => item.id === selectedRoleId.value);
    if (role) role.permissionCount = checkedPermissions.value.length;
    ElMessage.success("权限策略已保存");
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "权限策略保存失败");
  } finally {
    saving.value = false;
  }
}

async function loadCatalog() {
  if (loading.value) return;
  loading.value = true;
  catalogError.value = false;
  const results = await Promise.allSettled([getRoleList(), getPermissionList()]);
  if (results.some((result) => result.status === "rejected")) {
    catalogError.value = true;
    loading.value = false;
    return;
  }

  roles.value = results[0].value.data || [];
  permissions.value = results[1].value.data || [];
  if (roles.value.length) await loadRolePermissions(roles.value[0].id);
  loading.value = false;
}

onMounted(loadCatalog);
</script>

<style scoped>
.authorization-console {
  min-width: 0;
  padding: var(--lm-admin-space-3);
}

.authorization-workspace {
  display: grid;
  min-height: 520px;
  grid-template-columns: 210px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.role-rail {
  min-width: 0;
  padding: 10px 8px;
  background: var(--lm-admin-surface-subtle);
  border-right: 1px solid var(--lm-admin-border);
}

.rail-heading {
  display: flex;
  min-height: 30px;
  align-items: center;
  justify-content: space-between;
  padding: 0 7px;
  color: var(--lm-admin-text-muted);
  font-size: 11px;
  font-weight: 700;
}

.rail-heading b {
  font-weight: 600;
}

.role-option {
  display: flex;
  width: 100%;
  min-height: 48px;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  padding: 7px;
  color: var(--lm-admin-text-default);
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--lm-admin-radius-control);
  cursor: pointer;
}

.role-option:hover {
  background: rgba(255, 255, 255, 0.7);
}

.role-option.active {
  color: var(--lm-admin-primary);
  background: var(--lm-admin-surface);
  border-color: #bfdbfe;
}

.role-mark {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  color: var(--lm-admin-info);
  background: #e5e7eb;
  border-radius: var(--lm-admin-radius-control);
  font-size: 11px;
  font-weight: 700;
}

.role-option.active .role-mark {
  color: #ffffff;
  background: var(--lm-admin-primary);
}

.role-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.role-copy strong,
.policy-title strong {
  overflow: hidden;
  color: var(--lm-admin-text-strong);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-copy small,
.role-user-count {
  color: var(--lm-admin-text-muted);
  font-size: 9px;
}

.role-user-count {
  flex: 0 0 auto;
}

.policy-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: var(--lm-admin-space-4);
}

.policy-header,
.policy-title,
.policy-toolbar,
.policy-toolbar > div,
.group-header,
.group-header > span,
.policy-save-bar {
  display: flex;
  align-items: center;
}

.policy-header {
  min-height: 48px;
  justify-content: space-between;
  gap: var(--lm-admin-space-4);
  padding-bottom: var(--lm-admin-space-3);
  border-bottom: 1px solid var(--lm-admin-border);
}

.policy-title {
  min-width: 0;
  gap: 8px;
}

.policy-title > span:first-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.policy-title code {
  margin-top: 2px;
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.policy-count {
  display: flex;
  flex: 0 0 auto;
  align-items: baseline;
  gap: 6px;
}

.policy-count strong {
  color: var(--lm-admin-text-strong);
  font-size: 16px;
}

.policy-count span {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.policy-toolbar {
  min-height: 48px;
  justify-content: space-between;
  gap: var(--lm-admin-space-3);
}

.policy-toolbar > div {
  gap: 2px;
}

.sync-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.sync-state i {
  width: 6px;
  height: 6px;
  background: var(--lm-admin-success);
  border-radius: 50%;
}

.sync-state.dirty {
  color: var(--lm-admin-warning);
}

.sync-state.dirty i {
  background: var(--lm-admin-warning);
}

.permission-groups {
  display: grid;
  gap: var(--lm-admin-space-2);
}

.permission-group {
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.group-header {
  justify-content: space-between;
  gap: var(--lm-admin-space-3);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--lm-admin-border);
}

.group-header > span {
  gap: 7px;
}

.group-header strong {
  color: var(--lm-admin-text-strong);
  font-size: 12px;
}

.group-header small {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.permission-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding-top: 8px;
}

.permission-options :deep(.el-checkbox) {
  height: auto;
  min-height: 48px;
  align-items: flex-start;
  margin: 0;
  padding: 8px;
  background: var(--lm-admin-surface-subtle);
  border-radius: var(--lm-admin-radius-control);
}

.permission-options :deep(.el-checkbox__input) {
  margin-top: 2px;
}

.permission-options :deep(.el-checkbox__label) {
  min-width: 0;
  white-space: normal;
}

.permission-options span {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.permission-options strong {
  color: var(--lm-admin-text-strong);
  font-size: 11px;
}

.permission-options code {
  margin-top: 1px;
  color: var(--lm-admin-primary);
  font-size: 9px;
}

.permission-options small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--lm-admin-text-muted);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.policy-save-bar {
  position: sticky;
  bottom: -16px;
  justify-content: space-between;
  gap: var(--lm-admin-space-4);
  margin: auto -16px -16px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid var(--lm-admin-border);
}

.policy-save-bar span {
  color: var(--lm-admin-text-muted);
  font-size: 10px;
}

.role-empty {
  margin: auto;
}

@media (max-width: 767px) {
  .authorization-console {
    padding: var(--lm-admin-space-2);
  }

  .authorization-workspace {
    grid-template-columns: 1fr;
  }

  .role-rail {
    display: flex;
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid var(--lm-admin-border);
  }

  .rail-heading {
    display: none;
  }

  .role-option {
    min-width: 148px;
    margin: 0 4px 0 0;
  }

  .policy-panel {
    padding: var(--lm-admin-space-3);
  }

  .permission-options {
    grid-template-columns: 1fr;
  }

  .policy-save-bar {
    bottom: -12px;
    margin: auto -12px -12px;
    padding: 10px 12px;
  }
}
</style>
