<template>
  <div class="version-catalog-page" v-loading="loading">
    <header class="catalog-header">
      <div>
        <span class="catalog-eyebrow">CAPABILITY CATALOG</span>
        <h2>AI 功能与版本目录</h2>
        <p>统一查看功能归属、用途、数据契约和所有已发布工作流版本；目录只展示后端真实发布的版本。</p>
      </div>
      <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新目录</el-button>
    </header>

    <div class="feature-grid">
      <article v-for="item in featureSummaries" :key="item.featureCode" class="feature-card" :class="{ unavailable: !item.available }">
        <div class="feature-card-top">
          <span class="feature-code">{{ item.featureCode }}</span>
          <el-tag :type="item.available ? 'success' : 'info'" size="small" effect="light">{{ item.available ? `${item.versionCount} 个版本` : '目录待接入' }}</el-tag>
        </div>
        <h3>{{ item.name }}</h3>
        <p>{{ item.description }}</p>
        <small>{{ item.available ? `由 ${item.ownerService || '未知服务'} 发布` : '该功能尚未发布统一版本目录，未生成虚构版本' }}</small>
      </article>
    </div>

    <el-card shadow="never" class="catalog-table-card">
      <div class="table-toolbar">
        <div><h3>版本查询</h3><p>可按功能、版本号、版本名称或状态过滤</p></div>
        <el-input v-model="keyword" clearable placeholder="搜索功能 / 版本 / 状态" style="width: 280px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <el-table :data="filteredVersions" stripe style="width: 100%">
        <el-table-column prop="featureName" label="AI 功能" min-width="150">
          <template #default="{ row }"><strong>{{ row.featureName }}</strong><div class="cell-muted">{{ row.featureCode }}</div></template>
        </el-table-column>
        <el-table-column prop="workflowVersion" label="工作流版本" min-width="170" />
        <el-table-column prop="name" label="版本名称" min-width="170" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small" effect="light">{{ row.status || 'UNKNOWN' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="ownerService" label="归属服务" min-width="180" />
        <el-table-column prop="compatibility" label="兼容性说明" min-width="220" show-overflow-tooltip />
        <el-table-column type="expand" width="54">
          <template #default="{ row }">
            <div class="contract-grid">
              <div><span>支持的数据集</span><p>{{ row.datasetTypes.join('、') || '未声明' }}</p></div>
              <div><span>支持的指标</span><p>{{ row.metricCodes.join('、') || '未声明' }}</p></div>
              <div><span>输入契约</span><pre>{{ row.inputSchema || '未声明' }}</pre></div>
              <div><span>输出契约</span><pre>{{ row.outputSchema || '未声明' }}</pre></div>
            </div>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="keyword ? '没有匹配的真实版本' : '暂无已发布版本'" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { listEvaluationFeatures } from "@/api/admin-ai";

const loading = ref(false);
const features = ref([]);
const keyword = ref("");
const descriptions = {
  REVIEW: "对论文或提交内容执行自动评审，输出结构化评价与评分依据。",
  ASSISTANT: "面向用户问题提供对话式答疑与客服辅助。",
  SUGGESTION: "结合评审结果生成可操作的内容改进建议。",
};

const featureSummaries = computed(() => {
  const byCode = new Map(features.value.map((item) => [item.featureCode, item]));
  return ["REVIEW", "SUGGESTION", "ASSISTANT"].map((featureCode) => {
    const feature = byCode.get(featureCode);
    return {
      featureCode,
      name: feature?.name || ({ REVIEW: "AI 评审", SUGGESTION: "改进建议", ASSISTANT: "AI 助手" }[featureCode]),
      description: descriptions[featureCode],
      ownerService: feature?.ownerService,
      versionCount: feature?.workflowVersions?.length || 0,
      available: !!feature,
    };
  });
});

const versionRows = computed(() => features.value.flatMap((feature) =>
  (feature.workflowVersions || []).map((version) => ({
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
  if (!normalized) return versionRows.value;
  return versionRows.value.filter((row) => [row.featureCode, row.featureName, row.workflowVersion, row.name, row.status, row.ownerService]
    .some((value) => String(value || "").toLowerCase().includes(normalized)));
});

function statusType(status) {
  if (status === "ACTIVE" || status === "PRODUCTION") return "success";
  if (status === "DEPRECATED" || status === "RETIRED") return "danger";
  return "info";
}

async function load() {
  loading.value = true;
  try {
    features.value = (await listEvaluationFeatures()).data || [];
  } catch (error) {
    features.value = [];
    ElMessage.error(error.message || "AI 版本目录加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.version-catalog-page { display: flex; flex-direction: column; gap: 18px; }
.catalog-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.catalog-eyebrow { color: #7c3aed; font-size: 11px; font-weight: 700; letter-spacing: .12em; }
.catalog-header h2 { margin: 6px 0; color: var(--lm-text-primary); font-size: 20px; }
.catalog-header p, .table-toolbar p { margin: 0; color: var(--lm-text-muted); font-size: 13px; }
.feature-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.feature-card { min-height: 156px; padding: 18px; border: 1px solid #ddd6fe; border-radius: 14px; background: linear-gradient(145deg, #fff, #f5f3ff); }
.feature-card.unavailable { border-style: dashed; border-color: var(--lm-border); background: #f8fafc; }
.feature-card-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.feature-code { color: #7c3aed; font-size: 11px; font-weight: 700; letter-spacing: .08em; }
.feature-card h3 { margin: 15px 0 7px; color: var(--lm-text-primary); font-size: 17px; }
.feature-card p { min-height: 40px; margin: 0 0 10px; color: var(--lm-text-secondary); font-size: 13px; line-height: 1.55; }
.feature-card small, .cell-muted { color: var(--lm-text-muted); font-size: 12px; }
.catalog-table-card :deep(.el-card__body) { padding: 18px; }
.table-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.table-toolbar h3 { margin: 0 0 4px; color: var(--lm-text-primary); font-size: 16px; }
.contract-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; padding: 8px 48px 18px; }
.contract-grid > div { min-width: 0; padding: 13px; border: 1px solid var(--lm-border); border-radius: 10px; background: #f8fafc; }
.contract-grid span { color: var(--lm-text-muted); font-size: 12px; }
.contract-grid p, .contract-grid pre { margin: 7px 0 0; color: var(--lm-text-primary); font-size: 12px; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 980px) { .feature-grid { grid-template-columns: 1fr; } .contract-grid { grid-template-columns: 1fr; padding-inline: 16px; } }
</style>
