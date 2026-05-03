<template>
  <div class="problem-list">
    <el-card shadow="never">
      <div class="action-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索题目"
          style="width: 300px"
          prefix-icon="Search"
          clearable
          @clear="fetchList"
          @keyup.enter="fetchList"
        />
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新增题目
        </el-button>
      </div>

      <el-table :data="tableData" style="width: 100%" stripe v-loading="tableLoading">
        <el-table-column prop="problemId" label="ID" width="80" />
        <el-table-column prop="problemTitle" label="题目名称" min-width="200" />
        <el-table-column prop="problemStatus" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.problemStatus)">
              {{ getStatusLabel(scope.row.problemStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="scope">{{ formatTime(scope.row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无题目数据" />
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          layout="prev, pager, next, total"
          :total="total"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑题目' : '新增题目'"
      width="600px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="题目标题" prop="problemTitle">
          <el-input v-model="form.problemTitle" placeholder="请输入题目标题" />
        </el-form-item>

        <el-form-item label="题目内容文件" prop="contentFileId">
          <div class="upload-area">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :file-list="fileList"
              accept=".md"
            >
              <el-button type="primary" plain :loading="uploading">
                {{ uploading ? '上传中...' : '选择 .md 文件' }}
              </el-button>
            </el-upload>
            <span v-if="form.contentFileId" class="upload-tip">
              文件已上传 (ID: {{ form.contentFileId }})
            </span>
          </div>
        </el-form-item>

        <el-form-item label="题目状态" prop="problemStatus">
          <el-select v-model="form.problemStatus" placeholder="请选择状态" style="width: 100%">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已下线" :value="2" />
            <el-option label="已归档" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';

const formatTime = (val) => {
  if (!val) return '-';
  const d = new Date(val);
  if (isNaN(d.getTime())) return val;
  return d.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  });
};
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { getProblemList, getProblemDetail, createProblem, updateProblem, deleteProblem } from '@/api/problem';
import { uploadFile } from '@/api/file';

const searchQuery = ref('');
const tableData = ref([]);
const tableLoading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);

const dialogVisible = ref(false);
const isEdit = ref(false);
const editId = ref(null);
const submitLoading = ref(false);
const uploading = ref(false);
const fileList = ref([]);
const formRef = ref();
const uploadRef = ref();

const form = reactive({
  problemTitle: '',
  contentFileId: null,
  problemStatus: 0
});

const rules = {
  problemTitle: [{ required: true, message: '请输入题目标题', trigger: 'blur' }],
  contentFileId: [{ required: true, message: '请上传题目内容文件', trigger: 'change' }]
};

const statusMap = {
  0: { label: '草稿', type: 'info' },
  1: { label: '已发布', type: 'success' },
  2: { label: '已下线', type: 'warning' },
  3: { label: '已归档', type: '' }
};

const getStatusLabel = (status) => statusMap[status]?.label || '未知';
const getStatusType = (status) => statusMap[status]?.type || 'info';

const fetchList = async () => {
  tableLoading.value = true;
  try {
    const params = { page: currentPage.value, pageSize: pageSize.value };
    if (searchQuery.value.trim()) {
      params.keyword = searchQuery.value.trim();
    }
    const res = await getProblemList(params);
    if (res.code === 20000 && res.data) {
      tableData.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (error) {
    console.error('获取题目列表失败', error);
    ElMessage.error('获取题目列表失败');
  } finally {
    tableLoading.value = false;
  }
};

const handleFileChange = async (uploadFileItem) => {
  const file = uploadFileItem.raw;
  if (!file) return;
  uploading.value = true;
  try {
    const res = await uploadFile(file);
    if (res.code === 20000) {
      form.contentFileId = res.data.fileId;
      ElMessage.success('文件上传成功');
    } else {
      ElMessage.error(res.msg || '文件上传失败');
      uploadRef.value.clearFiles();
    }
  } catch (error) {
    console.error('文件上传失败', error);
    ElMessage.error('文件上传失败');
    uploadRef.value.clearFiles();
  } finally {
    uploading.value = false;
  }
};

const handleFileRemove = () => {
  form.contentFileId = null;
};

const openCreateDialog = () => {
  isEdit.value = false;
  editId.value = null;
  dialogVisible.value = true;
};

const openEditDialog = async (row) => {
  isEdit.value = true;
  editId.value = row.problemId;
  try {
    const res = await getProblemDetail(row.problemId);
    if (res.code === 20000 && res.data) {
      const d = res.data;
      form.problemTitle = d.problemTitle;
      form.contentFileId = d.contentFileId;
      form.problemStatus = d.problemStatus;
      if (d.contentFileId) {
        fileList.value = [{ name: d.contentFileUrl || '已上传文件', id: d.contentFileId }];
      }
    }
  } catch (error) {
    console.error('获取题目详情失败', error);
    ElMessage.error('获取题目详情失败');
    return;
  }
  dialogVisible.value = true;
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除题目「${row.problemTitle}」吗？`, '确认删除', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteProblem(row.problemId);
      ElMessage.success('删除成功');
      fetchList();
    } catch (error) {
      console.error('删除题目失败', error);
      ElMessage.error('删除题目失败');
    }
  }).catch(() => {});
};

const resetForm = () => {
  form.problemTitle = '';
  form.contentFileId = null;
  form.problemStatus = 0;
  fileList.value = [];
  formRef.value?.resetFields();
};

const onSubmit = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    const payload = {
      problemTitle: form.problemTitle,
      contentFileId: form.contentFileId,
      problemStatus: form.problemStatus
    };
    if (isEdit.value) {
      await updateProblem(editId.value, payload);
      ElMessage.success('更新成功');
    } else {
      await createProblem(payload);
      ElMessage.success('创建成功');
    }
    dialogVisible.value = false;
    fetchList();
  } catch (error) {
    console.error('保存题目失败', error);
    ElMessage.error('保存题目失败');
  } finally {
    submitLoading.value = false;
  }
};

onMounted(() => {
  fetchList();
});
</script>

<style scoped>
.action-bar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.upload-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.upload-tip {
  color: #67c23a;
  font-size: 13px;
}
</style>
