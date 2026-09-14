<template>
  <div class="operation-page">
    <div class="operation-toolbar">
      <div class="result-scope"><strong>{{ filteredRows.length }}</strong><span>条结果 · 数据范围：最近 50 条</span></div>
      <div class="filter-actions">
        <el-input v-model="keyword" clearable prefix-icon="Search" placeholder="搜索队伍名称或题号" />
        <el-select v-model="practiceStatus" clearable placeholder="全部阶段">
          <el-option label="组建中" value="PREPARING" />
          <el-option label="练习中" value="IN_PROGRESS" />
          <el-option label="已结束" value="ENDED" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError && !rows.length" type="error" title="队伍数据加载失败" action-label="重新加载" @action="load" />
    <div v-else>
      <div v-if="loadError" class="stale-warning">刷新失败，当前保留上次取得的数据</div>
      <el-table :data="filteredRows" stripe v-loading="loading" class="operation-table">
        <el-table-column label="队伍" min-width="220"><template #default="{ row }"><strong>{{ row.name || "未命名队伍" }}</strong></template></el-table-column>
        <el-table-column label="题目" min-width="250">
          <template #default="{ row }"><div class="primary-cell"><strong>题号 {{ problemLabel(problemMap, row.problemId).code }}</strong><span>{{ problemLabel(problemMap, row.problemId).title }}</span></div></template>
        </el-table-column>
        <el-table-column label="成员" width="86" align="center"><template #default="{ row }">{{ row.memberCount ?? "—" }}</template></el-table-column>
        <el-table-column label="练习阶段" width="112"><template #default="{ row }"><AdminStatusBadge :status="statusForBadge(row.practiceStatus)" :label="statusLabel(row.practiceStatus)" /></template></el-table-column>
        <el-table-column label="开始时间" width="152"><template #default="{ row }">{{ formatAdminTime(row.startedAt) }}</template></el-table-column>
        <el-table-column label="截止时间" width="152"><template #default="{ row }">{{ formatAdminTime(row.deadlineAt) }}</template></el-table-column>
        <el-table-column label="操作" width="76" fixed="right" align="right"><template #default="{ row }"><el-button link type="primary" @click="openDetails(row)">详情</el-button></template></el-table-column>
        <template #empty><AdminStatePanel :type="rows.length ? 'filtered' : 'empty'" :title="rows.length ? '没有符合条件的队伍' : '最近没有队伍记录'" /></template>
      </el-table>
    </div>

    <el-drawer v-model="detailsVisible" title="队伍详情" size="min(520px, 86vw)">
      <dl v-if="selectedRow" class="details-list">
        <div><dt>队伍</dt><dd>{{ selectedRow.name || "未命名队伍" }}</dd></div>
        <div><dt>题目</dt><dd>题号 {{ problemLabel(problemMap, selectedRow.problemId).code }} · {{ problemLabel(problemMap, selectedRow.problemId).title }}</dd></div>
        <div><dt>成员数</dt><dd>{{ selectedRow.memberCount ?? "—" }}</dd></div>
        <div><dt>练习阶段</dt><dd>{{ statusLabel(selectedRow.practiceStatus) }}</dd></div>
        <div><dt>开始时间</dt><dd>{{ formatAdminTime(selectedRow.startedAt) }}</dd></div>
        <div><dt>截止时间</dt><dd>{{ formatAdminTime(selectedRow.deadlineAt) }}</dd></div>
        <div><dt>结束时间</dt><dd>{{ formatAdminTime(selectedRow.endedAt) }}</dd></div>
        <div><dt>队伍 ID</dt><dd class="identifier-value"><span>{{ selectedRow.id }}</span><el-button link type="primary" @click="copyIdentifier(selectedRow.id)">复制</el-button></dd></div>
        <div><dt>队长 ID</dt><dd class="identifier-value"><span>{{ selectedRow.leaderId ?? "—" }}</span><el-button v-if="selectedRow.leaderId != null" link type="primary" @click="copyIdentifier(selectedRow.leaderId)">复制</el-button></dd></div>
        <div><dt>题目 ID</dt><dd class="identifier-value"><span>{{ selectedRow.problemId ?? "—" }}</span><el-button v-if="selectedRow.problemId != null" link type="primary" @click="copyIdentifier(selectedRow.problemId)">复制</el-button></dd></div>
      </dl>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminTeams } from "@/api/admin-ops";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { copyAdminText, formatAdminTime, problemLabel } from "../operation-utils";

const props = defineProps({ problemMap: { type: Object, default: () => ({}) } });
const rows = ref([]);
const loading = ref(false);
const loadError = ref(false);
const keyword = ref("");
const practiceStatus = ref("");
const selectedRow = ref(null);
const detailsVisible = ref(false);
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => {
    if (practiceStatus.value && row.practiceStatus !== practiceStatus.value) return false;
    if (!query) return true;
    const problem = problemLabel(props.problemMap, row.problemId);
    return [row.name, problem.code, problem.title].some(value => String(value || "").toLowerCase().includes(query));
  });
});

function statusLabel(status) { return ({ PREPARING: "组建中", IN_PROGRESS: "练习中", ENDED: "已结束" })[status] || status || "未知"; }
function statusForBadge(status) { return ({ PREPARING: "WAITING", IN_PROGRESS: "RUNNING", ENDED: "COMPLETED" })[status] || "UNKNOWN"; }
async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try { rows.value = (await getAdminTeams(50)).data || []; }
  catch (error) { loadError.value = true; if (rows.value.length) ElMessage.error(error.message || "队伍数据刷新失败"); }
  finally { loading.value = false; }
}
function openDetails(row) { selectedRow.value = row; detailsVisible.value = true; }
async function copyIdentifier(value) { await copyAdminText(value); ElMessage.success("标识已复制"); }

onMounted(load);
</script>

<style scoped>
@import './operations-workspace.css';
</style>
