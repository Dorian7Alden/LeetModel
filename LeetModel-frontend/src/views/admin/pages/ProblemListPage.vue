<template>
  <div class="problem-list">
    <!-- 集中为一行且居中的筛选栏：搜索框放在首位，控件宽度紧凑自适应 -->
    <div class="action-bar-single-line">
      <!-- 1. 搜索框置首 -->
      <el-input
        v-model="searchQuery"
        placeholder="搜索编码 / 标题"
        class="filter-ctrl ctrl-search"
        prefix-icon="Search"
        clearable
        @clear="applyFilters"
        @keyup.enter="applyFilters"
      />

      <!-- 2. 所属赛事 -->
      <el-select
        v-model="filters.contestId"
        placeholder="全部赛事"
        clearable
        class="filter-ctrl ctrl-contest"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部赛事" :value="null" />
        <el-option v-for="contest in contests" :key="contest.id" :label="contest.name" :value="contest.id" />
      </el-select>

      <!-- 3. 年份 -->
      <el-select
        v-model="filters.year"
        placeholder="年份"
        clearable
        class="filter-ctrl ctrl-year"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部年份" :value="null" />
        <el-option v-for="y in recentYears" :key="y" :label="`${y} 年`" :value="y" />
      </el-select>

      <!-- 4. 赛事题号 -->
      <el-select
        v-model="filters.problemNumber"
        placeholder="题号"
        clearable
        class="filter-ctrl ctrl-number"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部题号" :value="null" />
        <el-option v-for="item in problemNumberOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>

      <!-- 5. 题目类型 -->
      <el-select
        v-model="filters.selectedTags.PROBLEM_TYPE"
        placeholder="题型"
        clearable
        class="filter-ctrl ctrl-tag"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部题型" :value="null" />
        <el-option v-for="t in problemTypeTags" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>

      <!-- 6. 背景领域 -->
      <el-select
        v-model="filters.selectedTags.BACKGROUND_DOMAIN"
        placeholder="领域"
        clearable
        class="filter-ctrl ctrl-tag"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部领域" :value="null" />
        <el-option v-for="t in domainTags" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>

      <!-- 7. 模型算法 -->
      <el-select
        v-model="filters.selectedTags.MODEL_ALGORITHM"
        placeholder="算法"
        clearable
        class="filter-ctrl ctrl-tag"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部算法" :value="null" />
        <el-option v-for="t in algorithmTags" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>

      <!-- 8. 难度 -->
      <el-select
        v-model="filters.difficulty"
        placeholder="难度"
        clearable
        class="filter-ctrl ctrl-diff"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部难度" :value="null" />
        <el-option label="简单" :value="1" />
        <el-option label="中等" :value="2" />
        <el-option label="困难" :value="3" />
      </el-select>

      <!-- 9. 语言 -->
      <el-select
        v-model="filters.statementLanguage"
        placeholder="语言"
        clearable
        class="filter-ctrl ctrl-lang"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部语言" value="" />
        <el-option label="中文" value="ZH" />
        <el-option label="英文" value="EN" />
      </el-select>

      <!-- 10. 状态 -->
      <el-select
        v-model="filters.status"
        placeholder="状态"
        clearable
        class="filter-ctrl ctrl-status"
        popper-class="admin-filter-popper"
        @change="applyFilters"
      >
        <el-option label="全部状态" :value="null" />
        <el-option v-for="(item, value) in statusMap" :key="value" :label="item.label" :value="Number(value)" />
      </el-select>

      <!-- 11. 动作区（无刷新按钮、无总条数，紧凑自适应） -->
      <div class="filter-actions-group">
        <el-button v-if="hasFilters" link type="primary" @click="clearFilters">重置</el-button>
        <el-button size="small" type="primary" @click="openCreateDialog"><el-icon><Plus /></el-icon>新增题目</el-button>
      </div>
    </div>

    <AdminStatePanel
      v-if="loadError && !tableData.length"
      type="error"
      title="题目列表加载失败"
      action-label="重新加载"
      @action="fetchList"
    />

    <template v-else>
      <div v-if="loadError" class="inline-warning" role="alert">
        <el-icon><WarningFilled /></el-icon>刷新失败，当前保留上次取得的数据
      </div>
      <div class="problem-table-scroll">
      <el-table
        :data="tableData"
        :show-header="false"
        stripe
        v-loading="tableLoading"
        row-key="id"
        style="width: 100%"
      >
        <el-table-column width="60" align="center">
          <template #default="scope">
            <span class="code-badge">#{{ scope.row.code ?? scope.row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column min-width="260">
          <template #default="scope">
            <div class="problem-title-single-line">
              <button
                class="problem-title-link"
                :title="scope.row.title"
                @click="openPreview(scope.row)"
              >
                {{ scope.row.title }}
              </button>
              <span class="problem-number-badge">{{ formatProblemNumber(scope.row.problemNumber) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column width="220" min-width="180">
          <template #default="scope">
            <div v-if="getDisplayTags(scope.row).length" class="problem-inline-tags">
              <span
                v-for="t in getDisplayTags(scope.row).slice(0, 3)"
                :key="t.id || t.name"
                class="problem-mini-tag"
                :class="`tag-type-${t.type}`"
                :title="`${tagTypeLabel(t.type)}: ${t.name}`"
              >
                {{ t.name }}
              </span>
              <span v-if="getDisplayTags(scope.row).length > 3" class="problem-mini-tag tag-more">
                +{{ getDisplayTags(scope.row).length - 3 }}
              </span>
            </div>
            <span v-else class="text-muted-dash">—</span>
          </template>
        </el-table-column>
        <el-table-column min-width="190">
          <template #default="scope">
            <el-tooltip
              v-if="scope.row.contestName"
              :content="`${scope.row.contestName}${scope.row.year ? ' (' + scope.row.year + '年)' : ''}`"
              placement="top"
              effect="light"
              popper-class="admin-menu-tooltip"
              :show-after="300"
              :hide-after="50"
              :enterable="false"
            >
              <div class="contest-cell-truncated">
                <span class="contest-name-text">{{ scope.row.contestName }}</span>
                <span v-if="scope.row.year" class="contest-year-badge">{{ scope.row.year }}</span>
              </div>
            </el-tooltip>
            <span v-else class="text-muted-dash">—</span>
          </template>
        </el-table-column>
        <el-table-column width="80" align="center">
          <template #default="scope">
            <span class="difficulty-chip" :class="`diff-${scope.row.difficulty}`">
              {{ getDifficultyLabel(scope.row.difficulty) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column width="80" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small">
              {{ getStatusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column width="164" fixed="right" align="center">
          <template #default="scope">
            <div class="row-actions">
              <el-button type="primary" link @click="openPreview(scope.row)">查看详情</el-button>
              <el-button link @click="openEditDialog(scope.row)">编辑</el-button>
              <el-button type="danger" link @click="handleDelete(scope.row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty><div class="table-empty">{{ hasFilters ? '没有符合条件的题目' : '暂无题目' }}</div></template>
      </el-table>
      </div>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </template>

    <el-drawer
      v-model="dialogVisible"
      :title="isEdit ? '编辑题目' : '新增题目'"
      size="640px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="problem-dialog-form">
        <el-form-item label="题目标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入题目标题" />
        </el-form-item>

        <el-form-item label="题面(Markdown)" prop="contentMarkdown">
          <el-input v-model="form.contentMarkdown" type="textarea" :rows="6" placeholder="填写可直接渲染的 Markdown 题面，可为空" />
        </el-form-item>

        <el-form-item label="解题提示" prop="solutionHint">
          <el-input
            v-model="form.solutionHint"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="用一两句话说明建模切入点，不提供完整答案"
          />
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
            <el-option v-for="tag in tags" :key="tag.id" :label="`${tag.name}（${tagTypeLabel(tag.type)}）`" :value="String(tag.id)" />
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
            <span class="attachment-title">附件与数据集</span>
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
    </el-drawer>

    <el-drawer v-model="previewVisible" size="min(860px, 76vw)" class="problem-preview-drawer" destroy-on-close>
      <template #header>
        <div class="preview-drawer-title">
          <span class="preview-kicker">题目详情</span>
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
            <span><small>题面语言</small><strong>{{ previewProblem.statementLanguage === 'EN' ? '英文' : '中文' }}</strong></span>
            <span><small>状态</small><strong>{{ getStatusLabel(previewProblem.status) }}</strong></span>
            <span><small>最近更新</small><strong>{{ formatTime(previewProblem.updateTime) }}</strong></span>
          </div>
          <div v-if="previewTagNames.length" class="preview-tags">
            <el-tag v-for="tag in previewTagNames" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
          <div v-if="previewProblem.solutionHint" class="preview-solution-hint">
            <small>解题提示</small>
            <p>{{ previewProblem.solutionHint }}</p>
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

          <!-- 统一解耦 Markdown 渲染组件 -->
          <div class="preview-markdown-wrapper">
            <MarkdownView :content="previewProblem.contentMarkdown" empty-text="该题目尚未填写 Markdown 题面" />
          </div>
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
import AdminStatePanel from '../components/AdminStatePanel.vue';
import MarkdownView from '@/components/common/MarkdownView.vue';
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

const emit = defineEmits(['changed']);
const searchQuery = ref('');
const filters = reactive({
  contestId: null,
  year: null,
  problemNumber: null,
  statementLanguage: '',
  difficulty: null,
  status: null,
  selectedTags: {
    PROBLEM_TYPE: null,
    BACKGROUND_DOMAIN: null,
    MODEL_ALGORITHM: null,
  },
});
const recentYears = Array.from({ length: 27 }, (_, index) => new Date().getFullYear() + 1 - index);
const tableData = ref([]);
const tableLoading = ref(false);
const loadError = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const contests = ref([]);
const tags = ref([]);

const problemTypeTags = computed(() => tags.value.filter((t) => t.type === 'PROBLEM_TYPE'));
const domainTags = computed(() => tags.value.filter((t) => t.type === 'BACKGROUND_DOMAIN'));
const algorithmTags = computed(() => tags.value.filter((t) => t.type === 'MODEL_ALGORITHM'));

const getDisplayTags = (row) => {
  if (row.tags && Array.isArray(row.tags) && row.tags.length) {
    return row.tags.map((t) => ({
      id: t.id,
      name: typeof t === 'string' ? t : t.name,
      type: t.type || 'other',
    }));
  }
  if (row.tagNames && Array.isArray(row.tagNames)) {
    return row.tagNames.map((name) => ({ id: name, name, type: 'other' }));
  }
  return [];
};

const previewVisible = ref(false);
const previewLoading = ref(false);
const previewProblem = ref(null);
const renderedPreview = computed(() => renderSafeMarkdown(previewProblem.value?.contentMarkdown || ''));
const previewTagNames = computed(() => {
  const value = previewProblem.value?.tagNames || previewProblem.value?.tags || [];
  return value.map((item) => typeof item === 'string' ? item : item.name).filter(Boolean);
});
const contestFullTooltip = (row) => {
  if (!row.contestName) return '';
  return row.year ? `${row.contestName} (${row.year}年)` : row.contestName;
};
const formatScore = (val) => {
  if (val == null || val === '') return '—';
  const num = Number(val);
  return Number.isFinite(num) && num >= 0 ? num.toFixed(1) : '—';
};
const hasFilters = computed(() => Boolean(
  searchQuery.value.trim()
  || filters.contestId
  || filters.year
  || filters.problemNumber
  || filters.statementLanguage
  || filters.difficulty
  || (filters.status !== null && filters.status !== '')
  || filters.selectedTags.PROBLEM_TYPE
  || filters.selectedTags.BACKGROUND_DOMAIN
  || filters.selectedTags.MODEL_ALGORITHM,
));

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
  solutionHint: '',
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
const tagTypeLabel = (type) => ({ BACKGROUND_DOMAIN: '背景领域', PROBLEM_TYPE: '题目类型', MODEL_ALGORITHM: '模型算法' })[type] || type || '其他';
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

const applyFilters = () => {
  currentPage.value = 1;
  fetchList();
};

const clearFilters = () => {
  searchQuery.value = '';
  filters.contestId = null;
  filters.year = null;
  filters.problemNumber = null;
  filters.statementLanguage = '';
  filters.difficulty = null;
  filters.status = null;
  filters.selectedTags.PROBLEM_TYPE = null;
  filters.selectedTags.BACKGROUND_DOMAIN = null;
  filters.selectedTags.MODEL_ALGORITHM = null;
  applyFilters();
};

const fetchList = async () => {
  if (tableLoading.value) return;
  tableLoading.value = true;
  loadError.value = false;
  try {
    const params = { page: currentPage.value, pageSize: pageSize.value };
    if (searchQuery.value.trim()) {
      params.keyword = searchQuery.value.trim();
    }
    if (filters.contestId) params.contestId = filters.contestId;
    if (filters.year) params.year = filters.year;
    if (filters.problemNumber) params.problemNumber = filters.problemNumber;
    if (filters.statementLanguage) params.statementLanguage = filters.statementLanguage;
    if (filters.difficulty) params.difficulty = filters.difficulty;
    if (filters.status !== null && filters.status !== '') params.status = filters.status;

    const tagIds = [
      filters.selectedTags.PROBLEM_TYPE,
      filters.selectedTags.BACKGROUND_DOMAIN,
      filters.selectedTags.MODEL_ALGORITHM,
    ].filter(Boolean);
    if (tagIds.length > 0) {
      params.tagIds = tagIds;
    }

    const res = await getAdminContentProblems(params);
    if (res.code === 20000 && res.data) {
      tableData.value = res.data.rows || [];
      total.value = res.data.total || 0;
    } else {
      throw new Error(res.msg || '获取题目列表失败');
    }
  } catch (error) {
    loadError.value = true;
    if (tableData.value.length) ElMessage.error(error.message || '题目列表刷新失败');
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
      form.solutionHint = d.solutionHint || '';
      form.contestId = d.contestId;
      form.problemNumber = d.problemNumber || 'X';
      form.year = d.year;
      form.statementLanguage = d.statementLanguage;
      form.durationMinutes = d.durationMinutes;
      form.difficulty = d.difficulty;
      form.status = d.status;
      form.tagIds = (d.tags || []).map(tag => String(tag.id)).filter(Boolean);
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
  ElMessageBox.confirm(`删除“${row.title}”后将不再出现在题库中，操作不可恢复。`, '删除题目？', {
    confirmButtonText: '删除题目',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteAdminContentProblem(row.id);
      ElMessage.success('题目已删除');
      await fetchList();
      emit('changed');
    } catch (error) {
      console.error('删除题目失败', error);
      ElMessage.error('删除题目失败');
    }
  }).catch(() => {});
};

const handleRowCommand = (command, row) => {
  if (command === 'edit') openEditDialog(row);
  if (command === 'delete') handleDelete(row);
};

const resetForm = () => {
  form.title = '';
  form.contentMarkdown = '';
  form.solutionHint = '';
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
      solutionHint: form.solutionHint,
      contestId: form.contestId,
      problemNumber: form.problemNumber,
      year: form.year,
      statementLanguage: form.statementLanguage,
      durationMinutes: form.durationMinutes,
      difficulty: form.difficulty,
      status: form.status,
    };
    payload.tagIds = form.tagIds;
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
    await fetchList();
    emit('changed');
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
.problem-list { min-width: 0; padding: var(--lm-admin-space-3); }
.action-bar-single-line {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 5px;
  padding: 8px 12px;
  margin-bottom: var(--lm-admin-space-3);
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-panel);
}
.action-bar-single-line :deep(.el-input__wrapper),
.action-bar-single-line :deep(.el-select__wrapper) {
  padding-left: 6px !important;
  padding-right: 6px !important;
  font-size: 12px;
  height: 30px;
  box-sizing: border-box !important;
}

/* 严格固定各筛选控件宽度，防止选项变更撑开导致抖动 */
.ctrl-search { width: 140px !important; min-width: 140px !important; max-width: 140px !important; flex: 0 0 140px !important; }
.ctrl-contest { width: 106px !important; min-width: 106px !important; max-width: 106px !important; flex: 0 0 106px !important; }
.ctrl-year { width: 76px !important; min-width: 76px !important; max-width: 76px !important; flex: 0 0 76px !important; }
.ctrl-number { width: 72px !important; min-width: 72px !important; max-width: 72px !important; flex: 0 0 72px !important; }
.ctrl-tag { width: 94px !important; min-width: 94px !important; max-width: 94px !important; flex: 0 0 94px !important; }
.ctrl-diff { width: 74px !important; min-width: 74px !important; max-width: 74px !important; flex: 0 0 74px !important; }
.ctrl-lang { width: 74px !important; min-width: 74px !important; max-width: 74px !important; flex: 0 0 74px !important; }
.ctrl-status { width: 76px !important; min-width: 76px !important; max-width: 76px !important; flex: 0 0 76px !important; }

/* 下拉框居中对齐与文本溢出省略 */
.filter-ctrl :deep(.el-select__wrapper) {
  width: 100% !important;
  max-width: 100% !important;
}
.filter-ctrl :deep(.el-select__selection) {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  height: 100% !important;
  width: calc(100% - 20px) !important;
  position: relative !important;
  text-align: center !important;
}
.filter-ctrl :deep(.el-select__placeholder) {
  position: relative !important;
  top: auto !important;
  transform: none !important;
  width: 100% !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap !important;
  color: var(--lm-admin-text-default, #374151) !important;
  font-weight: 500 !important;
  z-index: 1 !important;
  text-align: center !important;
}
.filter-ctrl :deep(.el-select__selected-item) {
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap !important;
  max-width: 100% !important;
  color: var(--lm-admin-primary, #2563eb) !important;
  font-weight: 600 !important;
  text-align: center !important;
  justify-content: center !important;
}

.filter-actions-group {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
  margin-left: auto;
}
.row-actions, .inline-warning { display: flex; align-items: center; }
.row-actions { display: flex; align-items: center; justify-content: center; gap: 4px; }
.problem-table-scroll {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  background: var(--lm-admin-surface);
}
.problem-table-scroll :deep(.el-table) {
  width: 100% !important;
  min-width: 960px;
}
.problem-table-scroll :deep(.el-table__row) { height: 46px; }
.problem-title-single-line {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}
.problem-inline-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.problem-mini-tag {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--lm-admin-text-muted);
  background: var(--lm-admin-surface-subtle);
  border: 1px solid var(--lm-admin-border);
  white-space: nowrap;
}
.problem-mini-tag.tag-type-PROBLEM_TYPE { color: #1e40af; background: #eff6ff; border-color: #dbeafe; }
.problem-mini-tag.tag-type-BACKGROUND_DOMAIN { color: #065f46; background: #ecfdf5; border-color: #d1fae5; }
.problem-mini-tag.tag-type-MODEL_ALGORITHM { color: #701a75; background: #fdf4ff; border-color: #fae8ff; }
.problem-mini-tag.tag-more { color: var(--lm-admin-text-muted); background: var(--lm-admin-surface-subtle); font-size: 10px; }
.code-badge { font-weight: 700; color: var(--lm-admin-text-muted); font-size: 12px; }
.problem-number-badge {
  flex-shrink: 0;
  display: inline-block;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--lm-admin-primary);
  background: #eff6ff;
  border: 1px solid #dbeafe;
}
.contest-cell-truncated {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}
.contest-name-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  color: var(--lm-admin-text-strong);
}
.contest-year-badge {
  flex-shrink: 0;
  font-size: 10.5px;
  color: var(--lm-admin-text-muted);
  background: var(--lm-admin-surface-subtle);
  border: 1px solid var(--lm-admin-border);
  padding: 1px 4px;
  border-radius: 3px;
}
.text-muted-dash {
  color: var(--lm-admin-text-muted);
}
.difficulty-chip { padding: 1px 6px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.diff-1 { color: #15803d; background: #f0fdf4; border: 1px solid #bbf7d0; }
.diff-2 { color: #b45309; background: #fffbeb; border: 1px solid #fde68a; }
.diff-3 { color: #b91c1c; background: #fef2f2; border: 1px solid #fecaca; }
.preview-markdown-wrapper {
  padding: 24px 28px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
}
.pagination-container {
  margin-top: var(--lm-admin-space-3);
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
.problem-title-link {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0;
  color: var(--lm-admin-text-strong);
  background: transparent;
  border: 0;
  font: inherit;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}
.problem-title-link:hover { color: var(--lm-admin-primary); }
.more-button { display: grid; width: 28px; height: 28px; place-items: center; color: var(--lm-admin-text-muted); background: transparent; border: 0; border-radius: var(--lm-admin-radius-control); cursor: pointer; }
.more-button:hover { color: var(--lm-admin-text-strong); background: var(--lm-admin-surface-subtle); }
.inline-warning { gap: 6px; margin-bottom: 8px; padding: 8px 10px; color: #92400e; background: #fffbeb; border: 1px solid #fde68a; border-radius: var(--lm-admin-radius-control); font-size: 12px; }
.table-empty { padding: 34px 0; color: var(--lm-admin-text-muted); font-size: 12px; }
.preview-drawer-title { display: flex; min-width: 0; flex-direction: column; }
.preview-drawer-title .preview-kicker { margin-bottom: 4px; color: var(--lm-primary); font-size: 10px; font-weight: 800; letter-spacing: 1px; }
.preview-drawer-title strong { overflow: hidden; color: var(--lm-text-primary); font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }
.preview-body { min-height: 280px; }
.preview-meta { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; }
.preview-meta span { display: flex; min-width: 0; flex-direction: column; padding: 12px 14px; background: var(--lm-bg-secondary); border-radius: 9px; }
.preview-meta small { color: var(--lm-text-muted); font-size: 10px; }
.preview-meta strong { margin-top: 3px; overflow: hidden; color: var(--lm-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.preview-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 18px; }
.preview-solution-hint { margin-bottom: 18px; padding: 12px 14px; border-left: 3px solid #f59e0b; background: #fffbeb; }
.preview-solution-hint small { color: #a16207; font-size: 10px; font-weight: 700; }
.preview-solution-hint p { margin: 5px 0 0; color: #44403c; font-size: 13px; line-height: 1.65; }
.problem-markdown { min-height: 320px; padding: 28px 32px; color: #1f2937; background: #fff; border: 1px solid var(--lm-border); border-radius: 12px; }
.problem-preview-drawer :deep(.el-drawer__header) { margin-bottom: 0; padding: 20px 24px; border-bottom: 1px solid var(--lm-border); }
.problem-preview-drawer :deep(.el-drawer__body) { padding: 22px 24px 32px; background: #f8fafc; }

/* 题目表单附件区段 */
.problem-dialog-form {
  max-height: calc(100vh - 150px);
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
