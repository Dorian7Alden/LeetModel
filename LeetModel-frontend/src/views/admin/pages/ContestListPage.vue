<template>
  <div class="contest-console">
    <div class="contest-toolbar">
      <el-input v-model="keyword" placeholder="赛事名称 / 编码" clearable class="contest-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="toolbar-actions">
        <span class="result-count">{{ filteredContests.length }} 项预置赛事</span>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
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
      <div class="table-scroll">
        <el-table :data="filteredContests" v-loading="loading" row-key="id" table-layout="fixed">
          <el-table-column label="赛事" min-width="300">
            <template #default="{ row }">
              <div class="contest-identity">
                <code>{{ row.code }}</code>
                <span><strong>{{ row.name }}</strong><small>{{ row.englishName || '—' }}</small></span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="scheduleDesc" label="赛程" min-width="190" show-overflow-tooltip>
            <template #default="{ row }">{{ row.scheduleDesc || '—' }}</template>
          </el-table-column>
          <el-table-column prop="teamRules" label="队伍规程" min-width="210" show-overflow-tooltip>
            <template #default="{ row }">{{ row.teamRules || '—' }}</template>
          </el-table-column>
          <el-table-column label="官网" width="70" align="center">
            <template #default="{ row }">
              <a v-if="row.officialUrl" :href="row.officialUrl" target="_blank" rel="noopener noreferrer" class="external-link" aria-label="打开赛事官网"><el-icon><TopRight /></el-icon></a>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="最近更新" width="150">
            <template #default="{ row }">{{ formatTime(row.updateTime || row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right" align="right">
            <template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button></template>
          </el-table-column>
          <template #empty><div class="table-empty">{{ keyword ? '没有符合条件的赛事' : '暂无赛事数据' }}</div></template>
        </el-table>
      </div>
    </template>

    <el-drawer v-model="editVisible" title="编辑赛事" size="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="contest-form">
        <div class="form-grid">
          <el-form-item label="赛事编码" prop="code"><el-input v-model="form.code" maxlength="32" /></el-form-item>
          <el-form-item label="赛事名称" prop="name"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        </div>
        <el-form-item label="英文全称" prop="englishName"><el-input v-model="form.englishName" maxlength="200" /></el-form-item>
        <el-form-item label="官方网址" prop="officialUrl"><el-input v-model="form.officialUrl" maxlength="255" /></el-form-item>
        <el-form-item label="赛程时限" prop="scheduleDesc"><el-input v-model="form.scheduleDesc" maxlength="100" /></el-form-item>
        <el-form-item label="队伍规程" prop="teamRules"><el-input v-model="form.teamRules" maxlength="100" /></el-form-item>
        <el-form-item label="成果交付" prop="submissionSpec"><el-input v-model="form.submissionSpec" maxlength="150" /></el-form-item>
        <el-form-item label="赛题范式" prop="problemSpec"><el-input v-model="form.problemSpec" maxlength="150" /></el-form-item>
        <el-form-item label="赛事概况" prop="description"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { TopRight } from "@element-plus/icons-vue";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import { getAdminContentContests, updateAdminContentContest } from "@/api/problem";

const emit = defineEmits(["changed"]);
const contests = ref([]);
const loading = ref(false);
const loadError = ref(false);
const saving = ref(false);
const keyword = ref("");
const editVisible = ref(false);
const editingId = ref(null);
const formRef = ref();
const form = reactive({ code: "", name: "", englishName: "", scheduleDesc: "", teamRules: "", submissionSpec: "", problemSpec: "", description: "", officialUrl: "" });
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
  return contests.value.filter((contest) => [contest.code, contest.name, contest.englishName]
    .some((field) => String(field || "").toLowerCase().includes(value)));
});

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "—";
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

async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || !editingId.value) return;
  saving.value = true;
  try {
    await updateAdminContentContest(editingId.value, Object.fromEntries(Object.entries(form).map(([key, value]) => [key, value?.trim() || null])));
    ElMessage.success("赛事已更新");
    editVisible.value = false;
    await load();
    emit("changed");
  } catch (error) {
    ElMessage.error(error.message || "赛事更新失败");
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.contest-console { min-width: 0; padding: var(--lm-admin-space-3); }
.contest-toolbar, .toolbar-actions, .contest-identity, .contest-identity > span, .inline-warning { display: flex; align-items: center; }
.contest-toolbar { justify-content: space-between; gap: var(--lm-admin-space-3); margin-bottom: var(--lm-admin-space-3); }
.toolbar-actions { gap: var(--lm-admin-space-2); }
.contest-search { width: 300px; }
.result-count { color: var(--lm-admin-text-muted); font-size: 12px; }
.table-scroll { min-width: 0; overflow-x: auto; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.table-scroll :deep(.el-table) { min-width: 1040px; }
.contest-identity { min-width: 0; gap: 10px; }
.contest-identity > code { min-width: 74px; padding: 3px 6px; color: #fff; background: var(--lm-admin-text-strong); border-radius: 4px; font-size: 10px; font-weight: 700; text-align: center; }
.contest-identity > span { min-width: 0; flex-direction: column; align-items: flex-start; }
.contest-identity strong, .contest-identity small { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.contest-identity strong { color: var(--lm-admin-text-strong); font-size: 13px; }
.contest-identity small { margin-top: 2px; color: var(--lm-admin-text-muted); font-size: 11px; }
.external-link { display: inline-flex; color: var(--lm-admin-primary); font-size: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1.6fr; gap: 12px; }
.inline-warning { gap: 6px; margin-bottom: 8px; padding: 8px 10px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: var(--lm-admin-radius-control); font-size: 12px; }
.table-empty { padding: 34px 0; color: var(--lm-admin-text-muted); font-size: 12px; }
</style>
