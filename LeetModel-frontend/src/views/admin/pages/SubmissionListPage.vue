<template>
  <div class="submission-list">
    <el-card shadow="never">
      <div class="action-bar">
        <el-input v-model="filters.keyword" placeholder="搜索题目/作者/标题" style="width: 250px" clearable />
        <el-select v-model="filters.status" placeholder="评审状态" clearable style="width: 150px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="评审中" value="EVALUATING" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <div class="action-buttons">
          <el-button type="primary" icon="Search" @click="applyFilters">筛选</el-button>
          <el-button icon="Refresh" @click="fetchData">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增作品
          </el-button>
        </div>
      </div>

      <el-table :data="tableData" style="width: 100%" stripe v-loading="loading">
        <el-table-column prop="submissionId" label="作品ID" width="80" />
        <el-table-column prop="problemTitle" label="所属题目" min-width="180" />
        <el-table-column prop="username" label="作者" width="120" />
        <el-table-column prop="title" label="作品标题" min-width="160" />
        <el-table-column label="提交时间" width="160">
          <template #default="scope">{{ formatTime(scope.row.submitTime) }}</template>
        </el-table-column>
        <el-table-column prop="totalScore" label="AI评分" width="80">
          <template #default="scope">
            <span :style="{ color: scope.row.totalScore != null && scope.row.totalScore >= 80 ? '#67C23A' : '#E6A23C' }">
              {{ scope.row.totalScore != null ? scope.row.totalScore : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="viewDetail(scope.row)">查看详情</el-button>
            <el-button size="small" type="warning" link @click="reEvaluate(scope.row)">重新评测</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无作品数据" />
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          background
          layout="prev, pager, next, total"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新增作品" width="720px" destroy-on-close @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-form-item label="关联题目" prop="problemId">
          <el-select v-model="form.problemId" placeholder="请选择题目" style="width: 100%" filterable>
            <el-option
              v-for="p in problemList"
              :key="p.problemId"
              :label="p.problemTitle"
              :value="p.problemId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="作品标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入作品标题" />
        </el-form-item>

        <el-form-item label="论文附件" prop="file">
          <el-upload
            class="upload-demo"
            drag
            action="#"
            :limit="1"
            accept=".md"
            :on-change="handleFileChange"
            :auto-upload="false"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">点击或拖拽上传论文 (Markdown .md)</div>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">确认提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="评审详情" width="860px" destroy-on-close @closed="stopDetailPolling">
      <div v-if="detail" class="detail-content">
        <div class="detail-header">
          <div class="detail-header-left">
            <h3 class="detail-title">{{ detail.title }}</h3>
            <div class="detail-meta">
              <span>题目：{{ detail.problemTitle }}</span>
              <span>作者：{{ detail.username }}</span>
              <span>提交：{{ formatTime(detail.submitTime) }}</span>
              <span>完成：{{ formatTime(detail.completeTime) }}</span>
            </div>
          </div>
          <div class="detail-header-right">
            <div class="total-score-circle" :class="scoreLevel(detail.totalScore)">
              <span class="total-score-num">{{ detail.totalScore != null ? detail.totalScore : '-' }}</span>
              <span class="total-score-label">总分</span>
            </div>
            <el-tag :type="getStatusType(detail.status)" size="default">{{ getStatusText(detail.status) }}</el-tag>
          </div>
        </div>

        <el-divider />

        <div v-if="detail.reviews && detail.reviews.length" class="review-cards">
          <div v-for="r in detail.reviews" :key="r.reviewId" class="review-card" :class="{ 'is-failed': r.status === 'FAILED', 'is-running': r.status === 'RUNNING' || r.status === 'PENDING' }">
            <div class="review-card-top">
              <span class="review-dim-name">{{ r.dimensionName }}</span>
              <el-tag :type="getStatusType(r.status)" size="small" effect="plain">{{ getStatusText(r.status) }}</el-tag>
            </div>
            <div class="review-card-body" v-if="r.status === 'COMPLETED'">
              <div class="review-score-row">
                <el-progress :percentage="r.score" :color="scoreBarColor(r.score)" :stroke-width="8">
                  <span class="score-text">{{ r.score }} / 100</span>
                </el-progress>
                <span class="review-weight">权重 {{ (r.weight * 100).toFixed(0) }}%</span>
              </div>
              <div class="review-feedback">{{ r.feedback }}</div>
            </div>
            <div class="review-card-body reviewing" v-else-if="r.status === 'RUNNING' || r.status === 'PENDING'">
              <el-skeleton :rows="2" animated />
            </div>
            <div class="review-card-body failed" v-else>
              <span class="failed-hint">评审失败，未能获得该维度分数</span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无评审数据" :image-size="80" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onUnmounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { getSubmissionList, getSubmissionDetail, createSubmission, reEvaluateSubmission, deleteSubmission } from '@/api/submission';
import { uploadFile } from '@/api/file';
import { getProblemList } from '@/api/problem';

const formatTime = (val) => {
  if (!val) return '-';
  const d = new Date(val);
  if (isNaN(d.getTime())) return val;
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

const getStatusType = (status) => {
  const map = { PENDING: 'info', EVALUATING: 'warning', COMPLETED: 'success', FAILED: 'danger' };
  return map[status] || 'info';
};

const getStatusText = (status) => {
  const map = { PENDING: '待审核', EVALUATING: '评审中', COMPLETED: '已完成', FAILED: '失败' };
  return map[status] || status;
};

const scoreLevel = (score) => {
  if (score == null) return '';
  if (score >= 80) return 'score-high';
  if (score >= 60) return 'score-mid';
  return 'score-low';
};

const scoreBarColor = (score) => {
  if (score >= 80) return '#67C23A';
  if (score >= 60) return '#E6A23C';
  return '#F56C6C';
};

// -- filters & pagination --
const filters = reactive({ keyword: '', status: '' });
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const tableData = ref([]);

// -- create dialog --
const createDialogVisible = ref(false);
const submitLoading = ref(false);
const formRef = ref(null);
const problemList = ref([]);
const form = reactive({ problemId: null, title: '', file: null });
const formRules = {
  problemId: [{ required: true, message: '请选择关联题目', trigger: 'change' }],
  title: [{ required: true, message: '作品标题必填', trigger: 'blur' }],
  file: [{ required: true, message: '请上传论文附件', trigger: 'change' }],
};

const resetForm = () => {
  form.problemId = null;
  form.title = '';
  form.file = null;
};

const loadProblems = async () => {
  try {
    const res = await getProblemList({ page: 1, pageSize: 200 });
    if (res.code === 20000 && res.data) {
      problemList.value = res.data.records || [];
    } else {
      ElMessage.error(res.msg || '获取题目列表失败');
    }
  } catch (error) {
    console.error('获取题目列表失败', error);
  }
};

const openCreateDialog = () => {
  loadProblems();
  createDialogVisible.value = true;
};

const handleFileChange = (file) => {
  form.file = file.raw;
};

const onSubmit = async () => {
  if (!formRef.value) return;
  try { await formRef.value.validate(); } catch { return; }
  submitLoading.value = true;
  try {
    const uploadRes = await uploadFile(form.file);
    if (uploadRes.code !== 20000) {
      ElMessage.error(uploadRes.msg || '文件上传失败');
      return;
    }
    const createRes = await createSubmission({
      problemId: form.problemId,
      title: form.title,
      contentFileId: uploadRes.data.fileId,
    });
    if (createRes.code === 20000) {
      ElMessage.success('作品提交成功，已加入评审队列');
      createDialogVisible.value = false;
      resetForm();
      fetchData();
    } else {
      ElMessage.error(createRes.msg || '提交失败');
    }
  } catch (error) {
    console.error('提交作品失败', error);
    ElMessage.error('提交作品失败，请稍后重试');
  } finally {
    submitLoading.value = false;
  }
};

const applyFilters = () => {
  currentPage.value = 1;
  fetchData();
};

// -- detail dialog --
const detailDialogVisible = ref(false);
const detail = ref(null);
let detailPollTimer = null;

const viewDetail = async (row) => {
  try {
    const res = await getSubmissionDetail(row.submissionId);
    if (res.code === 20000) {
      detail.value = res.data;
      detailDialogVisible.value = true;
      if (detail.value.status === 'PENDING' || detail.value.status === 'EVALUATING') {
        startDetailPolling(row.submissionId);
      }
    } else {
      ElMessage.error(res.msg || '获取详情失败');
    }
  } catch (error) {
    console.error('获取详情失败', error);
    ElMessage.error('获取详情失败');
  }
};

const startDetailPolling = (submissionId) => {
  stopDetailPolling();
  detailPollTimer = setInterval(async () => {
    try {
      const res = await getSubmissionDetail(submissionId);
      if (res.code === 20000) {
        detail.value = res.data;
        if (detail.value.status !== 'PENDING' && detail.value.status !== 'EVALUATING') {
          stopDetailPolling();
        }
      }
    } catch { /* silent */ }
  }, 3000);
};

const stopDetailPolling = () => {
  if (detailPollTimer) { clearInterval(detailPollTimer); detailPollTimer = null; }
};

// -- actions --
const reEvaluate = (row) => {
  ElMessageBox.confirm(`确定重新评测作品「${row.title}」吗？`, '确认', { type: 'warning' })
    .then(async () => {
      try {
        const res = await reEvaluateSubmission(row.submissionId);
        if (res.code === 20000) {
          ElMessage.success('已提交重新评测请求');
          fetchData();
        } else {
          ElMessage.error(res.msg || '操作失败');
        }
      } catch (error) {
        console.error('重新评测失败', error);
        ElMessage.error('操作失败');
      }
    })
    .catch(() => {});
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除作品「${row.title}」吗？此操作不可恢复。`, '确认删除', { type: 'warning', confirmButtonText: '确定删除' })
    .then(async () => {
      try {
        const res = await deleteSubmission(row.submissionId);
        if (res.code === 20000) {
          ElMessage.success('删除成功');
          fetchData();
        } else {
          ElMessage.error(res.msg || '删除失败');
        }
      } catch (error) {
        console.error('删除失败', error);
        ElMessage.error('删除失败');
      }
    })
    .catch(() => {});
};

// -- data fetch & polling --
let pollTimer = null;

const shouldPoll = () => {
  return tableData.value.some(row => row.status === 'PENDING' || row.status === 'EVALUATING');
};

const stopPolling = () => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
};

const doFetch = async (silent) => {
  if (!silent) loading.value = true;
  try {
    const params = { page: currentPage.value, pageSize: pageSize.value };
    if (filters.keyword) params.keyword = filters.keyword;
    if (filters.status) params.status = filters.status;
    const res = await getSubmissionList(params);
    if (res.code === 20000) {
      tableData.value = res.data.records || [];
      total.value = res.data.total || 0;
    } else if (!silent) {
      ElMessage.error(res.msg || '获取数据失败');
    }
  } catch (error) {
    if (!silent) {
      console.error('获取作品列表失败', error);
      ElMessage.error('获取数据失败');
    }
  } finally {
    if (!silent) loading.value = false;
  }
};

const startPolling = () => {
  if (pollTimer) return;
  pollTimer = setInterval(() => {
    if (!shouldPoll()) { stopPolling(); return; }
    doFetch(true);
  }, 3000);
};

const fetchData = () => {
  doFetch(false).then(() => {
    if (shouldPoll()) startPolling();
  });
};

fetchData();

onUnmounted(() => {
  stopPolling();
  stopDetailPolling();
});
</script>

<style scoped>
.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.action-buttons {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.detail-content {
  max-height: 70vh;
  overflow-y: auto;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
}

.detail-header-left {
  flex: 1;
  min-width: 0;
}

.detail-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: #303133;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  font-size: 13px;
  color: #909399;
}

.detail-header-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.total-score-circle {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 3px solid #dcdfe6;
  background: #fafafa;
}

.total-score-circle.score-high {
  border-color: #67C23A;
  background: #f0f9eb;
}

.total-score-circle.score-mid {
  border-color: #E6A23C;
  background: #fdf6ec;
}

.total-score-circle.score-low {
  border-color: #F56C6C;
  background: #fef0f0;
}

.total-score-num {
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

.total-score-circle.score-high .total-score-num { color: #67C23A; }
.total-score-circle.score-mid .total-score-num { color: #E6A23C; }
.total-score-circle.score-low .total-score-num { color: #F56C6C; }

.total-score-label {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.review-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fff;
  transition: border-color 0.2s;
}

.review-card.is-failed {
  border-color: #fab6b6;
  background: #fef0f0;
}

.review-card.is-running {
  border-color: #e6c37a;
  background: #fef9f0;
}

.review-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.review-dim-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.review-score-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.review-score-row .el-progress {
  flex: 1;
}

.score-text {
  font-size: 12px;
  font-weight: 600;
}

.review-weight {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  flex-shrink: 0;
}

.review-feedback {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.review-feedback::before {
  content: '反馈：';
  font-weight: 600;
  color: #303133;
}

.failed-hint {
  font-size: 13px;
  color: #F56C6C;
}

.review-card-body.failed {
  padding: 8px 0;
}
</style>
