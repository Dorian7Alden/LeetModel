<template>
  <div class="evaluation-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="测试集" name="datasets">
        <div class="pane-toolbar">
          <h2 class="panel-title">固定评价测试集</h2>
          <el-button type="primary" :loading="loadingSubmissions" @click="openCreateDataset">新建测试集</el-button>
        </div>
        <el-table :data="datasets" stripe v-loading="loading" style="width: 100%">
          <el-table-column prop="datasetId" label="ID" width="120" />
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
          <el-table-column label="样本数" width="90" align="center">
            <template #default="{ row }">{{ row.sampleCount || row.samples?.length || 0 }}</template>
          </el-table-column>
          <el-table-column label="创建人" width="90" align="center">{{ row.createdBy }}</el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <template #empty><el-empty description="暂无测试集" /></template>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="评价任务" name="tasks">
        <div class="pane-toolbar">
          <h2 class="panel-title">评价任务</h2>
          <el-button type="primary" @click="openCreateTask">新建评价任务</el-button>
        </div>
        <el-table :data="tasks" stripe v-loading="loading" style="width: 100%">
          <el-table-column prop="taskId" label="任务 ID" width="120" />
          <el-table-column prop="datasetId" label="测试集" width="90" />
          <el-table-column prop="workflowVersion" label="评审版本" width="130" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small" effect="light">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="综合分" width="90" align="center">
            <template #default="{ row }">{{ row.overallScore != null ? row.overallScore : '-' }}</template>
          </el-table-column>
          <el-table-column label="成功率" width="90" align="center">
            <template #default="{ row }">{{ row.successRate != null ? row.successRate : '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="showTask(row)">详情</el-button>
              <el-button v-if="row.status === 'FAILED'" link type="warning" @click="retryTask(row)">重试</el-button>
              <el-button v-if="['WAITING', 'RUNNING'].includes(row.status)" link type="warning" @click="controlTask(row, 'pause')">暂停</el-button>
              <el-button v-if="row.status === 'PAUSED'" link type="primary" @click="controlTask(row, 'resume')">恢复</el-button>
              <el-button v-if="['WAITING', 'RUNNING', 'PAUSED', 'FAILED'].includes(row.status)" link type="danger" @click="controlTask(row, 'cancel')">取消</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无评价任务" /></template>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="版本对比" name="compare">
        <div class="compare-toolbar">
          <el-select v-model="compareDatasetId" placeholder="选择测试集" clearable style="width: 260px" @change="runCompare">
            <el-option v-for="d in datasets" :key="d.datasetId" :label="d.name" :value="d.datasetId" />
          </el-select>
          <el-input-number v-model="compareRepeat" :min="1" :max="20" />
          <el-button type="primary" :loading="comparing" @click="runCompare">对比</el-button>
        </div>
        <el-table v-if="comparison" :data="comparison.versions || []" stripe v-loading="comparing" style="width: 100%; margin-top: 16px">
          <el-table-column prop="workflowVersion" label="评审版本" width="140" />
          <el-table-column label="综合分" width="100" align="center">
            <template #default="{ row }">{{ row.overallScore != null ? row.overallScore : '-' }}</template>
          </el-table-column>
          <el-table-column label="有效性" width="100" align="center">{{ row.validityScore ?? '-' }}</el-table-column>
          <el-table-column label="稳定性" width="100" align="center">{{ row.stabilityScore ?? '-' }}</el-table-column>
          <el-table-column label="成功率" width="100" align="center">{{ row.successRate ?? '-' }}</el-table-column>
          <el-table-column label="延迟分" width="100" align="center">{{ row.latencyScore ?? '-' }}</el-table-column>
          <el-table-column label="平均耗时" width="110" align="center">
            <template #default="{ row }">{{ row.avgDurationMs != null ? `${row.avgDurationMs}ms` : '-' }}</template>
          </el-table-column>
          <template #empty><el-empty description="选择测试集后点击对比" /></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建测试集 -->
    <el-dialog v-model="datasetDialogVisible" title="新建测试集" width="720px">
      <el-form :model="datasetForm" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="datasetForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="datasetForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="样本" required>
          <el-table :data="submissions" height="260" @selection-change="onSampleSelect">
            <el-table-column type="selection" width="48" />
            <el-table-column prop="id" label="提交 ID" width="130" />
            <el-table-column prop="originalFilename" label="文件名" min-width="180" />
            <el-table-column prop="teamId" label="队伍" width="90" />
            <el-table-column prop="problemId" label="题目" width="90" />
          </el-table>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDataset" @click="saveDataset">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新建评价任务 -->
    <el-dialog v-model="taskDialogVisible" title="新建评价任务" width="460px">
      <el-form :model="taskForm" label-width="90px">
        <el-form-item label="测试集" required>
          <el-select v-model="taskForm.datasetId" style="width: 100%">
            <el-option v-for="d in datasets" :key="d.datasetId" :label="d.name" :value="d.datasetId" />
          </el-select>
        </el-form-item>
        <el-form-item label="评审版本" required>
          <el-select v-model="taskForm.workflowVersion" placeholder="请选择已启用版本" style="width: 100%">
            <el-option
              v-for="version in enabledReviewVersions"
              :key="version.workflowVersion"
              :label="`${version.name}（${version.workflowVersion}）`"
              :value="version.workflowVersion"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="重复次数" required><el-input-number v-model="taskForm.repeatCount" :min="1" :max="20" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTask" @click="saveTask">运行</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情 -->
    <el-dialog v-model="taskDetailVisible" title="评价任务详情" width="720px">
      <el-descriptions v-if="taskDetail" :column="3" border size="small">
        <el-descriptions-item label="状态">{{ statusLabel(taskDetail.status) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ taskDetail.workflowVersion }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ taskDetail.retryCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="综合分">{{ taskDetail.overallScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="成功率">{{ taskDetail.successRate ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="平均耗时">{{ taskDetail.avgDurationMs != null ? `${taskDetail.avgDurationMs}ms` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近操作">{{ taskDetail.lastOperation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作者">{{ taskDetail.lastOperatedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatTime(taskDetail.lastOperatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="taskDetail?.runs?.length" :data="taskDetail.runs" stripe style="margin-top: 16px">
        <el-table-column prop="sampleId" label="样本" width="90" />
        <el-table-column prop="submissionId" label="提交" width="100" />
        <el-table-column prop="repetitionNo" label="重复" width="70" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="得分" width="90" align="center">{{ row.score ?? '-' }}</el-table-column>
        <el-table-column prop="failureType" label="失败类型" min-width="120" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "@/store/user";
import { getAdminSubmissions } from "@/api/admin-ops";
import {
  listEvaluationDatasets, createEvaluationDataset, listEvaluationTasks, createEvaluationTask,
  estimateEvaluation,
  getEvaluationTask, retryEvaluationTask, compareEvaluation, listEvaluationFeatures,
  pauseEvaluationTask, resumeEvaluationTask, cancelEvaluationTask,
} from "@/api/admin-ai";

const userStore = useUserStore();
const activeTab = ref("datasets");
const loading = ref(false);
const datasets = ref([]);
const tasks = ref([]);
const submissions = ref([]);
const loadingSubmissions = ref(false);
const savingDataset = ref(false);
const savingTask = ref(false);
const comparing = ref(false);
const datasetDialogVisible = ref(false);
const taskDialogVisible = ref(false);
const taskDetailVisible = ref(false);
const selectedSampleSubmissions = ref([]);
const taskDetail = ref(null);
const compareDatasetId = ref(null);
const compareRepeat = ref(2);
const comparison = ref(null);
const features = ref([]);
const enabledReviewVersions = computed(() => {
  const review = features.value.find((feature) => feature.featureCode === "REVIEW");
  return (review?.workflowVersions || []).filter((version) => version.status === "ENABLED");
});

const datasetForm = reactive({ name: "", description: "" });
const taskForm = reactive({ datasetId: null, workflowVersion: "", repeatCount: 2 });

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}
function statusLabel(status) {
  return ({ WAITING: "等待", RUNNING: "运行中", PAUSED: "已暂停", CANCELLED: "已取消", COMPLETED: "已完成", FAILED: "失败" })[status] || status;
}
function statusType(status) {
  return ({ COMPLETED: "success", FAILED: "danger", CANCELLED: "info", PAUSED: "warning", RUNNING: "warning" })[status] || "info";
}

async function loadDatasets() {
  loading.value = true;
  try {
    datasets.value = (await listEvaluationDatasets()).data || [];
    const [taskResponse, featureResponse] = await Promise.all([
      listEvaluationTasks(50), listEvaluationFeatures(),
    ]);
    tasks.value = taskResponse.data || [];
    features.value = featureResponse.data || [];
  } catch (error) {
    ElMessage.error(error.message || "评价数据加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadSubmissions() {
  loadingSubmissions.value = true;
  try {
    submissions.value = (await getAdminSubmissions(200)).data || [];
  } catch (error) {
    ElMessage.error(error.message || "提交数据加载失败");
  } finally {
    loadingSubmissions.value = false;
  }
}

async function openCreateDataset() {
  Object.assign(datasetForm, { name: "", description: "" });
  selectedSampleSubmissions.value = [];
  datasetDialogVisible.value = true;
  if (!submissions.value.length) await loadSubmissions();
}

function onSampleSelect(rows) {
  selectedSampleSubmissions.value = rows;
}

async function saveDataset() {
  if (!datasetForm.name.trim()) return ElMessage.warning("请输入测试集名称");
  if (selectedSampleSubmissions.value.length === 0) return ElMessage.warning("请至少选择一个测试样本");
  savingDataset.value = true;
  try {
    const samples = selectedSampleSubmissions.value.map((item) => ({ submissionId: item.id, note: "" }));
    await createEvaluationDataset({
      name: datasetForm.name.trim(),
      description: datasetForm.description.trim(),
      createdBy: Number(userStore.userId),
      samples,
    });
    datasetDialogVisible.value = false;
    await loadDatasets();
    ElMessage.success("测试集已创建");
  } catch (error) {
    ElMessage.error(error.message || "测试集创建失败");
  } finally {
    savingDataset.value = false;
  }
}

function openCreateTask() {
  if (!datasets.value.length) return ElMessage.warning("请先创建测试集");
  if (!enabledReviewVersions.value.length) return ElMessage.warning("当前没有可运行的评审版本");
  Object.assign(taskForm, { datasetId: null, workflowVersion: "", repeatCount: 2 });
  taskDialogVisible.value = true;
}

async function saveTask() {
  if (!taskForm.datasetId) return ElMessage.warning("请选择测试集");
  if (!taskForm.workflowVersion) return ElMessage.warning("请选择评审版本");
  savingTask.value = true;
  try {
    const candidate = {
      workflowVersion: taskForm.workflowVersion,
      modelExecutionConfigVersion: "MODEL_CFG_REVIEW_MULTIMODAL_0001",
      ragIndexVersion: null,
    };
    const estimate = (await estimateEvaluation({
      datasetId: taskForm.datasetId,
      candidates: [candidate],
      repeatCount: taskForm.repeatCount,
    })).data;
    if (!estimate.withinLimits) {
      ElMessage.error(`批次超过限制：${(estimate.violations || []).join("；")}`);
      return;
    }
    await ElMessageBox.confirm(
      `样本 ${estimate.sampleCount} 个，候选版本 ${estimate.versionCount} 个，重复 ${estimate.repeatCount} 次，`
        + `共 ${estimate.totalSlots} 个槽位，预计 ${estimate.estimatedCallCount} 次模型调用，优先级 ${estimate.priority}。`
        + `费用：${estimate.costExplanation}`,
      "确认运行评价批次",
      { confirmButtonText: "确认运行", cancelButtonText: "返回调整", type: "warning" },
    );
    await createEvaluationTask({
      datasetId: taskForm.datasetId,
      workflowVersion: taskForm.workflowVersion,
      repeatCount: taskForm.repeatCount,
      modelExecutionConfigVersion: candidate.modelExecutionConfigVersion,
      clientRequestId: `eval-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`,
    });
    taskDialogVisible.value = false;
    await loadDatasets();
    ElMessage.success("评价任务已启动");
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || "评价任务创建失败");
  } finally {
    savingTask.value = false;
  }
}

async function showTask(row) {
  try {
    taskDetail.value = (await getEvaluationTask(row.taskId)).data;
    taskDetailVisible.value = true;
  } catch (error) {
    ElMessage.error(error.message || "任务详情加载失败");
  }
}

async function retryTask(row) {
  try {
    await retryEvaluationTask(row.taskId);
    ElMessage.success("已重新排队");
    await loadDatasets();
  } catch (error) {
    ElMessage.error(error.message || "重试失败");
  }
}

async function controlTask(row, action) {
  const labels = { pause: "暂停", resume: "恢复", cancel: "取消" };
  try {
    if (action === "cancel") {
      await ElMessageBox.confirm("取消不会删除历史，运行中的结果仍会保留。确认取消？", "取消评价任务", {
        confirmButtonText: "确认取消", cancelButtonText: "返回", type: "warning",
      });
    }
    const operations = { pause: pauseEvaluationTask, resume: resumeEvaluationTask, cancel: cancelEvaluationTask };
    await operations[action](row.taskId);
    ElMessage.success(`任务已${labels[action]}`);
    await loadDatasets();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || `${labels[action]}任务失败`);
  }
}

async function runCompare() {
  if (!compareDatasetId.value) return;
  comparing.value = true;
  try {
    comparison.value = (await compareEvaluation(compareDatasetId.value, compareRepeat.value)).data;
  } catch (error) {
    ElMessage.error(error.message || "对比数据加载失败");
  } finally {
    comparing.value = false;
  }
}

onMounted(() => {
  loadDatasets();
});
</script>

<style scoped>
.pane-toolbar, .compare-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.panel-title { margin: 0; font-size: 18px; }
</style>
