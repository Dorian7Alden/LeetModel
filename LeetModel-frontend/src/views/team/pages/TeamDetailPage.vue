<template>
  <div v-loading="loading" class="team-detail-page">
    <template v-if="team">
      <div class="page-nav">
        <el-button text class="back-button" @click="router.push('/team')">← 返回我的队伍</el-button>
        <span class="team-id">队伍 ID · {{ team.id }}</span>
      </div>
      <div class="hero-section">
        <div class="hero-content">
          <div class="hero-info">
            <div class="hero-eyebrow">{{ isLeader ? '我管理的队伍' : '我加入的队伍' }}</div>
            <h1 class="team-name">{{ team.name }}</h1>
            <p class="team-desc">{{ team.description || '暂无队伍简介' }}</p>
            <div class="hero-meta">
              <span class="meta-item"><el-icon><Calendar /></el-icon>{{ formatDate(team.createTime) }} 创建</span>
              <span class="meta-item"><el-icon><UserFilled /></el-icon>{{ team.members.length }} / {{ team.maxMembers }} 人</span>
              <span class="status-pill" :class="`status-${team.practiceStatus?.toLowerCase()}`"><i></i>{{ practiceLabel }}</span>
            </div>
          </div>
          <div v-if="team.status === 1" class="hero-action">
            <el-button v-if="canEditTeam" @click="openEditDialog">编辑资料</el-button>
            <el-button v-if="canManageFormation" type="danger" plain @click="handleDissolve">解散队伍</el-button>
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
              <span class="section-kicker">PRACTICE BRIEF</span>
              <h3 class="section-title">练习题目</h3>
              <p class="problem-title">{{ problem?.title || '题目信息加载中' }}</p>
              <div class="problem-meta-list"><span>题号 {{ team.problemId }}</span><span v-if="problem?.difficulty">{{ difficultyLabel(problem.difficulty) }}</span><span v-if="problem?.durationMinutes">{{ formatDuration(problem.durationMinutes) }}</span></div>
            </div>
            <el-button v-if="canManage && team.practiceStatus === 'PREPARING'" type="primary" :loading="startingPractice" @click="handleStartPractice">开始练习</el-button>
            <el-button v-if="canManage && team.practiceStatus === 'IN_PROGRESS'" type="danger" plain :loading="endingPractice" @click="handleEndPractice">提前结束</el-button>
          </div>
          <div v-if="team.practiceStatus === 'PREPARING'" class="readiness" :class="{ ready: allRolesCovered }"><span class="readiness-dot"></span><div><strong>{{ allRolesCovered ? '已具备开始条件' : `职责准备度 ${coveredRoleCount} / 3` }}</strong><p>{{ allRolesCovered ? '三类核心职责均已有成员承担，可以开始练习。' : '开始前需要覆盖建模、编程和论文三类职责。' }}</p></div></div>
          <div v-else-if="team.practiceStatus === 'IN_PROGRESS'" class="countdown-card"><span>距离截止</span><strong>{{ remainingTimeText }}</strong><small>{{ formatDate(team.deadlineAt) }}</small></div>
          <div v-else-if="team.deadlineAt" class="ended-summary"><span>练习已结束</span><strong>{{ formatDate(team.endedAt || team.deadlineAt) }}</strong></div>
        </div>

        <div v-if="isMember && team.practiceStatus !== 'PREPARING'" class="section">
          <div class="section-heading">
            <div>
              <span class="section-kicker">SUBMISSIONS</span>
              <h3 class="section-title">论文提交</h3>
            </div>
            <el-button plain @click="loadSubmissions">刷新历史</el-button>
          </div>
          <el-alert v-if="team.practiceStatus === 'IN_PROGRESS' && !currentMemberCanSubmit" title="队长尚未授予你作品提交权限" type="warning" :closable="false" show-icon />
          <div v-if="team.practiceStatus === 'IN_PROGRESS' && currentMemberCanSubmit" class="upload-panel">
            <div class="upload-toolbar">
              <el-upload :auto-upload="false" :show-file-list="false" accept="application/pdf,.pdf" :on-change="handlePdfChange">
                <el-button type="primary" plain>选择 PDF</el-button>
              </el-upload>
              <div v-if="selectedPdf" class="selected-file">
                <div class="file-icon">PDF</div>
                <div class="file-meta">
                  <strong>{{ selectedPdf.name }}</strong>
                  <span>{{ formatFileSize(selectedPdf.size) }}</span>
                </div>
                <el-button text class="remove-file" @click="handlePdfRemove">移除</el-button>
              </div>
              <div v-else class="file-placeholder">尚未选择文件</div>
              <el-button type="primary" :disabled="!selectedPdf" :loading="submitting" class="submit-button" @click="handleSubmitPdf">提交第 {{ nextVersion }} 版</el-button>
            </div>
            <p class="upload-tip">仅支持 PDF 文件；每次成功提交都会保留为一个新版本。</p>
          </div>
          <div v-if="team.practiceStatus !== 'IN_PROGRESS'" class="final-version-bar">
            <span>练习已经结束，可以锁定并查看最终提交版本。</span>
            <el-button type="primary" plain :loading="finalizing" @click="handleFinalize">锁定最终版本</el-button>
          </div>
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
          <div class="section-heading"><div><span class="section-kicker">TEAM ROSTER</span><h3 class="section-title">成员与职责</h3></div><div class="capacity"><strong>{{ team.members.length }}</strong><span>/ {{ team.maxMembers }} 人</span></div></div>
          <div class="member-list">
            <div v-for="member in team.members" :key="member.userId" class="member-item" :class="{ 'is-leader': member.role === 'leader' }">
              <div class="member-avatar" :class="{ 'has-image': member.avatarUrl }"><img v-if="member.avatarUrl" :src="member.avatarUrl" class="member-avatar-image" /><template v-else>{{ (member.nickname || String(member.userId)).charAt(0) }}</template></div>
              <div class="member-info">
                <span class="member-name">{{ member.nickname || `用户 ${member.userId}` }}</span>
                <span class="member-role">{{ member.role === 'leader' ? '队长 · 创建者' : '队伍成员' }}</span>
              </div>
              <div class="professional-roles">
                <template v-if="canManageFormation"><el-checkbox :model-value="member.modeler" @change="value => handleRoleChange(member, 'modeler', value)">建模</el-checkbox><el-checkbox :model-value="member.programmer" @change="value => handleRoleChange(member, 'programmer', value)">编程</el-checkbox><el-checkbox :model-value="member.writer" @change="value => handleRoleChange(member, 'writer', value)">论文</el-checkbox></template>
                <template v-else><span v-for="role in memberRoles(member)" :key="role" class="role-chip">{{ role }}</span><span v-if="memberRoles(member).length === 0" class="role-empty">暂未分配职责</span></template>
              </div>
              <div class="submission-permission">
                <el-tag v-if="member.role === 'leader'" type="success" size="small">可提交作品</el-tag>
                <el-switch v-else :model-value="member.canSubmit" :disabled="!canManageSubmissionPermission" active-text="可提交" inactive-text="不可提交" @change="value => handleSubmissionPermissionChange(member, value)" />
              </div>
              <el-button v-if="canManageFormation && member.role !== 'leader'" type="danger" text @click="handleRemoveMember(member.userId)">移除</el-button>
            </div>
          </div>
        </div>

        <div v-if="team.practiceStatus === 'PREPARING'" class="section highlight-section">
          <div class="section-heading"><div><span class="section-kicker">RECRUITMENT</span><h3 class="section-title">招募位置</h3></div><span class="section-note">每条招募对应 1 个成员位置</span></div>
          <el-form :model="recruitmentForm" class="recruitment-publisher">
            <el-form-item label="招募角色："><el-checkbox v-model="recruitmentForm.needModeler" :disabled="!canManageFormation">建模手</el-checkbox><el-checkbox v-model="recruitmentForm.needProgrammer" :disabled="!canManageFormation">编程手</el-checkbox><el-checkbox v-model="recruitmentForm.needWriter" :disabled="!canManageFormation">论文手</el-checkbox><el-button v-if="canManageFormation" class="publish-recruitment-button" type="primary" :disabled="team.members.length + openRecruitments.length >= team.maxMembers" @click="handlePublishRecruitment">发布招募</el-button><span class="slot-hint">已占 {{ team.members.length + openRecruitments.length }} / {{ team.maxMembers }}</span></el-form-item>
          </el-form>
          <el-empty v-if="openRecruitments.length === 0" description="暂无招募信息" :image-size="60" />
          <div v-for="item in openRecruitments" :key="item.id" class="recruitment-slot">
            <div class="vacant-avatar"><el-icon><User /></el-icon></div>
            <div class="vacant-info"><strong>等待队友加入</strong><span>{{ recruitmentRolesText(item) }}</span></div>
            <el-tag type="success" effect="plain" round>招募中</el-tag>
            <el-button v-if="canManageFormation" type="danger" text @click="handleCloseRecruitment(item.id)">关闭招募</el-button>
          </div>
        </div>

        <div v-if="isLeader && team.practiceStatus === 'PREPARING'" class="section">
          <div class="section-heading"><div><span class="section-kicker">APPLICATIONS</span><h3 class="section-title">入队申请</h3></div><el-badge :value="pendingApplicationCount" :hidden="pendingApplicationCount === 0" /></div>
          <el-empty v-if="applications.length === 0" description="暂无入队申请" :image-size="70" />
          <div v-for="application in applications" :key="application.id" class="application-card" :class="`application-${application.status}`">
            <div class="application-person">
              <div class="application-avatar" :class="{ 'has-image': application.avatarUrl }"><img v-if="application.avatarUrl" :src="application.avatarUrl" /><template v-else>{{ (application.nickname || String(application.applicantId)).charAt(0) }}</template></div>
              <div><strong>{{ application.nickname || `用户 ${application.applicantId}` }}</strong><span>{{ formatDate(application.createTime) }} 申请</span></div>
            </div>
            <div class="application-content">
              <div class="application-roles"><span v-for="role in recruitmentRoleList(application)" :key="role">{{ role }}</span></div>
              <p>{{ application.message || '申请人没有填写补充说明。' }}</p>
            </div>
            <div class="application-side">
              <el-tag :type="applicationStatusType(application.status)" effect="light" round>{{ applicationStatusLabel(application.status) }}</el-tag>
              <div v-if="application.status === 'pending'" class="application-actions"><el-button type="primary" size="small" @click="handleReview(application.id, 'approved')">通过申请</el-button><el-button size="small" @click="handleReview(application.id, 'rejected')">拒绝</el-button></div>
              <span v-else-if="application.handledAt" class="handled-time">{{ formatDate(application.handledAt) }} 处理</span>
            </div>
          </div>
        </div>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="队伍不存在" />

    <el-dialog v-model="showEditDialog" title="编辑队伍资料" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="队伍名称" required><el-input v-model="editForm.name" :disabled="team.practiceStatus !== 'PREPARING'" maxlength="64" show-word-limit /></el-form-item>
        <el-form-item label="队伍简介"><el-input v-model="editForm.description" type="textarea" :rows="4" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showEditDialog = false">取消</el-button><el-button type="primary" @click="handleUpdate">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showApplyDialog" title="申请加入队伍" width="500px">
      <el-form :model="applyForm" label-width="90px">
        <el-form-item label="招募职位"><el-radio-group v-model="applyForm.recruitmentId"><el-radio v-for="item in openRecruitments" :key="item.id" :value="item.id">{{ recruitmentRolesText(item) }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="申请说明"><el-input v-model="applyForm.message" type="textarea" maxlength="256" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showApplyDialog = false">取消</el-button><el-button type="primary" @click="handleSubmitApplication">提交申请</el-button></template>
    </el-dialog>
    <el-dialog v-model="showReviewDialog" title="AI 评审详情" width="680px"><pre class="review-json">{{ selectedReviewJson }}</pre></el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, User, UserFilled } from '@element-plus/icons-vue'
import { cancelTeamApplication, closeTeamRecruitment, dissolveTeam, endTeamPractice, getTeamApplications, getTeamDetail, leaveTeam, publishTeamRecruitment, removeTeamMember, reviewTeamApplication, startTeamPractice, submitTeamApplication, updateTeam, updateTeamMemberRoles, updateTeamSubmissionPermission } from '@/api/team'
import { getPublicProblemDetail } from '@/api/problem'
import { finalizeTeamSubmission, getTeamSubmissionHistory, submitTeamPdf } from '@/api/submission'
import { getTeamReviews, retryReviewTask } from '@/api/review'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const team = ref(null)
const problem = ref(null)
const showEditDialog = ref(false)
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
const endingPractice = ref(false)
const now = ref(Date.now())
let clockTimer
const editForm = reactive({ name: '', description: '' })
const recruitmentForm = reactive({ needModeler: false, needProgrammer: false, needWriter: false })
const applyForm = reactive({ recruitmentId: null, message: '' })
const currentUserId = computed(() => Number(userStore.userId))
const isLeader = computed(() => team.value?.leaderId === currentUserId.value)
const isMember = computed(() => team.value?.members.some(member => member.userId === currentUserId.value) || false)
const canManage = computed(() => isLeader.value && team.value?.status === 1)
const canManageFormation = computed(() => canManage.value && team.value?.practiceStatus === 'PREPARING')
const canEditTeam = computed(() => canManage.value && ['PREPARING', 'IN_PROGRESS'].includes(team.value?.practiceStatus))
const canManageSubmissionPermission = computed(() => canManage.value && ['PREPARING', 'IN_PROGRESS'].includes(team.value?.practiceStatus))
const currentMember = computed(() => team.value?.members.find(member => member.userId === currentUserId.value))
const currentMemberCanSubmit = computed(() => currentMember.value?.role === 'leader' || Boolean(currentMember.value?.canSubmit))
const openRecruitments = computed(() => (team.value?.recruitments || []).filter(item => item.status === 'OPEN'))
const coveredRoleCount = computed(() => ['modeler', 'programmer', 'writer'].filter(role => team.value?.members.some(member => member[role])).length)
const allRolesCovered = computed(() => coveredRoleCount.value === 3)
const pendingApplicationCount = computed(() => applications.value.filter(item => item.status === 'pending').length)
const remainingSeconds = computed(() => Math.max(0, Math.floor((new Date(team.value?.deadlineAt || 0).getTime() - now.value) / 1000)))
const remainingTimeText = computed(() => { const seconds = remainingSeconds.value; const hours = Math.floor(seconds / 3600); const minutes = Math.floor((seconds % 3600) / 60); return `${String(hours).padStart(2, '0')} : ${String(minutes).padStart(2, '0')} : ${String(seconds % 60).padStart(2, '0')}` })
const nextVersion = computed(() => Math.max(0, ...submissions.value.map(item => item.version || 0)) + 1)
const practiceLabel = computed(() => ({ PREPARING: '组建中', IN_PROGRESS: '练习中', ENDED: '已结束' })[team.value?.practiceStatus] || team.value?.practiceStatus || '未知')

async function loadTeam() {
  loading.value = true
  try {
    team.value = (await getTeamDetail(route.params.id)).data
    problem.value = (await getPublicProblemDetail(team.value.problemId)).data
    if (team.value.canManage) applications.value = (await getTeamApplications(team.value.id)).data || []
    if (team.value.members?.some(member => member.userId === currentUserId.value) && team.value.practiceStatus !== 'PREPARING') await Promise.all([loadSubmissions(), loadReviews()])
  }
  catch (error) { ElMessage.error(error.message || '队伍详情加载失败') }
  finally { loading.value = false }
}

function formatDate(value) { return value ? value.replace('T', ' ').slice(0, 16) : '未知时间' }
function difficultyLabel(value) { return ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知' }
function formatDuration(minutes) { return minutes % 60 === 0 ? `${minutes / 60} 小时` : `${minutes} 分钟` }
function formatFileSize(value) { return value == null ? '-' : value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB` }
function memberRoles(member) { return [member.modeler && '建模', member.programmer && '编程', member.writer && '论文'].filter(Boolean) }
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

async function handleEndPractice() {
  try {
    await ElMessageBox.confirm('结束后将立即停止作品上传且不能恢复，确定提前结束吗？', '提前结束练习', { type: 'warning' })
    endingPractice.value = true
    team.value = (await endTeamPractice(team.value.id)).data
    ElMessage.success('练习已结束')
  } catch (error) { if (error !== 'cancel') ElMessage.error(error.message || '结束练习失败') }
  finally { endingPractice.value = false }
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

async function handleSubmissionPermissionChange(member, value) {
  const previous = member.canSubmit
  member.canSubmit = value
  try {
    Object.assign(member, (await updateTeamSubmissionPermission(team.value.id, member.userId, value)).data)
    ElMessage.success(value ? '已授予作品提交权限' : '已撤销作品提交权限')
  } catch (error) {
    member.canSubmit = previous
    ElMessage.error(error.message || '提交权限更新失败')
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

function recruitmentRolesText(item) {
  return [item.needModeler && '建模手', item.needProgrammer && '编程手', item.needWriter && '论文手'].filter(Boolean).join('、')
}
function recruitmentRoleList(item) { return [item.needModeler && '建模手', item.needProgrammer && '编程手', item.needWriter && '论文手'].filter(Boolean) }
function applicationStatusLabel(status) { return ({ pending: '待审核', approved: '已通过', rejected: '已拒绝', cancelled: '已取消', closed: '已关闭' })[status] || status }
function applicationStatusType(status) { return ({ pending: 'warning', approved: 'success', rejected: 'danger', cancelled: 'info', closed: 'info' })[status] || 'info' }
async function handlePublishRecruitment() {
  if (!recruitmentRolesText(recruitmentForm)) return ElMessage.warning('请至少选择一个招募职位')
  try { team.value = (await publishTeamRecruitment(team.value.id, recruitmentForm)).data; Object.assign(recruitmentForm, { needModeler: false, needProgrammer: false, needWriter: false }); ElMessage.success('招募已发布') }
  catch (error) { ElMessage.error(error.message || '招募发布失败') }
}
async function handleCloseRecruitment(recruitmentId) {
  try { await closeTeamRecruitment(team.value.id, recruitmentId); await loadTeam(); ElMessage.success('招募已关闭') }
  catch (error) { ElMessage.error(error.message || '招募关闭失败') }
}
async function handleSubmitApplication() {
  if (!applyForm.recruitmentId) return ElMessage.warning('请选择要申请的招募职位')
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
watch(remainingSeconds, (value, previous) => {
  if (value === 0 && previous > 0 && team.value?.practiceStatus === 'IN_PROGRESS') loadTeam()
})
</script>

<style scoped>
@import '../style.css';
.practice-meta { margin: 6px 0; color: var(--lm-text-secondary); font-size: 13px; }
.submission-table { margin-top: 18px; }
.upload-panel { margin-top: 18px; padding: 16px; border: 1px solid var(--lm-border-light); border-radius: 14px; background: #f8fafc; }
.upload-toolbar { display: flex; min-width: 0; align-items: center; gap: 14px; }
.selected-file { display: flex; min-width: 0; flex: 1; align-items: center; gap: 10px; }
.file-icon { display: flex; width: 38px; height: 38px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 9px; background: #fee2e2; color: #dc2626; font-size: 10px; font-weight: 800; }
.file-meta { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }
.file-meta strong { overflow: hidden; color: var(--lm-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.file-meta span, .upload-tip { color: var(--lm-text-muted); font-size: 11px; }
.file-placeholder { flex: 1; color: var(--lm-text-muted); font-size: 13px; }
.remove-file { flex-shrink: 0; color: var(--lm-text-secondary); }
.submit-button { flex-shrink: 0; margin-left: auto; }
.upload-tip { margin: 11px 0 0; }
.final-version-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 18px; padding: 14px 16px; border-radius: 12px; background: #f8fafc; color: var(--lm-text-secondary); font-size: 13px; }
.submission-permission { min-width: 128px; }
.slot-hint { margin-left: 12px; color: var(--lm-text-muted); font-size: 13px; }
.review-json { max-height: 520px; overflow: auto; padding: 16px; border-radius: 8px; background: var(--lm-bg-secondary); white-space: pre-wrap; word-break: break-word; }
@media (max-width: 700px) {
  .upload-toolbar { align-items: stretch; flex-direction: column; }
  .submit-button { width: 100%; margin-left: 0; }
  .final-version-bar { align-items: flex-start; flex-direction: column; }
}
</style>
