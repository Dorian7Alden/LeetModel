<template>
  <div class="contest-console">
    <div class="contest-toolbar">
      <el-input v-model="keyword" placeholder="搜索赛事名称 / 编码 / 规程" clearable class="contest-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="toolbar-actions">
        <span class="result-count">共 {{ filteredContests.length }} 项赛事</span>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增赛事</el-button>
      </div>
    </div>

    <AdminStatePanel
      v-if="loadError && !contests.length"
      type="error"
      title="赛事数据加载失败"
      action-label="重新加载"
      @action="load"
    />

    <template v-else>
      <div v-if="loadError" class="inline-warning" role="alert">
        <el-icon><WarningFilled /></el-icon>刷新失败，当前保留上次取得的数据
      </div>

      <!-- 赛事直观卡片网格呈现 -->
      <div v-if="filteredContests.length" class="contest-card-grid" v-loading="loading">
        <div
          v-for="contest in filteredContests"
          :key="contest.id"
          class="contest-card"
        >
          <!-- 卡片头部：编码徽章 + 名称 + 英文名 + 操作区 -->
          <div class="contest-card-header">
            <div class="contest-card-title-group">
              <div class="title-top-line">
                <span class="contest-code-pill">{{ contest.code }}</span>
                <strong class="contest-name" :title="contest.name">{{ contest.name }}</strong>
              </div>
              <span v-if="contest.englishName" class="contest-english-name" :title="contest.englishName">
                {{ contest.englishName }}
              </span>
            </div>
            <div class="header-action-group">
              <a
                v-if="contest.officialUrl"
                :href="contest.officialUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="official-site-btn"
                title="打开赛事官网"
              >
                <el-icon><TopRight /></el-icon>
              </a>
              <el-button
                type="primary"
                size="small"
                plain
                @click="openEdit(contest)"
              >
                <el-icon><Edit /></el-icon> 编辑
              </el-button>
              <el-button
                type="danger"
                size="small"
                plain
                @click="confirmDelete(contest)"
              >
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </div>
          </div>

          <!-- 卡片主体：四维核心参数网格 -->
          <div class="contest-card-body">
            <div class="contest-facts-grid">
              <div class="fact-item">
                <div class="fact-label">
                  <el-icon><Calendar /></el-icon>
                  <span>赛程时限</span>
                </div>
                <div class="fact-value" :title="contest.scheduleDesc || '未设定'">
                  {{ contest.scheduleDesc || '未设定' }}
                </div>
              </div>

              <div class="fact-item">
                <div class="fact-label">
                  <el-icon><UserFilled /></el-icon>
                  <span>队伍规程</span>
                </div>
                <div class="fact-value" :title="contest.teamRules || '未设定'">
                  {{ contest.teamRules || '未设定' }}
                </div>
              </div>

              <div class="fact-item">
                <div class="fact-label">
                  <el-icon><Document /></el-icon>
                  <span>赛题范式</span>
                </div>
                <div class="fact-value" :title="contest.problemSpec || '未设定'">
                  {{ contest.problemSpec || '未设定' }}
                </div>
              </div>

              <div class="fact-item">
                <div class="fact-label">
                  <el-icon><Files /></el-icon>
                  <span>成果交付</span>
                </div>
                <div class="fact-value" :title="contest.submissionSpec || '未设定'">
                  {{ contest.submissionSpec || '未设定' }}
                </div>
              </div>
            </div>

            <!-- 赛事概况 -->
            <div v-if="contest.description" class="contest-description-box" :title="contest.description">
              <p>{{ contest.description }}</p>
            </div>
          </div>

          <!-- 卡片底部：最近更新与域名 -->
          <div class="contest-card-footer">
            <span class="update-time-text">更新于 {{ formatTime(contest.updateTime || contest.createTime) }}</span>
            <span v-if="contest.officialUrl" class="domain-host-badge">
              {{ getDomainHost(contest.officialUrl) }}
            </span>
          </div>
        </div>
      </div>

      <AdminStatePanel
        v-else
        type="empty"
        :title="keyword ? '没有符合条件的赛事' : '暂无赛事数据'"
      />
    </template>

    <el-drawer v-model="editVisible" :title="isCreate ? '新增赛事' : '编辑赛事'" size="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="contest-form">
        <div class="form-grid">
          <el-form-item label="赛事编码" prop="code">
            <el-input v-model="form.code" placeholder="如 MCM_ICM, CUMCM" maxlength="32" />
          </el-form-item>
          <el-form-item label="赛事名称" prop="name">
            <el-input v-model="form.name" placeholder="如 全国大学生数学建模竞赛" maxlength="100" />
          </el-form-item>
        </div>
        <el-form-item label="英文全称" prop="englishName">
          <el-input v-model="form.englishName" placeholder="如 Contemporary Undergraduate Mathematical Contest in Modeling" maxlength="200" />
        </el-form-item>
        <el-form-item label="官方网址" prop="officialUrl">
          <el-input v-model="form.officialUrl" placeholder="如 http://www.mcm.edu.cn" maxlength="255" />
        </el-form-item>
        <el-form-item label="赛程时限" prop="scheduleDesc">
          <el-input v-model="form.scheduleDesc" placeholder="如 每年9月第二周，持续72小时" maxlength="100" />
        </el-form-item>
        <el-form-item label="队伍规程" prop="teamRules">
          <el-input v-model="form.teamRules" placeholder="如 本科专科每队至多3人，独立指导教师" maxlength="100" />
        </el-form-item>
        <el-form-item label="成果交付" prop="submissionSpec">
          <el-input v-model="form.submissionSpec" placeholder="如 承诺书、论文正文PDF及模型运行源码" maxlength="150" />
        </el-form-item>
        <el-form-item label="赛题范式" prop="problemSpec">
          <el-input v-model="form.problemSpec" placeholder="如 本科组A/B/C题，专科组D/E题" maxlength="150" />
        </el-form-item>
        <el-form-item label="赛事概况" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="简要介绍赛事的学术定位与创办背景..." maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ isCreate ? '立即创建' : '保存' }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { TopRight, Edit, Delete, Plus, Refresh, Calendar, UserFilled, Document, Files } from "@element-plus/icons-vue";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import {
  getAdminContentContests,
  createAdminContentContest,
  updateAdminContentContest,
  deleteAdminContentContest,
} from "@/api/problem";

const emit = defineEmits(["changed"]);
const contests = ref([]);
const loading = ref(false);
const loadError = ref(false);
const saving = ref(false);
const keyword = ref("");
const editVisible = ref(false);
const editingId = ref(null);
const isCreate = computed(() => !editingId.value);
const formRef = ref();
const form = reactive({
  code: "",
  name: "",
  englishName: "",
  scheduleDesc: "",
  teamRules: "",
  submissionSpec: "",
  problemSpec: "",
  description: "",
  officialUrl: "",
});

const rules = {
  code: [
    { required: true, message: "请输入赛事编码", trigger: "blur" },
    { pattern: /^[A-Za-z0-9_-]+$/, message: "只能使用字母、数字、下划线和短横线", trigger: "blur" },
  ],
  name: [{ required: true, message: "请输入赛事名称", trigger: "blur" }],
  officialUrl: [{ type: "url", message: "请输入完整网址", trigger: "blur" }],
};

const filteredContests = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return contests.value;
  return contests.value.filter((contest) =>
    [
      contest.code,
      contest.name,
      contest.englishName,
      contest.scheduleDesc,
      contest.teamRules,
      contest.problemSpec,
      contest.submissionSpec,
      contest.description,
    ].some((field) => String(field || "").toLowerCase().includes(value))
  );
});

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
}

function getDomainHost(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return "官网链接";
  }
}

async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    contests.value = (await getAdminContentContests()).data || [];
  } catch (error) {
    loadError.value = true;
    if (contests.value.length) ElMessage.error(error.message || "赛事数据刷新失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    code: "",
    name: "",
    englishName: "",
    scheduleDesc: "",
    teamRules: "",
    submissionSpec: "",
    problemSpec: "",
    description: "",
    officialUrl: "",
  });
  editVisible.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    code: row.code || "",
    name: row.name || "",
    englishName: row.englishName || "",
    scheduleDesc: row.scheduleDesc || "",
    teamRules: row.teamRules || "",
    submissionSpec: row.submissionSpec || "",
    problemSpec: row.problemSpec || "",
    description: row.description || "",
    officialUrl: row.officialUrl || "",
  });
  editVisible.value = true;
}

async function confirmDelete(contest) {
  try {
    await ElMessageBox.confirm(
      `确定要删除赛事【${contest.name}】（${contest.code}）吗？若该赛事已被题目引用，则系统将拒绝删除。`,
      "确认删除",
      {
        confirmButtonText: "确定删除",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
  } catch {
    return;
  }

  try {
    await deleteAdminContentContest(contest.id);
    ElMessage.success(`赛事【${contest.name}】已删除`);
    await load();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "删除赛事失败");
  }
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload = Object.fromEntries(
      Object.entries(form).map(([key, value]) => [key, value?.trim() || null])
    );
    if (isCreate.value) {
      await createAdminContentContest(payload);
      ElMessage.success("赛事已创建");
    } else {
      await updateAdminContentContest(editingId.value, payload);
      ElMessage.success("赛事已更新");
    }
    editVisible.value = false;
    await load();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || (isCreate.value ? "创建赛事失败" : "更新赛事失败"));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.contest-console {
  min-width: 0;
  padding: var(--lm-admin-space-3);
}
.contest-toolbar {
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
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--lm-admin-space-2);
}
.contest-search {
  width: 280px;
}
.result-count {
  color: var(--lm-admin-text-muted);
  font-size: 12px;
}

/* 赛事卡片网格 */
.contest-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
  min-width: 0;
}

.contest-card {
  display: flex;
  flex-direction: column;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
  overflow: hidden;
  transition: border-color var(--lm-admin-transition), box-shadow var(--lm-admin-transition);
}

.contest-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.05);
}

.contest-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--lm-admin-border);
  background: #fafbfc;
}

.contest-card-title-group {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.title-top-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.contest-code-pill {
  flex-shrink: 0;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.3px;
  color: #1e40af;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 4px;
}

.contest-name {
  font-size: 14.5px;
  font-weight: 700;
  color: var(--lm-admin-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contest-english-name {
  font-size: 11.5px;
  color: var(--lm-admin-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-action-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.official-site-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--lm-admin-text-muted);
  background: #ffffff;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  transition: all var(--lm-admin-transition-fast);
}

.official-site-btn:hover {
  color: var(--lm-admin-primary);
  border-color: #93c5fd;
  background: #eff6ff;
}

.contest-card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  flex: 1;
}

.contest-facts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 12px;
}

.fact-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.fact-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-admin-text-muted);
}

.fact-label .el-icon {
  font-size: 12px;
  color: #94a3b8;
}

.fact-value {
  font-size: 12px;
  color: var(--lm-admin-text-strong);
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contest-description-box {
  padding: 8px 10px;
  background: var(--lm-admin-surface-subtle);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}

.contest-description-box p {
  margin: 0;
  font-size: 11.5px;
  color: var(--lm-admin-text-default);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.contest-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-top: 1px solid var(--lm-admin-border);
  background: #fafbfc;
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}

.domain-host-badge {
  font-size: 10.5px;
  color: #64748b;
  font-weight: 500;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1.6fr;
  gap: 12px;
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
</style>
