<template>
  <div class="operation-page">
    <div class="operation-toolbar">
      <div class="result-scope"><strong>{{ filteredRows.length }}</strong><span>条结果 · 数据范围：最近 50 条</span></div>
      <div class="filter-actions">
        <el-input v-model="keyword" clearable prefix-icon="Search" placeholder="搜索队伍、题号或文件名" />
        <el-select v-model="status" clearable placeholder="全部状态">
          <el-option v-for="item in statusOptions" :key="item" :label="statusLabel(item)" :value="item" />
        </el-select>
        <el-select v-model="finalOnly" clearable placeholder="全部版本">
          <el-option label="仅最终版" value="final" />
          <el-option label="非最终版" value="history" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError && !rows.length" type="error" title="提交数据加载失败" action-label="重新加载" @action="load" />
    <div v-else>
      <div v-if="loadError" class="stale-warning">刷新失败，当前保留上次取得的数据</div>
      <el-table :data="filteredRows" stripe v-loading="loading" class="operation-table">
        <el-table-column label="队伍" min-width="170">
          <template #default="{ row }"><strong>{{ teamName(teamMap, row.teamId) }}</strong></template>
        </el-table-column>
        <el-table-column label="题目" min-width="210">
          <template #default="{ row }">
            <div class="primary-cell"><strong>题号 {{ problemLabel(problemMap, row.problemId).code }}</strong><span>{{ problemLabel(problemMap, row.problemId).title }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="文件 / 版本" min-width="250">
          <template #default="{ row }">
            <div class="primary-cell"><strong :title="row.originalFilename">{{ row.originalFilename || "未命名文件" }}</strong><span>V{{ row.version ?? "—" }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="最终版" width="82" align="center">
          <template #default="{ row }"><el-tag :type="row.finalVersion ? 'success' : 'info'" size="small" effect="plain">{{ row.finalVersion ? "是" : "否" }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><AdminStatusBadge :status="normalizedStatus(row.status)" :label="statusLabel(row.status)" /></template>
        </el-table-column>
        <el-table-column label="提交时间" width="152"><template #default="{ row }">{{ formatAdminTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="操作" width="126" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row)">预览</el-button>
            <el-button link @click="openDetails(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AdminStatePanel :type="rows.length ? 'filtered' : 'empty'" :title="rows.length ? '没有符合条件的提交' : '最近没有提交记录'" />
        </template>
      </el-table>
    </div>

    <el-drawer v-model="detailsVisible" title="提交详情" size="min(520px, 86vw)">
      <dl v-if="selectedRow" class="details-list">
        <div><dt>队伍</dt><dd>{{ teamName(teamMap, selectedRow.teamId) }}</dd></div>
        <div><dt>题目</dt><dd>题号 {{ problemLabel(problemMap, selectedRow.problemId).code }} · {{ problemLabel(problemMap, selectedRow.problemId).title }}</dd></div>
        <div><dt>文件</dt><dd>{{ selectedRow.originalFilename || "—" }}</dd></div>
        <div><dt>版本</dt><dd>V{{ selectedRow.version ?? "—" }}{{ selectedRow.finalVersion ? " · 最终版" : "" }}</dd></div>
        <div><dt>状态</dt><dd>{{ statusLabel(selectedRow.status) }}</dd></div>
        <div><dt>提交时间</dt><dd>{{ formatAdminTime(selectedRow.createTime) }}</dd></div>
        <IdentifierRow label="提交 ID" :value="selectedRow.id" @copy="copyIdentifier" />
        <IdentifierRow label="队伍 ID" :value="selectedRow.teamId" @copy="copyIdentifier" />
        <IdentifierRow label="题目 ID" :value="selectedRow.problemId" @copy="copyIdentifier" />
        <IdentifierRow label="提交者 ID" :value="selectedRow.submitterId" @copy="copyIdentifier" />
      </dl>
    </el-drawer>

    <el-drawer v-model="previewVisible" :title="previewFilename || '提交预览'" size="min(1000px, 88vw)" destroy-on-close>
      <div v-loading="previewLoading" class="pdf-preview-body">
        <template v-if="previewUrl">
          <div class="preview-toolbar"><span>临时预览地址</span><a :href="previewUrl" target="_blank" rel="noopener noreferrer">在新窗口打开</a></div>
          <iframe :src="previewUrl" :title="`PDF 预览：${previewFilename}`" class="pdf-frame"></iframe>
        </template>
        <AdminStatePanel v-else-if="previewError" type="error" title="PDF 预览地址不可用" action-label="重试" @action="retryPreview" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from "vue";
import { ElButton, ElMessage } from "element-plus";
import { getAdminSubmissions, getAdminSubmissionPreview } from "@/api/admin-ops";
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
const finalOnly = ref("");
const selectedRow = ref(null);
const detailsVisible = ref(false);
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewUrl = ref("");
const previewFilename = ref("");
const previewError = ref(false);
const previewRow = ref(null);

const IdentifierRow = defineComponent({
  props: { label: String, value: [String, Number] },
  emits: ["copy"],
  setup(rowProps, { emit }) {
    return () => h("div", [h("dt", rowProps.label), h("dd", { class: "identifier-value" }, [h("span", String(rowProps.value ?? "—")), rowProps.value != null ? h(ElButton, { link: true, type: "primary", onClick: () => emit("copy", rowProps.value) }, () => "复制") : null])]);
  },
});

const statusOptions = computed(() => [...new Set(rows.value.map(item => item.status).filter(Boolean))]);
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return rows.value.filter(row => {
    if (status.value && row.status !== status.value) return false;
    if (finalOnly.value === "final" && !row.finalVersion) return false;
    if (finalOnly.value === "history" && row.finalVersion) return false;
    if (!query) return true;
    const problem = problemLabel(props.problemMap, row.problemId);
    return [teamName(props.teamMap, row.teamId), problem.code, problem.title, row.originalFilename]
      .some(value => String(value || "").toLowerCase().includes(query));
  });
});

function statusLabel(value) { return ({ SUCCESS: "已提交", FAILED: "失败", PROCESSING: "处理中", PENDING: "等待处理" })[value] || value || "未知"; }
function normalizedStatus(value) { return value === "SUCCESS" ? "SUCCEEDED" : value || "UNKNOWN"; }
async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    rows.value = (await getAdminSubmissions(50)).data || [];
    emit("reference-ids", rows.value.map(item => item.teamId));
  }
  catch (error) { loadError.value = true; if (rows.value.length) ElMessage.error(error.message || "提交数据刷新失败"); }
  finally { loading.value = false; }
}
function openDetails(row) { selectedRow.value = row; detailsVisible.value = true; }
function safeHttpUrl(value) {
  try { const url = new URL(value); return ["http:", "https:"].includes(url.protocol) ? url.href : ""; }
  catch { return ""; }
}
async function openPreview(row) {
  previewRow.value = row;
  previewVisible.value = true;
  previewLoading.value = true;
  previewUrl.value = "";
  previewError.value = false;
  previewFilename.value = row.originalFilename || "提交论文.pdf";
  try {
    const response = await getAdminSubmissionPreview(row.id);
    previewUrl.value = safeHttpUrl(response.data?.previewUrl);
    previewFilename.value = response.data?.originalFilename || previewFilename.value;
    previewError.value = !previewUrl.value;
  } catch (error) {
    previewError.value = true;
    ElMessage.error(error.message || "PDF 预览加载失败");
  } finally { previewLoading.value = false; }
}
function retryPreview() { if (previewRow.value) openPreview(previewRow.value); }
async function copyIdentifier(value) { await copyAdminText(value); ElMessage.success("标识已复制"); }

onMounted(load);
</script>

<style scoped>
@import './operations-workspace.css';
.pdf-preview-body { min-height: 460px; }
.preview-toolbar { display: flex; justify-content: space-between; margin-bottom: 10px; padding: 8px 10px; color: var(--lm-admin-text-muted); background: var(--lm-admin-surface-subtle); border-radius: 6px; font-size: 12px; }
.pdf-frame { width: 100%; height: calc(100vh - 150px); background: #475569; border: 0; border-radius: 8px; }
</style>
