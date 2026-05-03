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
        />
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新增题目
        </el-button>
      </div>

      <el-table :data="tableData" style="width: 100%" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="题目名称" min-width="200" />
        <el-table-column prop="difficulty" label="难度" width="100">
          <template #default="scope">
            <el-tag :type="getDifficultyType(scope.row.difficulty)">
              {{ getDifficultyLabel(scope.row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-switch v-model="scope.row.status" active-color="#13ce66" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link>编辑</el-button>
            <el-button size="small" type="danger" link>删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="12"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="createDialogVisible"
      title="新增题目"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="题目标题" prop="problem_title">
          <el-input v-model="form.problem_title" placeholder="请输入题目标题" />
        </el-form-item>

        <el-form-item label="题目简介" prop="intro_file">
          <el-upload
            action=""
            :auto-upload="false"
            :limit="1"
            :on-change="handleIntroChange"
            accept=".md"
          >
            <el-button type="primary" plain>选择简介文件 (.md)</el-button>
          </el-upload>
        </el-form-item>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="所属赛事" prop="competition_id">
              <el-select v-model="form.competition_id" placeholder="请选择赛事" style="width: 100%">
                <el-option
                  v-for="comp in competitions"
                  :key="comp.competitionId"
                  :label="comp.title"
                  :value="comp.competitionId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年份" prop="year">
              <el-select v-model="form.year" placeholder="请选择年份" style="width: 100%">
                <el-option
                  v-for="item in years"
                  :key="item.tagId"
                  :label="item.name"
                  :value="item.tagId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="form.difficulty" placeholder="请选择难度" style="width: 100%">
                <el-option
                  v-for="item in difficulties"
                  :key="item.tagId"
                  :label="item.name"
                  :value="item.tagId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据特征" prop="data_feature">
              <el-select v-model="form.data_feature" placeholder="请选择数据特征" style="width: 100%">
                <el-option
                  v-for="item in dataFeatures"
                  :key="item.tagId"
                  :label="item.name"
                  :value="item.tagId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="行业领域" prop="industry">
          <el-select v-model="form.industry" placeholder="请选择行业领域" style="width: 100%">
            <el-option
              v-for="item in industries"
              :key="item.tagId"
              :label="item.name"
              :value="item.tagId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="涉及模型" prop="models">
          <el-select v-model="form.models" multiple filterable placeholder="请选择模型" style="width: 100%">
            <el-option
              v-for="item in modelsOptions"
              :key="item.tagId"
              :label="item.name"
              :value="item.tagId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="题型" prop="problem_types">
          <el-select v-model="form.problem_types" multiple filterable placeholder="请选择题型" style="width: 100%">
            <el-option
              v-for="item in problemTypesOptions"
              :key="item.tagId"
              :label="item.name"
              :value="item.tagId"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

const searchQuery = ref('');
const createDialogVisible = ref(false);
const submitLoading = ref(false);
const formRef = ref();

const tableData = ref([
  { id: 1, title: '2023高教社杯A题 定日镜场的优化设计', difficulty: 'hard', updateTime: '2023-09-08 10:00:00', status: true },
  { id: 2, title: '投资的收益和风险', difficulty: 'medium', updateTime: '2023-10-12 14:20:00', status: true },
  { id: 3, title: '最优路径规划问题', difficulty: 'easy', updateTime: '2023-11-05 09:15:00', status: false },
  { id: 4, title: '传染病模型与预测', difficulty: 'medium', updateTime: '2024-01-20 16:45:00', status: true },
  { id: 5, title: '车间调度问题研究', difficulty: 'hard', updateTime: '2024-02-18 11:30:00', status: true }
]);

const competitions = ref([]);
const difficulties = ref([]);
const years = ref([]);
const dataFeatures = ref([]);
const industries = ref([]);
const modelsOptions = ref([]);
const problemTypesOptions = ref([]);

const form = reactive({
  problem_title: '',
  intro_file: null,
  competition_id: '',
  year: '',
  difficulty: '',
  data_feature: '',
  industry: '',
  models: [],
  problem_types: []
});

const rules = {
  problem_title: [{ required: true, message: '请输入题目标题', trigger: 'blur' }],
  competition_id: [{ required: true, message: '请选择所属赛事', trigger: 'change' }],
  year: [{ required: true, message: '请选择年份', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  data_feature: [{ required: true, message: '请选择数据特征', trigger: 'change' }],
  industry: [{ required: true, message: '请选择行业领域', trigger: 'change' }]
};

const loadTags = async () => {
  console.log('题目管理功能正在开发中');
};

const openCreateDialog = async () => {
  createDialogVisible.value = true;
  if (!competitions.value.length) {
    await loadTags();
  }
};

const resetForm = () => {
  form.problem_title = '';
  form.intro_file = null;
  form.competition_id = '';
  form.year = '';
  form.difficulty = '';
  form.data_feature = '';
  form.industry = '';
  form.models = [];
  form.problem_types = [];
};

const handleIntroChange = (file) => {
  form.intro_file = file.raw;
};

const getDifficultyType = (level) => {
  const map = { easy: 'success', medium: 'warning', hard: 'danger' };
  return map[level] || 'info';
};

const getDifficultyLabel = (level) => {
  const map = { easy: '简单', medium: '中等', hard: '困难' };
  return map[level] || level;
};

const resolveDifficultyCode = (difficultyTagId) => {
  const selected = difficulties.value.find((item) => item.tagId === difficultyTagId);
  const name = selected?.name || '';

  if (name.includes('简')) {
    return 'easy';
  }
  if (name.includes('难')) {
    return 'hard';
  }
  return 'medium';
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
    tableData.value.unshift({
      id: tableData.value.length + 1,
      title: form.problem_title,
      difficulty: resolveDifficultyCode(form.difficulty),
      updateTime: new Date().toLocaleString('zh-CN', { hour12: false }),
      status: true
    });

    ElMessage.success('新增题目成功');
    createDialogVisible.value = false;
    resetForm();
  } catch (error) {
    console.error('新增题目失败', error);
    ElMessage.error('新增题目失败，请稍后重试');
  } finally {
    submitLoading.value = false;
  }
};

onMounted(() => {
  loadTags();
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
</style>
