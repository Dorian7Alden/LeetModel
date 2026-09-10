<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div>
        <h2 class="panel-title">赛事基础数据与学术档案</h2>
        <p class="panel-subtitle">维护赛事的官方中英文全称、赛制规约、命题范式与官方主页，修改后实时同步影响题库专页展示</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-table :data="contests" v-loading="loading" stripe style="width: 100%" class="contest-table">
      <!-- 展开行查看详细描述与成果规范 -->
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="contest-expand-detail">
            <div class="expand-grid">
              <div class="expand-item">
                <span class="expand-label">成果交付规范：</span>
                <span class="expand-val">{{ row.submissionSpec || '—' }}</span>
              </div>
              <div class="expand-item">
                <span class="expand-label">赛题命题范式：</span>
                <span class="expand-val">{{ row.problemSpec || '—' }}</span>
              </div>
              <div class="expand-item full-width">
                <span class="expand-label">赛事客观简介：</span>
                <span class="expand-val">{{ row.description || '—' }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="code" label="编码" width="110">
        <template #default="{ row }">
          <span class="table-code-badge">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="赛事全称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="englishName" label="英文全称" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="text-muted">{{ row.englishName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="scheduleDesc" label="赛程时限" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.scheduleDesc || '—' }}</template>
      </el-table-column>
      <el-table-column prop="teamRules" label="队伍规程" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.teamRules || '—' }}</template>
      </el-table-column>
      <el-table-column label="官网链接" width="90" align="center">
        <template #default="{ row }">
          <a
            v-if="row.officialUrl"
            :href="row.officialUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="table-link"
            title="打开赛事官网"
          >
            <el-icon><TopRight /></el-icon>
          </a>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatTime(row.updateTime || row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }"><el-button type="primary" link @click="openEdit(row)">编辑</el-button></template>
      </el-table-column>
      <template #empty><el-empty description="暂无赛事数据" /></template>
    </el-table>

    <el-dialog v-model="editVisible" title="编辑赛事档案与赛制规约" width="640px" append-to-body destroy-on-close class="contest-edit-dialog">
      <el-alert title="赛事信息修改后将通过多级缓存与 ETag 协商缓存即时同步至前台题库专页。" type="info" :closable="false" show-icon class="edit-alert" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="contest-edit-form">
        <div class="form-section-title">基本标识</div>
        <el-form-item label="赛事编码" prop="code">
          <el-input v-model="form.code" maxlength="32" placeholder="例如 MCM_ICM、CUMCM" />
        </el-form-item>
        <el-form-item label="赛事名称" prop="name">
          <el-input v-model="form.name" maxlength="100" placeholder="例如 全国大学生数学建模竞赛" show-word-limit />
        </el-form-item>
        <el-form-item label="英文全称" prop="englishName">
          <el-input v-model="form.englishName" maxlength="200" placeholder="官方英文全称" show-word-limit />
        </el-form-item>
        <el-form-item label="官方网址" prop="officialUrl">
          <el-input v-model="form.officialUrl" maxlength="255" placeholder="例如 http://www.cumcm.cn/" />
        </el-form-item>

        <div class="form-section-title">官方赛制规约</div>
        <el-form-item label="赛程时限" prop="scheduleDesc">
          <el-input v-model="form.scheduleDesc" maxlength="100" placeholder="例如 每年 9 月上旬 · 连续 72 小时（3天3夜）" />
        </el-form-item>
        <el-form-item label="队伍规程" prop="teamRules">
          <el-input v-model="form.teamRules" maxlength="100" placeholder="说明是否支持跨校组队、个人或学校统一报名方式" />
        </el-form-item>
        <el-form-item label="成果交付" prop="submissionSpec">
          <el-input v-model="form.submissionSpec" maxlength="150" placeholder="例如 中文学术论文（PDF 格式）与支撑材料（代码/数据）" />
        </el-form-item>
        <el-form-item label="赛题范式" prop="problemSpec">
          <el-input v-model="form.problemSpec" maxlength="150" placeholder="例如 涵盖物理机理推导、运筹优化调度与大数据分析应用" />
        </el-form-item>

        <div class="form-section-title">赛事概况</div>
        <el-form-item label="背景简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="简明客观陈述赛事主办方、创办背景与学术考查核心"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存修改</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { TopRight } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { getAdminContentContests, updateAdminContentContest } from "@/api/problem";

const contests = ref([]);
const loading = ref(false);
const saving = ref(false);
const editVisible = ref(false);
const editingId = ref(null);
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
};

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}

async function load() {
  loading.value = true;
  try {
    contests.value = (await getAdminContentContests()).data || [];
  } catch (error) {
    ElMessage.error(error.message || "赛事数据加载失败");
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
    await updateAdminContentContest(editingId.value, {
      code: form.code.trim(),
      name: form.name.trim(),
      englishName: form.englishName?.trim() || null,
      scheduleDesc: form.scheduleDesc?.trim() || null,
      teamRules: form.teamRules?.trim() || null,
      submissionSpec: form.submissionSpec?.trim() || null,
      problemSpec: form.problemSpec?.trim() || null,
      description: form.description?.trim() || null,
      officialUrl: form.officialUrl?.trim() || null,
    });
    ElMessage.success("赛事档案与赛制规约已更新");
    editVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error.message || "赛事更新失败");
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.panel-title { margin: 0; font-size: 18px; }
.panel-subtitle { margin: 6px 0 0; color: var(--lm-text-muted); font-size: 13px; }
.edit-alert { margin-bottom: 18px; }

.table-code-badge {
  display: inline-block;
  padding: 2px 6px;
  border-radius: var(--lm-radius-sm);
  background: #18181b;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--lm-code-font-family);
}
.text-muted {
  color: var(--lm-text-muted);
}
.table-link {
  color: var(--lm-primary);
  font-size: 16px;
  display: inline-flex;
  align-items: center;
}
.table-link .el-icon {
  font-size: 16px;
}
.table-link:hover {
  color: #18181b;
}

/* 展开行详情 */
.contest-expand-detail {
  padding: 12px 16px;
  background: #fafafa;
  border-radius: var(--lm-radius-sm);
}
.expand-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 20px;
  font-size: 12px;
}
.expand-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.expand-item.full-width {
  grid-column: 1 / -1;
}
.expand-label {
  flex-shrink: 0;
  color: var(--lm-text-muted);
  font-weight: 500;
}
.expand-val {
  color: var(--lm-text-primary);
  line-height: 1.5;
}

/* 表单分组标题 */
.form-section-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--lm-text-primary);
  margin: 14px 0 10px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--lm-border-light);
}
.form-section-title:first-child {
  margin-top: 0;
}
</style>
