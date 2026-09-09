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
        <el-table-column label="题号" width="90">
          <template #default="scope">{{ scope.row.code ?? scope.row.id }}</template>
        </el-table-column>
        <el-table-column prop="problemNumber" label="赛事题号" width="90">
          <template #default="scope">{{ formatProblemNumber(scope.row.problemNumber) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="题目名称" min-width="220">
          <template #default="scope">
            <button class="problem-title-link" @click="openPreview(scope.row)">{{ scope.row.title }}</button>
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button size="small" type="success" link @click="openPreview(scope.row)">预览</el-button>
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
      width="680px"
      top="6vh"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="problem-dialog-form">
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

        <el-form-item label="赛事题号" prop="problemNumber">
          <el-select v-model="form.problemNumber" style="width: 100%" placeholder="请选择赛事题号">
            <el-option v-for="item in problemNumberOptions" :key="item.value" :label="item.label" :value="item.value" />
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

        <!-- 题目附件与数据集管理区 -->
        <div class="form-attachment-section">
          <div class="attachment-section-header">
            <div>
              <span class="attachment-title">题目附件与数据集</span>
              <span class="attachment-subtitle">支持 ZIP、RAR、7Z、TAR.GZ 等压缩包，以及 CSV、XLSX、PDF 等题目材料</span>
            </div>
            <div>
              <input
                ref="fileInputRef"
                type="file"
                multiple
                accept=".txt,.md,.csv,.pdf,.doc,.docx,.xlsx,.jpg,.jpeg,.png,.gif,.webp,.zip,.rar,.7z,.tar,.gz,.tgz,.bz2,.xz"
                style="display: none"
                @change="handleFileSelected"
              />
              <el-button size="small" type="primary" plain :loading="uploadingAttachment" @click="triggerChooseFile">
                <el-icon><Upload /></el-icon> 上传附件
              </el-button>
            </div>
          </div>

          <!-- 编辑模式：已关联附件列表 -->
          <div v-if="isEdit" class="attachment-list">
            <div v-if="existingAttachments.length" class="attachment-items">
              <div v-for="att in existingAttachments" :key="att.id" class="attachment-item-card">
                <el-icon class="file-icon"><Document /></el-icon>
                <div class="file-info">
                  <div class="file-name" :title="att.fileName">{{ att.fileName }}</div>
                  <div class="file-meta">
                    <span>{{ formatFileSize(att.fileSize) }}</span>
                    <span v-if="att.description" class="file-desc">{{ att.description }}</span>
                  </div>
                </div>
                <div class="file-actions">
                  <a v-if="att.downloadUrl" :href="att.downloadUrl" target="_blank" rel="noopener noreferrer" class="action-btn link">下载</a>
                  <el-button type="danger" link size="small" @click="handleDeleteExistingAttachment(att.id)">删除</el-button>
                </div>
              </div>
            </div>
            <div v-else class="attachment-empty-tip">暂无附件，可点击右上角上传新附件</div>
          </div>

          <!-- 新增模式：暂存待上传附件列表 -->
          <div v-else class="attachment-list">
            <div v-if="pendingAttachments.length" class="attachment-items">
              <div v-for="(att, idx) in pendingAttachments" :key="idx" class="attachment-item-card is-pending">
                <el-icon class="file-icon"><Document /></el-icon>
                <div class="file-info">
                  <div class="file-name">{{ att.fileName }}</div>
                  <div class="file-meta">
                    <span>{{ formatFileSize(att.fileSize) }}</span>
                    <el-input
                      v-model="att.description"
                      size="small"
                      placeholder="添加说明（可选）"
                      class="desc-inline-input"
                    />
                  </div>
                </div>
                <el-button type="danger" link size="small" @click="removePendingAttachment(idx)">移除</el-button>
              </div>
            </div>
            <div v-else class="attachment-empty-tip">可以在此处预选附件，保存题目时将自动联动上传</div>
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" size="min(860px, 76vw)" class="problem-preview-drawer" destroy-on-close>
      <template #header>
        <div class="preview-drawer-title">
          <span class="preview-kicker">题目发布预览</span>
          <strong>{{ previewProblem?.title || '加载中' }}</strong>
        </div>
      </template>
      <div v-loading="previewLoading" class="preview-body">
        <template v-if="previewProblem">
          <div class="preview-meta">
            <span><small>题号</small><strong>{{ previewProblem.code ?? previewProblem.id }}</strong></span>
            <span><small>赛事题号</small><strong>{{ formatProblemNumber(previewProblem.problemNumber) }}</strong></span>
            <span><small>赛事</small><strong>{{ previewProblem.contestName || '未设置' }}</strong></span>
            <span><small>年份</small><strong>{{ previewProblem.year || '—' }}</strong></span>
            <span><small>难度</small><strong>{{ getDifficultyLabel(previewProblem.difficulty) }}</strong></span>
          </div>
          <div v-if="previewTagNames.length" class="preview-tags">
            <el-tag v-for="tag in previewTagNames" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>

          <!-- 附件与数据集展示 -->
          <div v-if="previewProblem.attachments && previewProblem.attachments.length" class="preview-attachments-box">
            <div class="preview-att-title">配套赛题附件与数据集（{{ previewProblem.attachments.length }}）</div>
            <div class="preview-att-grid">
              <div v-for="att in previewProblem.attachments" :key="att.id" class="preview-att-card">
                <el-icon class="att-card-icon"><Document /></el-icon>
                <div class="att-text">
                  <div class="att-name" :title="att.fileName">{{ att.fileName }}</div>
                  <div class="att-size">{{ formatFileSize(att.fileSize) }}</div>
                </div>
                <a v-if="att.downloadUrl" :href="att.downloadUrl" target="_blank" rel="noopener noreferrer" class="att-download-btn">
                  下载
                </a>
              </div>
            </div>
          </div>

          <article v-if="renderedPreview" class="markdown-body problem-markdown" v-html="renderedPreview"></article>
          <el-empty v-else description="该题目尚未填写 Markdown 题面" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

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
import { Document, Plus, Upload } from '@element-plus/icons-vue';
import { renderSafeMarkdown } from '@/utils/markdown';
import {
  getAdminContentProblems,
  getAdminContentProblem,
  createAdminContentProblem,
  updateAdminContentProblem,
  deleteAdminContentProblem,
  uploadAdminAttachment,
  deleteAdminAttachment,
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
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewProblem = ref(null);
const renderedPreview = computed(() => renderSafeMarkdown(previewProblem.value?.contentMarkdown || ''));
const previewTagNames = computed(() => {
  const value = previewProblem.value?.tagNames || previewProblem.value?.tags || [];
  return value.map((item) => typeof item === 'string' ? item : item.name).filter(Boolean);
});

const dialogVisible = ref(false);
const isEdit = ref(false);
const editId = ref(null);
const submitLoading = ref(false);
const formRef = ref();
const fileInputRef = ref(null);
const uploadingAttachment = ref(false);
const existingAttachments = ref([]);
const pendingAttachments = ref([]);

const formatFileSize = (bytes) => {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

const triggerChooseFile = () => {
  fileInputRef.value?.click();
};

const handleFileSelected = async (e) => {
  const files = Array.from(e.target.files || []);
  if (!files.length) return;

  if (isEdit.value && editId.value) {
    // 编辑模式：直接调用后端接口上传并挂载至已有题目
    uploadingAttachment.value = true;
    try {
      for (const file of files) {
        const res = await uploadAdminAttachment(editId.value, file);
        if (res.code === 20000 && res.data) {
          existingAttachments.value.push(res.data);
        }
      }
      ElMessage.success('附件已上传并关联至该题目');
    } catch (err) {
      ElMessage.error(err.message || '附件上传失败');
    } finally {
      uploadingAttachment.value = false;
      if (fileInputRef.value) fileInputRef.value.value = '';
    }
  } else {
    // 新增模式：将待上传的 File 对象暂存在前端队列，保存题目时联动上传
    for (const file of files) {
      pendingAttachments.value.push({
        file,
        fileName: file.name,
        fileSize: file.size,
        description: '',
      });
    }
    if (fileInputRef.value) fileInputRef.value.value = '';
  }
};

const handleDeleteExistingAttachment = async (attachmentId) => {
  try {
    await ElMessageBox.confirm('确定要从该题目中删除此附件吗？存储对象将被一并清除。', '删除附件确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteAdminAttachment(editId.value, attachmentId);
    existingAttachments.value = existingAttachments.value.filter(a => a.id !== attachmentId);
    ElMessage.success('附件已删除');
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message || '删除附件失败');
  }
};

const removePendingAttachment = (index) => {
  pendingAttachments.value.splice(index, 1);
};

const form = reactive({
  title: '',
  contentMarkdown: '',
  contestId: null,
  problemNumber: null,
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
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  problemNumber: [{ required: true, message: '请选择赛事题号', trigger: 'change' }]
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
const problemNumberOptions = [
  { value: 'A', label: 'A 题' },
  { value: 'B', label: 'B 题' },
  { value: 'C', label: 'C 题' },
  { value: 'D', label: 'D 题' },
  { value: 'E', label: 'E 题' },
  { value: 'F', label: 'F 题' },
  { value: 'X', label: 'X 题' },
];
const formatProblemNumber = (value) => problemNumberOptions.find(item => item.value === value)?.label || 'X 题';

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
  pendingAttachments.value = [];
  existingAttachments.value = [];
  dialogVisible.value = true;
};

const openPreview = async (row) => {
  previewProblem.value = { ...row };
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const res = await getAdminContentProblem(row.id);
    if (res.code === 20000 && res.data) {
      previewProblem.value = res.data;
    } else {
      ElMessage.error(res.msg || '获取题目详情失败');
    }
  } catch (error) {
    console.error('获取题目预览失败', error);
    ElMessage.error(error.message || '题目预览加载失败');
  } finally {
    previewLoading.value = false;
  }
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
      form.problemNumber = d.problemNumber || 'X';
      form.year = d.year;
      form.statementLanguage = d.statementLanguage;
      form.durationMinutes = d.durationMinutes;
      form.difficulty = d.difficulty;
      form.status = d.status;
      form.tagIds = d.tagIds || [];
      existingAttachments.value = d.attachments || [];
      pendingAttachments.value = [];
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
  form.problemNumber = null;
  form.year = new Date().getFullYear();
  form.statementLanguage = 'ZH';
  form.durationMinutes = 4320;
  form.difficulty = 1;
  form.status = 0;
  form.tagIds = [];
  pendingAttachments.value = [];
  existingAttachments.value = [];
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
      problemNumber: form.problemNumber,
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
      const createRes = await createAdminContentProblem(payload);
      const newProblemId = createRes.data?.id;
      if (newProblemId && pendingAttachments.value.length > 0) {
        let uploadSuccessCount = 0;
        for (let i = 0; i < pendingAttachments.value.length; i++) {
          const att = pendingAttachments.value[i];
          try {
            await uploadAdminAttachment(newProblemId, att.file, {
              description: att.description?.trim() || null,
              sortOrder: i,
            });
            uploadSuccessCount++;
          } catch (uploadErr) {
            console.error(`附件 ${att.fileName} 联动上传失败:`, uploadErr);
          }
        }
        ElMessage.success(`题目创建成功，已上传 ${uploadSuccessCount} 个配套附件`);
      } else {
        ElMessage.success('题目创建成功');
      }
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
@import 'github-markdown-css/github-markdown-light.css';
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
.problem-title-link { max-width: 100%; overflow: hidden; padding: 0; color: var(--lm-text-primary); background: transparent; border: 0; font: inherit; font-weight: 600; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.problem-title-link:hover { color: var(--lm-primary); }
.preview-drawer-title { display: flex; min-width: 0; flex-direction: column; }
.preview-drawer-title .preview-kicker { margin-bottom: 4px; color: var(--lm-primary); font-size: 10px; font-weight: 800; letter-spacing: 1px; }
.preview-drawer-title strong { overflow: hidden; color: var(--lm-text-primary); font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }
.preview-body { min-height: 280px; }
.preview-meta { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.preview-meta span { display: flex; min-width: 0; flex-direction: column; padding: 12px 14px; background: var(--lm-bg-secondary); border-radius: 9px; }
.preview-meta small { color: var(--lm-text-muted); font-size: 10px; }
.preview-meta strong { margin-top: 3px; overflow: hidden; color: var(--lm-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.preview-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 18px; }
.problem-markdown { min-height: 320px; padding: 28px 32px; color: #1f2937; background: #fff; border: 1px solid var(--lm-border); border-radius: 12px; }
.problem-preview-drawer :deep(.el-drawer__header) { margin-bottom: 0; padding: 20px 24px; border-bottom: 1px solid var(--lm-border); }
.problem-preview-drawer :deep(.el-drawer__body) { padding: 22px 24px 32px; background: #f8fafc; }

/* 题目表单附件区段 */
.problem-dialog-form {
  max-height: 68vh;
  overflow-y: auto;
  padding-right: 8px;
}
.problem-dialog-form::-webkit-scrollbar {
  width: 4px;
}
.problem-dialog-form::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 2px;
}

.form-attachment-section {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--lm-border-light);
}
.attachment-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.attachment-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--lm-text-primary);
  display: block;
}
.attachment-subtitle {
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 2px;
  display: block;
}
.attachment-empty-tip {
  padding: 14px;
  text-align: center;
  color: var(--lm-text-muted);
  font-size: 12px;
  background: #f8fafc;
  border-radius: var(--lm-radius-sm);
  border: 1px dashed var(--lm-border);
}
.attachment-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.attachment-item-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
}
.attachment-item-card.is-pending {
  border-style: dashed;
}
.file-icon {
  font-size: 18px;
  color: var(--lm-primary);
  flex-shrink: 0;
}
.file-info {
  flex: 1;
  min-width: 0;
}
.file-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-top: 2px;
}
.file-desc {
  color: var(--lm-text-secondary);
}
.desc-inline-input {
  max-width: 200px;
}
.file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.action-btn.link {
  font-size: 12px;
  color: var(--lm-primary);
  text-decoration: none;
}
.action-btn.link:hover {
  text-decoration: underline;
}

/* 题目预览抽屉内附件卡片 */
.preview-attachments-box {
  margin-bottom: 18px;
  padding: 14px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 8px;
}
.preview-att-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--lm-text-primary);
  margin-bottom: 10px;
}
.preview-att-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}
.preview-att-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid var(--lm-border-light);
  border-radius: var(--lm-radius-sm);
}
.att-card-icon {
  font-size: 16px;
  color: var(--lm-primary);
  flex-shrink: 0;
}
.att-text {
  flex: 1;
  min-width: 0;
}
.att-name {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-size {
  font-size: 10.5px;
  color: var(--lm-text-muted);
  margin-top: 1px;
}
.att-download-btn {
  flex-shrink: 0;
  padding: 3px 8px;
  font-size: 11px;
  color: var(--lm-primary);
  background: var(--lm-primary-bg);
  border-radius: var(--lm-radius-sm);
  text-decoration: none;
  transition: all var(--lm-transition);
}
.att-download-btn:hover {
  background: var(--lm-primary);
  color: #ffffff;
}
</style>
