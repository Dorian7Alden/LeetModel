<template>
  <div class="submission-list">
    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="filters.query" placeholder="搜索题目ID或作者" style="width: 250px" />
        <el-select v-model="filters.status" placeholder="评测状态" clearable style="width: 150px">
          <el-option label="Pending" value="Pending" />
          <el-option label="Evaluating" value="Evaluating" />
          <el-option label="Finished" value="Finished" />
        </el-select>
        <div class="action-buttons">
          <el-button type="primary" icon="Search">筛选</el-button>
          <el-button icon="Refresh" @click="fetchData">刷新</el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增作品
          </el-button>
        </div>
      </div>

      <el-table :data="tableData" style="width: 100%" stripe v-loading="loading">
        <el-table-column prop="id" label="作品流水" width="100" />
        <el-table-column prop="problemTitle" label="所属题目" min-width="180" />
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <el-table-column prop="score" label="AI评分" width="80">
          <template #default="scope">
            <span :style="{ color: scope.row.score >= 80 ? '#67C23A' : '#E6A23C' }">
              {{ scope.row.score || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link>查看详情</el-button>
            <el-button size="small" type="danger" link>重新评测</el-button>
          </template>
        </el-table-column>
      </el-table>

       <div class="pagination-container">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="45"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="createDialogVisible"
      title="新增作品"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-form-item label="关联题目ID" prop="problemId">
          <el-input v-model="form.problemId" placeholder="例如：12" />
        </el-form-item>

        <el-form-item label="作者或队伍名" prop="author">
          <el-input v-model="form.author" placeholder="参赛队伍 / 个人姓名" />
        </el-form-item>

        <el-form-item label="论文附件" prop="paper">
          <el-upload
            class="upload-demo"
            drag
            action="https://run.mocky.io/v3/9d059bf9-4660-45f2-925d-ce80ad6c4d15"
            :limit="1"
            accept=".pdf,.doc,.docx"
            :on-change="handlePaperChange"
            :auto-upload="false"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">点击或拖拽上传论文 (PDF / DOC)</div>
          </el-upload>
        </el-form-item>

        <el-form-item label="代码附件" prop="code">
          <el-upload
            class="upload-demo"
            action="https://run.mocky.io/v3/9d059bf9-4660-45f2-925d-ce80ad6c4d15"
            accept=".zip,.rar,.tar.gz"
            :on-change="handleCodeChange"
            :auto-upload="false"
          >
            <el-button type="primary" plain>上传代码包</el-button>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';

const filters = reactive({ query: '', status: '' });
const loading = ref(false);
const createDialogVisible = ref(false);
const submitLoading = ref(false);
const formRef = ref(null);

const tableData = ref([
  { id: 'S1001', problemTitle: '2023高教社杯A题', author: 'Team Alpha', submitTime: '10:05:00', score: 85, status: 'Finished' },
  { id: 'S1002', problemTitle: '2023高教社杯A题', author: 'Team Beta', submitTime: '10:12:30', score: null, status: 'Evaluating' },
  { id: 'S1003', problemTitle: '最优路径规划', author: 'User123', submitTime: '11:00:22', score: null, status: 'Pending' },
  { id: 'S1004', problemTitle: '车间调度问题研究', author: 'Matrix', submitTime: '11:30:10', score: 92, status: 'Finished' },
  { id: 'S1005', problemTitle: '传染病模型与预测', author: 'Omega', submitTime: '12:05:15', score: 76, status: 'Finished' },
]);

const form = reactive({
  problemId: '',
  author: '',
  paper: null,
  code: null,
});

const formRules = {
  problemId: [{ required: true, message: '题目ID必填', trigger: 'blur' }],
  author: [{ required: true, message: '作者或队伍名必填', trigger: 'blur' }],
};

const getStatusType = (status) => {
  const map = { Pending: 'info', Evaluating: 'warning', Finished: 'success' };
  return map[status] || 'info';
};

const resetForm = () => {
  form.problemId = '';
  form.author = '';
  form.paper = null;
  form.code = null;
};

const openCreateDialog = () => {
  createDialogVisible.value = true;
};

const handlePaperChange = (file) => {
  form.paper = file.raw;
};

const handleCodeChange = (file) => {
  form.code = file.raw;
};

const onSubmit = async () => {
  if (!formRef.value) {
    return;
  }

  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  submitLoading.value = true;
  try {
    const nextId = `S${1000 + tableData.value.length + 1}`;
    tableData.value.unshift({
      id: nextId,
      problemTitle: `题目 #${form.problemId}`,
      author: form.author,
      submitTime: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
      score: null,
      status: 'Pending',
    });
    ElMessage.success('作品提交成功');
    createDialogVisible.value = false;
    resetForm();
  } catch (error) {
    console.error('提交作品失败', error);
    ElMessage.error('提交作品失败，请稍后重试');
  } finally {
    submitLoading.value = false;
  }
};

const fetchData = () => {
  loading.value = true;
  setTimeout(() => {
    loading.value = false;
    ElMessage.success('刷新成功');
  }, 500);
};
</script>

<style scoped>
.filter-bar {
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
</style>
