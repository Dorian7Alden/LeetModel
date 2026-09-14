<template>
  <div class="catalog-console">
    <div class="catalog-toolbar">
      <el-input v-model="keyword" placeholder="权限名称 / 编码" clearable class="catalog-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div>
        <el-button :loading="loading" @click="loadPermissions"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增权限</el-button>
      </div>
    </div>

    <AdminStatePanel
      v-if="loadError && !permissions.length"
      type="error"
      title="权限目录加载失败"
      action-label="重新加载"
      @action="loadPermissions"
    />

    <template v-else>
      <div v-if="loadError" class="inline-warning" role="alert">
        <el-icon><WarningFilled /></el-icon>
        刷新失败，当前保留上次取得的数据
      </div>
      <div class="catalog-table-scroll">
        <el-table :data="filteredPermissions" v-loading="loading" row-key="id" table-layout="fixed">
          <el-table-column label="权限" min-width="240">
            <template #default="{ row }">
              <div class="catalog-identity">
                <span class="identity-mark"><el-icon><Key /></el-icon></span>
                <span><strong>{{ row.name }}</strong><code>{{ row.code }}</code></span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="业务域" width="112">
            <template #default="{ row }"><span class="domain-label">{{ permissionDomain(row.code) }}</span></template>
          </el-table-column>
          <el-table-column label="关联角色" width="106" align="right">
            <template #default="{ row }">{{ formatCount(row.roleCount) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="作用说明" min-width="260" show-overflow-tooltip>
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
                      <el-dropdown-item command="delete" divided :disabled="Number(row.roleCount) > 0">删除权限</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty">{{ keyword ? '没有符合条件的权限' : '暂无权限' }}</div></template>
        </el-table>
      </div>
    </template>

    <el-drawer v-model="editorVisible" :title="editorMode === 'create' ? '新增权限' : '编辑权限'" size="min(480px, 94vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" maxlength="64" show-word-limit placeholder="例如：查看审计记录" />
        </el-form-item>
        <el-form-item label="权限编码" prop="code">
          <el-input v-model="form.code" maxlength="64" show-word-limit placeholder="资源:动作，例如 audit:read" />
        </el-form-item>
        <el-form-item label="作用说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="128" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePermission">{{ editorMode === 'create' ? '创建' : '保存' }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { createPermission, deletePermission, getPermissionList, updatePermission } from "@/api/permission";
import AdminStatePanel from "../components/AdminStatePanel.vue";

const emit = defineEmits(["changed"]);
const loading = ref(false);
const loadError = ref(false);
const saving = ref(false);
const permissions = ref([]);
const keyword = ref("");
const editorVisible = ref(false);
const editorMode = ref("create");
const formRef = ref();
const form = reactive({ id: "", name: "", code: "", description: "" });
const codePattern = /^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$/;
const domainLabels = {
  user: "用户",
  role: "角色",
  permission: "权限",
  submission: "提交",
  suggestion: "建议",
  problem: "题目",
  contest: "赛事",
  tag: "标签",
  file: "文件",
  ai: "AI",
  audit: "审计",
};
const rules = {
  name: [{ required: true, message: "请输入权限名称", trigger: "blur" }],
  code: [
    { required: true, message: "请输入权限编码", trigger: "blur" },
    { pattern: codePattern, message: "使用资源:动作格式，例如 audit:read", trigger: "blur" },
  ],
};

const filteredPermissions = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return permissions.value;
  return permissions.value.filter((permission) => [permission.name, permission.code, permission.description]
    .some((field) => String(field || "").toLowerCase().includes(value)));
});

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

function formatCount(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString("zh-CN") : "未取得";
}

function permissionDomain(code) {
  const domain = String(code || "").split(":")[0].toLowerCase();
  return domainLabels[domain] || domain || "其他";
}

async function loadPermissions() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    permissions.value = (await getPermissionList()).data || [];
  } catch (error) {
    loadError.value = true;
    if (permissions.value.length) ElMessage.error(error.message || "权限目录刷新失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editorMode.value = "create";
  Object.assign(form, { id: "", name: "", code: "", description: "" });
  editorVisible.value = true;
}

function openEdit(permission) {
  editorMode.value = "edit";
  Object.assign(form, {
    id: permission.id,
    name: permission.name || "",
    code: permission.code || "",
    description: permission.description || "",
  });
  editorVisible.value = true;
}

async function savePermission() {
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
    if (editorMode.value === "create") await createPermission(payload);
    else await updatePermission(form.id, payload);
    ElMessage.success(editorMode.value === "create" ? "权限已创建" : "权限已更新");
    editorVisible.value = false;
    await loadPermissions();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "权限保存失败");
  } finally {
    saving.value = false;
  }
}

async function removePermission(permission) {
  if (Number(permission.roleCount) > 0) return;
  const impact = Number.isFinite(Number(permission.roleCount))
    ? `${formatCount(permission.roleCount)} 个角色`
    : "角色影响范围未取得";
  try {
    await ElMessageBox.confirm(
      `“${permission.name}”当前关联 ${impact}。删除后不可恢复。`,
      "删除权限？",
      { type: "warning", confirmButtonText: "删除权限", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await deletePermission(permission.id);
    ElMessage.success("权限已删除");
    await loadPermissions();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "权限删除失败");
  }
}

async function copyCode(value) {
  try {
    await navigator.clipboard.writeText(String(value || ""));
    ElMessage.success("权限编码已复制");
  } catch {
    ElMessage.error("复制失败，请手动复制");
  }
}

function handleCommand(command, permission) {
  if (command === "copy-code") copyCode(permission.code);
  if (command === "delete") removePermission(permission);
}

onMounted(loadPermissions);
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
  min-width: 860px;
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

.domain-label {
  display: inline-flex;
  padding: 3px 7px;
  color: var(--lm-admin-info);
  background: var(--lm-admin-surface-subtle);
  border-radius: var(--lm-admin-radius-control);
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
