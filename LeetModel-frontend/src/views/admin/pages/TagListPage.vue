<template>
  <div class="tag-console">
    <div class="tag-toolbar">
      <div class="tag-filters">
        <el-input v-model="keyword" placeholder="搜索标签名称..." clearable class="tag-search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="typeFilter" placeholder="全部分类" clearable class="type-select" popper-class="admin-filter-popper">
          <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <div class="category-summary-chips">
          <span
            v-for="item in typeOptions"
            :key="item.value"
            class="summary-chip"
            :class="[item.styleKey, { active: typeFilter === item.value }]"
            @click="toggleTypeFilter(item.value)"
          >
            <span class="chip-dot"></span>
            <span class="chip-name">{{ item.label }}</span>
            <span class="chip-num">{{ getTypeCount(item.value) }}</span>
          </span>
        </div>
      </div>
      <div class="toolbar-actions">
        <span class="result-count">共 {{ totalFilteredCount }} 个标签</span>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="openCreate()">
          <el-icon><Plus /></el-icon>
          <span>新增标签</span>
        </el-button>
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
      <div v-loading="loading" class="tag-groups-container">
        <div v-if="groupedTags.length" class="tag-sections">
          <section
            v-for="group in groupedTags"
            :key="group.type"
            class="tag-group-card"
            :class="group.styleKey"
          >
            <!-- 分组头部 -->
            <div class="tag-group-header">
              <div class="group-title">
                <span class="group-badge-icon">
                  <el-icon><component :is="group.icon" /></el-icon>
                </span>
                <h3 class="group-name">{{ group.label }}</h3>
                <span class="group-count-badge">{{ group.tags.length }}</span>
                <span class="group-desc-subtle">{{ group.description }}</span>
              </div>
              <el-button
                size="small"
                class="group-quick-add-btn"
                @click="openCreate(group.type)"
              >
                <el-icon><Plus /></el-icon>
                <span>添加{{ group.label }}</span>
              </el-button>
            </div>

            <!-- 分组内容：高密度优雅标签矩阵 -->
            <div class="tag-chips-matrix">
              <div
                v-for="tag in group.tags"
                :key="tag.id"
                class="tag-card-chip"
              >
                <el-tooltip
                  placement="top"
                  :show-after="300"
                  popper-class="admin-menu-tooltip"
                >
                  <template #content>
                    <div class="tag-tooltip-content">
                      <div class="tip-header">
                        <strong>{{ tag.name }}</strong>
                        <span class="tip-count-tag" :class="{ unused: !tag.problemCount }">
                          {{ tag.problemCount ? `${tag.problemCount} 题引用` : '暂无引用（可安全删除）' }}
                        </span>
                      </div>
                      <div class="tip-sub">创建时间：{{ formatTime(tag.createTime) }}</div>
                      <div class="tip-sub">最近更新：{{ formatTime(tag.updateTime) }}</div>
                    </div>
                  </template>
                  <div class="chip-main">
                    <span class="chip-lead-dot"></span>
                    <span class="chip-text">{{ tag.name }}</span>
                    <span
                      class="chip-usage-badge"
                      :class="{ 'is-zero': !tag.problemCount }"
                      :title="`${tag.problemCount || 0} 道题目引用`"
                    >
                      {{ tag.problemCount || 0 }}
                    </span>
                  </div>
                </el-tooltip>

                <!-- 操作区（Hover 或 Focus 显现） -->
                <div class="chip-actions">
                  <button
                    type="button"
                    class="chip-action-btn edit"
                    :aria-label="`编辑标签 ${tag.name}`"
                    title="编辑"
                    @click.stop="openEdit(tag)"
                  >
                    <el-icon><Edit /></el-icon>
                  </button>
                  <button
                    type="button"
                    class="chip-action-btn delete"
                    :aria-label="`删除标签 ${tag.name}`"
                    title="删除"
                    @click.stop="removeTag(tag)"
                  >
                    <el-icon><Close /></el-icon>
                  </button>
                </div>
              </div>

              <div v-if="!group.tags.length" class="group-empty-hint">
                暂无匹配的{{ group.label }}
              </div>
            </div>
          </section>
        </div>
        <div v-else-if="!loading" class="empty-state">
          <div class="table-empty">{{ hasFilters ? "没有符合条件的标签" : "暂无标签" }}</div>
        </div>
      </div>
    </template>

    <!-- 录入与编辑对话框 -->
    <el-dialog
      v-model="editorVisible"
      :title="editingId ? '编辑标签' : '新增知识标签'"
      width="440px"
      append-to-body
      destroy-on-close
      class="tag-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
        <el-form-item label="所属业务分类" prop="type">
          <el-select v-model="form.type" style="width: 100%" popper-class="admin-filter-popper">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="如 运筹优化 / 机器学习 / 动态规划"
            maxlength="50"
            show-word-limit
            autofocus
            @keyup.enter="save"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editorVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">
            {{ editingId ? "保存修改" : "确认创建" }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Plus, Refresh, WarningFilled, Edit, Close, CollectionTag, Coordinate, Cpu } from "@element-plus/icons-vue";
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
  {
    label: "背景领域",
    value: "BACKGROUND_DOMAIN",
    styleKey: "type-domain",
    icon: "Coordinate",
    description: "问题所属学科背景与行业应用领域，如医疗、金融、交通等",
  },
  {
    label: "题目类型",
    value: "PROBLEM_TYPE",
    styleKey: "type-problem",
    icon: "CollectionTag",
    description: "数学建模题型归属，如机理分析、预测评估、优化调度等",
  },
  {
    label: "模型算法",
    value: "MODEL_ALGORITHM",
    styleKey: "type-algo",
    icon: "Cpu",
    description: "赛题核心建模与求解算法，如微分方程、神经网络、蒙特卡洛等",
  },
];
const rules = {
  name: [{ required: true, message: "请输入标签名称", trigger: "blur" }],
  type: [{ required: true, message: "请选择标签类型", trigger: "change" }],
};

const hasFilters = computed(() => Boolean(keyword.value.trim() || typeFilter.value));
const totalFilteredCount = computed(() => {
  return groupedTags.value.reduce((acc, group) => acc + group.tags.length, 0);
});

function getTypeCount(typeValue) {
  return tags.value.filter((tag) => tag.type === typeValue).length;
}

function toggleTypeFilter(typeValue) {
  typeFilter.value = typeFilter.value === typeValue ? "" : typeValue;
}

const groupedTags = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  const filtered = tags.value.filter((tag) => {
    const keywordMatched = !value || String(tag.name || "").toLowerCase().includes(value);
    return keywordMatched && (!typeFilter.value || tag.type === typeFilter.value);
  });

  const groupMap = new Map();
  typeOptions.forEach((item) => {
    groupMap.set(item.value, {
      type: item.value,
      label: item.label,
      styleKey: item.styleKey,
      icon: item.icon,
      description: item.description,
      tags: [],
    });
  });

  filtered.forEach((tag) => {
    const typeKey = tag.type || "OTHER";
    if (!groupMap.has(typeKey)) {
      groupMap.set(typeKey, {
        type: typeKey,
        label: typeLabel(typeKey),
        styleKey: "type-domain",
        icon: "CollectionTag",
        description: "",
        tags: [],
      });
    }
    groupMap.get(typeKey).tags.push(tag);
  });

  return Array.from(groupMap.values()).filter((group) => group.tags.length > 0);
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

function openCreate(defaultType = "MODEL_ALGORITHM") {
  editingId.value = null;
  const type = typeof defaultType === "string" && typeOptions.some((item) => item.value === defaultType)
    ? defaultType
    : "MODEL_ALGORITHM";
  Object.assign(form, { name: "", type });
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

onMounted(load);
</script>

<style scoped>
.tag-console {
  min-width: 0;
  padding: var(--lm-admin-space-3);
}

/* 顶部工具条 */
.tag-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lm-admin-space-3);
  margin-bottom: var(--lm-admin-space-3);
  padding: 10px 14px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}

.tag-filters {
  display: flex;
  align-items: center;
  gap: var(--lm-admin-space-2);
  flex-wrap: wrap;
}

.tag-search {
  width: 220px;
}

.type-select {
  width: 130px;
}

/* 分类快捷统计 Chip */
.category-summary-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: 4px;
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  font-size: 11.5px;
  border-radius: 14px;
  border: 1px solid var(--lm-admin-border);
  background: var(--lm-admin-surface-subtle);
  color: var(--lm-admin-text-default);
  cursor: pointer;
  user-select: none;
  transition: all var(--lm-admin-transition-fast);
}

.summary-chip .chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.summary-chip.type-domain .chip-dot { background: #3b82f6; }
.summary-chip.type-problem .chip-dot { background: #8b5cf6; }
.summary-chip.type-algo .chip-dot { background: #10b981; }

.summary-chip .chip-num {
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-admin-text-muted);
  background: rgba(0, 0, 0, 0.04);
  padding: 0 5px;
  border-radius: 8px;
}

.summary-chip:hover {
  border-color: #cbd5e1;
  background: #ffffff;
}

.summary-chip.active {
  background: #ffffff;
  border-color: var(--lm-admin-primary);
  color: var(--lm-admin-primary);
  box-shadow: 0 1px 4px rgba(37, 99, 235, 0.12);
}

.summary-chip.active .chip-num {
  color: var(--lm-admin-primary);
  background: #eff6ff;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--lm-admin-space-2);
  flex-shrink: 0;
}

.result-count {
  color: var(--lm-admin-text-muted);
  font-size: 12px;
}

/* 分组工作板区域 */
.tag-groups-container {
  min-width: 0;
}

.tag-sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 分类工作卡片 */
.tag-group-card {
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
  transition: border-color var(--lm-admin-transition);
}

.tag-group-card:hover {
  border-color: #cbd5e1;
}

/* 分组卡片头部 */
.tag-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #fafbfc;
  border-bottom: 1px solid var(--lm-admin-border);
}

.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.group-badge-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  font-size: 13px;
}

.tag-group-card.type-domain .group-badge-icon {
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #dbeafe;
}
.tag-group-card.type-problem .group-badge-icon {
  color: #7c3aed;
  background: #f5f3ff;
  border: 1px solid #ede9fe;
}
.tag-group-card.type-algo .group-badge-icon {
  color: #059669;
  background: #ecfdf5;
  border: 1px solid #d1fae5;
}

.group-name {
  margin: 0;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--lm-admin-text-strong);
}

.group-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-admin-text-muted);
  background: var(--lm-admin-surface-subtle);
  border-radius: 9px;
}

.group-desc-subtle {
  font-size: 11.5px;
  color: #94a3b8;
  margin-left: 4px;
}

.group-quick-add-btn {
  font-size: 11.5px;
  height: 26px;
  padding: 0 8px;
  border-radius: 4px;
}

/* 分类内容：标签矩阵 */
.tag-chips-matrix {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  min-height: 52px;
}

/* 单个标签 Chip */
.tag-card-chip {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 8px 0 10px;
  background: #ffffff;
  border: 1px solid var(--lm-admin-border);
  border-radius: 6px;
  transition: all var(--lm-admin-transition-fast);
  user-select: none;
}

.tag-card-chip:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.chip-main {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: default;
}

.chip-lead-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #94a3b8;
  transition: background var(--lm-admin-transition-fast);
}

.tag-group-card.type-domain .tag-card-chip:hover .chip-lead-dot { background: #3b82f6; }
.tag-group-card.type-problem .tag-card-chip:hover .chip-lead-dot { background: #8b5cf6; }
.tag-group-card.type-algo .tag-card-chip:hover .chip-lead-dot { background: #10b981; }

.chip-text {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--lm-admin-text-strong);
  white-space: nowrap;
  margin-right: 4px;
}

.chip-usage-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 9px;
  color: #475569;
  background: #f1f5f9;
  margin-left: 2px;
  margin-right: 2px;
  line-height: 1;
  transition: all var(--lm-admin-transition-fast);
}

.tag-card-chip:hover .chip-usage-badge {
  background: #e2e8f0;
  color: #1e293b;
}

.chip-usage-badge.is-zero {
  color: #94a3b8;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}

.tip-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.tip-count-tag {
  font-size: 10.5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1e40af;
  font-weight: 600;
}

.tip-count-tag.unused {
  background: #fef2f2;
  color: #b91c1c;
}

/* 操作图标区 */
.chip-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: 2px;
  opacity: 0.45;
  transition: opacity var(--lm-admin-transition-fast);
}

.tag-card-chip:hover .chip-actions {
  opacity: 1;
}

.chip-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  background: transparent;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
  color: var(--lm-admin-text-muted);
  transition: all var(--lm-admin-transition-fast);
}

.chip-action-btn.edit:hover {
  color: var(--lm-admin-primary);
  background: #eff6ff;
}

.chip-action-btn.delete:hover {
  color: #ef4444;
  background: #fef2f2;
}

.group-empty-hint {
  font-size: 12px;
  color: var(--lm-admin-text-muted);
  padding: 8px 0;
}

.tag-tooltip-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
}
.tip-sub {
  color: #64748b;
}

.empty-state {
  border: 1px dashed var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  background: var(--lm-admin-surface);
  text-align: center;
  padding: 40px 0;
}

.inline-warning {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 8px 10px;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: var(--lm-admin-radius-control);
  font-size: 12px;
}

.table-empty {
  color: var(--lm-admin-text-muted);
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
