<template>
  <div class="tag-console">
    <div class="tag-toolbar">
      <div class="tag-filters">
        <el-input v-model="keyword" placeholder="标签名称" clearable class="tag-search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="typeFilter" placeholder="全部类型" clearable class="type-select">
          <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
      <div class="toolbar-actions">
        <span class="result-count">{{ filteredTags.length }} 项</span>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增标签</el-button>
      </div>
    </div>

    <AdminStatePanel
      v-if="loadError && !tags.length"
      type="error"
      title="标签列表加载失败"
      action-label="重新加载"
      @action="load"
    />

    <template v-else>
      <div v-if="loadError" class="inline-warning" role="alert">
        <el-icon><WarningFilled /></el-icon>刷新失败，当前保留上次取得的数据
      </div>
      <div class="table-scroll">
        <el-table :data="filteredTags" v-loading="loading" row-key="id" table-layout="fixed">
          <el-table-column label="标签" min-width="240">
            <template #default="{ row }">
              <div class="tag-identity">
                <span class="tag-mark"><el-icon><CollectionTag /></el-icon></span>
                <strong>{{ row.name }}</strong>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="150">
            <template #default="{ row }"><span class="type-label">{{ typeLabel(row.type) }}</span></template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="最近更新" width="160">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="112" fixed="right" align="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-dropdown trigger="click" @command="handleCommand($event, row)">
                  <button class="more-button" type="button" :aria-label="`${row.name} 更多操作`"><el-icon><MoreFilled /></el-icon></button>
                  <template #dropdown>
                    <el-dropdown-menu><el-dropdown-item command="delete">删除标签</el-dropdown-item></el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #empty><div class="table-empty">{{ hasFilters ? '没有符合条件的标签' : '暂无标签' }}</div></template>
        </el-table>
      </div>
    </template>

    <el-drawer v-model="editorVisible" :title="editingId ? '编辑标签' : '新增标签'" size="420px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" maxlength="50" show-word-limit /></el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存' : '创建' }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import { createAdminContentTag, deleteAdminContentTag, getAdminContentTags, updateAdminContentTag } from "@/api/problem";

const emit = defineEmits(["changed"]);
const tags = ref([]);
const loading = ref(false);
const loadError = ref(false);
const saving = ref(false);
const keyword = ref("");
const typeFilter = ref("");
const editorVisible = ref(false);
const editingId = ref(null);
const formRef = ref();
const form = reactive({ name: "", type: "MODEL_ALGORITHM" });
const typeOptions = [
  { label: "背景领域", value: "BACKGROUND_DOMAIN" },
  { label: "题目类型", value: "PROBLEM_TYPE" },
  { label: "模型算法", value: "MODEL_ALGORITHM" },
];
const rules = {
  name: [{ required: true, message: "请输入标签名称", trigger: "blur" }],
  type: [{ required: true, message: "请选择标签类型", trigger: "change" }],
};

const hasFilters = computed(() => Boolean(keyword.value.trim() || typeFilter.value));
const filteredTags = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return tags.value.filter((tag) => {
    const keywordMatched = !value || String(tag.name || "").toLowerCase().includes(value);
    return keywordMatched && (!typeFilter.value || tag.type === typeFilter.value);
  });
});

function typeLabel(value) {
  return typeOptions.find((item) => item.value === value)?.label || value || "—";
}

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    tags.value = (await getAdminContentTags()).data || [];
  } catch (error) {
    loadError.value = true;
    if (tags.value.length) ElMessage.error(error.message || "标签列表刷新失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, { name: "", type: "MODEL_ALGORITHM" });
  editorVisible.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, { name: row.name || "", type: row.type || "MODEL_ALGORITHM" });
  editorVisible.value = true;
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload = { name: form.name.trim(), type: form.type };
    if (editingId.value) await updateAdminContentTag(editingId.value, payload);
    else await createAdminContentTag(payload);
    ElMessage.success(editingId.value ? "标签已更新" : "标签已创建");
    editorVisible.value = false;
    await load();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "标签保存失败");
  } finally {
    saving.value = false;
  }
}

async function removeTag(row) {
  try {
    await ElMessageBox.confirm(
      `删除“${row.name}”后不可恢复；已被题目引用的标签会由系统拒绝删除。`,
      "删除标签？",
      { type: "warning", confirmButtonText: "删除标签", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await deleteAdminContentTag(row.id);
    ElMessage.success("标签已删除");
    await load();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "标签删除失败，可能仍被题目引用");
  }
}

function handleCommand(command, row) {
  if (command === "delete") removeTag(row);
}

onMounted(load);
</script>

<style scoped>
.tag-console { min-width: 0; padding: var(--lm-admin-space-3); }
.tag-toolbar, .tag-filters, .toolbar-actions, .tag-identity, .row-actions, .inline-warning { display: flex; align-items: center; }
.tag-toolbar { justify-content: space-between; gap: var(--lm-admin-space-3); margin-bottom: var(--lm-admin-space-3); }
.tag-filters, .toolbar-actions, .row-actions { gap: var(--lm-admin-space-2); }
.tag-search { width: 280px; }
.type-select { width: 150px; }
.result-count { color: var(--lm-admin-text-muted); font-size: 12px; }
.table-scroll { min-width: 0; overflow-x: auto; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.table-scroll :deep(.el-table) { min-width: 820px; }
.tag-identity { min-width: 0; gap: 10px; }
.tag-mark { display: grid; width: 28px; height: 28px; flex: 0 0 28px; place-items: center; color: var(--lm-admin-primary); background: #eaf2ff; border-radius: var(--lm-admin-radius-control); }
.tag-identity strong { overflow: hidden; color: var(--lm-admin-text-strong); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.type-label { display: inline-flex; padding: 2px 7px; color: var(--lm-admin-info); background: var(--lm-admin-surface-subtle); border-radius: 10px; font-size: 11px; }
.more-button { display: grid; width: 28px; height: 28px; place-items: center; color: var(--lm-admin-text-muted); background: transparent; border: 0; border-radius: var(--lm-admin-radius-control); cursor: pointer; }
.more-button:hover { color: var(--lm-admin-text-strong); background: var(--lm-admin-surface-subtle); }
.inline-warning { gap: 6px; margin-bottom: 8px; padding: 8px 10px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: var(--lm-admin-radius-control); font-size: 12px; }
.table-empty { padding: 34px 0; color: var(--lm-admin-text-muted); font-size: 12px; }
</style>
