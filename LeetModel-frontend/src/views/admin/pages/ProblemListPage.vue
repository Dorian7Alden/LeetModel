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
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="title" label="题目名称" min-width="200" />
        <el-table-column prop="contestName" label="赛事" width="180" />
        <el-table-column prop="year" label="年份" width="80" />
        <el-table-column prop="statementLanguage" label="题面" width="70"><template #default="scope">{{ scope.row.statementLanguage === 'EN' ? '英文' : '中文' }}</template></el-table-column>
        <el-table-column prop="difficulty" label="难度" width="80">
          <template #default="scope">{{ getDifficultyLabel(scope.row.difficulty) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
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
        <el-form-item label="题目标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入题目标题" />
        </el-form-item>

        <el-form-item label="题面(Markdown)" prop="contentMarkdown">
          <el-input v-model="form.contentMarkdown" type="textarea" :rows="6" placeholder="填写可直接渲染的 Markdown 题面，可为空" />
        </el-form-item>

        <el-form-item label="所属赛事" prop="contestId">
          <el-select v-model="form.contestId" style="width: 100%">
            <el-option v-for="contest in contests" :key="contest.id" :label="contest.name" :value="contest.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="题目年份" prop="year"><el-input-number v-model="form.year" :min="2000" :max="2100" /></el-form-item>
        <el-form-item label="题面语言" prop="statementLanguage"><el-radio-group v-model="form.statementLanguage"><el-radio value="ZH">中文</el-radio><el-radio value="EN">英文</el-radio></el-radio-group></el-form-item>
        <el-form-item label="完成时长" prop="durationMinutes"><el-input-number v-model="form.durationMinutes" :min="1" :max="10080" /><span class="field-tip">分钟</span></el-form-item>

        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="form.difficulty" style="width: 100%">
            <el-option label="简单" :value="1" /><el-option label="中等" :value="2" /><el-option label="困难" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item label="标签">
          <el-select v-model="form.tagIds" multiple clearable placeholder="请选择标签" style="width: 100%">
            <el-option v-for="tag in tags" :key="tag.id" :label="`${tag.name}（${tag.type}）`" :value="tag.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="题目状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
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
import {
  getAdminContentProblems,
  getAdminContentProblem,
  createAdminContentProblem,
  updateAdminContentProblem,
  deleteAdminContentProblem,
  getAdminContentContests,
  getAdminContentTags,
} from '@/api/problem';

const searchQuery = ref('');
const tableData = ref([]);
const tableLoading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const contests = ref([]);
const tags = ref([]);

const dialogVisible = ref(false);
const isEdit = ref(false);
const editId = ref(null);
const submitLoading = ref(false);
const formRef = ref();

const form = reactive({
  title: '',
  contentMarkdown: '',
  contestId: null,
  year: new Date().getFullYear(),
  statementLanguage: 'ZH',
  durationMinutes: 4320,
  difficulty: 1,
  status: 0,
  tagIds: [],
});

const rules = {
  title: [{ required: true, message: '请输入题目标题', trigger: 'blur' }],
  contestId: [{ required: true, message: '请选择赛事', trigger: 'change' }],
  year: [{ required: true, message: '请输入年份', trigger: 'change' }],
  statementLanguage: [{ required: true, message: '请选择题面语言', trigger: 'change' }],
  durationMinutes: [{ required: true, message: '请输入完成时长', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }]
};

const statusMap = {
  0: { label: '草稿', type: 'info' },
  1: { label: '已发布', type: 'success' },
  2: { label: '已下线', type: 'warning' },
  3: { label: '已归档', type: '' }
};

const getStatusLabel = (status) => statusMap[status]?.label || '未知';
const getStatusType = (status) => statusMap[status]?.type || 'info';
const getDifficultyLabel = (difficulty) => ({ 1: '简单', 2: '中等', 3: '困难' })[difficulty] || '未知';

const fetchList = async () => {
  tableLoading.value = true;
  try {
    const params = { page: currentPage.value, pageSize: pageSize.value };
    if (searchQuery.value.trim()) {
      params.keyword = searchQuery.value.trim();
    }
    const res = await getAdminContentProblems(params);
    if (res.code === 20000 && res.data) {
      tableData.value = res.data.rows || [];
      total.value = res.data.total || 0;
    } else {
      ElMessage.error(res.msg || '获取题目列表失败');
    }
  } catch (error) {
    console.error('获取题目列表失败', error);
    ElMessage.error('获取题目列表失败');
  } finally {
    tableLoading.value = false;
  }
};

const openCreateDialog = () => {
  isEdit.value = false;
  editId.value = null;
  dialogVisible.value = true;
};

const openEditDialog = async (row) => {
  isEdit.value = true;
  editId.value = row.id;
  try {
    const res = await getAdminContentProblem(row.id);
    if (res.code === 20000 && res.data) {
      const d = res.data;
      form.title = d.title;
      form.contentMarkdown = d.contentMarkdown || '';
      form.contestId = d.contestId;
      form.year = d.year;
      form.statementLanguage = d.statementLanguage;
      form.durationMinutes = d.durationMinutes;
      form.difficulty = d.difficulty;
      form.status = d.status;
      form.tagIds = d.tagIds || [];
    } else {
      ElMessage.error(res.msg || '获取题目详情失败');
      return;
    }
  } catch (error) {
    console.error('获取题目详情失败', error);
    ElMessage.error('获取题目详情失败');
    return;
  }
  dialogVisible.value = true;
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除题目「${row.title}」吗？`, '确认删除', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteAdminContentProblem(row.id);
      ElMessage.success('删除成功');
      fetchList();
    } catch (error) {
      console.error('删除题目失败', error);
      ElMessage.error('删除题目失败');
    }
  }).catch(() => {});
};

const resetForm = () => {
  form.title = '';
  form.contentMarkdown = '';
  form.contestId = null;
  form.year = new Date().getFullYear();
  form.statementLanguage = 'ZH';
  form.durationMinutes = 4320;
  form.difficulty = 1;
  form.status = 0;
  form.tagIds = [];
  formRef.value?.resetFields();
};

const onSubmit = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    const payload = {
      title: form.title,
      contentMarkdown: form.contentMarkdown,
      contestId: form.contestId,
      year: form.year,
      statementLanguage: form.statementLanguage,
      durationMinutes: form.durationMinutes,
      difficulty: form.difficulty,
      status: form.status,
    };
    // 详情接口仅返回 tagNames，编辑时未选择标签则不带 tagIds，避免误清空既有标签
    if (!isEdit.value || form.tagIds.length > 0) payload.tagIds = form.tagIds;
    if (isEdit.value) {
      await updateAdminContentProblem(editId.value, payload);
      ElMessage.success('更新成功');
    } else {
      await createAdminContentProblem(payload);
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
  getAdminContentContests().then(res => { contests.value = (res.data || []); }).catch(error => ElMessage.error(error.message || '赛事数据加载失败'));
  getAdminContentTags().then(res => { tags.value = (res.data || []); }).catch(() => {});
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
.field-tip { margin-left: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
