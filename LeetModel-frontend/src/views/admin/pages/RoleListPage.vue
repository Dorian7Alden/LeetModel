<template>
  <div class="catalog-console">
    <div class="catalog-toolbar">
      <el-input v-model="keyword" placeholder="角色名称 / 编码" clearable class="catalog-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div>
        <el-button :loading="loading" @click="loadRoles"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增角色</el-button>
      </div>
    </div>

    <AdminStatePanel
      v-if="loadError && !roles.length"
      type="error"
      title="角色列表加载失败"
      action-label="重新加载"
      @action="loadRoles"
    />

    <template v-else>
      <div v-if="loadError" class="inline-warning" role="alert">
        <el-icon><WarningFilled /></el-icon>
        刷新失败，当前保留上次取得的数据
      </div>
      <div class="catalog-table-scroll">
        <el-table :data="filteredRoles" v-loading="loading" row-key="id" table-layout="fixed">
          <el-table-column label="角色" min-width="210">
            <template #default="{ row }">
              <div class="catalog-identity">
                <span class="identity-mark"><el-icon><UserFilled /></el-icon></span>
                <span><strong>{{ row.name }}</strong><code>{{ row.code }}</code></span>
                <AdminStatusBadge v-if="row.system" status="PROTECTED" label="系统" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="关联用户" width="106" align="right">
            <template #default="{ row }">{{ formatCount(row.userCount) }}</template>
          </el-table-column>
          <el-table-column label="权限" width="88" align="right">
            <template #default="{ row }">{{ formatCount(row.permissionCount) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="职责说明" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '—' }}</template>
          </el-table-column>
          <el-table-column label="最近更新" width="150">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right" align="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-dropdown trigger="click" @command="handleCommand($event, row)">
                  <button class="more-button" type="button" :aria-label="`${row.name} 更多操作`"><el-icon><MoreFilled /></el-icon></button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="copy-code">复制编码</el-dropdown-item>
                      <el-dropdown-item command="delete" divided :disabled="row.system">删除角色</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty">{{ keyword ? '没有符合条件的角色' : '暂无角色' }}</div></template>
        </el-table>
      </div>
    </template>

    <el-drawer v-model="editorVisible" :title="editorMode === 'create' ? '新增角色' : '编辑角色'" size="min(480px, 94vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" maxlength="32" show-word-limit placeholder="例如：内容审核员" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" maxlength="32" show-word-limit placeholder="例如：content-reviewer" :disabled="form.system" />
        </el-form-item>
        <el-form-item label="职责说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="128" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">{{ editorMode === 'create' ? '创建' : '保存' }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { createRole, deleteRole, getRoleList, updateRole } from "@/api/role";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";

const emit = defineEmits(["changed"]);
const loading = ref(false);
const loadError = ref(false);
const saving = ref(false);
const roles = ref([]);
const keyword = ref("");
const editorVisible = ref(false);
const editorMode = ref("create");
const formRef = ref();
const form = reactive({ id: "", name: "", code: "", description: "", system: false });
const rules = {
  name: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
  code: [{ required: true, message: "请输入角色编码", trigger: "blur" }],
};

const filteredRoles = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return roles.value;
  return roles.value.filter((role) => [role.name, role.code, role.description]
    .some((field) => String(field || "").toLowerCase().includes(value)));
});

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

function formatCount(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString("zh-CN") : "未取得";
}

async function loadRoles() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    roles.value = (await getRoleList()).data || [];
  } catch (error) {
    loadError.value = true;
    if (roles.value.length) ElMessage.error(error.message || "角色列表刷新失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editorMode.value = "create";
  Object.assign(form, { id: "", name: "", code: "", description: "", system: false });
  editorVisible.value = true;
}

function openEdit(role) {
  editorMode.value = "edit";
  Object.assign(form, {
    id: role.id,
    name: role.name || "",
    code: role.code || "",
    description: role.description || "",
    system: Boolean(role.system),
  });
  editorVisible.value = true;
}

async function saveRole() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  const payload = {
    name: form.name.trim(),
    code: form.code.trim().toLowerCase(),
    description: form.description.trim(),
  };
  try {
    if (editorMode.value === "create") await createRole(payload);
    else await updateRole(form.id, payload);
    ElMessage.success(editorMode.value === "create" ? "角色已创建" : "角色已更新");
    editorVisible.value = false;
    await loadRoles();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "角色保存失败");
  } finally {
    saving.value = false;
  }
}

async function removeRole(role) {
  if (role.system) return;
  const userImpact = Number.isFinite(Number(role.userCount)) ? `${formatCount(role.userCount)} 位用户` : "用户影响范围未取得";
  const permissionImpact = Number.isFinite(Number(role.permissionCount)) ? `${formatCount(role.permissionCount)} 项权限关系` : "权限影响范围未取得";
  try {
    await ElMessageBox.confirm(
      `删除“${role.name}”将解除 ${userImpact}和 ${permissionImpact}，操作不可撤销。`,
      "删除角色？",
      { type: "warning", confirmButtonText: "删除角色", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await deleteRole(role.id);
    ElMessage.success("角色已删除");
    await loadRoles();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "角色删除失败");
  }
}

async function copyCode(value) {
  try {
    await navigator.clipboard.writeText(String(value || ""));
    ElMessage.success("角色编码已复制");
  } catch {
    ElMessage.error("复制失败，请手动复制");
  }
}

function handleCommand(command, role) {
  if (command === "copy-code") copyCode(role.code);
  if (command === "delete") removeRole(role);
}

onMounted(loadRoles);
</script>

<style scoped>
.catalog-console {
  min-width: 0;
  padding: var(--lm-admin-space-3);
}

.catalog-toolbar,
.catalog-toolbar > div,
.catalog-identity,
.row-actions,
.inline-warning {
  display: flex;
  align-items: center;
}

.catalog-toolbar {
  justify-content: space-between;
  gap: var(--lm-admin-space-3);
  margin-bottom: var(--lm-admin-space-3);
}

.catalog-toolbar > div {
  gap: var(--lm-admin-space-2);
}

.catalog-search {
  width: 300px;
}

.catalog-table-scroll {
  min-width: 0;
  overflow-x: auto;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.catalog-table-scroll :deep(.el-table) {
  min-width: 820px;
}

.catalog-table-scroll :deep(.el-table__header th) {
  height: 38px;
  color: var(--lm-admin-text-muted);
  background: var(--lm-admin-surface-subtle);
  font-size: 11px;
}

.catalog-table-scroll :deep(.el-table__cell) {
  padding: 8px 0;
}

.catalog-identity {
  min-width: 0;
  gap: 8px;
}

.identity-mark {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  color: var(--lm-admin-info);
  background: var(--lm-admin-surface-subtle);
  border-radius: var(--lm-admin-radius-control);
}

.catalog-identity > span:nth-child(2) {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.catalog-identity strong {
  overflow: hidden;
  color: var(--lm-admin-text-strong);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.catalog-identity code {
  overflow: hidden;
  margin-top: 2px;
  color: var(--lm-admin-text-muted);
  font-size: 10px;
  text-overflow: ellipsis;
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

.inline-warning {
  gap: 6px;
  margin-bottom: var(--lm-admin-space-2);
  padding: 8px 10px;
  color: var(--lm-admin-warning);
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: var(--lm-admin-radius-control);
  font-size: 11px;
}

.table-empty {
  padding: 36px 0;
  color: var(--lm-admin-text-muted);
  font-size: 12px;
}

.editor-form {
  max-width: 420px;
}

@media (max-width: 767px) {
  .catalog-console {
    padding: var(--lm-admin-space-2);
  }

  .catalog-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .catalog-search {
    width: auto;
  }

  .catalog-toolbar > div {
    justify-content: flex-end;
  }
}
</style>
