<template>
  <div v-loading="loading" class="team-detail-page">
    <el-alert
      v-if="refreshAfterCancelError"
      title="申请已取消，但最新队伍状态加载失败"
      type="warning"
      :description="refreshAfterCancelError"
      :closable="false"
      show-icon
      class="partial-success-alert"
    >
      <template #default><el-button type="warning" plain @click="retryRefreshAfterCancel">重新加载</el-button></template>
    </el-alert>
    <template v-if="team">
      <div class="page-nav">
        <el-button text class="back-button" @click="handleBack">← 返回上一页</el-button>
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
              <div class="problem-meta-list"><span>题号 {{ problem?.code || team.problemCode }}</span><span v-if="problem?.difficulty">{{ difficultyLabel(problem.difficulty) }}</span><span v-if="problem?.durationMinutes">{{ formatDuration(problem.durationMinutes) }}</span></div>
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
            <el-button plain @click="refreshSubmissionReviews">刷新记录</el-button>
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
                <el-button text class="remove-file" :disabled="submitting" @click="handlePdfRemove">移除</el-button>
              </div>
              <div v-else class="file-placeholder">尚未选择文件</div>
              <el-progress v-if="submitting || uploadProgress > 0" type="circle" :percentage="uploadProgress" :width="48" :stroke-width="5" class="upload-progress" />
              <el-button type="primary" :disabled="!selectedPdf" :loading="submitting" class="submit-button" @click="handleSubmitPdf">提交第 {{ nextVersion }} 版</el-button>
            </div>
            <p class="upload-tip">{{ uploadStage || '仅支持 20MB 以内 PDF；中断后重新选择同一文件可继续上传。' }}</p>
          </div>
          <div v-if="team.practiceStatus !== 'IN_PROGRESS'" class="final-version-bar">
            <span>{{ finalSubmission ? `最终提交已锁定为 V${finalSubmission.version}` : '练习已经结束，可以锁定并查看最终提交版本。' }}</span>
            <el-button v-if="!finalSubmission" type="primary" plain :loading="finalizing" @click="handleFinalize">锁定最终版本</el-button>
          </div>
          <div v-if="scoreSummary" class="score-summary" :class="{ final: scoreSummary.submission.finalVersion }">
            <div><span>{{ scoreSummaryLabel }}</span><strong>V{{ scoreSummary.submission.version }}</strong></div>
            <div class="score-value"><strong>{{ scoreSummary.review?.score ?? '--' }}</strong><span>/ 100</span></div>
            <el-tag :type="reviewStatusType(scoreSummary.review?.status)" effect="light">{{ reviewStatusLabel(scoreSummary.review?.status) }}</el-tag>
          </div>
          <el-table :data="submissionRows" size="small" class="submission-table" empty-text="暂无提交版本">
            <el-table-column label="版本" width="120"><template #default="scope"><div class="version-cell"><strong>V{{ scope.row.version }}</strong><el-tag v-if="scope.row.finalVersion" type="success" size="small" effect="light">最终版</el-tag></div></template></el-table-column>
            <el-table-column prop="originalFilename" label="文件名" min-width="180" />
            <el-table-column prop="fileSize" label="大小" width="110"><template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template></el-table-column>
            <el-table-column label="评审状态" width="110"><template #default="scope"><el-tag :type="reviewStatusType(scope.row.review?.status)" size="small" effect="light">{{ reviewStatusLabel(scope.row.review?.status) }}</el-tag></template></el-table-column>
            <el-table-column label="得分" width="90"><template #default="scope"><strong v-if="scope.row.review?.score != null" class="table-score">{{ scope.row.review.score }}</strong><span v-else>--</span></template></el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="170"><template #default="scope">{{ formatDate(scope.row.createTime) }}</template></el-table-column>
            <el-table-column label="操作" width="300"><template #default="scope"><el-button link type="primary"><a :href="scope.row.downloadUrl" target="_blank" rel="noopener">下载</a></el-button><el-button v-if="scope.row.review" type="primary" link @click="showReviewResult(scope.row.review)">查看评审</el-button><el-button v-if="scope.row.review?.status === 'COMPLETED'" type="success" link @click="showSuggestion(scope.row)">改进建议</el-button><el-button v-if="scope.row.review?.status === 'FAILED'" type="danger" link @click="handleRetryReview(scope.row.review.taskId)">重试</el-button></template></el-table-column>
          </el-table>
        </div>

        <div class="section">
          <div class="section-heading"><div><span class="section-kicker">TEAM ROSTER</span><h3 class="section-title">成员与职责</h3></div><div class="capacity"><strong>{{ team.members.length }}</strong><span>/ {{ team.maxMembers }} 人</span></div></div>
          <div class="member-list">
            <div v-for="member in team.members" :key="member.userId" class="member-item" :class="{ 'is-leader': member.role === 'leader' }">
              <button type="button" class="member-avatar member-avatar-button" :class="{ 'has-image': member.avatarUrl }" :aria-label="`查看${member.nickname || `用户 ${member.userId}`}的个人名片`" @click="showUserCard(member)"><img v-if="member.avatarUrl" :src="member.avatarUrl" class="member-avatar-image" /><template v-else>{{ (member.nickname || String(member.userId)).charAt(0) }}</template></button>
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
          <div class="section-heading"><div><span class="section-kicker">RECRUITMENT</span><h3 class="section-title">招募位置</h3></div><div class="recruitment-heading-actions"><span class="section-note">已占 {{ team.members.length + openRecruitments.length }} / {{ team.maxMembers }}</span><el-button v-if="canManageFormation" type="primary" :disabled="team.members.length + openRecruitments.length >= team.maxMembers" @click="openPublishRecruitmentDialog">发布招募</el-button></div></div>
          <el-empty v-if="openRecruitments.length === 0" description="暂无招募信息" :image-size="60" />
          <div v-for="item in openRecruitments" :key="item.id" class="recruitment-slot">
            <div class="vacant-avatar"><el-icon><User /></el-icon></div>
            <div class="vacant-info"><strong>{{ recruitmentRolesText(item) }}</strong><div v-if="item.description" class="recruitment-markdown compact" v-html="renderSafeMarkdown(item.description)" /><span v-else>未填写招募说明</span><small>{{ formatDate(item.createTime) }} 发布</small></div>
            <el-tag type="success" effect="plain" round>招募中</el-tag>
            <el-button v-if="canManageFormation" type="primary" text @click="openEditRecruitmentDialog(item)">编辑</el-button>
            <el-button v-if="canManageFormation" type="danger" text @click="handleCloseRecruitment(item.id)">关闭招募</el-button>
          </div>
        </div>

        <div v-if="isLeader && team.practiceStatus === 'PREPARING'" class="section">
          <div class="section-heading">
            <div><span class="section-kicker">APPLICATIONS</span><h3 class="section-title">入队申请</h3></div>
            <div class="application-tools">
              <el-badge v-if="applicationStatus === 'pending'" :value="applicationTotal" :hidden="applicationTotal === 0" />
              <el-select v-model="applicationStatus" size="small" class="application-status-select" @change="handleApplicationFilterChange">
                <el-option label="待审核" value="pending" />
                <el-option label="全部记录" value="all" />
                <el-option label="已通过" value="approved" />
                <el-option label="已拒绝" value="rejected" />
                <el-option label="已取消" value="cancelled" />
                <el-option label="已关闭" value="closed" />
              </el-select>
            </div>
          </div>
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
          <el-pagination v-if="applicationTotal > applicationPageSize" background layout="prev, pager, next" :current-page="applicationPage" :page-size="applicationPageSize" :total="applicationTotal" class="application-pagination" @current-change="handleApplicationPageChange" />
        </div>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="队伍不存在" />

    <el-dialog v-model="showEditDialog" title="编辑队伍资料" width="500px">
      <el-form :model="editForm" label-width="96px" class="edit-team-form">
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
    <el-dialog v-model="showRecruitmentDialog" :title="editingRecruitmentId ? '编辑招募信息' : '发布招募信息'" width="520px">
      <el-form :model="recruitmentForm" label-width="108px" class="recruitment-dialog-form">
        <el-form-item label="招募职位" required>
          <el-checkbox v-model="recruitmentForm.needModeler">建模手</el-checkbox>
          <el-checkbox v-model="recruitmentForm.needProgrammer">编程手</el-checkbox>
          <el-checkbox v-model="recruitmentForm.needWriter">论文手</el-checkbox>
        </el-form-item>
        <el-form-item label="招募说明">
          <el-input v-model="recruitmentForm.description" type="textarea" :rows="5" maxlength="512" show-word-limit placeholder="支持 Markdown，例如：**协作要求**、- 每周讨论两次" />
          <span class="markdown-hint">支持 Markdown 语法</span>
        </el-form-item>
        <el-form-item v-if="recruitmentForm.description" label="实时预览"><div class="recruitment-markdown preview" v-html="renderSafeMarkdown(recruitmentForm.description)" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showRecruitmentDialog = false">取消</el-button><el-button type="primary" @click="handleSaveRecruitment">{{ editingRecruitmentId ? '保存修改' : '发布招募' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="showReviewDialog" title="AI 评审详情" width="760px">
      <div v-if="selectedReview" class="review-detail">
        <div class="review-version"><div><strong>{{ selectedReview.versionName || selectedReview.workflowVersion }}</strong><p>{{ selectedReview.versionDescription }}</p></div><el-tag :type="reviewStatusType(selectedReview.status)">{{ reviewStatusLabel(selectedReview.status) }}</el-tag></div>
        <p class="review-process">{{ selectedReview.processSummary }}</p>
        <el-alert v-if="selectedReview.status === 'FAILED'" type="error" :title="selectedReview.errorMessage || '评审执行失败'" :closable="false" show-icon />
        <el-empty v-else-if="selectedReview.status !== 'COMPLETED'" :description="selectedReview.status === 'RUNNING' ? 'AI 正在阅读论文，请稍后刷新' : '评审任务正在等待执行'" :image-size="72" />
        <template v-else-if="selectedReviewResult">
          <div class="review-score"><span>论文总分</span><strong>{{ selectedReviewResult.score }}</strong><small>/ 100</small></div>
          <template v-if="selectedReviewIsV2">
            <el-alert title="平台训练评分，用于练习反馈，不代表赛事官方评分" type="info" :closable="false" show-icon />
            <p class="review-summary">{{ selectedReviewResult.overallAssessment }}</p>
            <div class="dimension-grid"><div v-for="item in reviewDimensions" :key="item.key" class="dimension-card"><div><strong>{{ item.label }}</strong><span>{{ item.value?.score }} / {{ item.value?.maxScore }} 分</span></div><p>{{ item.value?.reason }}</p></div></div>
            <div class="review-list"><h4>题目要求覆盖</h4><div v-for="item in selectedReviewResult.requirementCoverage" :key="item.requirementId" class="coverage-item"><el-tag size="small" :type="coverageType(item.status)">{{ coverageLabel(item.status) }}</el-tag><div><strong>{{ item.requirement }}</strong><p>{{ item.explanation }}</p></div></div></div>
            <div class="review-list"><h4>结构化评审发现</h4><div v-for="item in v2Findings" :key="item.findingId" class="finding-item"><div><el-tag size="small" :type="item.type === 'STRENGTH' ? 'success' : 'warning'">{{ item.type === 'STRENGTH' ? '优点' : item.severity }}</el-tag><strong>{{ item.findingId }} · {{ item.statement }}</strong></div><p>{{ item.scoreImpact }}</p><span v-if="item.pages.length">论文第 {{ item.pages.join('、') }} 页</span></div></div>
            <p v-if="selectedReviewResult.limitations?.length" class="review-limitations">运行限制：{{ selectedReviewResult.limitations.join('；') }}</p>
          </template>
          <template v-else>
            <p class="review-summary">{{ selectedReviewResult.summary }}</p>
            <div class="dimension-grid"><div v-for="item in reviewDimensions" :key="item.key" class="dimension-card"><div><strong>{{ item.label }}</strong><span>{{ item.value?.score }} 分</span></div><p>{{ item.value?.comment }}</p></div></div>
            <div v-for="section in reviewLists" :key="section.key" class="review-list"><h4>{{ section.label }}</h4><ul><li v-for="item in section.items" :key="item">{{ item }}</li></ul></div>
          </template>
        </template>
        <el-alert v-else type="warning" title="评审结果暂时无法解析" :closable="false" show-icon />
      </div>
    </el-dialog>
    <UserMiniCardDialog v-model="showMiniCard" :member="selectedMember" />
    <SubmissionSuggestionDialog v-model="showSuggestionDialog" :submission="suggestionSubmission" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { renderSafeMarkdown } from '@/utils/markdown'
import { Calendar, User, UserFilled } from '@element-plus/icons-vue'
import { cancelTeamApplication, closeTeamRecruitment, dissolveTeam, endTeamPractice, getTeamApplications, getTeamDetail, leaveTeam, publishTeamRecruitment, removeTeamMember, reviewTeamApplication, startTeamPractice, submitTeamApplication, updateTeam, updateTeamMemberRoles, updateTeamRecruitment, updateTeamSubmissionPermission } from '@/api/team'
import { getPublicProblemDetail } from '@/api/problem'
import { finalizeTeamSubmission, getTeamSubmissionHistory } from '@/api/submission'
import { getTeamReviews, retryReviewTask } from '@/api/review'
import { useUserStore } from '@/store/user'
import { uploadPdfResumably } from '@/utils/resumablePdfUpload'
import UserMiniCardDialog from '../components/UserMiniCardDialog.vue'
import SubmissionSuggestionDialog from '../components/SubmissionSuggestionDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const refreshAfterCancelError = ref('')
const team = ref(null)
const problem = ref(null)
const showEditDialog = ref(false)
const showApplyDialog = ref(false)
const showRecruitmentDialog = ref(false)
const editingRecruitmentId = ref(null)
const showMiniCard = ref(false)
const selectedMember = ref(null)
const applications = ref([])
const applicationPage = ref(1)
const applicationPageSize = 6
const applicationTotal = ref(0)
const applicationStatus = ref('pending')
const submissions = ref([])
const reviews = ref([])
const showReviewDialog = ref(false)
const selectedReview = ref(null)
const showSuggestionDialog = ref(false)
const suggestionSubmission = ref(null)
const selectedPdf = ref(null)
const submitting = ref(false)
const uploadProgress = ref(0)
const uploadStage = ref('')
const finalizing = ref(false)
const startingPractice = ref(false)
const endingPractice = ref(false)
const now = ref(Date.now())
let clockTimer
let reviewTimer
const editForm = reactive({ name: '', description: '' })
const recruitmentForm = reactive({ needModeler: false, needProgrammer: false, needWriter: false, description: '' })
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
const remainingSeconds = computed(() => Math.max(0, Math.floor((new Date(team.value?.deadlineAt || 0).getTime() - now.value) / 1000)))
const remainingTimeText = computed(() => { const seconds = remainingSeconds.value; const hours = Math.floor(seconds / 3600); const minutes = Math.floor((seconds % 3600) / 60); return `${String(hours).padStart(2, '0')} : ${String(minutes).padStart(2, '0')} : ${String(seconds % 60).padStart(2, '0')}` })
const nextVersion = computed(() => Math.max(0, ...submissions.value.map(item => item.version || 0)) + 1)
const reviewBySubmissionId = computed(() => {
  const map = new Map()
  reviews.value.forEach(item => { if (!map.has(String(item.submissionId))) map.set(String(item.submissionId), item) })
  return map
})
const submissionRows = computed(() => submissions.value.map(item => ({ ...item, review: reviewBySubmissionId.value.get(String(item.id)) })))
const finalSubmission = computed(() => submissions.value.find(item => item.finalVersion))
const scoreSummary = computed(() => {
  if (submissionRows.value.length === 0) return null
  const submission = team.value?.practiceStatus === 'ENDED'
    ? submissionRows.value.find(item => item.finalVersion) || submissionRows.value[0]
    : submissionRows.value[0]
  return { submission, review: submission.review }
})
const scoreSummaryLabel = computed(() => scoreSummary.value?.submission.finalVersion ? '最终得分' : '当前版本得分')
const selectedReviewResult = computed(() => {
  if (!selectedReview.value?.resultJson) return null
  try { return JSON.parse(selectedReview.value.resultJson) } catch { return null }
})
const selectedReviewIsV2 = computed(() => selectedReview.value?.workflowVersion === 'EVIDENCE_REVIEW_V2')
const reviewDimensions = computed(() => {
  if (selectedReviewIsV2.value) {
    return (selectedReviewResult.value?.dimensions || []).map(item => ({ key: item.dimensionId, label: item.name, value: item }))
  }
  const values = selectedReviewResult.value?.dimensions || {}
  return [
    { key: 'assumptionRationality', label: '假设合理性', value: values.assumptionRationality },
    { key: 'modelCreativity', label: '建模创造性', value: values.modelCreativity },
    { key: 'resultCorrectness', label: '结果正确性', value: values.resultCorrectness },
    { key: 'expressionClarity', label: '表达清晰性', value: values.expressionClarity },
  ]
})
const reviewLists = computed(() => [
  { key: 'strengths', label: '主要优点', items: selectedReviewResult.value?.strengths || [] },
  { key: 'weaknesses', label: '主要问题', items: selectedReviewResult.value?.weaknesses || [] },
  { key: 'suggestions', label: '改进建议', items: selectedReviewResult.value?.suggestions || [] },
])
const v2Findings = computed(() => {
  const evidence = new Map((selectedReviewResult.value?.evidence || []).map(item => [item.evidenceId, item]))
  return (selectedReviewResult.value?.findings || []).map(item => ({
    ...item,
    pages: [...new Set((item.evidenceIds || []).map(id => evidence.get(id)?.physicalPage).filter(Boolean))],
  }))
})
const practiceLabel = computed(() => ({ PREPARING: '组建中', IN_PROGRESS: '练习中', ENDED: '已结束' })[team.value?.practiceStatus] || team.value?.practiceStatus || '未知')

async function loadTeam() {
  loading.value = true
  try {
    team.value = (await getTeamDetail(route.params.id)).data
    problem.value = (await getPublicProblemDetail(team.value.problemId)).data
    const requestedRecruitment = team.value.recruitments?.find(item => String(item.id) === String(route.query.recruitmentId) && item.status === 'OPEN')
    if (route.query.apply === '1' && requestedRecruitment && team.value.canApply) {
      applyForm.recruitmentId = requestedRecruitment.id
      showApplyDialog.value = true
    }
    if (team.value.canManage) await loadApplications()
    if (team.value.members?.some(member => member.userId === currentUserId.value) && team.value.practiceStatus !== 'PREPARING') await Promise.all([loadSubmissions(), loadReviews()])
    return true
  }
  catch (error) {
    ElMessage.error(error.message || '队伍详情加载失败')
    return false
  }
  finally { loading.value = false }
}

async function loadApplications() {
  const params = { page: applicationPage.value, pageSize: applicationPageSize }
  if (applicationStatus.value !== 'all') params.status = applicationStatus.value
  const page = (await getTeamApplications(team.value.id, params)).data
  applications.value = page?.rows || []
  applicationTotal.value = page?.total || 0
  if (applicationPage.value > 1 && applications.value.length === 0) {
    applicationPage.value -= 1
    return loadApplications()
  }
}

async function handleApplicationFilterChange() {
  applicationPage.value = 1
  try { await loadApplications() }
  catch (error) { ElMessage.error(error.message || '入队申请加载失败') }
}

async function handleApplicationPageChange(page) {
  applicationPage.value = page
  try { await loadApplications() }
  catch (error) { ElMessage.error(error.message || '入队申请加载失败') }
}

function formatDate(value) { return value ? value.replace('T', ' ').slice(0, 16) : '未知时间' }
function showUserCard(member) { selectedMember.value = member; showMiniCard.value = true }
function handleBack() {
  if (window.history.state?.back) router.back()
  else router.push(route.name === 'TeamSquareDetail' ? '/team/square' : '/team')
}
function difficultyLabel(value) { return ({ 1: '简单', 2: '中等', 3: '困难' })[value] || '未知' }
function formatDuration(minutes) { return minutes % 60 === 0 ? `${minutes / 60} 小时` : `${minutes} 分钟` }
function formatFileSize(value) { return value == null ? '-' : value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB` }
function reviewStatusLabel(value) { return ({ WAITING: '等待评审', RUNNING: '评审中', COMPLETED: '已完成', FAILED: '评审失败' })[value] || '等待评审' }
function reviewStatusType(value) { return ({ COMPLETED: 'success', FAILED: 'danger', RUNNING: 'warning' })[value] || 'info' }
function coverageLabel(value) { return ({ COMPLETED: '已完成', PARTIAL: '部分完成', MISSING: '缺失', UNVERIFIABLE: '无法判断' })[value] || value }
function coverageType(value) { return ({ COMPLETED: 'success', PARTIAL: 'warning', MISSING: 'danger', UNVERIFIABLE: 'info' })[value] || 'info' }
function memberRoles(member) { return [member.modeler && '建模', member.programmer && '编程', member.writer && '论文'].filter(Boolean) }
const MAX_PDF_SIZE = 20 * 1024 * 1024
function handlePdfChange(file) {
  const rawFile = file.raw
  if (!rawFile) return
  const isPdf = rawFile.name?.toLowerCase().endsWith('.pdf') && (!rawFile.type || rawFile.type === 'application/pdf')
  if (!isPdf) {
    selectedPdf.value = null
    uploadStage.value = ''
    return ElMessage.warning('请选择 PDF 文件')
  }
  if (rawFile.size > MAX_PDF_SIZE) {
    selectedPdf.value = null
    uploadStage.value = ''
    return ElMessage.warning('PDF 文件大小不能超过 20MB')
  }
  selectedPdf.value = rawFile
  uploadProgress.value = 0
  uploadStage.value = ''
}
function handlePdfRemove() { selectedPdf.value = null; uploadProgress.value = 0; uploadStage.value = '' }

async function loadSubmissions() {
  try { submissions.value = (await getTeamSubmissionHistory(team.value.id)).data || [] }
  catch (error) { ElMessage.error(error.message || '提交历史加载失败') }
}
async function loadReviews() {
  try { reviews.value = (await getTeamReviews(team.value.id)).data || [] }
  catch (error) { ElMessage.error(error.message || '评审结果加载失败') }
}
async function refreshSubmissionReviews() { await Promise.all([loadSubmissions(), loadReviews()]) }
function showReviewResult(review) {
  selectedReview.value = review
  showReviewDialog.value = true
}
function showSuggestion(row) {
  suggestionSubmission.value = row
  showSuggestionDialog.value = true
}
async function handleRetryReview(taskId) {
  try { await retryReviewTask(taskId); await refreshSubmissionReviews(); ElMessage.success('评审任务已重新排队') }
  catch (error) { ElMessage.error(error.message || '评审任务重试失败') }
}

async function handleStartPractice() {
  try {
    await ElMessageBox.confirm('开始后将立即按题目时长倒计时，成员和职责不可再修改。确定开始吗？', '开始限时练习', { type: 'warning' })
    startingPractice.value = true
    team.value = (await startTeamPractice(team.value.id)).data
    await refreshSubmissionReviews()
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
  if (selectedPdf.value.size > MAX_PDF_SIZE) return ElMessage.warning('PDF 文件大小不能超过 20MB')
  submitting.value = true
  uploadProgress.value = 0
  try {
    await uploadPdfResumably({
      teamId: team.value.id,
      file: selectedPdf.value,
      onProgress: value => { uploadProgress.value = value },
      onStage: value => { uploadStage.value = value },
    })
    uploadProgress.value = 100
    uploadStage.value = '提交完成'
    selectedPdf.value = null
    await refreshSubmissionReviews()
    ElMessage.success('PDF 提交成功')
    uploadProgress.value = 0
    uploadStage.value = ''
  }
  catch (error) {
    uploadStage.value = '上传已中断，再次提交将从已上传分片继续'
    ElMessage.error(error.message || 'PDF 提交失败')
  }
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
function resetRecruitmentForm() { Object.assign(recruitmentForm, { needModeler: false, needProgrammer: false, needWriter: false, description: '' }) }
function openPublishRecruitmentDialog() { editingRecruitmentId.value = null; resetRecruitmentForm(); showRecruitmentDialog.value = true }
function openEditRecruitmentDialog(item) { editingRecruitmentId.value = item.id; Object.assign(recruitmentForm, { needModeler: item.needModeler, needProgrammer: item.needProgrammer, needWriter: item.needWriter, description: item.description || '' }); showRecruitmentDialog.value = true }
async function handleSaveRecruitment() {
  if (!recruitmentRolesText(recruitmentForm)) return ElMessage.warning('请至少选择一个招募职位')
  try {
    const request = editingRecruitmentId.value
      ? updateTeamRecruitment(team.value.id, editingRecruitmentId.value, recruitmentForm)
      : publishTeamRecruitment(team.value.id, recruitmentForm)
    team.value = (await request).data
    showRecruitmentDialog.value = false
    ElMessage.success(editingRecruitmentId.value ? '招募信息已更新' : '招募已发布')
  } catch (error) { ElMessage.error(error.message || '招募信息保存失败') }
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
  try {
    await cancelTeamApplication(team.value.id)
    refreshAfterCancelError.value = ''
    team.value.currentUserRelation = 'none'
    team.value.canApply = openRecruitments.value.length > 0
    try {
      team.value = (await getTeamDetail(route.params.id)).data
      problem.value = (await getPublicProblemDetail(team.value.problemId)).data
      ElMessage.success('申请已取消')
    }
    catch (error) {
      refreshAfterCancelError.value = error.message || '请重新加载以获取服务端最新状态'
      ElMessage.warning('申请已取消，状态刷新失败')
    }
  } catch (error) {
    if (error.code === 40312) await loadTeam()
    ElMessage.error(error.message || '取消申请失败')
  }
}

async function retryRefreshAfterCancel() {
  if (await loadTeam()) refreshAfterCancelError.value = ''
}

async function handleReview(applicationId, decision) {
  try { await reviewTeamApplication(team.value.id, applicationId, decision); await loadTeam(); ElMessage.success(decision === 'approved' ? '申请已通过' : '申请已拒绝') }
  catch (error) { ElMessage.error(error.message || '申请审核失败') }
}

onMounted(() => {
  loadTeam()
  clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
  reviewTimer = window.setInterval(() => {
    if (reviews.value.some(item => ['WAITING', 'RUNNING'].includes(item.status))) loadReviews()
  }, 5000)
})
onBeforeUnmount(() => { window.clearInterval(clockTimer); window.clearInterval(reviewTimer) })
watch(remainingSeconds, (value, previous) => {
  if (value === 0 && previous > 0 && team.value?.practiceStatus === 'IN_PROGRESS') loadTeam()
})
</script>

<style scoped>
@import '../style.css';
.practice-meta { margin: 6px 0; color: var(--lm-text-secondary); font-size: 13px; }
.submission-table { margin-top: 18px; }
.score-summary { display: flex; align-items: center; gap: 18px; margin-top: 18px; padding: 16px 18px; border: 1px solid #dbeafe; border-radius: 14px; background: linear-gradient(135deg, #eff6ff, #f8fafc); }
.score-summary.final { border-color: #bbf7d0; background: linear-gradient(135deg, #f0fdf4, #f8fafc); }
.score-summary > div:first-child { display: flex; flex: 1; flex-direction: column; gap: 4px; color: var(--lm-text-muted); font-size: 12px; }
.score-summary > div:first-child strong { color: var(--lm-text-primary); font-size: 16px; }
.score-value { display: flex; align-items: baseline; gap: 4px; }
.score-value strong { color: #2563eb; font-size: 30px; line-height: 1; }
.score-summary.final .score-value strong { color: #16a34a; }
.score-value span { color: var(--lm-text-muted); font-size: 12px; }
.version-cell { display: flex; align-items: center; gap: 7px; }
.table-score { color: #2563eb; font-size: 16px; }
.upload-panel { margin-top: 18px; padding: 16px; border: 1px solid var(--lm-border-light); border-radius: 14px; background: #f8fafc; }
.partial-success-alert { margin-bottom: 18px; }
.upload-toolbar { display: flex; min-width: 0; align-items: center; gap: 14px; }
.selected-file { display: flex; min-width: 0; flex: 1; align-items: center; gap: 10px; }
.file-icon { display: flex; width: 38px; height: 38px; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 9px; background: #fee2e2; color: #dc2626; font-size: 10px; font-weight: 800; }
.file-meta { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }
.file-meta strong { overflow: hidden; color: var(--lm-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.file-meta span, .upload-tip { color: var(--lm-text-muted); font-size: 11px; }
.file-placeholder { flex: 1; color: var(--lm-text-muted); font-size: 13px; }
.remove-file { flex-shrink: 0; color: var(--lm-text-secondary); }
.upload-progress { flex-shrink: 0; }
.submit-button { flex-shrink: 0; margin-left: auto; }
.upload-tip { margin: 11px 0 0; }
.edit-team-form :deep(.el-form-item__label) { white-space: nowrap; }
.application-tools { display: flex; min-width: 180px; align-items: center; justify-content: flex-end; gap: 14px; }
.application-status-select { width: 120px; }
.application-pagination { justify-content: center; margin-top: 18px; }
.member-avatar-button { padding: 0; border: 2px solid transparent; cursor: pointer; transition: border-color .2s ease,box-shadow .2s ease,transform .2s ease; }
.member-avatar-button:hover,.member-avatar-button:focus-visible { outline: none; border-color: #60a5fa; box-shadow: 0 0 0 4px rgba(37,99,235,.16); transform: translateY(-2px); }
.final-version-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 18px; padding: 14px 16px; border-radius: 12px; background: #f8fafc; color: var(--lm-text-secondary); font-size: 13px; }
.submission-permission { min-width: 128px; }
.slot-hint { margin-left: 12px; color: var(--lm-text-muted); font-size: 13px; }
.review-detail { display: grid; gap: 18px; }
.review-version { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.review-version p, .review-process, .review-summary, .dimension-card p { margin: 6px 0 0; color: var(--lm-text-secondary); line-height: 1.7; }
.review-process { padding: 12px 14px; border-radius: 10px; background: var(--lm-bg-secondary); }
.review-score { display: flex; align-items: baseline; gap: 8px; padding: 18px; border-radius: 12px; background: #eef7ff; }
.review-score strong { font-size: 42px; color: var(--el-color-primary); }
.dimension-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.dimension-card { padding: 14px; border: 1px solid var(--lm-border); border-radius: 10px; }
.dimension-card > div { display: flex; justify-content: space-between; gap: 12px; }
.dimension-card span { color: var(--el-color-primary); font-weight: 600; }
.review-list h4 { margin: 0 0 8px; }
.review-list ul { margin: 0; padding-left: 22px; color: var(--lm-text-secondary); line-height: 1.8; }
.coverage-item { display: flex; align-items: flex-start; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--lm-border-light); }
.coverage-item p, .finding-item p { margin: 5px 0 0; color: var(--lm-text-secondary); line-height: 1.6; }
.finding-item { padding: 12px 0; border-bottom: 1px solid var(--lm-border-light); }
.finding-item > div { display: flex; align-items: center; gap: 8px; }
.finding-item > span, .review-limitations { color: var(--lm-text-muted); font-size: 12px; }
@media (max-width: 720px) { .dimension-grid { grid-template-columns: 1fr; } }
@media (max-width: 700px) {
  .upload-toolbar { align-items: stretch; flex-direction: column; }
  .submit-button { width: 100%; margin-left: 0; }
  .final-version-bar { align-items: flex-start; flex-direction: column; }
}
</style>
