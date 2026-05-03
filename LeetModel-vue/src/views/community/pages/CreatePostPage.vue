<template>
  <div class="create-post-page">
    <PageHeader title="发布帖子" description="分享你的建模经验、技巧或参与讨论交流" />

    <div class="editor-layout">
      <!-- Main editor -->
      <div class="editor-main">
        <el-card shadow="never">
          <!-- Title -->
          <div class="form-group">
            <label class="form-label">标题</label>
            <el-input
              v-model="postTitle"
              placeholder="请输入帖子标题..."
              size="large"
              maxlength="100"
              show-word-limit
            />
          </div>

          <!-- Type & Tags -->
          <div class="form-row">
            <div class="form-group" style="flex: 1">
              <label class="form-label">类型</label>
              <el-select v-model="postType" size="large" style="width: 100%">
                <el-option label="经验分享" value="experience">
                  <el-icon><StarFilled /></el-icon>
                  <span>经验分享</span>
                </el-option>
                <el-option label="技巧教程" value="skill">
                  <el-icon><EditPen /></el-icon>
                  <span>技巧教程</span>
                </el-option>
                <el-option label="讨论交流" value="discuss">
                  <el-icon><ChatDotSquare /></el-icon>
                  <span>讨论交流</span>
                </el-option>
              </el-select>
            </div>

            <div class="form-group" style="flex: 2">
              <label class="form-label">标签</label>
              <el-select
                v-model="selectedTags"
                multiple
                filterable
                allow-create
                placeholder="选择或创建标签..."
                size="large"
                style="width: 100%"
              >
                <el-option
                  v-for="tag in presetTags"
                  :key="tag"
                  :label="tag"
                  :value="tag"
                />
              </el-select>
            </div>
          </div>

          <!-- Editor tabs -->
          <div class="form-group">
            <label class="form-label">内容</label>
            <div class="editor-tabs">
              <el-radio-group v-model="editorMode" size="small">
                <el-radio-button value="edit">编辑</el-radio-button>
                <el-radio-button value="preview">预览</el-radio-button>
                <el-radio-button value="split">分屏</el-radio-button>
              </el-radio-group>
            </div>

            <div class="editor-area" :class="{ split: editorMode === 'split' }">
              <div v-show="editorMode === 'edit' || editorMode === 'split'" class="editor-pane">
                <el-input
                  v-model="postContent"
                  type="textarea"
                  :rows="editorMode === 'split' ? 20 : 16"
                  placeholder="支持 Markdown 格式...&#10;&#10;## 标题&#10;正文内容...&#10;&#10;```python&#10;print('代码块')&#10;```"
                  resize="vertical"
                />
              </div>
              <div v-show="editorMode === 'preview' || editorMode === 'split'" class="preview-pane">
                <div class="preview-content markdown-body" v-html="renderedPreview"></div>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="form-actions">
            <el-button @click="$router.back()">取消</el-button>
            <el-button type="primary" :disabled="!canPublish" @click="publish" :loading="publishing">
              <el-icon><Promotion /></el-icon>
              发布帖子
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- Sidebar tips -->
      <div class="editor-sidebar">
        <DataCard title="写作指南">
          <ul class="tips-list">
            <li>使用清晰、具体的标题</li>
            <li>Markdown 格式排版更美观</li>
            <li>代码块使用 ```language 包裹</li>
            <li>添加相关标签获得更多曝光</li>
            <li>经验分享类建议包含具体数据</li>
          </ul>
        </DataCard>

        <DataCard title="预览" style="margin-top: 16px" v-if="postTitle || postContent">
          <div class="mini-preview">
            <h4 class="mini-title">{{ postTitle || '(未填写标题)' }}</h4>
            <p class="mini-excerpt">{{ excerpt }}</p>
            <div class="mini-tags" v-if="selectedTags.length > 0">
              <el-tag v-for="t in selectedTags" :key="t" size="small">{{ t }}</el-tag>
            </div>
          </div>
        </DataCard>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { StarFilled, EditPen, ChatDotSquare, Promotion } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataCard from '@/components/common/DataCard.vue'

const router = useRouter()

const postTitle = ref('')
const postType = ref('discuss')
const postContent = ref('')
const selectedTags = ref([])
const editorMode = ref('edit')
const publishing = ref(false)

const presetTags = ['经验分享', '建模技巧', 'Python', 'MATLAB', '深度学习', '时间序列', '国赛', '美赛', '优化算法', '数据可视化', '论文写作']

const canPublish = computed(() => postTitle.value.trim() && postContent.value.trim())

const excerpt = computed(() => {
  return postContent.value.replace(/[#>*`\n]/g, ' ').slice(0, 80) + (postContent.value.length > 80 ? '...' : '')
})

const renderedPreview = computed(() => {
  if (!postContent.value) return '<p style="color: #94a3b8">暂无内容</p>'
  return marked(postContent.value)
})

function publish() {
  if (!canPublish.value) return
  publishing.value = true
  setTimeout(() => {
    publishing.value = false
    ElMessage.success('帖子发布成功！')
    router.push('/community')
  }, 800)
}
</script>

<style scoped>
.create-post-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 20px;
}

.editor-layout {
  display: flex;
  gap: 24px;
}

.editor-main {
  flex: 1;
  min-width: 0;
}

.editor-sidebar {
  width: 260px;
  flex-shrink: 0;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--lm-text-primary);
  margin-bottom: 8px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.editor-tabs {
  margin-bottom: 10px;
}

.editor-area.split {
  display: flex;
  gap: 16px;
}

.editor-pane {
  flex: 1;
  min-width: 0;
}

.preview-pane {
  flex: 1;
  min-width: 0;
  background: var(--lm-bg);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  padding: 16px;
  max-height: 500px;
  overflow-y: auto;
}

.preview-content :deep(h2) { font-size: 18px; margin: 16px 0 8px; }
.preview-content :deep(h3) { font-size: 15px; margin: 12px 0 6px; }
.preview-content :deep(p) { margin: 0 0 10px; line-height: 1.7; font-size: 14px; }
.preview-content :deep(code) { background: rgba(0,0,0,0.06); padding: 2px 6px; border-radius: 3px; font-size: 13px; }
.preview-content :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 14px; border-radius: 8px; overflow-x: auto; font-size: 13px; }

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--lm-border-light);
}

.tips-list {
  margin: 0;
  padding: 0 0 0 16px;
}

.tips-list li {
  font-size: 13px;
  color: var(--lm-text-secondary);
  margin-bottom: 6px;
  line-height: 1.5;
}

.mini-preview {
  background: var(--lm-bg);
  border-radius: var(--lm-radius-sm);
  padding: 10px;
}

.mini-title {
  font-size: 14px; font-weight: 600; margin: 0 0 6px;
  color: var(--lm-text-primary);
}

.mini-excerpt {
  font-size: 12px; color: var(--lm-text-secondary); margin: 0 0 8px; line-height: 1.4;
}

.mini-tags {
  display: flex; gap: 4px; flex-wrap: wrap;
}

@media (max-width: 900px) {
  .editor-layout { flex-direction: column; }
  .editor-sidebar { width: 100%; }
  .form-row { flex-direction: column; gap: 0; }
}
</style>
