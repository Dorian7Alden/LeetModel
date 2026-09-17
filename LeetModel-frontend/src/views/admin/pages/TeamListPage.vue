<template>
  <div class="operation-page">
    <!-- 1. 顶部统计大盘指标条 -->
    <div v-if="stats" class="team-metric-strip">
      <div class="metric-item">
        <span class="metric-label">队伍总数</span>
        <strong class="metric-val">{{ stats.totalTeams ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">活跃队伍</span>
        <strong class="metric-val text-success">{{ stats.activeTeams ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">组建中</span>
        <strong class="metric-val text-warning">{{ stats.preparingTeams ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">练习中</span>
        <strong class="metric-val text-primary">{{ stats.inProgressTeams ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">已结束</span>
        <strong class="metric-val">{{ stats.endedTeams ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-item">
        <span class="metric-label">已解散</span>
        <strong class="metric-val text-muted">{{ stats.disbandedTeams ?? 0 }}</strong>
      </div>
      <div class="metric-divider" />
      <div class="metric-action-right">
        <el-button link type="primary" size="small" @click="showCharts = !showCharts">
          <el-icon><DataAnalysis /></el-icon>
          {{ showCharts ? "收起图表分析" : "展开图表分析" }}
        </el-button>
      </div>
    </div>

    <!-- 2. 可视化图表分析区（折叠/展开） -->
    <el-collapse-transition>
      <div v-show="showCharts && stats" class="charts-grid">
        <!-- 图表 1: 实训阶段与状态构成 -->
        <div class="chart-card">
          <div class="chart-header">
            <strong>实训阶段与状态构成</strong>
            <span class="chart-subtitle">全量队伍生命周期</span>
          </div>
          <div ref="statusPieRef" class="chart-box" />
        </div>

        <!-- 图表 2: 队伍人数规模分布 -->
        <div class="chart-card">
          <div class="chart-header">
            <strong>队伍人数规模构成</strong>
            <span class="chart-subtitle">1人 / 2人 / 3人满编</span>
          </div>
          <div ref="sizeBarRef" class="chart-box" />
        </div>

        <!-- 图表 3: 热门赛题参赛热度 -->
        <div class="chart-card">
          <div class="chart-header">
            <strong>热门赛题参赛热度 Top 5</strong>
            <span class="chart-subtitle">有效队伍选题分布</span>
          </div>
          <div ref="problemBarRef" class="chart-box" />
        </div>
      </div>
    </el-collapse-transition>

    <!-- 3. 操作与高级筛选工具栏 -->
    <div class="operation-toolbar">
      <div class="result-scope">
        <strong>{{ total }}</strong>
        <span>支队伍 · 服务端分页检索</span>
      </div>
      <div class="filter-actions">
        <el-input
          v-model="keyword"
          clearable
          prefix-icon="Search"
          placeholder="搜索队伍名称 / 队长ID / 队伍ID"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="selectedProblemId"
          clearable
          filterable
          placeholder="全部赛题"
          style="width: 180px;"
          @change="handleSearch"
        >
          <el-option label="全部赛题" :value="null" />
          <el-option
            v-for="p in problemOptions"
            :key="p.id"
            :label="`题号 ${p.code ?? p.problemNumber ?? '—'} · ${p.title}`"
            :value="p.id"
          />
        </el-select>
        <el-select v-model="practiceStatus" clearable placeholder="全部阶段" @change="handleSearch">
          <el-option label="全部阶段" :value="null" />
          <el-option label="组建中" value="PREPARING" />
          <el-option label="练习中" value="IN_PROGRESS" />
          <el-option label="已结束" value="ENDED" />
          <el-option label="已解散" value="DISBANDED" />
        </el-select>
        <el-select v-model="teamStatus" clearable placeholder="队伍状态" @change="handleSearch">
          <el-option label="全部状态" :value="null" />
          <el-option label="正常" :value="1" />
          <el-option label="已解散" :value="0" />
        </el-select>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>代建队伍
        </el-button>
      </div>
    </div>

    <AdminStatePanel v-if="loadError && !rows.length" type="error" title="队伍数据加载失败" action-label="重新加载" @action="loadData" />
    <div v-else>
      <div v-if="loadError" class="stale-warning">刷新失败，当前保留上次取得的数据</div>
      <el-table :data="rows" stripe v-loading="loading" class="operation-table">
        <!-- 队伍列：队长头像作为队伍标识 + 队伍名称 + 描述 -->
        <el-table-column label="队伍" min-width="240">
          <template #default="{ row }">
            <div class="team-cell">
              <el-avatar
                :size="34"
                :src="row.leaderAvatarUrl"
                class="team-avatar"
              >
                {{ (row.name || '队').slice(0, 1) }}
              </el-avatar>
              <div class="primary-cell min-w0">
                <strong :title="row.name" class="team-title">{{ row.name || "未命名队伍" }}</strong>
                <span v-if="row.description" :title="row.description" class="team-desc">{{ row.description }}</span>
                <span v-else class="team-id-sub">队伍 ID: {{ row.id }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="赛题" min-width="230">
          <template #default="{ row }">
            <div class="primary-cell">
              <strong>题号 {{ row.problemCode ?? problemLabel(problemMap, row.problemId).code }}</strong>
              <span>{{ row.problemTitle || problemLabel(problemMap, row.problemId).title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="队长" min-width="150">
          <template #default="{ row }">
            <div class="leader-cell">
              <el-avatar
                :size="24"
                :src="row.leaderAvatarUrl"
                class="mini-avatar"
              >
                {{ (row.leaderNickname || '长').slice(0, 1) }}
              </el-avatar>
              <div class="primary-cell min-w0">
                <strong>{{ row.leaderNickname || "未命名用户" }}</strong>
                <span class="code-id">ID: {{ row.leaderId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="成员" width="85" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.memberCount >= 3 ? 'success' : 'info'" effect="plain">
              {{ row.memberCount ?? "—" }} / {{ row.maxMembers ?? 3 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="练习阶段" width="105">
          <template #default="{ row }">
            <AdminStatusBadge :status="statusForBadge(row.practiceStatus)" :label="statusLabel(row.practiceStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 1 ? "正常" : "已解散" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="145">
          <template #default="{ row }">{{ formatAdminTime(row.deadlineAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetails(row)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="warning" @click="openStatusDialog(row)">阶段</el-button>
            <el-button
              v-if="row.status === 1"
              link
              type="danger"
              @click="confirmDissolve(row)"
            >
              解散
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AdminStatePanel :type="rows.length ? 'filtered' : 'empty'" :title="rows.length ? '没有符合条件的队伍' : '暂无队伍数据'" />
        </template>
      </el-table>

      <!-- 服务端分页器 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 4. 队伍档案详情抽屉 -->
    <el-drawer v-model="detailsVisible" title="队伍完整档案" size="min(600px, 90vw)">
      <div v-if="selectedRow" class="drawer-content">
        <div class="drawer-header-card">
          <el-avatar :size="52" :src="selectedRow.leaderAvatarUrl" class="drawer-big-avatar">
            {{ (selectedRow.name || '队').slice(0, 1) }}
          </el-avatar>
          <div class="drawer-team-title">
            <h3>{{ selectedRow.name || "未命名队伍" }}</h3>
            <div class="drawer-team-meta">
              <span class="code-id">队伍 ID: {{ selectedRow.id }}</span>
              <el-tag size="small" :type="selectedRow.status === 1 ? 'success' : 'danger'" effect="plain">
                {{ selectedRow.status === 1 ? "正常" : "已解散" }}
              </el-tag>
              <AdminStatusBadge :status="statusForBadge(selectedRow.practiceStatus)" :label="statusLabel(selectedRow.practiceStatus)" />
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <h4 class="section-title">基本信息</h4>
          <dl class="details-list">
            <div><dt>队伍描述</dt><dd>{{ selectedRow.description || "—" }}</dd></div>
            <div><dt>所属题目</dt><dd>题号 {{ selectedRow.problemCode ?? problemLabel(problemMap, selectedRow.problemId).code }} · {{ selectedRow.problemTitle || problemLabel(problemMap, selectedRow.problemId).title }}</dd></div>
            <div><dt>队长信息</dt><dd class="identifier-value"><span>{{ selectedRow.leaderNickname || "—" }} (ID: {{ selectedRow.leaderId }})</span><el-button v-if="selectedRow.leaderId != null" link type="primary" @click="copyIdentifier(selectedRow.leaderId)">复制ID</el-button></dd></div>
            <div><dt>开始时间</dt><dd>{{ formatAdminTime(selectedRow.startedAt) }}</dd></div>
            <div><dt>截止时间</dt><dd>{{ formatAdminTime(selectedRow.deadlineAt) }}</dd></div>
            <div><dt>结束时间</dt><dd>{{ formatAdminTime(selectedRow.endedAt) }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatAdminTime(selectedRow.createTime) }}</dd></div>
            <div><dt>队伍 ID</dt><dd class="identifier-value"><span>{{ selectedRow.id }}</span><el-button link type="primary" @click="copyIdentifier(selectedRow.id)">复制</el-button></dd></div>
          </dl>
        </div>

        <!-- 队员明细列表 -->
        <div class="drawer-section">
          <h4 class="section-title">队员明细 ({{ detailMembers.length }} 人)</h4>
          <el-table :data="detailMembers" size="small" stripe border class="member-table">
            <el-table-column label="成员" min-width="140">
              <template #default="{ row }">
                <div class="member-avatar-cell">
                  <el-avatar :size="24" :src="row.avatarUrl">{{ (row.nickname || '用').slice(0, 1) }}</el-avatar>
                  <div class="min-w0">
                    <strong>{{ row.nickname || "用户" }}</strong>
                    <span class="code-id block-text">ID: {{ row.userId }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="身份" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.role === 'leader' ? 'warning' : 'info'" effect="plain">
                  {{ row.role === 'leader' ? "队长" : "队员" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="分工职责" min-width="130">
              <template #default="{ row }">
                <div class="role-tags">
                  <el-tag v-if="row.modeler" size="small" type="primary" effect="light">建模</el-tag>
                  <el-tag v-if="row.programmer" size="small" type="success" effect="light">编程</el-tag>
                  <el-tag v-if="row.writer" size="small" type="warning" effect="light">论文</el-tag>
                  <span v-if="!row.modeler && !row.programmer && !row.writer" class="text-muted">—</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="提交资格" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.canSubmit ? 'success' : 'info'" effect="plain">
                  {{ row.canSubmit ? "允许" : "只读" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="加入时间" width="130">
              <template #default="{ row }">{{ formatAdminTime(row.joinedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>

    <!-- 5. 代建队伍对话框 -->
    <el-dialog v-model="createDialogVisible" title="管理端代建队伍" width="500px">
      <el-form :model="createForm" label-width="90px" class="dialog-form">
        <el-form-item label="所属赛题" required>
          <el-select v-model="createForm.problemId" filterable placeholder="请选择赛题" style="width: 100%;">
            <el-option
              v-for="p in problemOptions"
              :key="p.id"
              :label="`题号 ${p.code ?? p.problemNumber ?? '—'} · ${p.title}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="队伍名称" required>
          <el-input v-model="createForm.name" placeholder="输入队伍名称（最多64字符）" maxlength="64" />
        </el-form-item>
        <el-form-item label="队长用户ID" required>
          <el-input v-model="createForm.leaderId" placeholder="输入队长用户数字 ID" />
        </el-form-item>
        <el-form-item label="队伍描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选队伍描述" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">确认代建</el-button>
      </template>
    </el-dialog>

    <!-- 6. 编辑队伍基础信息对话框 -->
    <el-dialog v-model="editDialogVisible" title="修改队伍基础信息" width="500px">
      <el-form :model="editForm" label-width="90px" class="dialog-form">
        <el-form-item label="队伍名称">
          <el-input v-model="editForm.name" placeholder="修改队伍名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="转让队长ID">
          <el-input v-model="editForm.leaderId" placeholder="输入新队长用户 ID（不填则保持不变）" />
        </el-form-item>
        <el-form-item label="队伍描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="修改队伍描述" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- 7. 调整实训阶段与截止时间对话框 -->
    <el-dialog v-model="statusDialogVisible" title="调控实训阶段与时间" width="480px">
      <el-form :model="statusForm" label-width="90px" class="dialog-form">
        <el-form-item label="练习阶段" required>
          <el-select v-model="statusForm.practiceStatus" style="width: 100%;">
            <el-option label="组建中 (PREPARING)" value="PREPARING" />
            <el-option label="练习中 (IN_PROGRESS)" value="IN_PROGRESS" />
            <el-option label="已结束 (ENDED)" value="ENDED" />
            <el-option label="已解散 (DISBANDED)" value="DISBANDED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="statusForm.practiceStatus === 'IN_PROGRESS'" label="截止时间">
          <el-date-picker
            v-model="statusForm.deadlineAt"
            type="datetime"
            placeholder="选择截止时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="statusSubmitting" @click="submitStatus">应用调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as echarts from "echarts";
import {
  getAdminTeamPage,
  getAdminTeamStats,
  getAdminTeamDetail,
  createAdminTeam,
  updateAdminTeam,
  updateAdminTeamPracticeStatus,
  dissolveAdminTeam,
} from "@/api/admin-ops";
import { getPublicProblemList } from "@/api/problem";
import AdminStatePanel from "../components/AdminStatePanel.vue";
import AdminStatusBadge from "../components/AdminStatusBadge.vue";
import { copyAdminText, formatAdminTime, problemLabel, useAdminReferences } from "../operation-utils";

const props = defineProps({ problemMap: { type: Object, default: () => ({}) } });
const { problemMap: refProblemMap, loadProblems } = useAdminReferences();
const problemMap = computed(() => (Object.keys(props.problemMap || {}).length ? props.problemMap : refProblemMap.value));

// 分页与筛选
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const loading = ref(false);
const loadError = ref(false);
const keyword = ref("");
const selectedProblemId = ref(null);
const practiceStatus = ref("");
const teamStatus = ref(null);
const problemOptions = ref([]);

// 统计大盘与图表
const stats = ref(null);
const showCharts = ref(true);
const statusPieRef = ref(null);
const sizeBarRef = ref(null);
const problemBarRef = ref(null);
let statusPieChart;
let sizeBarChart;
let problemBarChart;

// 详情
const selectedRow = ref(null);
const detailsVisible = ref(false);
const detailMembers = ref([]);

// 代建队伍
const createDialogVisible = ref(false);
const createSubmitting = ref(false);
const createForm = reactive({ name: "", problemId: null, leaderId: "", description: "" });

// 编辑队伍
const editDialogVisible = ref(false);
const editSubmitting = ref(false);
const editTargetId = ref(null);
const editForm = reactive({ name: "", leaderId: "", description: "" });

// 调整阶段
const statusDialogVisible = ref(false);
const statusSubmitting = ref(false);
const statusTargetId = ref(null);
const statusForm = reactive({ practiceStatus: "IN_PROGRESS", deadlineAt: "" });

function statusLabel(status) {
  return ({ PREPARING: "组建中", IN_PROGRESS: "练习中", ENDED: "已结束", DISBANDED: "已解散" })[status] || status || "未知";
}
function statusForBadge(status) {
  return ({ PREPARING: "WAITING", IN_PROGRESS: "RUNNING", ENDED: "COMPLETED", DISBANDED: "TERMINATED" })[status] || "UNKNOWN";
}

async function loadStats() {
  try {
    const res = await getAdminTeamStats();
    stats.value = res.data;
    renderCharts();
  } catch {
    // ignore
  }
}

function renderCharts() {
  if (!stats.value) return;
  nextTick(() => {
    // 图表 1: 状态饼图
    if (statusPieRef.value) {
      statusPieChart = statusPieChart || echarts.init(statusPieRef.value);
      const pieData = [
        { name: "组建中", value: stats.value.preparingTeams ?? 0, itemStyle: { color: "#d97706" } },
        { name: "练习中", value: stats.value.inProgressTeams ?? 0, itemStyle: { color: "#2563eb" } },
        { name: "已结束", value: stats.value.endedTeams ?? 0, itemStyle: { color: "#16a34a" } },
        { name: "已解散", value: stats.value.disbandedTeams ?? 0, itemStyle: { color: "#9ca3af" } },
      ].filter((item) => item.value > 0);

      statusPieChart.setOption({
        tooltip: { trigger: "item", formatter: "{b}: {c} 队 ({d}%)" },
        legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11 } },
        series: [
          {
            type: "pie",
            radius: ["42%", "68%"],
            center: ["50%", "44%"],
            data: pieData,
          },
        ],
      }, true);
    }

    // 图表 2: 人数规模构成
    if (sizeBarRef.value) {
      sizeBarChart = sizeBarChart || echarts.init(sizeBarRef.value);
      const dist = stats.value.memberSizeDistribution || {};
      const barData = [
        { name: "1 人", count: dist[1] || dist["1"] || 0 },
        { name: "2 人", count: dist[2] || dist["2"] || 0 },
        { name: "3 人满员", count: dist[3] || dist["3"] || 0 },
      ];

      sizeBarChart.setOption({
        tooltip: { trigger: "axis", formatter: "{b}: {c} 支队伍" },
        grid: { left: 40, right: 15, top: 20, bottom: 26 },
        xAxis: {
          type: "category",
          data: barData.map((d) => d.name),
          axisLabel: { color: "#6b7280", fontSize: 11 },
        },
        yAxis: { type: "value", minInterval: 1 },
        series: [
          {
            type: "bar",
            data: barData.map((d) => d.count),
            itemStyle: { color: "#10b981", borderRadius: [4, 4, 0, 0] },
          },
        ],
      }, true);
    }

    // 图表 3: 热门赛题排行
    if (problemBarRef.value) {
      problemBarChart = problemBarChart || echarts.init(problemBarRef.value);
      const topList = stats.value.topProblems || [];
      const reversed = [...topList].reverse();

      problemBarChart.setOption({
        tooltip: { trigger: "axis", formatter: (items) => `${items[0].name}: ${items[0].value} 队参赛` },
        grid: { left: 80, right: 25, top: 15, bottom: 20 },
        xAxis: { type: "value", minInterval: 1 },
        yAxis: {
          type: "category",
          data: reversed.map((d) => `题号 ${d.problemCode ?? "—"}`),
          axisLabel: { color: "#475569", fontSize: 11 },
        },
        series: [
          {
            type: "bar",
            data: reversed.map((d) => d.teamCount),
            itemStyle: { color: "#6366f1", borderRadius: [0, 4, 4, 0] },
          },
        ],
      }, true);
    }
  });
}

function disposeCharts() {
  statusPieChart?.dispose();
  sizeBarChart?.dispose();
  problemBarChart?.dispose();
  statusPieChart = undefined;
  sizeBarChart = undefined;
  problemBarChart = undefined;
}

function resizeCharts() {
  statusPieChart?.resize();
  sizeBarChart?.resize();
  problemBarChart?.resize();
}

async function loadProblemOptions() {
  try {
    const res = await getPublicProblemList({ page: 1, pageSize: 100 });
    problemOptions.value = res.data?.rows || [];
  } catch {
    // ignore
  }
}

async function loadData() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  try {
    const res = await getAdminTeamPage({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      problemId: selectedProblemId.value || undefined,
      practiceStatus: practiceStatus.value || undefined,
      status: teamStatus.value != null ? teamStatus.value : undefined,
    });
    rows.value = res.data?.rows || [];
    total.value = res.data?.total || 0;
  } catch (error) {
    loadError.value = true;
    if (rows.value.length) ElMessage.error(error.message || "队伍数据加载失败");
  } finally {
    loading.value = false;
  }
  loadProblems();
}

function handleSearch() {
  page.value = 1;
  loadData();
}

function handlePageChange(val) {
  page.value = val;
  loadData();
}

function handleSizeChange(val) {
  pageSize.value = val;
  page.value = 1;
  loadData();
}

async function openDetails(row) {
  selectedRow.value = row;
  detailMembers.value = row.members || [];
  detailsVisible.value = true;
  try {
    const res = await getAdminTeamDetail(row.id);
    if (res.data) {
      selectedRow.value = res.data;
      detailMembers.value = res.data.members || [];
    }
  } catch {
    // fallback
  }
}

function openCreateDialog() {
  createForm.name = "";
  createForm.problemId = null;
  createForm.leaderId = "";
  createForm.description = "";
  createDialogVisible.value = true;
}

async function submitCreate() {
  if (!createForm.name.trim()) return ElMessage.warning("请输入队伍名称");
  if (!createForm.problemId) return ElMessage.warning("请选择所属赛题");
  if (!createForm.leaderId) return ElMessage.warning("请输入队长用户 ID");
  createSubmitting.value = true;
  try {
    await createAdminTeam({
      name: createForm.name.trim(),
      problemId: createForm.problemId,
      leaderId: Number(createForm.leaderId),
      description: createForm.description.trim() || undefined,
    });
    ElMessage.success("队伍代建成功");
    createDialogVisible.value = false;
    loadData();
    loadStats();
  } catch (error) {
    ElMessage.error(error.message || "队伍代建失败");
  } finally {
    createSubmitting.value = false;
  }
}

function openEditDialog(row) {
  editTargetId.value = row.id;
  editForm.name = row.name || "";
  editForm.leaderId = "";
  editForm.description = row.description || "";
  editDialogVisible.value = true;
}

async function submitEdit() {
  editSubmitting.value = true;
  try {
    await updateAdminTeam(editTargetId.value, {
      name: editForm.name.trim() || undefined,
      leaderId: editForm.leaderId ? Number(editForm.leaderId) : undefined,
      description: editForm.description.trim() || undefined,
    });
    ElMessage.success("队伍信息修改成功");
    editDialogVisible.value = false;
    loadData();
  } catch (error) {
    ElMessage.error(error.message || "队伍信息修改失败");
  } finally {
    editSubmitting.value = false;
  }
}

function openStatusDialog(row) {
  statusTargetId.value = row.id;
  statusForm.practiceStatus = row.practiceStatus || "IN_PROGRESS";
  statusForm.deadlineAt = row.deadlineAt ? String(row.deadlineAt).replace("T", " ") : "";
  statusDialogVisible.value = true;
}

async function submitStatus() {
  statusSubmitting.value = true;
  try {
    await updateAdminTeamPracticeStatus(statusTargetId.value, {
      practiceStatus: statusForm.practiceStatus,
      deadlineAt: statusForm.deadlineAt ? statusForm.deadlineAt.replace(" ", "T") : undefined,
    });
    ElMessage.success("实训阶段调控成功");
    statusDialogVisible.value = false;
    loadData();
    loadStats();
  } catch (error) {
    ElMessage.error(error.message || "调控失败");
  } finally {
    statusSubmitting.value = false;
  }
}

async function confirmDissolve(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `确定强制解散队伍「${row.name}」(ID: ${row.id}) 吗？此操作不可逆，队伍将置为已解散状态。`,
      "强制解散队伍",
      {
        confirmButtonText: "确认解散",
        cancelButtonText: "取消",
        inputPlaceholder: "可选：填写解散原因",
        type: "warning",
      }
    );
    await dissolveAdminTeam(row.id, reason);
    ElMessage.success("队伍已强制解散");
    loadData();
    loadStats();
  } catch {
    // cancelled
  }
}

async function copyIdentifier(value) {
  await copyAdminText(value);
  ElMessage.success("标识已复制");
}

onMounted(() => {
  window.addEventListener("resize", resizeCharts);
  loadStats();
  loadProblemOptions();
  loadData();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  disposeCharts();
});
</script>

<style scoped>
@import './operations-workspace.css';

.team-metric-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 18px;
  padding: 10px 14px;
  margin-bottom: var(--lm-admin-space-3);
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
  font-size: 12px;
}
.metric-item { display: flex; align-items: baseline; gap: 6px; }
.metric-label { color: var(--lm-admin-text-muted); font-size: 11px; }
.metric-val { color: var(--lm-admin-text-strong); font-size: 15px; font-variant-numeric: tabular-nums; }
.metric-divider { width: 1px; height: 14px; background: var(--lm-admin-border); }
.metric-action-right { margin-left: auto; }
.text-success { color: #16a34a; }
.text-warning { color: #d97706; }
.text-primary { color: #2563eb; }
.text-muted { color: #6b7280; }

/* 可视化分析卡片网格 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  margin-bottom: var(--lm-admin-space-3);
}
.chart-card {
  padding: 12px;
  background: var(--lm-admin-surface);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.chart-header strong { font-size: 12px; color: var(--lm-admin-text-strong); }
.chart-subtitle { font-size: 11px; color: var(--lm-admin-text-muted); }
.chart-box { width: 100%; height: 180px; }

/* 队伍表格行与头像 */
.team-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.team-avatar {
  flex-shrink: 0;
  background: #3b82f6;
  color: #ffffff;
  font-weight: bold;
  font-size: 14px;
}
.team-title {
  font-size: 13px;
  color: var(--lm-admin-text-strong);
}
.team-desc {
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}
.team-id-sub {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}

.leader-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mini-avatar {
  flex-shrink: 0;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
}
.min-w0 { min-width: 0; }

.code-id {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  color: var(--lm-admin-text-muted);
}
.block-text { display: block; }
.pagination-container { display: flex; justify-content: flex-end; padding: 14px 0 6px; }

/* 抽屉内样式 */
.drawer-content { display: flex; flex-direction: column; gap: 20px; }
.drawer-header-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px;
  background: var(--lm-admin-surface-subtle, #f8fafc);
  border: 1px solid var(--lm-admin-border);
  border-radius: var(--lm-admin-radius-control);
}
.drawer-big-avatar {
  background: #2563eb;
  color: #ffffff;
  font-size: 20px;
  font-weight: bold;
}
.drawer-team-title h3 { margin: 0 0 6px; font-size: 16px; color: var(--lm-admin-text-strong); }
.drawer-team-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.section-title { margin: 0 0 10px; font-size: 13px; font-weight: 600; color: var(--lm-admin-text-strong); }
.member-table { width: 100%; }
.member-avatar-cell { display: flex; align-items: center; gap: 8px; }
.role-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.dialog-form { padding: 8px 12px 0 0; }
</style>
