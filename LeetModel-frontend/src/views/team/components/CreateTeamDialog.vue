<template>
  <el-dialog
    :model-value="modelValue"
    class="create-team-dialog"
    title="创建队伍"
    width="min(540px, calc(100vw - 32px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetForm"
  >
    <el-form :model="form" label-width="88px" @submit.prevent>
      <el-form-item label="练习题目" required>
        <div v-if="presetProblem" class="preset-problem">
          <span class="problem-number">题号 {{ presetProblem.id }}</span>
          <span class="problem-title">{{ presetProblem.title }}</span>
        </div>
        <el-select
          v-else
          v-model="form.problemId"
          class="problem-select"
          filterable
          remote
          clearable
          :remote-method="searchProblems"
          :loading="problemLoading"
          :reserve-keyword="false"
          placeholder="选择题目或输入标题关键词检索"
          no-data-text="暂无可选择的已发布题目"
          @visible-change="handleProblemSelectVisible"
          @clear="loadProblemOptions('')"
        >
          <el-option
            v-for="problem in problemOptions"
            :key="problem.id"
            :label="`题号 ${problem.id} · ${problem.title}`"
            :value="String(problem.id)"
          >
            <div class="problem-option">
              <span class="problem-number">题号 {{ problem.id }}</span>
              <span class="problem-title">{{ problem.title }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="队伍名称" required>
        <el-input v-model="form.name" maxlength="64" show-word-limit />
      </el-form-item>
      <el-form-item label="队伍简介">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="256" show-word-limit />
      </el-form-item>
      <div class="capacity-hint">队伍固定最多 3 人，创建后你将成为队长。</div>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleCreate">确认创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTeam } from '@/api/team'
import { getPublicProblemList } from '@/api/problem'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  presetProblem: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'created'])
const router = useRouter()
const form = reactive({ problemId: null, name: '', description: '' })
const problemOptions = ref([])
const problemLoading = ref(false)
const submitting = ref(false)
let searchSequence = 0

watch(
  () => [props.modelValue, props.presetProblem?.id],
  ([visible]) => {
    if (!visible) return
    if (props.presetProblem) form.problemId = String(props.presetProblem.id)
    else loadProblemOptions('')
  },
  { immediate: true },
)

async function searchProblems(keyword) {
  const normalizedKeyword = keyword.trim()
  await loadProblemOptions(normalizedKeyword)
}

async function loadProblemOptions(keyword = '') {
  const sequence = ++searchSequence
  problemLoading.value = true
  try {
    const response = await getPublicProblemList({ page: 1, pageSize: 20, ...(keyword ? { keyword } : {}) })
    if (sequence === searchSequence) problemOptions.value = response.data?.rows || []
  } catch (error) {
    if (sequence === searchSequence) {
      problemOptions.value = []
      ElMessage.error(error.message || '题目检索失败')
    }
  } finally {
    if (sequence === searchSequence) problemLoading.value = false
  }
}

function handleProblemSelectVisible(visible) {
  if (visible && problemOptions.value.length === 0) loadProblemOptions('')
}

async function handleCreate() {
  const problemId = props.presetProblem?.id ?? form.problemId
  if (!problemId) return ElMessage.warning('请先通过关键词检索并选择练习题目')
  if (!form.name.trim()) return ElMessage.warning('请输入队伍名称')

  submitting.value = true
  try {
    const response = await createTeam({
      problemId: String(problemId),
      name: form.name.trim(),
      description: form.description.trim() || null,
    })
    emit('created', response.data)
    emit('update:modelValue', false)
    ElMessage.success(`队伍“${response.data.name}”创建成功`)
    await router.push('/team')
  } catch (error) {
    ElMessage.error(error.message || '队伍创建失败')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  Object.assign(form, { problemId: null, name: '', description: '' })
  problemOptions.value = []
  problemLoading.value = false
  searchSequence += 1
}
</script>

<style scoped>
.problem-select { width: 100%; }
.preset-problem { display: flex; width: 100%; min-width: 0; align-items: center; gap: 10px; padding: 8px 12px; border: 1px solid var(--lm-border); border-radius: var(--el-border-radius-base); background: var(--lm-bg); line-height: 22px; }
.problem-option { display: flex; align-items: center; gap: 10px; }
.problem-number { flex-shrink: 0; color: var(--lm-primary); font-size: 12px; font-weight: 700; }
.problem-title { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.capacity-hint { margin: -2px 0 0 88px; color: var(--lm-text-secondary); font-size: 12px; }
:deep(.create-team-dialog .el-dialog__title) { white-space: nowrap; }
:deep(.el-form-item__label) { white-space: nowrap; }
@media (max-width: 600px) {
  .capacity-hint { margin-left: 0; }
  :deep(.el-form-item) { display: block; }
  :deep(.el-form-item__label) { width: auto !important; margin-bottom: 6px; }
  :deep(.el-form-item__content) { margin-left: 0 !important; }
}
</style>
