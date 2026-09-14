<template>
  <div class="operation-page">
    <div class="operation-toolbar">
      <div class="result-scope"><strong>{{ filteredRows.length }}</strong><span>条结果 · 数据范围：最近 50 条</span></div>
      <div class="filter-actions">
        <el-input v-model="keyword" clearable prefix-icon="Search" placeholder="搜索队伍、题号、工作流或模型" />
        <el-select v-model="status" clearable placeholder="全部状态">
          <el-option v-for="item in statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError && !rows.length" type="error" title="建议数据加载失败" action-label="重新加载" @action="load" />
    <div v-else>
      <div v-if="loadError" class="stale-warning">刷新失败，当前保留上次取得的数据</div>
      <el-table :data="filteredRows" stripe v-loading="loading" class="operation-table">
        <el-table-column label="状态" width="116"><template #default="{ row }"><AdminStatusBadge :status="row.status || 'UNKNOWN'" :label="statusLabel(row.status)" /></template></el-table-column>
        <el-table-column label="队伍" min-width="160"><template #default="{ row }"><strong>{{ teamName(teamMap, row.teamId) }}</strong></template></el-table-column>
        <el-table-column label="题目" min-width="210"><template #default="{ row }"><div class="primary-cell"><strong>题号 {{ problemLabel(problemMap, row.problemId).code }}</strong><span>{{ problemLabel(problemMap, row.problemId).title }}</span></div></template></el-table-column>
        <el-table-column label="工作流 / 模型" min-width="250"><template #default="{ row }"><div class="primary-cell"><strong>{{ row.workflowVersion || "—" }}</strong><span>{{ row.modelName || "模型未取得" }}</span></div></template></el-table-column>
        <el-table-column label="创建时间" width="152"><template #default="{ row }">{{ formatAdminTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="完成时间" width="152"><template #default="{ row }">{{ formatAdminTime(row.finishedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="76" fixed="right" align="right"><template #default="{ row }"><el-button link type="primary" @click="openDetails(row)">详情</el-button></template></el-table-column>
        <template #empty><AdminStatePanel :type="rows.length ? 'filtered' : 'empty'" :title="rows.length ? '没有符合条件的建议任务' : '最近没有建议任务'" /></template>
      </el-table>
    </div>

    <el-drawer v-model="detailsVisible" title="建议任务详情" size="min(580px, 88vw)">
      <dl v-if="selectedRow" class="details-list">
        <div><dt>状态</dt><dd><AdminStatusBadge :status="selectedRow.status || 'UNKNOWN'" :label="statusLabel(selectedRow.status)" /></dd></div>
        <div><dt>队伍</dt><dd>{{ teamName(teamMap, selectedRow.teamId) }}</dd></div>
        <div><dt>题目</dt><dd>题号 {{ problemLabel(problemMap, selectedRow.problemId).code }} · {{ problemLabel(problemMap, selectedRow.problemId).title }}</dd></div>
        <div><dt>工作流</dt><dd>{{ selectedRow.workflowVersion || "—" }}</dd></div>
        <div><dt>模型</dt><dd>{{ selectedRow.modelName || "—" }}</dd></div>
        <div v-if="selectedRow.errorMessage" class="error-detail"><dt>失败依据</dt><dd>{{ selectedRow.errorMessage }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ formatAdminTime(selectedRow.createTime) }}</dd></div>
        <div><dt>完成时间</dt><dd>{{ formatAdminTime(selectedRow.finishedAt) }}</dd></div>
        <IdentifierLine label="任务 ID" :value="selectedRow.taskId" @copy="copyIdentifier" />
        <IdentifierLine label="提交 ID" :value="selectedRow.submissionId" @copy="copyIdentifier" />
        <IdentifierLine label="队伍 ID" :value="selectedRow.teamId" @copy="copyIdentifier" />
        <IdentifierLine label="题目 ID" :value="selectedRow.problemId" @copy="copyIdentifier" />
        <IdentifierLine label="AI 调用 ID" :value="selectedRow.aiCallId" @copy="copyIdentifier" />
      </dl>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from "vue";
import { ElButton, ElMessage } from "element-plus";
import { getAdminSuggestions } from "@/api/admin-ops";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { copyAdminText, formatAdminTime, problemLabel, teamName } from "../operation-utils";

const props = defineProps({ teamMap: { type: Object, default: () => ({}) }, problemMap: { type: Object, default: () => ({}) } });
const emit = defineEmits(["reference-ids"]);
const rows = ref([]);
const loading = ref(false);
const loadError = ref(false);
const keyword = ref("");
const status = ref("");
const selectedRow = ref(null);
const detailsVisible = ref(false);
const priority = { FAILED: 0, UNKNOWN: 1, RUNNING: 2, WAITING: 3, COMPLETED: 4 };
const statusOptions = computed(() => [...new Set(rows.value.map(item => item.status).filter(Boolean))]);
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => {
    if (status.value && row.status !== status.value) return false;
    if (!query) return true;
    const problem = problemLabel(props.problemMap, row.problemId);
    return [teamName(props.teamMap, row.teamId), problem.code, problem.title, row.workflowVersion, row.modelName, row.errorMessage]
      .some(value => String(value || "").toLowerCase().includes(query));
  }).sort((left, right) => (priority[left.status] ?? 3) - (priority[right.status] ?? 3));
});

const IdentifierLine = defineComponent({
  props: { label: String, value: [String, Number] },
  emits: ["copy"],
  setup(lineProps, { emit }) {
    return () => h("div", [h("dt", lineProps.label), h("dd", { class: "identifier-value" }, [h("span", String(lineProps.value ?? "—")), lineProps.value != null ? h(ElButton, { link: true, type: "primary", onClick: () => emit("copy", lineProps.value) }, () => "复制") : null])]);
  },
});

function statusLabel(value) { return ({ WAITING: "等待", RUNNING: "进行中", COMPLETED: "已完成", FAILED: "失败", UNKNOWN: "结果待确认" })[value] || value || "未知"; }
async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    rows.value = (await getAdminSuggestions(50)).data || [];
    emit("reference-ids", rows.value.map(item => item.teamId));
  }
  catch (error) { loadError.value = true; if (rows.value.length) ElMessage.error(error.message || "建议数据刷新失败"); }
  finally { loading.value = false; }
}
function openDetails(row) { selectedRow.value = row; detailsVisible.value = true; }
async function copyIdentifier(value) { await copyAdminText(value); ElMessage.success("标识已复制"); }

onMounted(load);
</script>

<style scoped>
@import './operations-workspace.css';
.error-detail dd { color: var(--lm-admin-danger); }
</style>
