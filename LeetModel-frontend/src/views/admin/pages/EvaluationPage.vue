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
          <el-table-column label="功能" width="110">
            <template #default="{ row }">{{ featureLabel(row.featureCode || 'REVIEW') }}</template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
          <el-table-column label="样本数" width="90" align="center">
            <template #default="{ row }">{{ row.sampleCount || row.samples?.length || 0 }}</template>
          </el-table-column>
          <el-table-column label="创建人" width="90" align="center">
            <template #default="{ row }">{{ row.createdBy }}</template>
          </el-table-column>
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
          <el-table-column label="功能" width="100">
            <template #default="{ row }">{{ featureLabel(row.featureCode || 'REVIEW') }}</template>
          </el-table-column>
          <el-table-column prop="workflowVersion" label="工作流版本" width="170" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small" effect="light">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" width="120" align="center">
            <template #default="{ row }">{{ row.terminalSlots ?? 0 }}/{{ row.totalSlots ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="版本选择指数" width="120" align="center">
            <template #default="{ row }">{{ row.versionSelectionIndex ?? '不可用' }}</template>
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
        <el-alert
          v-if="comparison"
          :title="comparison.comparable
            ? `同口径可比：${comparison.featureCode} / ${comparison.datasetVersion}`
            : `仅并排展示，不执行排名：${(comparison.incompatibilityReasons || []).join('；')}`"
          :type="comparison.comparable ? 'success' : 'warning'"
          :closable="false"
          show-icon
        />
        <el-table v-if="comparison" :data="comparison.versions || []" stripe v-loading="comparing" style="width: 100%; margin-top: 16px">
          <el-table-column prop="workflowVersion" label="工作流版本" width="170" />
          <el-table-column prop="modelExecutionConfigVersion" label="执行配置" min-width="210" show-overflow-tooltip />
          <el-table-column prop="metricSetVersion" label="指标口径" width="140" />
          <el-table-column prop="latestScoreResultVersion" label="结果口径" width="150" />
          <el-table-column label="版本选择指数" width="130" align="center">
            <template #default="{ row }">{{ row.versionSelectionIndex ?? '不可用' }}</template>
          </el-table-column>
          <el-table-column label="成功率" width="100" align="center"><template #default="{ row }">{{ row.successRate ?? '-' }}</template></el-table-column>
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
        <el-form-item label="功能" required>
          <el-select v-model="datasetForm.featureCode" style="width: 100%" @change="selectedSampleSubmissions = []">
            <el-option v-for="feature in features" :key="feature.featureCode" :label="feature.name" :value="feature.featureCode" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="datasetForm.featureCode === 'REVIEW'" label="样本" required>
          <el-table :data="submissions" height="260" @selection-change="onSampleSelect">
            <el-table-column type="selection" width="48" />
            <el-table-column prop="id" label="提交 ID" width="130" />
            <el-table-column prop="originalFilename" label="文件名" min-width="180" />
            <el-table-column prop="teamId" label="队伍" width="90" />
            <el-table-column prop="problemId" label="题目" width="90" />
          </el-table>
        </el-form-item>
        <el-form-item v-else label="客服问题" required>
          <el-input
            v-model="datasetForm.assistantQuestions"
            type="textarea"
            :rows="8"
            placeholder="每行一个独立测试问题，空行会被忽略"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDataset" @click="saveDataset">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新建评价任务 -->
    <el-dialog v-model="taskDialogVisible" title="新建评价任务" width="640px">
      <el-form :model="taskForm" label-width="110px">
        <el-form-item label="评价功能" required>
          <el-select v-model="taskForm.featureCode" style="width: 100%" @change="onTaskFeatureChange">
            <el-option v-for="feature in features" :key="feature.featureCode" :label="feature.name" :value="feature.featureCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="测试集" required>
          <el-select v-model="taskForm.datasetId" style="width: 100%" @change="taskEstimate = null">
            <el-option v-for="d in availableDatasets" :key="d.datasetId" :label="`${d.name} · ${d.datasetVersion || '历史数据集'}`" :value="d.datasetId" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作流版本" required>
          <el-select v-model="taskForm.workflowVersion" placeholder="请选择已启用版本" style="width: 100%" @change="onWorkflowChange">
            <el-option
              v-for="version in enabledWorkflowVersions"
              :key="version.workflowVersion"
              :label="`${version.name}（${version.workflowVersion}）`"
              :value="version.workflowVersion"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="requiresRagIndex" label="RAG 索引版本" required>
          <el-input v-model="taskForm.ragIndexVersion" placeholder="填写已经构建并固定的 ragIndexVersion" @input="taskEstimate = null" />
        </el-form-item>
        <el-form-item label="权重方案" required>
          <el-select v-model="taskForm.weightSchemeId" placeholder="请选择活动权重方案" style="width: 100%" @change="taskEstimate = null">
            <el-option
              v-for="scheme in availableWeightSchemes"
              :key="scheme.schemeId"
              :label="`${scheme.name} · ${scheme.schemeVersion}`"
              :value="scheme.schemeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="重复次数" required><el-input-number v-model="taskForm.repeatCount" :min="1" :max="20" @change="taskEstimate = null" /></el-form-item>
        <el-descriptions v-if="selectedWorkflow" :column="1" border size="small" class="config-summary">
          <el-descriptions-item label="输入口径">{{ selectedWorkflow.inputSchema }}</el-descriptions-item>
          <el-descriptions-item label="输出口径">{{ selectedWorkflow.outputSchema }}</el-descriptions-item>
          <el-descriptions-item label="模型执行配置">{{ modelConfigVersion }}</el-descriptions-item>
          <el-descriptions-item label="RAG 索引">{{ requiresRagIndex ? (taskForm.ragIndexVersion || '待填写') : '不适用' }}</el-descriptions-item>
          <el-descriptions-item label="兼容说明">{{ selectedWorkflow.compatibility }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="taskEstimate"
          :title="estimateSummary(taskEstimate)"
          :type="isHighCost(taskEstimate) ? 'warning' : 'info'"
          :closable="false"
          show-icon
          class="estimate-alert"
        />
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button :loading="estimatingTask" @click="previewTaskEstimate">更新预估</el-button>
        <el-button type="primary" :loading="savingTask" @click="saveTask">运行</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情 -->
    <el-dialog v-model="taskDetailVisible" title="评价任务详情" width="min(1180px, 94vw)">
      <el-descriptions v-if="taskDetail" :column="3" border size="small">
        <el-descriptions-item label="状态">{{ statusLabel(taskDetail.status) }}</el-descriptions-item>
        <el-descriptions-item label="功能">{{ featureLabel(taskDetail.featureCode || 'REVIEW') }}</el-descriptions-item>
        <el-descriptions-item label="工作流版本">{{ taskDetail.workflowVersion }}</el-descriptions-item>
        <el-descriptions-item label="执行配置">{{ taskDetail.modelExecutionConfigVersion || '历史默认配置' }}</el-descriptions-item>
        <el-descriptions-item label="RAG 索引">{{ taskDetail.ragIndexVersion || '不适用' }}</el-descriptions-item>
        <el-descriptions-item label="权重方案">{{ taskDetail.weightSchemeVersion || '历史任务未锁定' }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ taskDetail.terminalSlots ?? 0 }}/{{ taskDetail.totalSlots ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ taskDetail.retryCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="版本选择指数">{{ taskDetail.versionSelectionIndex ?? '不可用' }}</el-descriptions-item>
        <el-descriptions-item label="成功率">{{ taskDetail.successRate ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="平均耗时">{{ taskDetail.avgDurationMs != null ? `${taskDetail.avgDurationMs}ms` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近操作">{{ taskDetail.lastOperation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作者">{{ taskDetail.lastOperatedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatTime(taskDetail.lastOperatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="taskDetail?.scoreResults?.length">
        <div class="detail-heading-row">
          <h3 class="detail-title">版本选择指数与权重贡献</h3>
          <div class="recalculate-actions">
            <el-select v-model="recalculateWeightSchemeId" placeholder="选择另一活动权重方案" clearable style="width: 280px">
              <el-option
                v-for="scheme in recalculationSchemes"
                :key="scheme.schemeId"
                :label="`${scheme.name} · ${scheme.schemeVersion}`"
                :value="scheme.schemeId"
              />
            </el-select>
            <el-button :loading="recalculatingScore" @click="recalculateScore">追加重算结果</el-button>
          </div>
        </div>
        <el-alert
          title="版本选择指数仅用于既定评价目标下的同口径版本选择，不是准确率或客观质量分；重算会追加结果版本，不覆盖历史。"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-collapse class="score-results">
          <el-collapse-item v-for="result in taskDetail.scoreResults" :key="result.scoreResultId" :name="result.scoreResultId">
            <template #title>
              <span class="score-result-title">
                {{ result.scoreResultVersion }} · {{ result.weightSchemeVersion }} ·
                {{ result.status === 'CALCULATED' ? `指数 ${result.versionSelectionIndex}` : `不可用：${result.unavailableReason || '缺少可比数据'}` }}
              </span>
            </template>
            <el-descriptions :column="4" border size="small">
              <el-descriptions-item label="结果口径">{{ result.scoreResultVersion }}</el-descriptions-item>
              <el-descriptions-item label="指标口径">{{ result.metricSetVersion }}</el-descriptions-item>
              <el-descriptions-item label="权重方案">{{ result.weightSchemeVersion }}</el-descriptions-item>
              <el-descriptions-item label="计算人">{{ result.calculatedBy ?? '系统' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ scoreAvailabilityLabel(result.status) }}</el-descriptions-item>
              <el-descriptions-item label="版本选择指数">{{ result.versionSelectionIndex ?? '不可用' }}</el-descriptions-item>
              <el-descriptions-item label="不可用原因" :span="2">{{ result.unavailableReason || '无' }}</el-descriptions-item>
            </el-descriptions>
            <el-table :data="result.items || []" stripe class="contribution-table">
              <el-table-column prop="metricCode" label="指标" min-width="170" />
              <el-table-column prop="metricVersion" label="指标版本" min-width="170" />
              <el-table-column label="原值" width="120"><template #default="{ row }">{{ availabilityValue(row.rawAvailability, row.rawValue) }}</template></el-table-column>
              <el-table-column prop="normalizationVersion" label="归一化口径" min-width="190" />
              <el-table-column label="归一化值" width="130"><template #default="{ row }">{{ availabilityValue(row.normalizationAvailability, row.normalizedValue) }}</template></el-table-column>
              <el-table-column label="权重" width="100"><template #default="{ row }">{{ row.weightPercent == null ? '缺失' : `${row.weightPercent}%` }}</template></el-table-column>
              <el-table-column label="贡献值" width="110"><template #default="{ row }">{{ row.contributionValue ?? '不可用' }}</template></el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </template>
      <el-alert
        v-else-if="taskDetail"
        title="该任务没有版本选择指数结果；历史任务可能未锁定权重方案，不能据此比较或排名。"
        type="warning"
        :closable="false"
        show-icon
        class="section-alert"
      />
      <template v-if="taskDetail?.rawMetrics">
        <h3 class="detail-title">可信原始指标</h3>
        <el-alert
          title="以下为运行、资源与稳定性事实，不代表准确率或客观质量。缺失数据不会按 0 处理。"
          type="info"
          :closable="false"
          show-icon
        />
        <el-descriptions :column="3" border size="small" class="metric-descriptions">
          <el-descriptions-item label="指标口径">{{ taskDetail.rawMetrics.metricSetVersion }}</el-descriptions-item>
          <el-descriptions-item label="槽位成功率">{{ formatRatio(taskDetail.rawMetrics.runSuccessRate) }}</el-descriptions-item>
          <el-descriptions-item label="结构有效率">{{ formatRatio(taskDetail.rawMetrics.structureValidRate) }}</el-descriptions-item>
          <el-descriptions-item label="失败分类">{{ formatMap(taskDetail.rawMetrics.failureCounts) }}</el-descriptions-item>
          <el-descriptions-item label="调用审计">
            {{ taskDetail.rawMetrics.observedCallCount ?? 0 }}/{{ taskDetail.rawMetrics.expectedCallCount ?? 0 }}
            （{{ completenessLabel(taskDetail.rawMetrics.callAuditCompleteness) }}）
          </el-descriptions-item>
          <el-descriptions-item label="Token 完整性">
            完整 {{ taskDetail.rawMetrics.callAggregate?.usageCompleteCount ?? 0 }}，
            缺失 {{ taskDetail.rawMetrics.callAggregate?.usageMissingCount ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="Token 合计">{{ formatNullable(taskDetail.rawMetrics.callAggregate?.totalTokens) }}</el-descriptions-item>
          <el-descriptions-item label="费用">
            {{ formatCost(taskDetail.rawMetrics.callAggregate) }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时缺失">{{ taskDetail.rawMetrics.callAggregate?.durationMissingCount ?? 0 }} 次</el-descriptions-item>
          <el-descriptions-item label="平均排队">{{ formatDuration(taskDetail.rawMetrics.callAggregate?.averageQueueMs) }}</el-descriptions-item>
          <el-descriptions-item label="平均执行">{{ formatDuration(taskDetail.rawMetrics.callAggregate?.averageExecutionMs) }}</el-descriptions-item>
          <el-descriptions-item label="平均总耗时">{{ formatDuration(taskDetail.rawMetrics.callAggregate?.averageTotalMs) }}</el-descriptions-item>
        </el-descriptions>
        <el-table
          v-if="taskDetail.rawMetrics.reviewSampleStatistics?.length"
          :data="taskDetail.rawMetrics.reviewSampleStatistics"
          stripe
          class="sample-statistics"
        >
          <el-table-column prop="sampleId" label="样本" width="90" />
          <el-table-column label="有效重复" width="100">
            <template #default="{ row }">{{ row.validCount }}/{{ row.expectedCount }}</template>
          </el-table-column>
          <el-table-column label="完整性" width="100">
            <template #default="{ row }">{{ completenessLabel(row.completeness) }}</template>
          </el-table-column>
          <el-table-column label="均值"><template #default="{ row }">{{ formatNullable(row.mean) }}</template></el-table-column>
          <el-table-column label="方差"><template #default="{ row }">{{ formatNullable(row.variance) }}</template></el-table-column>
          <el-table-column label="标准差"><template #default="{ row }">{{ formatNullable(row.standardDeviation) }}</template></el-table-column>
          <el-table-column label="极差"><template #default="{ row }">{{ formatNullable(row.range) }}</template></el-table-column>
        </el-table>
        <el-table
          v-if="taskDetail.rawMetrics.assistantMetricSummaries?.length"
          :data="taskDetail.rawMetrics.assistantMetricSummaries"
          stripe
          class="sample-statistics"
        >
          <el-table-column label="客服/RAG 指标" min-width="190">
            <template #default="{ row }">{{ assistantMetricLabel(row.metricCode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ metricStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="值" width="100">
            <template #default="{ row }">{{ row.value == null ? '未评价' : `${Number(row.value).toFixed(2)}%` }}</template>
          </el-table-column>
          <el-table-column label="证据覆盖" width="100">
            <template #default="{ row }">{{ row.evaluatedCount }}/{{ row.eligibleCount }}</template>
          </el-table-column>
          <el-table-column prop="evidence" label="证据/规则" min-width="240" show-overflow-tooltip />
        </el-table>
      </template>
      <template v-if="taskDetail">
        <div class="detail-heading-row">
          <h3 class="detail-title">单次调用追踪</h3>
          <el-button :loading="loadingTaskCalls" @click="loadTaskCalls(taskDetail.taskId)">刷新调用</el-button>
        </div>
        <el-alert
          v-if="taskCallsError"
          :title="taskCallsError"
          type="error"
          :closable="false"
          show-icon
        />
        <el-table :data="taskCalls" stripe v-loading="loadingTaskCalls" class="call-table">
          <el-table-column prop="callId" label="callId" min-width="190" show-overflow-tooltip />
          <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="callStatusType(row.status)" size="small">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="Token" width="130"><template #default="{ row }">{{ usageText(row) }}</template></el-table-column>
          <el-table-column label="费用" width="150"><template #default="{ row }">{{ callCostText(row) }}</template></el-table-column>
          <el-table-column label="排队/执行/总耗时" min-width="180"><template #default="{ row }">{{ durationTraceText(row) }}</template></el-table-column>
          <el-table-column label="队列状态" width="120"><template #default="{ row }">{{ queueState(row.callId) }}</template></el-table-column>
          <el-table-column label="失败依据" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.status === 'FAILED' ? (row.errorMessage || `错误码 ${row.errorCode ?? '缺失'}`) : '-' }}</template></el-table-column>
          <template #empty><el-empty :description="taskCallsError || '未查询到调用记录；可点击运行槽位中的 callId 单独追踪'" /></template>
        </el-table>
      </template>
      <el-table v-if="taskDetail?.runs?.length" :data="taskDetail.runs" stripe style="margin-top: 16px">
        <el-table-column prop="sampleId" label="样本" width="90" />
        <el-table-column prop="submissionId" label="提交" width="100" />
        <el-table-column prop="repetitionNo" label="重复" width="70" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="得分" width="90" align="center"><template #default="{ row }">{{ row.score ?? '-' }}</template></el-table-column>
        <el-table-column label="callId" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.aiCallId" link type="primary" @click="loadCall(row.aiCallId)">{{ row.aiCallId }}</el-button>
            <span v-else>缺失</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100"><template #default="{ row }">{{ formatDuration(row.durationMs) }}</template></el-table-column>
        <el-table-column prop="failureType" label="失败类型" min-width="120" />
        <el-table-column prop="errorMessage" label="失败信息" min-width="180" show-overflow-tooltip />
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
  listEvaluationWeightSchemes, recalculateEvaluationScore, getAdminAiCalls, getAdminAiQueue,
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
const estimatingTask = ref(false);
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
const weightSchemes = ref([]);
const taskEstimate = ref(null);
const taskCalls = ref([]);
const taskQueue = ref([]);
const taskCallsError = ref("");
const loadingTaskCalls = ref(false);
const recalculateWeightSchemeId = ref(null);
const recalculatingScore = ref(false);
const selectedFeature = computed(() => features.value.find(
  (feature) => feature.featureCode === taskForm.featureCode,
));
const enabledWorkflowVersions = computed(() => (selectedFeature.value?.workflowVersions || [])
  .filter((version) => version.status === "ENABLED"));
const selectedWorkflow = computed(() => enabledWorkflowVersions.value.find(
  (version) => version.workflowVersion === taskForm.workflowVersion,
));
const availableDatasets = computed(() => datasets.value.filter(
  (dataset) => (dataset.featureCode || "REVIEW") === taskForm.featureCode,
));
const availableWeightSchemes = computed(() => weightSchemes.value.filter(
  (scheme) => scheme.featureCode === taskForm.featureCode && scheme.status === "ACTIVE",
));
const requiresRagIndex = computed(() => taskForm.workflowVersion === "ASSISTANT_RAG_V1");
const modelConfigVersion = computed(() => taskForm.featureCode === "ASSISTANT"
  ? "MODEL_CFG_ASSISTANT_TEXT_0001" : "MODEL_CFG_REVIEW_MULTIMODAL_0001");
const recalculationSchemes = computed(() => weightSchemes.value.filter((scheme) => (
  scheme.featureCode === (taskDetail.value?.featureCode || "REVIEW")
  && scheme.status === "ACTIVE"
  && !taskDetail.value?.scoreResults?.some((result) => String(result.weightSchemeId) === String(scheme.schemeId))
)));

const datasetForm = reactive({ name: "", description: "", featureCode: "REVIEW", assistantQuestions: "" });
const taskForm = reactive({
  featureCode: "REVIEW", datasetId: null, workflowVersion: "", ragIndexVersion: "",
  weightSchemeId: null, repeatCount: 2,
});

function featureLabel(code) {
  return features.value.find((feature) => feature.featureCode === code)?.name || code;
}

function formatTime(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "-";
}
function formatNullable(value) {
  return value == null ? "未提供" : value;
}
function formatRatio(value) {
  return value == null ? "不可计算" : `${Number(value).toFixed(2)}%`;
}
function formatDuration(value) {
  return value == null ? "未提供" : `${value}ms`;
}
function formatMap(value) {
  const entries = Object.entries(value || {});
  return entries.length ? entries.map(([key, count]) => `${key}: ${count}`).join("；") : "无";
}
function completenessLabel(value) {
  return ({ COMPLETE: "完整", PARTIAL: "部分缺失", MISSING: "缺失", INSUFFICIENT: "样本不足" })[value] || value || "未知";
}
function formatCost(aggregate) {
  const totals = Object.entries(aggregate?.costTotals || {});
  const amount = totals.length ? totals.map(([currency, value]) => `${value} ${currency}`).join("；") : "未提供";
  return `${amount}（实际 ${aggregate?.actualCostCount ?? 0}，估算 ${aggregate?.estimatedCostCount ?? 0}，缺失 ${aggregate?.costMissingCount ?? 0}）`;
}
function assistantMetricLabel(code) {
  return ({
    RETRIEVAL_HIT_RATE: "检索命中率",
    SOURCE_COVERAGE_RATE: "标准来源覆盖率",
    FORMAT_RULE_PASS_RATE: "格式规则通过率",
    EXPECTED_POINT_COVERAGE_RATE: "标准要点文本覆盖率",
    HUMAN_QUALITY_SCORE: "人工质量评分",
  })[code] || code;
}
function metricStatusLabel(status) {
  return ({ AVAILABLE: "已评价", PARTIAL: "部分评价", NOT_EVALUATED: "未评价", NOT_APPLICABLE: "不适用" })[status] || status;
}
function scoreAvailabilityLabel(status) {
  return ({ AVAILABLE: "可用", CALCULATED: "已计算", UNAVAILABLE: "不可用" })[status] || status || "未知";
}
function availabilityValue(availability, value) {
  if (availability !== "AVAILABLE" || value == null) return `${scoreAvailabilityLabel(availability)}（无数值）`;
  return value;
}
function callStatusType(status) {
  return ({ SUCCEEDED: "success", FAILED: "danger", RUNNING: "warning", QUEUED: "info" })[status] || "info";
}
function usageText(call) {
  return call.totalTokens == null
    ? `缺失（${call.usageCompleteness || "未说明"}）`
    : `${call.totalTokens}（${call.usageCompleteness || "完整性未知"}）`;
}
function callCostText(call) {
  if (call.costAmount == null) return `缺失（${call.costCompleteness || "未说明"}）`;
  return `${call.costAmount} ${call.costCurrency || ""} · ${call.costSource || "来源未知"}`;
}
function durationTraceText(call) {
  return `${call.queueMs == null ? "缺失" : `${call.queueMs}ms`} / `
    + `${call.executionMs == null ? "缺失" : `${call.executionMs}ms`} / `
    + `${call.totalMs == null ? "缺失" : `${call.totalMs}ms`}`;
}
function queueState(callId) {
  const queue = taskQueue.value.find((item) => item.callId === callId);
  if (!queue) return "无活动队列项";
  return `${queue.state}${queue.waitMs == null ? "" : ` · ${queue.waitMs}ms`}`;
}
function statusLabel(status) {
  return ({ WAITING: "等待", RUNNING: "运行中", PAUSED: "已暂停", CANCELLED: "已取消", COMPLETED: "已完成", FAILED: "失败" })[status] || status;
}
function statusType(status) {
  return ({ COMPLETED: "success", FAILED: "danger", CANCELLED: "info", PAUSED: "warning", RUNNING: "warning" })[status] || "info";
}
function estimateSummary(estimate) {
  const cost = estimate.estimatedCostAmount == null
    ? estimate.costExplanation
    : `${estimate.estimatedCostAmount} ${estimate.costCurrency || ""} · ${estimate.costExplanation}`;
  return `${estimate.sampleCount} 个样本 × ${estimate.repeatCount} 次，${estimate.totalSlots} 个槽位，`
    + `预计 ${estimate.estimatedCallCount} 次模型调用；费用 ${cost}`;
}
function isHighCost(estimate) {
  return Number(estimate?.estimatedCallCount || 0) >= 20
    || Number(estimate?.estimatedCostAmount || 0) >= 1;
}

async function loadDatasets() {
  loading.value = true;
  try {
    datasets.value = (await listEvaluationDatasets()).data || [];
    const [taskResponse, featureResponse, weightResponse] = await Promise.all([
      listEvaluationTasks(50), listEvaluationFeatures(), listEvaluationWeightSchemes({ status: "ACTIVE" }),
    ]);
    tasks.value = taskResponse.data || [];
    features.value = featureResponse.data || [];
    weightSchemes.value = weightResponse.data || [];
  } catch (error) {
    ElMessage.error(error.message || "评价数据加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadSubmissions() {
  loadingSubmissions.value = true;
  try {
    submissions.value = (await getAdminSubmissions(100)).data || [];
  } catch (error) {
    ElMessage.error(error.message || "提交数据加载失败");
  } finally {
    loadingSubmissions.value = false;
  }
}

async function openCreateDataset() {
  Object.assign(datasetForm, { name: "", description: "", featureCode: "REVIEW", assistantQuestions: "" });
  selectedSampleSubmissions.value = [];
  datasetDialogVisible.value = true;
  if (!submissions.value.length) await loadSubmissions();
}

function onSampleSelect(rows) {
  selectedSampleSubmissions.value = rows;
}

async function saveDataset() {
  if (!datasetForm.name.trim()) return ElMessage.warning("请输入测试集名称");
  const questions = datasetForm.assistantQuestions.split("\n").map((item) => item.trim()).filter(Boolean);
  if (datasetForm.featureCode === "REVIEW" && selectedSampleSubmissions.value.length === 0) {
    return ElMessage.warning("请至少选择一个测试样本");
  }
  if (datasetForm.featureCode === "ASSISTANT" && questions.length === 0) {
    return ElMessage.warning("请至少填写一个客服测试问题");
  }
  savingDataset.value = true;
  try {
    const samples = datasetForm.featureCode === "REVIEW"
      ? selectedSampleSubmissions.value.map((item) => ({ submissionId: item.id, note: "" }))
      : questions.map((question) => ({
        note: "",
        payload: {
          sampleType: "QUESTION",
          payloadSchemaVersion: "ASSISTANT_QUESTION_V1",
          payloadJson: JSON.stringify({ question }),
        },
      }));
    await createEvaluationDataset({
      name: datasetForm.name.trim(),
      description: datasetForm.description.trim(),
      createdBy: Number(userStore.userId),
      featureCode: datasetForm.featureCode,
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
  if (!features.value.some((feature) => feature.workflowVersions?.some((version) => version.status === "ENABLED"))) {
    return ElMessage.warning("当前没有可运行的工作流版本");
  }
  Object.assign(taskForm, {
    featureCode: datasets.value[0]?.featureCode || "REVIEW",
    datasetId: null,
    workflowVersion: "",
    ragIndexVersion: "",
    weightSchemeId: null,
    repeatCount: 2,
  });
  taskEstimate.value = null;
  taskDialogVisible.value = true;
}

function onTaskFeatureChange() {
  Object.assign(taskForm, {
    datasetId: null, workflowVersion: "", ragIndexVersion: "", weightSchemeId: null,
  });
  taskEstimate.value = null;
}

function onWorkflowChange() {
  if (!requiresRagIndex.value) taskForm.ragIndexVersion = "";
  taskEstimate.value = null;
}

function evaluationCandidate() {
  return {
    workflowVersion: taskForm.workflowVersion,
    modelExecutionConfigVersion: modelConfigVersion.value,
    ragIndexVersion: requiresRagIndex.value ? taskForm.ragIndexVersion.trim() : null,
  };
}

function validateTaskForm() {
  if (!taskForm.datasetId) return ElMessage.warning("请选择测试集");
  if (!taskForm.workflowVersion) return ElMessage.warning("请选择工作流版本");
  if (requiresRagIndex.value && !taskForm.ragIndexVersion.trim()) return ElMessage.warning("请填写固定的 RAG 索引版本");
  if (!taskForm.weightSchemeId) return ElMessage.warning("请选择权重方案");
  return true;
}

async function requestTaskEstimate() {
  const response = await estimateEvaluation({
    datasetId: taskForm.datasetId,
    candidates: [evaluationCandidate()],
    repeatCount: taskForm.repeatCount,
  });
  taskEstimate.value = response.data;
  if (!taskEstimate.value.withinLimits) {
    throw new Error(`批次超过限制：${(taskEstimate.value.violations || []).join("；")}`);
  }
  return taskEstimate.value;
}

async function previewTaskEstimate() {
  if (validateTaskForm() !== true) return;
  estimatingTask.value = true;
  try {
    await requestTaskEstimate();
  } catch (error) {
    ElMessage.error(error.message || "评价规模预估失败");
  } finally {
    estimatingTask.value = false;
  }
}

async function saveTask() {
  if (validateTaskForm() !== true) return;
  savingTask.value = true;
  try {
    const estimate = await requestTaskEstimate();
    await ElMessageBox.confirm(
      estimateSummary(estimate),
      "确认评价任务配置",
      { confirmButtonText: "继续", cancelButtonText: "返回调整", type: "info" },
    );
    if (isHighCost(estimate)) {
      await ElMessageBox.confirm(
        `${estimateSummary(estimate)}。该任务达到高成本门槛，请再次确认。`,
        "高成本任务二次确认",
        { confirmButtonText: "确认运行", cancelButtonText: "返回调整", type: "warning" },
      );
    }
    const candidate = evaluationCandidate();
    await createEvaluationTask({
      datasetId: taskForm.datasetId,
      workflowVersion: taskForm.workflowVersion,
      repeatCount: taskForm.repeatCount,
      modelExecutionConfigVersion: candidate.modelExecutionConfigVersion,
      ragIndexVersion: candidate.ragIndexVersion,
      weightSchemeId: taskForm.weightSchemeId,
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
    recalculateWeightSchemeId.value = null;
    taskDetailVisible.value = true;
    await loadTaskCalls(row.taskId);
  } catch (error) {
    ElMessage.error(error.message || "任务详情加载失败");
  }
}

async function loadTaskCalls(taskId) {
  loadingTaskCalls.value = true;
  taskCallsError.value = "";
  try {
    const [callsResponse, queueResponse] = await Promise.all([
      getAdminAiCalls({ evaluationTaskId: String(taskId), limit: 100 }),
      getAdminAiQueue({ evaluationTaskId: String(taskId), limit: 100 }),
    ]);
    taskCalls.value = callsResponse.data || [];
    taskQueue.value = queueResponse.data || [];
  } catch (error) {
    taskCalls.value = [];
    taskQueue.value = [];
    taskCallsError.value = error.message || "调用与队列追踪加载失败";
  } finally {
    loadingTaskCalls.value = false;
  }
}

async function loadCall(callId) {
  loadingTaskCalls.value = true;
  taskCallsError.value = "";
  try {
    const rows = (await getAdminAiCalls({ callId, limit: 1 })).data || [];
    if (!rows.length) {
      taskCallsError.value = `未找到 callId ${callId} 的网关审计记录`;
      return;
    }
    taskCalls.value = [rows[0], ...taskCalls.value.filter((item) => item.callId !== callId)];
  } catch (error) {
    taskCallsError.value = error.message || `callId ${callId} 追踪失败`;
  } finally {
    loadingTaskCalls.value = false;
  }
}

async function recalculateScore() {
  if (!recalculateWeightSchemeId.value) return ElMessage.warning("请选择另一活动权重方案");
  recalculatingScore.value = true;
  try {
    await ElMessageBox.confirm(
      "重算只引用原始指标并追加新的结果版本，不会覆盖历史。确认继续？",
      "追加选择指数结果",
      { confirmButtonText: "确认重算", cancelButtonText: "取消", type: "warning" },
    );
    await recalculateEvaluationScore(taskDetail.value.taskId, recalculateWeightSchemeId.value);
    taskDetail.value = (await getEvaluationTask(taskDetail.value.taskId)).data;
    recalculateWeightSchemeId.value = null;
    await loadDatasets();
    ElMessage.success("已追加重算结果版本");
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || "选择指数重算失败");
  } finally {
    recalculatingScore.value = false;
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
.detail-title { margin: 20px 0 12px; font-size: 16px; }
.metric-descriptions { margin-top: 12px; }
.sample-statistics { margin-top: 16px; }
.config-summary, .estimate-alert { margin-top: 12px; }
.detail-heading-row { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.recalculate-actions { display: flex; align-items: center; gap: 10px; margin-top: 16px; }
.score-results, .contribution-table, .call-table, .section-alert { margin-top: 16px; }
.score-result-title { font-weight: 600; }
</style>
