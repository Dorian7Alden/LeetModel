<template>
  <div class="authorization-page" v-loading="loading">
    <div class="authorization-heading">
      <div>
        <span class="authorization-kicker">ROLE POLICY</span>
        <h3>按职责组合权限策略</h3>
        <p>角色承载一组稳定权限，用户只需要分配角色，不直接维护零散权限。</p>
      </div>
      <el-tag type="info" effect="plain">{{ allRoles.length }} 个角色 · {{ allPermissions.length }} 项权限</el-tag>
    </div>

    <div class="authorization-workspace">
      <aside class="role-rail">
        <div class="role-rail-title">选择角色</div>
        <button
          v-for="role in allRoles"
          :key="role.id"
          class="role-option"
          :class="{ active: role.id === selectedRoleId }"
          @click="selectRole(role.id)"
        >
          <span class="role-avatar">{{ (role.name || '角').slice(0, 1) }}</span>
          <span class="role-option-copy"><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span>
          <el-icon><ArrowRight /></el-icon>
        </button>
      </aside>

      <main v-if="selectedRole" class="policy-panel">
        <div class="policy-summary">
          <div class="policy-title">
            <span class="policy-icon"><el-icon><Lock /></el-icon></span>
            <span><small>正在配置</small><strong>{{ selectedRole.name }}</strong><em>{{ selectedRole.description || '暂无职责说明' }}</em></span>
          </div>
          <div class="policy-coverage">
            <span><small>权限覆盖</small><strong>{{ checkedPermissions.length }} / {{ allPermissions.length }}</strong></span>
            <el-progress :percentage="coveragePercentage" :stroke-width="7" :show-text="false" />
          </div>
        </div>

        <div class="policy-toolbar">
          <span class="dirty-indicator" :class="{ dirty }"><i></i>{{ dirty ? '有未保存修改' : '策略已同步' }}</span>
          <div>
            <el-button size="small" @click="selectAll">全部授权</el-button>
            <el-button size="small" @click="clearAll">清空</el-button>
            <el-button size="small" :disabled="!dirty" @click="resetChanges">撤销修改</el-button>
          </div>
        </div>

        <div v-if="permissionGroups.length" class="permission-groups">
          <section v-for="group in permissionGroups" :key="group.key" class="permission-group-card">
            <div class="permission-group-header">
              <div><span class="group-icon"><el-icon><component :is="group.icon" /></el-icon></span><span><strong>{{ group.label }}</strong><small>{{ groupCheckedCount(group) }}/{{ group.permissions.length }} 已授权</small></span></div>
              <el-checkbox
                :model-value="isGroupChecked(group)"
                :indeterminate="isGroupIndeterminate(group)"
                @change="toggleGroup(group, $event)"
              >整组选取</el-checkbox>
            </div>
            <el-checkbox-group v-model="checkedPermissions" class="permission-options">
              <el-checkbox v-for="permission in group.permissions" :key="permission.id" :value="permission.id" class="permission-option">
                <span><strong>{{ permission.name }}</strong><small>{{ permission.code }}</small><em>{{ permission.description || '控制对应操作的访问权限' }}</em></span>
              </el-checkbox>
            </el-checkbox-group>
          </section>
        </div>
        <el-empty v-else description="当前没有可配置权限" />

        <div class="policy-save-bar">
          <span>保存后，所有拥有“{{ selectedRole.name }}”角色的用户将按新策略访问。</span>
          <el-button type="primary" :loading="saving" :disabled="!dirty" @click="saveRolePermissions">保存权限策略</el-button>
        </div>
      </main>
      <el-empty v-else class="policy-empty" description="暂无可配置角色" />
    </div>
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
const savedPermissions = ref([]);
const groupDefinitions = [
  { key: "GLOBAL", label: "全局控制", icon: "Platform" },
  { key: "USER", label: "用户与账号", icon: "User" },
  { key: "ROLE", label: "角色与授权", icon: "Lock" },
  { key: "PERMISSION", label: "权限目录", icon: "Key" },
  { key: "AUTH", label: "授权策略", icon: "Connection" },
  { key: "PROBLEM", label: "题目内容", icon: "Document" },
  { key: "TAG", label: "标签内容", icon: "CollectionTag" },
  { key: "CONTEST", label: "赛事内容", icon: "Trophy" },
  { key: "SUBMISSION", label: "提交作品", icon: "Upload" },
  { key: "FILE", label: "文件能力", icon: "FolderOpened" },
  { key: "AI", label: "AI 能力", icon: "Cpu" },
];

const selectedRole = computed(() => allRoles.value.find((role) => role.id === selectedRoleId.value));
const dirty = computed(() => normalizeIds(checkedPermissions.value) !== normalizeIds(savedPermissions.value));
const coveragePercentage = computed(() => allPermissions.value.length
  ? Math.round(checkedPermissions.value.length / allPermissions.value.length * 100)
  : 0);
const permissionGroups = computed(() => {
  const assigned = new Set();
  const groups = groupDefinitions.map((definition) => {
    const permissions = allPermissions.value.filter((permission) => {
      const code = String(permission.code || "").toUpperCase();
      const matches = code === definition.key || code.startsWith(`${definition.key}_`);
      if (matches) assigned.add(permission.id);
      return matches;
    });
    return { ...definition, permissions };
  }).filter((group) => group.permissions.length);
  const remaining = allPermissions.value.filter((permission) => !assigned.has(permission.id));
  if (remaining.length) groups.push({ key: "OTHER", label: "其他能力", icon: "MoreFilled", permissions: remaining });
  return groups;
});

function normalizeIds(values) { return [...values].map(String).sort().join(","); }
function groupCheckedCount(group) { return group.permissions.filter((item) => checkedPermissions.value.includes(item.id)).length; }
function isGroupChecked(group) { return group.permissions.length > 0 && groupCheckedCount(group) === group.permissions.length; }
function isGroupIndeterminate(group) { const count = groupCheckedCount(group); return count > 0 && count < group.permissions.length; }
function toggleGroup(group, value) {
  const ids = group.permissions.map((item) => item.id);
  if (value) checkedPermissions.value = [...new Set([...checkedPermissions.value, ...ids])];
  else checkedPermissions.value = checkedPermissions.value.filter((id) => !ids.includes(id));
}
function selectAll() { checkedPermissions.value = allPermissions.value.map((item) => item.id); }
function clearAll() { checkedPermissions.value = []; }
function resetChanges() { checkedPermissions.value = [...savedPermissions.value]; }

async function selectRole(roleId) {
  selectedRoleId.value = roleId;
  checkedPermissions.value = [];
  savedPermissions.value = [];
  if (!roleId) return;
  try {
    const response = await getRolePermissions(roleId);
    const ids = (response.data || []).map((permission) => permission.id);
    checkedPermissions.value = [...ids];
    savedPermissions.value = [...ids];
  } catch (error) {
    ElMessage.error(error.message || "角色权限加载失败");
  }
}

async function saveRolePermissions() {
  if (!selectedRoleId.value || !dirty.value) return;
  saving.value = true;
  try {
    await assignRolePermissions(selectedRoleId.value, checkedPermissions.value);
    savedPermissions.value = [...checkedPermissions.value];
    ElMessage.success(`“${selectedRole.value.name}”权限策略已保存`);
  } catch (error) {
    ElMessage.error(error.message || "权限策略保存失败");
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  try {
    const [roleResponse, permissionResponse] = await Promise.all([getRoleList(), getPermissionList()]);
    allRoles.value = roleResponse.data || [];
    allPermissions.value = permissionResponse.data || [];
    if (allRoles.value.length) await selectRole(allRoles.value[0].id);
  } catch (error) {
    ElMessage.error(error.message || "授权数据加载失败");
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.authorization-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.authorization-kicker { color: var(--lm-primary); font-size: 9px; font-weight: 800; letter-spacing: 1.2px; }
.authorization-heading h3 { margin: 3px 0 0; color: var(--lm-text-primary); font-size: 19px; }
.authorization-heading p { margin: 5px 0 0; color: var(--lm-text-muted); font-size: 12px; }
.authorization-workspace { display: grid; grid-template-columns: 210px minmax(0, 1fr); min-height: 520px; overflow: hidden; background: var(--lm-surface); border: 1px solid var(--lm-border); border-radius: 14px; }
.role-rail { padding: 16px 12px; background: #f1f5f9; border-right: 1px solid var(--lm-border); }
.role-rail-title { padding: 0 8px 9px; color: var(--lm-text-muted); font-size: 10px; font-weight: 700; letter-spacing: .8px; }
.role-option { display: flex; width: 100%; align-items: center; gap: 9px; margin-bottom: 6px; padding: 10px; color: var(--lm-text-secondary); background: transparent; border: 1px solid transparent; border-radius: 10px; cursor: pointer; text-align: left; }
.role-option:hover { background: rgba(255,255,255,.75); }
.role-option.active { color: var(--lm-primary); background: #fff; border-color: #bfdbfe; box-shadow: var(--lm-shadow-xs); }
.role-avatar { display: grid; width: 30px; height: 30px; flex: 0 0 30px; place-items: center; color: #475569; background: #e2e8f0; border-radius: 8px; font-size: 12px; font-weight: 700; }
.role-option.active .role-avatar { color: #fff; background: var(--lm-primary); }
.role-option-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.role-option-copy strong { color: var(--lm-text-primary); font-size: 12px; }
.role-option-copy small { margin-top: 2px; color: var(--lm-text-muted); font-size: 9px; }
.policy-panel { min-width: 0; padding: 20px; }
.policy-summary { display: grid; grid-template-columns: minmax(0, 1fr) minmax(180px, 260px); align-items: center; gap: 24px; padding-bottom: 18px; border-bottom: 1px solid var(--lm-border); }
.policy-title { display: flex; align-items: center; gap: 12px; }
.policy-icon { display: grid; width: 42px; height: 42px; place-items: center; color: var(--lm-primary); background: var(--lm-primary-bg); border-radius: 11px; }
.policy-title > span:last-child, .policy-coverage > span { display: flex; min-width: 0; flex-direction: column; }
.policy-title small, .policy-coverage small { color: var(--lm-text-muted); font-size: 9px; }
.policy-title strong { color: var(--lm-text-primary); font-size: 16px; }
.policy-title em { margin-top: 2px; overflow: hidden; color: var(--lm-text-muted); font-size: 10px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.policy-coverage strong { margin-top: 2px; color: var(--lm-text-primary); font-size: 16px; }
.policy-coverage :deep(.el-progress) { margin-top: 7px; }
.policy-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 0; }
.dirty-indicator { display: inline-flex; align-items: center; gap: 7px; color: var(--lm-text-muted); font-size: 10px; }
.dirty-indicator i { width: 7px; height: 7px; background: var(--lm-success); border-radius: 50%; }
.dirty-indicator.dirty { color: var(--lm-warning); }
.dirty-indicator.dirty i { background: var(--lm-warning); }
.permission-groups { display: grid; gap: 10px; }
.permission-group-card { padding: 14px; border: 1px solid var(--lm-border); border-radius: 11px; }
.permission-group-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 10px; border-bottom: 1px dashed var(--lm-border); }
.permission-group-header > div { display: flex; align-items: center; gap: 9px; }
.group-icon { display: grid; width: 30px; height: 30px; place-items: center; color: var(--lm-primary); background: var(--lm-primary-bg); border-radius: 8px; }
.permission-group-header > div > span:last-child { display: flex; flex-direction: column; }
.permission-group-header strong { color: var(--lm-text-primary); font-size: 12px; }
.permission-group-header small { color: var(--lm-text-muted); font-size: 9px; }
.permission-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; padding-top: 10px; }
.permission-option { height: auto; margin: 0; padding: 10px; align-items: flex-start; background: #f8fafc; border-radius: 8px; }
.permission-option :deep(.el-checkbox__input) { margin-top: 2px; }
.permission-option :deep(.el-checkbox__label) { min-width: 0; flex: 1; white-space: normal; }
.permission-option span { display: flex; min-width: 0; flex-direction: column; }
.permission-option strong { color: var(--lm-text-primary); font-size: 11px; }
.permission-option small { margin-top: 2px; color: var(--lm-primary); font-size: 9px; }
.permission-option em { margin-top: 3px; overflow: hidden; color: var(--lm-text-muted); font-size: 9px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.policy-save-bar { position: sticky; bottom: -20px; display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 18px -20px -20px; padding: 14px 20px; background: rgba(255,255,255,.96); border-top: 1px solid var(--lm-border); }
.policy-save-bar span { color: var(--lm-text-muted); font-size: 10px; }
.policy-empty { margin: auto; }
@media (max-width: 760px) { .authorization-workspace { grid-template-columns: 1fr; } .role-rail { display: flex; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--lm-border); } .role-rail-title { display: none; } .role-option { min-width: 150px; margin: 0 6px 0 0; } .policy-summary { grid-template-columns: 1fr; } .permission-options { grid-template-columns: 1fr; } }
</style>
