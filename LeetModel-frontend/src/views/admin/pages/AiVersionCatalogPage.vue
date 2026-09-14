<template>
  <div class="version-catalog-page">
    <div class="catalog-toolbar">
      <el-input v-model="keyword" clearable placeholder="搜索功能或版本" class="catalog-search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <AdminStatePanel
      v-if="loadError && !features.length"
      type="error"
      title="版本目录加载失败"
      action-label="重新加载"
      @action="load"
    />

    <div v-else class="catalog-layout" :aria-busy="loading">
      <aside class="feature-registry" aria-label="AI 功能注册表">
        <button type="button" :class="{ active: !selectedFeature }" @click="selectedFeature = ''">
          <span>全部功能</span><strong>{{ versionRows.length }}</strong>
        </button>
        <button
          v-for="item in featureSummaries"
          :key="item.featureCode"
          type="button"
          :class="{ active: selectedFeature === item.featureCode, unavailable: !item.available }"
          @click="selectedFeature = item.featureCode"
        >
          <span><strong>{{ item.name }}</strong><small>{{ item.featureCode }}</small></span>
          <span class="registry-state">
            <b>{{ item.available ? item.versionCount : "—" }}</b>
            <small>{{ item.available ? item.ownerService : "未知" }}</small>
          </span>
        </button>
      </aside>

      <div class="version-table-wrap">
        <el-table :data="filteredVersions" stripe>
          <el-table-column label="功能" min-width="142">
            <template #default="{ row }"><strong class="primary-cell">{{ row.featureName }}</strong><span class="secondary-cell">{{ row.featureCode }}</span></template>
          </el-table-column>
          <el-table-column label="版本" min-width="190">
            <template #default="{ row }"><strong class="primary-cell">{{ row.name }}</strong><span class="secondary-cell">{{ row.workflowVersion }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="104">
            <template #default="{ row }"><AdminStatusBadge :status="row.status || 'UNKNOWN'" :label="statusLabel(row.status)" /></template>
          </el-table-column>
          <el-table-column prop="ownerService" label="归属服务" min-width="168" />
          <el-table-column prop="compatibility" label="兼容性" min-width="240" show-overflow-tooltip />
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="contract-grid">
                <div><span>数据集</span><p>{{ row.datasetTypes.join("、") || "未声明" }}</p></div>
                <div><span>指标</span><p>{{ row.metricCodes.join("、") || "未声明" }}</p></div>
                <div class="schema-block"><span>输入契约</span><pre>{{ row.inputSchema || "未声明" }}</pre></div>
                <div class="schema-block"><span>输出契约</span><pre>{{ row.outputSchema || "未声明" }}</pre></div>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <AdminStatePanel :type="keyword || selectedFeature ? 'filtered' : 'empty'" :title="keyword || selectedFeature ? '没有符合条件的真实版本' : '暂无已发布版本'" />
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { listEvaluationFeatures } from "@/api/admin-ai";

const loading = ref(false);
const loadError = ref(false);
const features = ref([]);
const keyword = ref("");
const selectedFeature = ref("");
const knownFeatures = [
  { featureCode: "REVIEW", name: "AI 论文评审" },
  { featureCode: "SUGGESTION", name: "AI 论文建议" },
  { featureCode: "ASSISTANT", name: "AI 客服" },
];

const featureSummaries = computed(() => {
  const byCode = new Map(features.value.map(item => [item.featureCode, item]));
  return knownFeatures.map(known => {
    const feature = byCode.get(known.featureCode);
    return {
      ...known,
      name: feature?.name || known.name,
      ownerService: feature?.ownerService,
      versionCount: feature?.workflowVersions?.length || 0,
      available: Boolean(feature),
    };
  });
});

const versionRows = computed(() => features.value.flatMap(feature =>
  (feature.workflowVersions || []).map(version => ({
    ...version,
    featureCode: feature.featureCode,
    featureName: feature.name,
    ownerService: feature.ownerService,
    datasetTypes: feature.supportedDatasetTypes || [],
    metricCodes: feature.supportedMetricCodes || [],
  })),
));

const filteredVersions = computed(() => {
  const normalized = keyword.value.trim().toLowerCase();
  return versionRows.value.filter(row => {
    const matchesFeature = !selectedFeature.value || row.featureCode === selectedFeature.value;
    const matchesKeyword = !normalized || [row.featureCode, row.featureName, row.workflowVersion, row.name, row.status, row.ownerService]
      .some(value => String(value || "").toLowerCase().includes(normalized));
    return matchesFeature && matchesKeyword;
  });
});

function statusLabel(status) {
  return ({ ENABLED: "可用", ACTIVE: "生效", PRODUCTION: "生产", DEPRECATED: "废弃", RETIRED: "退役" })[status] || "未知";
}

async function load() {
  loading.value = true;
  loadError.value = false;
  try {
    features.value = (await listEvaluationFeatures()).data || [];
  } catch (error) {
    loadError.value = true;
    ElMessage.error(error.message || "版本目录加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.version-catalog-page { display: flex; min-width: 0; flex-direction: column; gap: 12px; padding: 12px; }
.catalog-toolbar { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }.catalog-search { width: 280px; }
.catalog-layout { display: grid; min-width: 0; grid-template-columns: 250px minmax(0, 1fr); overflow: hidden; border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }
.feature-registry { display: flex; min-width: 0; flex-direction: column; padding: 6px; background: var(--lm-admin-surface-subtle); border-right: 1px solid var(--lm-admin-border); }
.feature-registry button { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 10px; color: var(--lm-admin-text-default); text-align: left; background: transparent; border: 0; border-radius: var(--lm-admin-radius-control); cursor: pointer; }
.feature-registry button:hover { background: var(--lm-admin-surface); }.feature-registry button.active { color: var(--lm-admin-primary); background: var(--lm-admin-surface); box-shadow: 0 0 0 1px var(--lm-admin-border); }.feature-registry button.unavailable { color: var(--lm-admin-text-muted); }
.feature-registry button > span { display: flex; min-width: 0; flex-direction: column; gap: 2px; }.feature-registry strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.feature-registry small { overflow: hidden; color: var(--lm-admin-text-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.registry-state { align-items: flex-end; flex: 0 0 auto; }.registry-state b { font-size: 16px; font-variant-numeric: tabular-nums; }
.version-table-wrap { min-width: 0; overflow: hidden; }
.primary-cell, .secondary-cell { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.primary-cell { color: var(--lm-admin-text-strong); font-size: 12px; }.secondary-cell { margin-top: 2px; color: var(--lm-admin-text-muted); font-size: 10px; }
.contract-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 8px 42px 16px; }.contract-grid > div { min-width: 0; padding: 10px 12px; background: var(--lm-admin-surface-subtle); border: 1px solid var(--lm-admin-border); border-radius: var(--lm-admin-radius-control); }.contract-grid span { color: var(--lm-admin-text-muted); font-size: 11px; }.contract-grid p, .contract-grid pre { margin: 5px 0 0; color: var(--lm-admin-text-strong); font-size: 11px; white-space: pre-wrap; overflow-wrap: anywhere; }.schema-block { min-height: 88px; }
@media (max-width: 1050px) { .catalog-layout { grid-template-columns: 210px minmax(0, 1fr); } }
@media (max-width: 760px) { .catalog-toolbar { align-items: stretch; flex-direction: column; }.catalog-search { width: 100%; }.catalog-layout { grid-template-columns: 1fr; }.feature-registry { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-right: 0; border-bottom: 1px solid var(--lm-admin-border); }.contract-grid { grid-template-columns: 1fr; padding-inline: 12px; } }
</style>
