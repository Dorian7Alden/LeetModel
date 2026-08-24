<template>
  <div v-loading="loading" class="team-detail-page">
    <template v-if="team">
      <div class="hero-section">
        <div class="hero-content">
          <div class="hero-info">
            <h1 class="team-name">{{ team.name }}</h1>
            <p class="team-desc">{{ team.description || '暂无队伍简介' }}</p>
            <div class="hero-meta">
              <span class="meta-item"><el-icon><Calendar /></el-icon>{{ formatDate(team.createTime) }} 创建</span>
              <span class="meta-item"><el-icon><UserFilled /></el-icon>{{ team.members.length }} / {{ team.maxMembers }} 人</span>
              <el-tag :type="team.status === 1 ? 'success' : 'info'">{{ team.status === 1 ? '活跃' : '已解散' }}</el-tag>
              <el-tag :type="practiceTagType">{{ practiceLabel }}</el-tag>
            </div>
          </div>
          <div v-if="team.status === 1" class="hero-action">
            <el-button v-if="isLeader" @click="openEditDialog">编辑资料</el-button>
            <el-button v-if="isLeader" type="danger" plain @click="handleDissolve">解散队伍</el-button>
            <el-button v-else-if="team.canLeave" type="danger" plain @click="handleLeave">退出队伍</el-button>
            <el-button v-else-if="team.currentUserRelation === 'pending'" type="warning" plain @click="handleCancelApplication">取消申请</el-button>
            <el-button v-else-if="team.canApply" type="primary" @click="showApplyDialog = true">申请加入</el-button>
          </div>
        </div>
      </div>

      <div class="detail-body">
        <div class="section practice-section">
          <div class="section-heading">
            <div>
              <h3 class="section-title">限时练习</h3>
              <p class="practice-meta">题目 ID：{{ team.problemId }}<template v-if="team.startedAt"> · 开始于 {{ formatDate(team.startedAt) }}</template></p>
            </div>
            <el-button v-if="canManage && team.practiceStatus === 'PREPARING'" type="primary" :loading="startingPractice" @click="handleStartPractice">开始练习</el-button>
          </div>
          <el-alert v-if="team.practiceStatus === 'PREPARING'" title="开始前需确保建模手、编程手和论文手三类职责均有人承担" type="info" :closable="false" show-icon />
          <el-statistic v-else-if="team.practiceStatus === 'IN_PROGRESS'" title="距离截止" :value="remainingSeconds" value-style="color: #e6a23c">
            <template #suffix>秒</template>
          </el-statistic>
          <p v-else-if="team.deadlineAt" class="practice-meta">截止时间：{{ formatDate(team.deadlineAt) }}</p>
        </div>

        <div v-if="isMember && team.practiceStatus !== 'PREPARING'" class="section">
          <div class="section-heading"><h3 class="section-title">论文提交</h3><el-button @click="loadSubmissions">刷新历史</el-button></div>
          <el-upload v-if="team.practiceStatus === 'IN_PROGRESS'" :auto-upload="false" :limit="1" accept="application/pdf,.pdf" :on-change="handlePdfChange" :on-remove="handlePdfRemove">
            <el-button type="primary" plain>选择 PDF</el-button>
            <template #tip><div class="el-upload__tip">仅支持 PDF，每次成功提交都会生成新版本</div></template>
          </el-upload>
          <el-button v-if="selectedPdf" type="primary" :loading="submitting" class="submit-button" @click="handleSubmitPdf">提交第 {{ nextVersion }} 版</el-button>
          <el-button v-if="team.practiceStatus !== 'IN_PROGRESS'" :loading="finalizing" @click="handleFinalize">锁定并查看最终版本</el-button>
          <el-table :data="submissions" size="small" class="submission-table" empty-text="暂无提交版本">
            <el-table-column prop="version" label="版本" width="80"><template #default="scope">V{{ scope.row.version }}</template></el-table-column>
            <el-table-column prop="originalFilename" label="文件名" min-width="180" />
            <el-table-column prop="fileSize" label="大小" width="110"><template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template></el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="170"><template #default="scope">{{ formatDate(scope.row.createTime) }}</template></el-table-column>
            <el-table-column label="操作" width="90"><template #default="scope"><a :href="scope.row.downloadUrl" target="_blank" rel="noopener">下载</a></template></el-table-column>
          </el-table>
        </div>

        <div v-if="isMember && team.practiceStatus !== 'PREPARING'" class="section">
          <div class="section-heading"><h3 class="section-title">AI 评审结果</h3><el-button @click="loadReviews">刷新结果</el-button></div>
          <el-table :data="reviews" size="small" empty-text="暂无已生成的评审结果">
            <el-table-column prop="submissionId" label="提交 ID" min-width="150" />
            <el-table-column prop="workflowVersion" label="评审版本" width="160" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="totalScore" label="总分" width="90" />
            <el-table-column prop="finishedAt" label="完成时间" width="170"><template #default="scope">{{ formatDate(scope.row.finishedAt) }}</template></el-table-column>
            <el-table-column label="操作" width="120"><template #default="scope"><el-button v-if="scope.row.status === 'FAILED'" type="danger" link @click="handleRetryReview(scope.row.taskId)">重试</el-button><el-button v-if="scope.row.resultJson" type="primary" link @click="showReviewResult(scope.row)">查看结果</el-button></template></el-table-column>
          </el-table>
        </div>

        <div class="section">
          <div class="section-heading">
            <h3 class="section-title">当前成员</h3>
            <el-button v-if="isLeader && team.status === 1" type="primary" size="small" @click="showAddDialog = true">添加成员</el-button>
          </div>
          <div class="member-list">
            <div v-for="member in team.members" :key="member.userId" class="member-item">
              <div class="member-avatar"><img v-if="member.avatarUrl" :src="member.avatarUrl" class="member-avatar-image" /><template v-else>{{ (member.nickname || String(member.userId)).charAt(0) }}</template></div>
              <div class="member-info">
                <span class="member-name">{{ member.nickname || `用户 ${member.userId}` }}</span>
                <span class="member-role">{{ member.role === 'leader' ? '队长' : '成员' }}</span>
              </div>
              <div class="professional-roles">
                <el-checkbox :model-value="member.modeler" :disabled="!canManage" @change="value => handleRoleChange(member, 'modeler', value)">建模手</el-checkbox>
                <el-checkbox :model-value="member.programmer" :disabled="!canManage" @change="value => handleRoleChange(member, 'programmer', value)">编程手</el-checkbox>
                <el-checkbox :model-value="member.writer" :disabled="!canManage" @change="value => handleRoleChange(member, 'writer', value)">论文手</el-checkbox>
              </div>
              <el-button v-if="canManage && member.role !== 'leader'" type="danger" text @click="handleRemoveMember(member.userId)">移除</el-button>
            </div>
          </div>
        </div>

        <div class="section highlight-section">
          <h3 class="section-title">招募需求</h3>
          <el-form :model="recruitmentForm" label-width="90px">
            <el-form-item label="招募状态"><el-switch v-model="recruitmentForm.recruiting" :disabled="!canManage" active-text="招募中" inactive-text="暂停招募" /></el-form-item>
            <el-form-item label="招募角色"><el-checkbox v-model="recruitmentForm.needModeler" :disabled="!canManage">建模手</el-checkbox><el-checkbox v-model="recruitmentForm.needProgrammer" :disabled="!canManage">编程手</el-checkbox><el-checkbox v-model="recruitmentForm.needWriter" :disabled="!canManage">论文手</el-checkbox></el-form-item>
            <el-form-item v-if="canManage"><el-button type="primary" @click="handleUpdateRecruitment">保存招募配置</el-button></el-form-item>
          </el-form>
        </div>

        <div v-if="isLeader" class="section">
          <h3 class="section-title">入队申请</h3>
          <el-empty v-if="applications.length === 0" description="暂无入队申请" :image-size="70" />
          <div v-for="application in applications" :key="application.id" class="application-item">
            <span>{{ application.nickname || `用户 ${application.applicantId}` }}</span>
            <span>{{ application.message || '无申请说明' }}</span>
            <el-tag>{{ application.status }}</el-tag>
            <template v-if="application.status === 'pending'"><el-button type="success" text @click="handleReview(application.id, 'approved')">通过</el-button><el-button type="danger" text @click="handleReview(application.id, 'rejected')">拒绝</el-button></template>
          </div>
        </div>
        <div class="section">
          <h3 class="section-title">队伍统计</h3>
          <el-empty description="暂无真实统计接口" :image-size="70" />
        </div>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="队伍不存在" />

    <el-dialog v-model="showEditDialog" title="编辑队伍资料" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="队伍名称" required><el-input v-model="editForm.name" maxlength="64" show-word-limit /></el-form-item>
        <el-form-item label="队伍简介"><el-input v-model="editForm.description" type="textarea" :rows="4" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showEditDialog = false">取消</el-button><el-button type="primary" @click="handleUpdate">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showAddDialog" title="添加成员" width="420px">
      <el-alert title="当前为队长按用户 ID 直接添加的辅助能力" type="warning" :closable="false" show-icon />
      <el-form label-width="80px" class="dialog-form"><el-form-item label="用户 ID"><el-input-number v-model="addUserId" :min="1" /></el-form-item></el-form>
      <template #footer><el-button @click="showAddDialog = false">取消</el-button><el-button type="primary" @click="handleAddMember">确认添加</el-button></template>
    </el-dialog>

    <el-dialog v-model="showApplyDialog" title="申请加入队伍" width="500px">
      <el-form :model="applyForm" label-width="90px">
        <el-form-item label="希望担任"><el-checkbox v-model="applyForm.desiredModeler">建模手</el-checkbox><el-checkbox v-model="applyForm.desiredProgrammer">编程手</el-checkbox><el-checkbox v-model="applyForm.desiredWriter">论文手</el-checkbox></el-form-item>
        <el-form-item label="申请说明"><el-input v-model="applyForm.message" type="textarea" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showApplyDialog = false">取消</el-button><el-button type="primary" @click="handleSubmitApplication">提交申请</el-button></template>
    </el-dialog>
    <el-dialog v-model="showReviewDialog" title="AI 评审详情" width="680px"><pre class="review-json">{{ selectedReviewJson }}</pre></el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, UserFilled } from '@element-plus/icons-vue'
import { addTeamMember, cancelTeamApplication, dissolveTeam, getTeamApplications, getTeamDetail, leaveTeam, removeTeamMember, reviewTeamApplication, startTeamPractice, submitTeamApplication, updateTeam, updateTeamMemberRoles, updateTeamRecruitment } from '@/api/team'
import { finalizeTeamSubmission, getTeamSubmissionHistory, submitTeamPdf } from '@/api/submission'
import { getTeamReviews, retryReviewTask } from '@/api/review'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const team = ref(null)
const showEditDialog = ref(false)
const showAddDialog = ref(false)
const showApplyDialog = ref(false)
const applications = ref([])
const submissions = ref([])
const reviews = ref([])
const showReviewDialog = ref(false)
const selectedReviewJson = ref('')
const selectedPdf = ref(null)
const submitting = ref(false)
const finalizing = ref(false)
const startingPractice = ref(false)
const now = ref(Date.now())
let clockTimer
const addUserId = ref(null)
const editForm = reactive({ name: '', description: '' })
const recruitmentForm = reactive({ recruiting: false, needModeler: false, needProgrammer: false, needWriter: false })
const applyForm = reactive({ desiredModeler: false, desiredProgrammer: false, desiredWriter: false, message: '' })
const currentUserId = computed(() => Number(userStore.userId))
const isLeader = computed(() => team.value?.leaderId === currentUserId.value)
const isMember = computed(() => team.value?.members.some(member => member.userId === currentUserId.value) || false)
const canManage = computed(() => isLeader.value && team.value?.status === 1)
const remainingSeconds = computed(() => Math.max(0, Math.floor((new Date(team.value?.deadlineAt || 0).getTime() - now.value) / 1000)))
const nextVersion = computed(() => Math.max(0, ...submissions.value.map(item => item.version || 0)) + 1)
const practiceLabel = computed(() => ({ PREPARING: '组建中', IN_PROGRESS: '练习中', ENDED: '已结束' })[team.value?.practiceStatus] || team.value?.practiceStatus || '未知')
const practiceTagType = computed(() => ({ PREPARING: 'info', IN_PROGRESS: 'warning', ENDED: 'success' })[team.value?.practiceStatus] || 'info')

async function loadTeam() {
  loading.value = true
  try {
    team.value = (await getTeamDetail(route.params.id)).data
    Object.assign(recruitmentForm, { recruiting: team.value.recruiting, needModeler: team.value.needModeler, needProgrammer: team.value.needProgrammer, needWriter: team.value.needWriter })
    if (team.value.canManage) applications.value = (await getTeamApplications(team.value.id)).data || []
    if (team.value.members?.some(member => member.userId === currentUserId.value) && team.value.practiceStatus !== 'PREPARING') await Promise.all([loadSubmissions(), loadReviews()])
  }
  catch (error) { ElMessage.error(error.message || '队伍详情加载失败') }
  finally { loading.value = false }
}

function formatDate(value) { return value ? value.replace('T', ' ').slice(0, 16) : '未知时间' }
function formatFileSize(value) { return value == null ? '-' : value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB` }
function handlePdfChange(file) { selectedPdf.value = file.raw || null }
function handlePdfRemove() { selectedPdf.value = null }

async function loadSubmissions() {
  try { submissions.value = (await getTeamSubmissionHistory(team.value.id)).data || [] }
  catch (error) { ElMessage.error(error.message || '提交历史加载失败') }
}
async function loadReviews() {
  try { reviews.value = (await getTeamReviews(team.value.id)).data || [] }
  catch (error) { ElMessage.error(error.message || '评审结果加载失败') }
}
function showReviewResult(review) {
  try { selectedReviewJson.value = JSON.stringify(JSON.parse(review.resultJson), null, 2) }
  catch { selectedReviewJson.value = review.resultJson }
  showReviewDialog.value = true
}
async function handleRetryReview(taskId) {
  try { await retryReviewTask(taskId); await loadReviews(); ElMessage.success('评审任务已重新排队') }
  catch (error) { ElMessage.error(error.message || '评审任务重试失败') }
}

async function handleStartPractice() {
  try {
    await ElMessageBox.confirm('开始后将立即按题目时长倒计时，成员和职责不可再修改。确定开始吗？', '开始限时练习', { type: 'warning' })
    startingPractice.value = true
    team.value = (await startTeamPractice(team.value.id)).data
    await loadSubmissions()
    ElMessage.success('限时练习已开始')
  } catch (error) { if (error !== 'cancel') ElMessage.error(error.message || '开始练习失败') }
  finally { startingPractice.value = false }
}

async function handleSubmitPdf() {
  if (!selectedPdf.value) return
  submitting.value = true
  try { await submitTeamPdf(team.value.id, selectedPdf.value); selectedPdf.value = null; await loadSubmissions(); ElMessage.success('PDF 提交成功') }
  catch (error) { ElMessage.error(error.message || 'PDF 提交失败') }
  finally { submitting.value = false }
}

async function handleFinalize() {
  finalizing.value = true
  try { const result = (await finalizeTeamSubmission(team.value.id)).data; ElMessage.success(`最终版本已锁定为 V${result.version}`); await loadSubmissions() }
  catch (error) { ElMessage.error(error.message || '最终版本锁定失败') }
  finally { finalizing.value = false }
}
function openEditDialog() {
  Object.assign(editForm, { name: team.value.name, description: team.value.description || '' })
  showEditDialog.value = true
}

async function handleUpdate() {
  if (!editForm.name.trim()) return ElMessage.warning('请输入队伍名称')
  try {
    team.value = (await updateTeam(team.value.id, { name: editForm.name.trim(), description: editForm.description.trim() })).data
    showEditDialog.value = false
    ElMessage.success('队伍资料已更新')
  } catch (error) { ElMessage.error(error.message || '队伍更新失败') }
}

async function handleAddMember() {
  if (!addUserId.value) return ElMessage.warning('请输入用户 ID')
  try {
    await addTeamMember(team.value.id, addUserId.value)
    showAddDialog.value = false
    addUserId.value = null
    await loadTeam()
    ElMessage.success('成员添加成功')
  } catch (error) { ElMessage.error(error.message || '成员添加失败') }
}

async function handleRemoveMember(userId) {
  try {
    await ElMessageBox.confirm(`确定移除用户 ${userId} 吗？`, '移除成员', { type: 'warning' })
    await removeTeamMember(team.value.id, userId)
    await loadTeam()
    ElMessage.success('成员已移除')
  } catch (error) { if (error !== 'cancel') ElMessage.error(error.message || '移除成员失败') }
}

async function handleRoleChange(member, field, value) {
  const previous = member[field]
  member[field] = value
  try {
    Object.assign(member, (await updateTeamMemberRoles(team.value.id, member.userId, {
      modeler: member.modeler, programmer: member.programmer, writer: member.writer,
    })).data)
    ElMessage.success('成员分工已更新')
  } catch (error) {
    member[field] = previous
    ElMessage.error(error.message || '成员分工更新失败')
  }
}

async function handleLeave() {
  try {
    await ElMessageBox.confirm('确定退出当前队伍吗？', '退出队伍', { type: 'warning' })
    await leaveTeam(team.value.id)
    ElMessage.success('已退出队伍')
    router.push('/team')
  } catch (error) { if (error !== 'cancel') ElMessage.error(error.message || '退出队伍失败') }
}

async function handleDissolve() {
  try {
    await ElMessageBox.confirm('解散后将保留历史记录，确定继续吗？', '解散队伍', { type: 'warning' })
    await dissolveTeam(team.value.id)
    await loadTeam()
    ElMessage.success('队伍已解散')
  } catch (error) { if (error !== 'cancel') ElMessage.error(error.message || '解散队伍失败') }
}

async function handleUpdateRecruitment() {
  try { team.value = (await updateTeamRecruitment(team.value.id, recruitmentForm)).data; ElMessage.success('招募配置已更新') }
  catch (error) { ElMessage.error(error.message || '招募配置更新失败') }
}

async function handleSubmitApplication() {
  try {
    await submitTeamApplication(team.value.id, applyForm)
    showApplyDialog.value = false
    await loadTeam()
    ElMessage.success('入队申请已提交')
  } catch (error) { ElMessage.error(error.message || '申请提交失败') }
}

async function handleCancelApplication() {
  try { await cancelTeamApplication(team.value.id); await loadTeam(); ElMessage.success('申请已取消') }
  catch (error) { ElMessage.error(error.message || '取消申请失败') }
}

async function handleReview(applicationId, decision) {
  try { await reviewTeamApplication(team.value.id, applicationId, decision); await loadTeam(); ElMessage.success(decision === 'approved' ? '申请已通过' : '申请已拒绝') }
  catch (error) { ElMessage.error(error.message || '申请审核失败') }
}

onMounted(() => { loadTeam(); clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => window.clearInterval(clockTimer))
</script>

<style scoped>
@import '../style.css';
.practice-meta { margin: 6px 0; color: var(--lm-text-secondary); font-size: 13px; }
.submit-button, .submission-table { margin-top: 16px; }
.review-json { max-height: 520px; overflow: auto; padding: 16px; border-radius: 8px; background: var(--lm-bg-secondary); white-space: pre-wrap; word-break: break-word; }
</style>
