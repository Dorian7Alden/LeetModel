<template>
  <div class="ranking-page">
    <!-- 顶部工作台导航条 (极简、无冗余宣传文字) -->
    <nav class="ranking-nav-bar" aria-label="排行榜模式切换">
      <div class="nav-left">
        <div class="view-mode-tabs">
          <button
            type="button"
            class="tab-btn"
            :class="{ active: viewMode === 'overview' }"
            @click="switchViewMode('overview')"
          >
            <el-icon><DataAnalysis /></el-icon>
            <span>赛题总览天梯</span>
          </button>
          <button
            type="button"
            class="tab-btn"
            :class="{ active: viewMode === 'detail' }"
            @click="switchViewMode('detail')"
          >
            <el-icon><Trophy /></el-icon>
            <span>赛题成果细览</span>
          </button>
        </div>

        <!-- 细览模式下：紧凑下拉切换题目按钮 (替代旧版笨重筛选面板) -->
        <div v-if="viewMode === 'detail'" class="compact-problem-selector">
          <el-dropdown trigger="click" max-height="360" @command="handleSelectProblemCommand">
            <button type="button" class="btn-select-problem" title="切换赛题">
              <span class="problem-num-tag">#{{ currentProblem?.code || currentProblem?.id || '?' }}</span>
              <span class="problem-name-text">{{ currentProblem?.title || '选择赛题' }}</span>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu class="problem-dropdown-menu">
                <el-dropdown-item
                  v-for="prob in problems"
                  :key="prob.id"
                  :command="prob.id"
                  :class="{ 'is-selected': String(prob.id) === String(selectedProblemId) }"
                >
                  <div class="dropdown-problem-item">
                    <span class="dp-code">#{{ prob.code || prob.id }}</span>
                    <span class="dp-title">{{ prob.title }}</span>
                    <span v-if="prob.year" class="dp-year">{{ prob.year }}</span>
                    <span class="dp-tag" :class="`diff-${prob.difficulty}`">{{ difficultyLabel(prob.difficulty) }}</span>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 临时看一眼题目详情 (轻量抽屉，绝不跳出页面) -->
          <button
            v-if="currentProblem"
            type="button"
            class="btn-quick-preview"
            title="查看本题题面与建模要求 (侧边抽屉速览)"
            @click="openProblemDrawer(currentProblem.id)"
          >
            <el-icon><View /></el-icon>
            <span>赛题速览</span>
          </button>
        </div>
      </div>

      <!-- 右侧辅助操作 -->
      <div class="nav-right">
        <!-- 细览模式下的队伍搜索 -->
        <div v-if="viewMode === 'detail'" class="team-search-wrap">
          <el-input
            v-model="keyword"
            placeholder="搜索上榜队伍..."
            clearable
            size="small"
            class="compact-search-input"
            @keyup.enter="handleSearch"
            @clear="handleClearSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <!-- 细览模式下：我的队伍快捷定位 -->
        <button
          v-if="viewMode === 'detail' && myTeamInCurrentProblem"
          type="button"
          class="btn-my-team-pill"
          :class="{ 'is-ranked': myTeamRankingItem }"
          @click="locateMyTeamAction"
        >
          <el-icon><UserFilled /></el-icon>
          <span>我的队伍</span>
          <strong v-if="myTeamRankingItem">#{{ myTeamRankingItem.rank }} ({{ formatScore(myTeamRankingItem.score) }}分)</strong>
          <small v-else>实训中</small>
        </button>

        <!-- 刷新数据 -->
        <button
          type="button"
          class="btn-icon-refresh"
          :disabled="loading"
          title="刷新数据"
          @click="handleManualRefresh"
        >
          <el-icon :class="{ 'is-spinning': loading }"><Refresh /></el-icon>
        </button>
      </div>
    </nav>

    <!-- 加载骨架屏 -->
    <div v-if="loading && !overview && !globalStats" class="skeleton-layout">
      <div class="skeleton-strip skeleton-pulse"></div>
      <div class="skeleton-grid-boxes">
        <div v-for="i in 4" :key="`sk-b-${i}`" class="sk-box skeleton-pulse"></div>
      </div>
      <div class="skeleton-main-card skeleton-pulse"></div>
    </div>

    <!-- 错误异常提示 -->
    <div v-else-if="rankingError" class="error-banner">
      <el-icon><Warning /></el-icon>
      <span class="error-msg">{{ rankingError }}</span>
      <button type="button" class="btn-error-retry" @click="handleManualRefresh">重新加载</button>
    </div>

    <!-- 模式一：全平台赛题总览天梯 (Global Overview) -->
    <main v-else-if="viewMode === 'overview'" class="overview-container">
      <!-- 全局指标统计卡片 -->
      <section class="global-metric-strip">
        <div class="g-metric-card">
          <span class="gm-label">已纳入赛题</span>
          <div class="gm-val-row">
            <strong>{{ globalStats?.problemCount || problems.length }}</strong>
            <small>道</small>
          </div>
        </div>
        <div class="g-metric-card">
          <span class="gm-label">上榜建模队伍</span>
          <div class="gm-val-row">
            <strong>{{ globalStats?.rankedTeams || 0 }}</strong>
            <small>支</small>
          </div>
        </div>
        <div class="g-metric-card">
          <span class="gm-label">完成最终评审</span>
          <div class="gm-val-row">
            <strong>{{ globalStats?.reviewedSubmissions || 0 }}</strong>
            <small>份</small>
          </div>
        </div>
        <div class="g-metric-card">
          <span class="gm-label">全平台平均得分</span>
          <div class="gm-val-row">
            <strong class="highlight-score">{{ formatScore(globalStats?.overallAverageScore) }}</strong>
            <small>分</small>
          </div>
        </div>
      </section>

      <!-- 全平台赛题多维分析可视化 (Diversity Visuals) -->
      <section v-if="globalStats?.items?.length" class="overview-visuals-grid">
        <!-- 可视化 1: 各赛题参赛热度与上榜队伍横向对比柱状图 -->
        <div class="chart-card">
          <div class="chart-head">
            <el-icon><DataAnalysis /></el-icon>
            <h4>各赛题上榜队伍分布对比</h4>
          </div>
          <div class="horizontal-bar-chart">
            <div
              v-for="item in sortedGlobalProblemsByTeams"
              :key="`hbar-${item.problemId}`"
              class="hbar-row"
              @click="enterDetail(item.problemId)"
            >
              <div class="hbar-label" :title="item.problemTitle">
                <span class="hbar-code">#{{ item.problemCode || item.problemId }}</span>
                <span class="hbar-title">{{ item.problemTitle }}</span>
              </div>
              <div class="hbar-track">
                <div
                  class="hbar-fill"
                  :style="{ width: `${getBarPercent(item.rankedTeamCount, maxGlobalRankedTeams)}%` }"
                ></div>
                <span class="hbar-val">{{ item.rankedTeamCount || 0 }} 支</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 可视化 2: 各赛题均分与峰值得分对比区间 -->
        <div class="chart-card">
          <div class="chart-head">
            <el-icon><TrendCharts /></el-icon>
            <h4>赛题竞技得分梯队区间 (均分 ~ 最高分)</h4>
          </div>
          <div class="range-plot-list">
            <div
              v-for="item in globalStats.items"
              :key="`range-${item.problemId}`"
              class="range-item-row"
              @click="enterDetail(item.problemId)"
            >
              <div class="range-info">
                <span class="range-title">#{{ item.problemCode || item.problemId }} {{ item.problemTitle }}</span>
                <span class="range-badge-scores">
                  均分 <b>{{ formatScore(item.averageScore) }}</b> · 最高 <b>{{ formatScore(item.highestScore) }}</b>
                </span>
              </div>
              <div class="range-track">
                <div
                  class="range-bar-span"
                  :style="{
                    left: `${Math.max(0, Math.min(Number(item.averageScore) || 0, 100))}%`,
                    width: `${Math.max(4, Math.min((Number(item.highestScore) || 0) - (Number(item.averageScore) || 0), 100))}%`
                  }"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 全局赛题天梯列表 -->
      <section class="problem-ladder-section">
        <div class="ladder-header">
          <h3>赛题竞赛成果天梯列表</h3>
          <div class="ladder-sort-group">
            <span class="sort-label">排序:</span>
            <button
              type="button"
              class="btn-sort"
              :class="{ active: globalSortBy === 'teams' }"
              @click="globalSortBy = 'teams'"
            >
              队伍数
            </button>
            <button
              type="button"
              class="btn-sort"
              :class="{ active: globalSortBy === 'avgScore' }"
              @click="globalSortBy = 'avgScore'"
            >
              平均分
            </button>
            <button
              type="button"
              class="btn-sort"
              :class="{ active: globalSortBy === 'maxScore' }"
              @click="globalSortBy = 'maxScore'"
            >
              最高分
            </button>
          </div>
        </div>

        <div class="ladder-table-wrap">
          <div class="ladder-table" role="table">
            <div class="ladder-tr ladder-th" role="row">
              <div class="l-cell l-col-prob" role="columnheader">赛题信息</div>
              <div class="l-cell l-col-num" role="columnheader">上榜队伍</div>
              <div class="l-cell l-col-score" role="columnheader">最高得分</div>
              <div class="l-cell l-col-score" role="columnheader">平均得分</div>
              <div class="l-cell l-col-actions" role="columnheader">操作</div>
            </div>

            <div
              v-for="item in sortedGlobalProblems"
              :key="item.problemId"
              class="ladder-tr ladder-td-row"
              role="row"
              @click="enterDetail(item.problemId)"
            >
              <div class="l-cell l-col-prob" role="cell">
                <span class="prob-tag">#{{ item.problemCode || item.problemId }}</span>
                <span class="prob-title-text" :title="item.problemTitle">{{ item.problemTitle }}</span>
              </div>
              <div class="l-cell l-col-num" role="cell">
                <strong>{{ item.rankedTeamCount || 0 }}</strong> 支队伍
              </div>
              <div class="l-cell l-col-score" role="cell">
                <span class="score-pill-gold">{{ formatScore(item.highestScore) }} 分</span>
              </div>
              <div class="l-cell l-col-score" role="cell">
                <span class="score-pill-plain">{{ formatScore(item.averageScore) }} 分</span>
              </div>
              <div class="l-cell l-col-actions" role="cell" @click.stop>
                <button
                  type="button"
                  class="btn-table-action"
                  title="查看该题题目详情"
                  @click="openProblemDrawer(item.problemId)"
                >
                  <el-icon><View /></el-icon>
                  <span>速览</span>
                </button>
                <button
                  type="button"
                  class="btn-table-action primary"
                  @click="enterDetail(item.problemId)"
                >
                  <span>查看细览</span>
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- 模式二：赛题成果细览 (Problem Detail Ranking) -->
    <main v-else class="detail-container">
      <!-- 核心指标摘要条 -->
      <section class="detail-summary-bar">
        <div class="summary-item">
          <span class="s-label">上榜队伍</span>
          <strong class="s-val">{{ rankingItems.length }} <small>支</small></strong>
        </div>
        <div class="summary-item">
          <span class="s-label">榜首最高分</span>
          <strong class="s-val score-gold">{{ highestScore }} <small v-if="highestScore !== '-'">分</small></strong>
        </div>
        <div class="summary-item">
          <span class="s-label">平均分</span>
          <strong class="s-val">{{ averageScore }} <small v-if="averageScore !== '-'">分</small></strong>
        </div>
        <div class="summary-item">
          <span class="s-label">前25%高分线(P75)</span>
          <strong class="s-val">{{ p75Score }} <small v-if="p75Score !== '-'">分</small></strong>
        </div>
        <div class="summary-item s-time-item">
          <span class="s-label">快照批次</span>
          <span class="s-time">{{ formatSnapshotTime(overview?.computedAt) }}</span>
        </div>
      </section>

      <!-- 主次分明：首先呈现核心排名 (Top3 领奖台 + 完整榜单) -->
      <template v-if="rankingItems.length">
        <!-- Top 3 领奖台 (主视图) -->
        <section v-if="!hasActiveKeyword && podiumItems.length" class="podium-strip" aria-label="前三名领奖台">
          <div
            v-for="item in podiumItems"
            :key="`podium-${item.teamId}`"
            class="podium-mini-card"
            :class="[`podium-rank-${item.rank}`, { 'is-my-team': isMyTeam(item.teamId) }]"
            @click="scrollToTeamRow(item.teamId)"
          >
            <div class="p-rank-indicator">
              <span class="p-crown-icon">{{ item.rank === 1 ? '🥇' : item.rank === 2 ? '🥈' : '🥉' }}</span>
              <span class="p-rank-text">第 {{ item.rank }} 名</span>
              <span v-if="isMyTeam(item.teamId)" class="p-my-tag">我的队伍</span>
            </div>
            <div class="p-team-row">
              <span class="p-avatar">{{ teamInitial(item.teamName) }}</span>
              <span class="p-team-name" :title="item.teamName">{{ item.teamName }}</span>
            </div>
            <div class="p-score-row">
              <strong class="p-score-val">{{ formatScore(item.score) }}</strong>
              <small>分</small>
            </div>
          </div>
        </section>

        <!-- 核心排名数据表格 -->
        <section class="ranking-table-card">
          <div class="rt-head-bar">
            <div class="rt-title-area">
              <h3>成果排名表</h3>
              <span class="rt-count">共 {{ rankingItems.length }} 支队伍</span>
              <span v-if="hasActiveKeyword" class="rt-filter-tag">已筛选 “{{ appliedKeyword }}”</span>
            </div>
            <div class="rt-rule-hint">排序依据：最终稿评审得分 (从高到低)</div>
          </div>

          <div class="rt-table-responsive">
            <div class="rt-table" role="table">
              <div class="rt-tr rt-th" role="row">
                <div class="rt-cell rt-col-rank" role="columnheader">排名</div>
                <div class="rt-cell rt-col-team" role="columnheader">参赛队伍</div>
                <div class="rt-cell rt-col-score" role="columnheader">最终得分</div>
                <div class="rt-cell rt-col-workflow" role="columnheader">AI 评审版本</div>
                <div class="rt-cell rt-col-time" role="columnheader">最终稿提交时间</div>
                <div class="rt-cell rt-col-action" role="columnheader">操作</div>
              </div>

              <div
                v-for="item in rankingItems"
                :id="`team-row-${item.teamId}`"
                :key="item.teamId"
                class="rt-tr rt-td-row"
                :class="{
                  'is-my-team-row': isMyTeam(item.teamId),
                  'row-highlighted': highlightedTeamId === item.teamId
                }"
                role="row"
              >
                <!-- 排名 -->
                <div class="rt-cell rt-col-rank" role="cell">
                  <span class="rank-badge" :class="`rank-badge-${item.rank}`">{{ item.rank }}</span>
                </div>

                <!-- 队伍 -->
                <div class="rt-cell rt-col-team" role="cell">
                  <span class="team-dot-avatar" :class="`avatar-rank-${item.rank <= 3 ? item.rank : 'def'}`">
                    {{ teamInitial(item.teamName) }}
                  </span>
                  <strong class="team-title-text" :title="item.teamName">{{ item.teamName }}</strong>
                  <span v-if="isMyTeam(item.teamId)" class="my-team-badge">我的队伍</span>
                </div>

                <!-- 得分 -->
                <div class="rt-cell rt-col-score" role="cell">
                  <strong class="score-number">{{ formatScore(item.score) }}</strong>
                  <span class="score-unit">分</span>
                </div>

                <!-- 评审版本 -->
                <div class="rt-cell rt-col-workflow" role="cell">
                  <span class="wf-chip">{{ workflowLabel(item.workflowVersion) }}</span>
                </div>

                <!-- 提交时间 -->
                <div class="rt-cell rt-col-time" role="cell">
                  <span class="date-text">{{ formatDate(item.submittedAt) }}</span>
                </div>

                <!-- 操作 -->
                <div class="rt-cell rt-col-action" role="cell">
                  <button
                    type="button"
                    class="btn-row-action"
                    title="复制队伍名称"
                    @click.stop="copyTeamName(item.teamName)"
                  >
                    <el-icon><CopyDocument /></el-icon>
                    <span>复制</span>
                  </button>
                  <router-link
                    v-if="item.teamId"
                    :to="`/team/detail/${item.teamId}`"
                    class="btn-row-action"
                    title="查看队伍详情"
                  >
                    <span>队伍</span>
                  </router-link>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 多样化数据分析面板 (位于榜单之后，作为深度分析支撑，不反客为主) -->
        <section class="analytics-dashboard-panel">
          <div class="panel-section-title">
            <el-icon><DataAnalysis /></el-icon>
            <h3>赛题数据洞察与分布分析</h3>
          </div>

          <div class="visuals-dual-grid">
            <!-- 图表 A: 分数梯队直方分布图 (带 Tooltip 与我的队伍标记) -->
            <div class="visual-sub-card">
              <div class="sub-card-header">
                <h4>分数梯队直方分布 (0–100 分)</h4>
                <span class="sub-card-note">共统计 {{ totalDistributionTeams }} 支队伍</span>
              </div>
              <div v-if="distributionBuckets.length" class="histogram-box">
                <div class="histogram-flex-track">
                  <div
                    v-for="(bucket, idx) in distributionBuckets"
                    :key="`b-${idx}`"
                    class="hist-bar-col"
                    :class="{ 'is-my-tier': isTeamInBucket(bucket) }"
                  >
                    <!-- Tooltip -->
                    <div class="hist-tooltip">
                      <div class="ht-label">{{ bucket.label }}</div>
                      <div class="ht-val">{{ bucket.count }} 支队伍 ({{ bucket.percentage }}%)</div>
                      <div v-if="isTeamInBucket(bucket)" class="ht-my">★ 我的队伍在此分数段</div>
                    </div>
                    <!-- 柱体 -->
                    <div class="hist-bar-wrap">
                      <span v-if="bucket.count > 0" class="hist-count-num">{{ bucket.count }}</span>
                      <div
                        class="hist-bar-inner"
                        :style="{ height: `${Math.max(bucket.heightRatio * 100, bucket.count > 0 ? 8 : 2)}%` }"
                      >
                        <div v-if="isTeamInBucket(bucket)" class="my-tier-dot"></div>
                      </div>
                    </div>
                    <span class="hist-x-axis">{{ bucket.shortLabel }}</span>
                  </div>
                </div>
              </div>
              <div v-else class="empty-chart-notice">暂无足够样本生成分布图</div>
            </div>

            <!-- 图表 B: 竞赛实力分层环形占比 (Donut Breakdown) -->
            <div class="visual-sub-card">
              <div class="sub-card-header">
                <h4>竞技实力层级构成</h4>
                <span class="sub-card-note">按标准建模评审分段</span>
              </div>
              <div v-if="tierBreakdownList.length" class="donut-visual-container">
                <!-- 纯 SVG 极速轻量 Donut 图 -->
                <div class="donut-chart-box">
                  <svg viewBox="0 0 120 120" class="donut-svg">
                    <circle
                      v-for="(seg, idx) in donutSegments"
                      :key="`seg-${idx}`"
                      cx="60"
                      cy="60"
                      r="45"
                      fill="transparent"
                      :stroke="seg.color"
                      stroke-width="16"
                      :stroke-dasharray="`${seg.dashLength} ${seg.circumference}`"
                      :stroke-dashoffset="seg.dashOffset"
                    />
                  </svg>
                  <div class="donut-center-text">
                    <strong>{{ totalDistributionTeams }}</strong>
                    <small>总作品</small>
                  </div>
                </div>

                <!-- 图例与明细列表 -->
                <div class="donut-legend-list">
                  <div
                    v-for="tier in tierBreakdownList"
                    :key="tier.name"
                    class="donut-legend-item"
                  >
                    <span class="legend-color-dot" :style="{ background: tier.color }"></span>
                    <span class="legend-name">{{ tier.name }}</span>
                    <span class="legend-count">{{ tier.count }} 支</span>
                    <span class="legend-percent">{{ tier.percentage }}%</span>
                  </div>
                </div>
              </div>
              <div v-else class="empty-chart-notice">暂无足够样本生成构成图</div>
            </div>
          </div>
        </section>
      </template>

      <!-- 细览空状态：搜索无匹配 -->
      <section v-else-if="hasActiveKeyword && !loading" class="detail-empty-box">
        <el-icon class="empty-icon"><Search /></el-icon>
        <h4>未找到匹配的队伍</h4>
        <p>在当前赛题中，未检索到包含 “{{ appliedKeyword }}” 的上榜队伍。</p>
        <button type="button" class="btn-clear-filter" @click="handleClearSearch">清空搜索条件</button>
      </section>

      <!-- 细览空状态：该题暂无成果上榜 -->
      <section v-else-if="!loading" class="detail-empty-box">
        <el-icon class="empty-icon"><Trophy /></el-icon>
        <h4>该赛题尚无队伍上榜</h4>
        <p>队伍完成最终学术论文提交并通过 AI 评审后将自动上榜。</p>
        <button type="button" class="btn-clear-filter" @click="openProblemDrawer(selectedProblemId)">
          查看本题详情与建模要求
        </button>
      </section>
    </main>

    <!-- 侧边轻量抽屉：临时看一眼题目详情 (免跳转做题页面) -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerProblem ? `#${drawerProblem.code || drawerProblem.id} · ${drawerProblem.title}` : '赛题速览'"
      direction="rtl"
      size="560px"
      destroy-on-close
      class="quick-problem-drawer"
    >
      <div v-if="loadingDrawer" class="drawer-loading-box">
        <el-icon class="is-spinning"><Refresh /></el-icon>
        <span>正在加载题目内容...</span>
      </div>
      <div v-else-if="drawerProblem" class="drawer-body-wrap">
        <!-- 标签元信息 -->
        <div class="drawer-tags-row">
          <span class="d-tag contest">{{ drawerProblem.contestName || '建模公开赛题' }}</span>
          <span v-if="drawerProblem.year" class="d-tag">{{ drawerProblem.year }} 年</span>
          <span class="d-tag">{{ languageLabel(drawerProblem.statementLanguage) }}</span>
          <span class="d-tag" :class="`diff-${drawerProblem.difficulty}`">
            {{ difficultyLabel(drawerProblem.difficulty) }}
          </span>
        </div>

        <!-- 题面正文渲染 -->
        <div class="drawer-markdown-content" v-html="drawerMarkdownHtml"></div>

        <!-- 底部快捷操作 -->
        <div class="drawer-footer-actions">
          <router-link :to="`/problem/${drawerProblem.id}`" class="btn-to-problem-page">
            <span>在做题工作台打开完整页面</span>
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  ArrowRight,
  CopyDocument,
  DataAnalysis,
  Refresh,
  Search,
  TrendCharts,
  Trophy,
  UserFilled,
  View,
  Warning
} from '@element-plus/icons-vue'
import { getPublicProblemDetail, getPublicProblemList } from '@/api/problem'
import { getGlobalRankingStats, getProblemScoreDistribution, getRanking } from '@/api/ranking'
import { getAllMyTeams } from '@/api/team'
import { useUserStore } from '@/store/user'
import { renderSafeMarkdown } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 视图模式: 'overview' (全平台总览天梯) | 'detail' (单题成果细览)
const viewMode = ref('overview')

// 基础数据状态
const loadingProblems = ref(false)
const loading = ref(false)
const problems = ref([])
const selectedProblemId = ref(null)
const keyword = ref('')
const appliedKeyword = ref('')
const overview = ref(null)
const distribution = ref(null)
const globalStats = ref(null)
const rankingError = ref('')
const myTeams = ref([])
const highlightedTeamId = ref(null)
const globalSortBy = ref('teams') // 'teams' | 'avgScore' | 'maxScore'

// 抽屉临时速览状态
const drawerVisible = ref(false)
const loadingDrawer = ref(false)
const drawerProblem = ref(null)
const drawerMarkdownHtml = ref('')

// 计算属性：当前选中赛题
const currentProblem = computed(() =>
  problems.value.find((item) => String(item.id) === String(selectedProblemId.value))
)

const rankingItems = computed(() => overview.value?.items || [])
const hasActiveKeyword = computed(() => Boolean(appliedKeyword.value))

// 细览榜单指标
const highestScore = computed(() => {
  if (!rankingItems.value.length) return '-'
  return formatScore(rankingItems.value[0].score)
})

const averageScore = computed(() => {
  if (!rankingItems.value.length) return '-'
  const validScores = rankingItems.value
    .map((item) => Number(item.score))
    .filter((score) => Number.isFinite(score))
  if (!validScores.length) return '-'
  const sum = validScores.reduce((acc, cur) => acc + cur, 0)
  return (sum / validScores.length).toFixed(1)
})

const p75Score = computed(() => {
  if (rankingItems.value.length < 4) return '-'
  const sorted = [...rankingItems.value].sort((a, b) => Number(b.score) - Number(a.score))
  const idx = Math.floor(sorted.length * 0.25)
  return formatScore(sorted[idx]?.score)
})

// Top 3 领奖台 (2-1-3 经典排布)
const podiumItems = computed(() => {
  const top = rankingItems.value.slice(0, 3)
  if (top.length === 3 && top[0].rank === 1 && top[1].rank === 2 && top[2].rank === 3) {
    return [top[1], top[0], top[2]] // 亚军、冠军、季军
  }
  return top
})

// 当前用户的队伍检测
const myTeamInCurrentProblem = computed(() => {
  if (!userStore.isLogin || !selectedProblemId.value || !myTeams.value.length) return null
  return myTeams.value.find((team) => String(team.problemId) === String(selectedProblemId.value))
})

const myTeamRankingItem = computed(() => {
  if (!myTeamInCurrentProblem.value || !rankingItems.value.length) return null
  return rankingItems.value.find((item) => String(item.teamId) === String(myTeamInCurrentProblem.value.id))
})

// 全局赛题排序
const sortedGlobalProblems = computed(() => {
  const list = [...(globalStats.value?.items || [])]
  if (globalSortBy.value === 'teams') {
    return list.sort((a, b) => (b.rankedTeamCount || 0) - (a.rankedTeamCount || 0))
  }
  if (globalSortBy.value === 'avgScore') {
    return list.sort((a, b) => (Number(b.averageScore) || 0) - (Number(a.averageScore) || 0))
  }
  if (globalSortBy.value === 'maxScore') {
    return list.sort((a, b) => (Number(b.highestScore) || 0) - (Number(a.highestScore) || 0))
  }
  return list
})

const sortedGlobalProblemsByTeams = computed(() => {
  return [...(globalStats.value?.items || [])].sort(
    (a, b) => (b.rankedTeamCount || 0) - (a.rankedTeamCount || 0)
  ).slice(0, 6)
})

const maxGlobalRankedTeams = computed(() => {
  const counts = (globalStats.value?.items || []).map((it) => it.rankedTeamCount || 0)
  return Math.max(...counts, 1)
})

function getBarPercent(val, max) {
  if (!max || !val) return 0
  return Math.round((val / max) * 100)
}

// 分数梯队聚合计算 (直方图)
const distributionBuckets = computed(() => {
  if (!distribution.value?.buckets || !distribution.value.buckets.length) return []
  const rawBuckets = distribution.value.buckets
  const totalTeams = rawBuckets.reduce((acc, cur) => acc + (Number(cur.teamCount) || 0), 0)
  if (totalTeams === 0) return []

  const tiers = [
    { label: '0–59 分 (需提升)', shortLabel: '<60', min: 0, max: 59 },
    { label: '60–69 分 (及格线)', shortLabel: '60-69', min: 60, max: 69 },
    { label: '70–79 分 (良好档)', shortLabel: '70-79', min: 70, max: 79 },
    { label: '80–84 分 (优良档)', shortLabel: '80-84', min: 80, max: 84 },
    { label: '85–89 分 (卓越档)', shortLabel: '85-89', min: 85, max: 89 },
    { label: '90–94 分 (领跑档)', shortLabel: '90-94', min: 90, max: 94 },
    { label: '95–100 分 (顶尖档)', shortLabel: '95-100', min: 95, max: 100 }
  ]

  const bucketStats = tiers.map((tier) => {
    let count = 0
    for (const item of rawBuckets) {
      const score = Number(item.score)
      if (score >= tier.min && score <= tier.max) {
        count += Number(item.teamCount) || 0
      }
    }
    const percentage = totalTeams > 0 ? ((count / totalTeams) * 100).toFixed(1) : '0.0'
    return { ...tier, count, percentage }
  })

  const maxCount = Math.max(...bucketStats.map((item) => item.count), 1)
  return bucketStats.map((item) => ({
    ...item,
    heightRatio: item.count / maxCount
  }))
})

const totalDistributionTeams = computed(() => {
  if (!distribution.value?.buckets) return 0
  return distribution.value.buckets.reduce((acc, cur) => acc + (Number(cur.teamCount) || 0), 0)
})

// 图表 B: 实力层级构成 (Donut Breakdown)
const tierBreakdownList = computed(() => {
  if (!distributionBuckets.value.length) return []
  const total = totalDistributionTeams.value
  if (!total) return []

  const colors = {
    '95-100': '#eab308', // 金黄 (顶尖)
    '90-94': '#3b82f6',  // 亮蓝 (领跑)
    '85-89': '#10b981',  // 翡翠绿 (卓越)
    '80-84': '#6366f1',  // 靛蓝 (优良)
    '70-79': '#94a3b8',  // 银灰 (良好)
    '60-69': '#cbd5e1',  // 浅灰 (及格)
    '<60': '#f87171'     // 浅红 (待提升)
  }

  return distributionBuckets.value.map((b) => ({
    name: b.shortLabel,
    count: b.count,
    percentage: b.percentage,
    color: colors[b.shortLabel] || '#94a3b8'
  }))
})

// 纯 SVG Donut 图计算段
const donutSegments = computed(() => {
  const list = tierBreakdownList.value
  if (!list.length) return []
  const circumference = 2 * Math.PI * 45 // 半径 45，周长约 282.74
  let accumulatedPercent = 0

  return list.map((item) => {
    const percent = Number(item.percentage) / 100
    const dashLength = percent * circumference
    const dashOffset = -(accumulatedPercent * circumference)
    accumulatedPercent += percent
    return {
      color: item.color,
      dashLength,
      dashOffset,
      circumference
    }
  })
})

// 格式化函数
function formatScore(val) {
  if (val == null || val === '') return '-'
  const num = Number(val)
  return Number.isFinite(num) ? num.toFixed(1) : '-'
}

function formatDate(val) {
  if (!val) return '-'
  return String(val).replace('T', ' ').slice(0, 16)
}

function formatSnapshotTime(val) {
  if (!val) return '-'
  const str = String(val).replace('T', ' ')
  return `${str.slice(5, 10)} ${str.slice(11, 16)}`
}

function languageLabel(lang) {
  return lang === 'EN' ? '英文题面' : '中文题面'
}

function difficultyLabel(diff) {
  return { 1: '简单', 2: '中等', 3: '困难' }[diff] || '中等'
}

function workflowLabel(ver) {
  if (!ver || ver === 'BASIC_REVIEW_V1') return '基础评审 V1'
  return String(ver).replaceAll('_', ' ')
}

function teamInitial(name) {
  return String(name || '队').trim().slice(0, 1).toUpperCase()
}

function isMyTeam(teamId) {
  if (!myTeamInCurrentProblem.value) return false
  return String(teamId) === String(myTeamInCurrentProblem.value.id)
}

function isTeamInBucket(bucket) {
  if (!myTeamRankingItem.value) return false
  const score = Math.round(Number(myTeamRankingItem.value.score))
  return score >= bucket.min && score <= bucket.max
}

// 复制队伍名称
async function copyTeamName(name) {
  if (!name) return
  try {
    await navigator.clipboard.writeText(name)
    ElMessage.success(`队伍 “${name}” 已复制`)
  } catch {
    ElMessage.info(`队伍名称：${name}`)
  }
}

// 平滑滚动高亮
function scrollToTeamRow(teamId) {
  nextTick(() => {
    const el = document.getElementById(`team-row-${teamId}`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      highlightedTeamId.value = teamId
      setTimeout(() => {
        if (highlightedTeamId.value === teamId) highlightedTeamId.value = null
      }, 2500)
    }
  })
}

function locateMyTeamAction() {
  if (!myTeamInCurrentProblem.value) return
  if (myTeamRankingItem.value) {
    if (hasActiveKeyword.value) {
      keyword.value = ''
      appliedKeyword.value = ''
      loadRanking().then(() => scrollToTeamRow(myTeamRankingItem.value.teamId))
    } else {
      scrollToTeamRow(myTeamRankingItem.value.teamId)
    }
  } else {
    ElMessage.info(`队伍 “${myTeamInCurrentProblem.value.name}” 正在实训中，尚未完成最终稿评审`)
  }
}

// 临时看一眼题目详情抽屉
async function openProblemDrawer(problemId) {
  if (!problemId) return
  drawerVisible.value = true
  loadingDrawer.value = true
  drawerProblem.value = null
  drawerMarkdownHtml.value = ''
  try {
    const res = await getPublicProblemDetail(problemId)
    drawerProblem.value = res.data
    drawerMarkdownHtml.value = renderSafeMarkdown(res.data?.contentMarkdown || '暂无题目说明')
  } catch (error) {
    ElMessage.error(error.message || '赛题内容加载失败')
  } finally {
    loadingDrawer.value = false
  }
}

// 切换总览与细览模式
function switchViewMode(mode) {
  viewMode.value = mode
  if (mode === 'overview') {
    router.replace({ query: {} })
    loadGlobalStats()
  } else {
    if (!selectedProblemId.value && problems.value.length) {
      selectedProblemId.value = problems.value[0].id
    }
    if (selectedProblemId.value) {
      router.replace({ query: { problemId: String(selectedProblemId.value) } })
      loadDetailData()
    }
  }
}

// 点击总览中的赛题直接穿透进细览
function enterDetail(problemId) {
  selectedProblemId.value = problemId
  viewMode.value = 'detail'
  router.replace({ query: { problemId: String(problemId) } })
  loadDetailData()
}

// 下拉菜单切换赛题命令
function handleSelectProblemCommand(problemId) {
  if (String(problemId) === String(selectedProblemId.value)) return
  enterDetail(problemId)
}

// 核心数据加载
async function loadProblems() {
  loadingProblems.value = true
  try {
    const res = await getPublicProblemList({ page: 1, pageSize: 100 })
    problems.value = res.data?.rows || []

    const qProblemId = route.query.problemId
    if (qProblemId) {
      selectedProblemId.value = Number(qProblemId) || qProblemId
      viewMode.value = 'detail'
      await loadDetailData()
    } else {
      viewMode.value = 'overview'
      await loadGlobalStats()
    }
  } catch (error) {
    rankingError.value = error.message || '加载赛题列表失败'
  } finally {
    loadingProblems.value = false
  }
}

async function loadGlobalStats() {
  loading.value = true
  rankingError.value = ''
  try {
    const res = await getGlobalRankingStats()
    globalStats.value = res.data
  } catch {
    // 若后端全局接口未通，由前端题库列表兜底合成基础统计
    globalStats.value = {
      problemCount: problems.value.length,
      rankedTeams: 0,
      reviewedSubmissions: 0,
      overallAverageScore: null,
      items: problems.value.map((p) => ({
        problemId: p.id,
        problemCode: p.code,
        problemTitle: p.title,
        rankedTeamCount: 0,
        averageScore: null,
        highestScore: null
      }))
    }
  } finally {
    loading.value = false
  }
}

async function loadDetailData() {
  if (!selectedProblemId.value) return
  loading.value = true
  rankingError.value = ''
  try {
    await Promise.all([loadRanking(), loadDistribution(), loadMyTeams()])
  } finally {
    loading.value = false
  }
}

async function loadRanking() {
  if (!selectedProblemId.value) return
  const trimmed = keyword.value.trim()
  try {
    const res = await getRanking(selectedProblemId.value, trimmed)
    overview.value = res.data
    appliedKeyword.value = trimmed
  } catch (error) {
    rankingError.value = error.message || '排行榜加载失败'
    overview.value = null
  }
}

async function loadDistribution() {
  if (!selectedProblemId.value) return
  try {
    const res = await getProblemScoreDistribution(selectedProblemId.value)
    distribution.value = res.data
  } catch {
    distribution.value = null
  }
}

async function loadMyTeams() {
  if (!userStore.isLogin) return
  try {
    const res = await getAllMyTeams()
    myTeams.value = res.rows || []
  } catch {
    myTeams.value = []
  }
}

async function handleSearch() {
  await loadRanking()
}

async function handleClearSearch() {
  keyword.value = ''
  await loadRanking()
}

async function handleManualRefresh() {
  if (viewMode.value === 'overview') {
    await loadGlobalStats()
  } else {
    await loadDetailData()
  }
  ElMessage.success('数据已更新')
}

watch(
  () => route.query.problemId,
  (newId) => {
    if (newId) {
      if (String(newId) !== String(selectedProblemId.value) || viewMode.value !== 'detail') {
        selectedProblemId.value = Number(newId) || newId
        viewMode.value = 'detail'
        loadDetailData()
      }
    } else {
      if (viewMode.value !== 'overview') {
        viewMode.value = 'overview'
        loadGlobalStats()
      }
    }
  }
)

onMounted(async () => {
  await loadProblems()
})
</script>

<style scoped>
/* ==========================================================================
   LeetModel 成果榜单 - 极致专业、紧凑干练的学术工作台
   ========================================================================== */

.ranking-page {
  width: min(100%, 1180px);
  margin: 0 auto;
  padding: 4px 16px 40px;
  color: var(--lm-text-primary);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 顶部导航条 (极简、操作集中) */
.ranking-nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 16px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  box-shadow: var(--lm-shadow-xs);
  flex-wrap: wrap;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.view-mode-tabs {
  display: inline-flex;
  padding: 3px;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  gap: 2px;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border: none;
  background: transparent;
  color: var(--lm-text-secondary);
  font-size: 12px;
  font-weight: 600;
  border-radius: 4px;
  cursor: pointer;
  transition: all var(--lm-transition);
}

.tab-btn:hover {
  color: var(--lm-text-primary);
}

.tab-btn.active {
  background: var(--lm-surface);
  color: var(--lm-text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

/* 紧凑赛题选择器按钮 (替代原占用全行的大面板) */
.compact-problem-selector {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.btn-select-problem {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  color: var(--lm-text-primary);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  transition: all var(--lm-transition);
  max-width: 380px;
}

.btn-select-problem:hover {
  border-color: #cbd5e1;
  background: var(--lm-bg-secondary);
}

.problem-num-tag {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 700;
}

.problem-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-arrow {
  font-size: 11px;
  color: var(--lm-text-muted);
}

/* 题目下拉菜单 */
.dropdown-problem-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  font-size: 12px;
}

.dp-code {
  font-family: var(--lm-code-font-family);
  font-weight: 700;
  color: var(--lm-text-muted);
}

.dp-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dp-year {
  font-size: 10px;
  color: var(--lm-text-muted);
}

.dp-tag {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
}

.diff-1 { color: #059669; }
.diff-2 { color: #d97706; }
.diff-3 { color: #dc2626; }

/* 赛题速览按钮 */
.btn-quick-preview {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 10px;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-sm);
  color: var(--lm-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--lm-transition);
  white-space: nowrap;
}

.btn-quick-preview:hover {
  background: #e2e8f0;
  color: var(--lm-text-primary);
}

/* 导航右侧 */
.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.compact-search-input {
  width: 170px;
}

.btn-my-team-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  border-radius: 999px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
  white-space: nowrap;
}

.btn-my-team-pill.is-ranked {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.btn-icon-refresh {
  width: 28px;
  height: 28px;
  border: 1px solid var(--lm-border);
  background: var(--lm-surface);
  border-radius: var(--lm-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-icon-refresh:hover:not(:disabled) {
  background: var(--lm-bg-secondary);
  color: var(--lm-text-primary);
}

.is-spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 骨架屏 */
.skeleton-layout {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skeleton-pulse {
  background: linear-gradient(90deg, #f4f4f5 25%, #e4e4e7 37%, #f4f4f5 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

@keyframes skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

.skeleton-strip { height: 60px; border-radius: var(--lm-radius); }
.skeleton-grid-boxes { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.sk-box { height: 80px; border-radius: var(--lm-radius); }
.skeleton-main-card { height: 360px; border-radius: var(--lm-radius-lg); }

/* 错误提示 */
.error-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--lm-radius);
  color: #991b1b;
  font-size: 13px;
}

.btn-error-retry {
  margin-left: auto;
  padding: 4px 10px;
  background: #dc2626;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
}

/* ==========================================================================
   模式一：赛题总览天梯 (Overview)
   ========================================================================== */

.overview-container {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 全局指标统计横条 */
.global-metric-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.g-metric-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 14px 18px;
  box-shadow: var(--lm-shadow-xs);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.gm-label {
  font-size: 11px;
  color: var(--lm-text-muted);
  font-weight: 600;
}

.gm-val-row {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.gm-val-row strong {
  font-size: 24px;
  font-weight: 800;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-primary);
  line-height: 1;
}

.gm-val-row strong.highlight-score {
  color: #d97706;
}

.gm-val-row small {
  font-size: 11px;
  color: var(--lm-text-muted);
}

/* 多样化图表看板 (总览) */
.overview-visuals-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.chart-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 16px 20px;
  box-shadow: var(--lm-shadow-xs);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chart-head {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--lm-text-primary);
}

.chart-head h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}

.chart-head .el-icon {
  font-size: 15px;
  color: #2563eb;
}

/* 横向对比条形图 */
.horizontal-bar-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hbar-row {
  display: grid;
  grid-template-columns: 160px 1fr;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: background var(--lm-transition);
}

.hbar-row:hover {
  background: var(--lm-bg-secondary);
}

.hbar-label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.hbar-code {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  color: var(--lm-text-muted);
  font-weight: 700;
}

.hbar-title {
  font-size: 12px;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hbar-track {
  height: 18px;
  background: var(--lm-bg-secondary);
  border-radius: 4px;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.hbar-fill {
  height: 100%;
  background: #3b82f6;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.hbar-val {
  position: absolute;
  right: 8px;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-secondary);
}

/* 均分~最高分区间分布图 */
.range-plot-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.range-item-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: background var(--lm-transition);
}

.range-item-row:hover {
  background: var(--lm-bg-secondary);
}

.range-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
}

.range-title {
  font-weight: 650;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.range-badge-scores b {
  color: var(--lm-text-primary);
}

.range-track {
  position: relative;
  height: 8px;
  background: var(--lm-bg-secondary);
  border-radius: 4px;
}

.range-bar-span {
  position: absolute;
  height: 100%;
  background: linear-gradient(90deg, #93c5fd 0%, #3b82f6 100%);
  border-radius: 4px;
}

/* 全平台赛题天梯表格 */
.problem-ladder-section {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-xs);
  overflow: hidden;
}

.ladder-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--lm-border);
}

.ladder-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 750;
}

.ladder-sort-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sort-label {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.btn-sort {
  border: 1px solid var(--lm-border);
  background: var(--lm-surface);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-sort.active {
  background: var(--lm-bg-secondary);
  color: var(--lm-text-primary);
  border-color: #cbd5e1;
  font-weight: 700;
}

.ladder-table {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.ladder-tr {
  display: grid;
  grid-template-columns: minmax(260px, 1.8fr) 140px 140px 140px 160px;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid var(--lm-border-light);
  transition: background var(--lm-transition);
}

.ladder-th {
  background: var(--lm-bg-secondary);
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
}

.ladder-td-row {
  cursor: pointer;
}

.ladder-td-row:hover {
  background: #f8fafc;
}

.l-cell {
  display: flex;
  align-items: center;
}

.l-col-prob {
  gap: 8px;
  min-width: 0;
}

.prob-tag {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  font-weight: 700;
  color: #2563eb;
  background: #eff6ff;
  padding: 1px 5px;
  border-radius: 3px;
}

.prob-title-text {
  font-size: 13px;
  font-weight: 650;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-pill-gold {
  font-size: 13px;
  font-family: var(--lm-code-font-family);
  font-weight: 800;
  color: #b45309;
}

.score-pill-plain {
  font-size: 13px;
  font-family: var(--lm-code-font-family);
  font-weight: 700;
  color: var(--lm-text-secondary);
}

.l-col-actions {
  gap: 6px;
}

.btn-table-action {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--lm-border);
  background: var(--lm-surface);
  border-radius: 4px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-table-action:hover {
  background: var(--lm-bg-secondary);
  color: var(--lm-text-primary);
}

.btn-table-action.primary {
  background: var(--lm-bg-secondary);
  font-weight: 600;
  color: #2563eb;
}

.btn-table-action.primary:hover {
  background: #eff6ff;
}

/* ==========================================================================
   模式二：单题细览 (Detail)
   ========================================================================== */

.detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 核心指标横条 (细览) */
.detail-summary-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr) minmax(180px, 1.2fr);
  gap: 10px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 12px 18px;
  box-shadow: var(--lm-shadow-xs);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.s-label {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.s-val {
  font-size: 18px;
  font-family: var(--lm-code-font-family);
  font-weight: 800;
  color: var(--lm-text-primary);
}

.s-val.score-gold {
  color: #b45309;
}

.s-val small {
  font-size: 11px;
  font-weight: 500;
  color: var(--lm-text-muted);
}

.s-time-item {
  border-left: 1px solid var(--lm-border-light);
  padding-left: 14px;
}

.s-time {
  font-size: 12px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-secondary);
  margin-top: 3px;
}

/* Top 3 领奖台 (主视图) */
.podium-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.podium-mini-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 14px 16px;
  box-shadow: var(--lm-shadow-xs);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: transform var(--lm-transition), box-shadow var(--lm-transition);
}

.podium-mini-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--lm-shadow-sm);
}

.podium-rank-1 {
  border-color: rgba(217, 119, 6, 0.35);
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.25) 0%, var(--lm-surface) 50%);
}

.podium-rank-2 {
  border-color: rgba(148, 163, 184, 0.35);
}

.podium-rank-3 {
  border-color: rgba(202, 138, 4, 0.25);
}

.p-rank-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 700;
}

.p-my-tag {
  margin-left: auto;
  font-size: 9px;
  background: #2563eb;
  color: #fff;
  padding: 1px 5px;
  border-radius: 999px;
}

.p-team-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.p-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--lm-bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
}

.podium-rank-1 .p-avatar { background: #fef3c7; color: #b45309; }

.p-team-name {
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.p-score-row {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.p-score-val {
  font-size: 22px;
  font-weight: 850;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-primary);
}

.podium-rank-1 .p-score-val { color: #b45309; }

/* 核心排名表格 */
.ranking-table-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-xs);
  overflow: hidden;
}

.rt-head-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid var(--lm-border);
}

.rt-title-area {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rt-title-area h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 750;
}

.rt-count {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.rt-filter-tag {
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
  padding: 1px 6px;
  border-radius: 3px;
}

.rt-rule-hint {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.rt-table-responsive {
  overflow-x: auto;
}

.rt-table {
  width: 100%;
  min-width: 780px;
  display: flex;
  flex-direction: column;
}

.rt-tr {
  display: grid;
  grid-template-columns: 70px minmax(200px, 1.4fr) 110px minmax(140px, 1fr) minmax(160px, 1.1fr) 110px;
  align-items: center;
  padding: 10px 18px;
  border-bottom: 1px solid var(--lm-border-light);
  transition: background var(--lm-transition);
}

.rt-th {
  background: var(--lm-bg-secondary);
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
  padding: 8px 18px;
}

.rt-td-row:hover {
  background: #f8fafc;
}

.rt-td-row.is-my-team-row {
  background: rgba(37, 99, 235, 0.03);
  border-left: 3px solid #2563eb;
}

/* 高亮动画 */
.rt-td-row.row-highlighted {
  animation: pulse-hl 2.5s ease-out;
}

@keyframes pulse-hl {
  0% { background: rgba(254, 240, 138, 0.5); }
  100% { background: transparent; }
}

.rt-cell {
  display: flex;
  align-items: center;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 5px;
  border-radius: 4px;
  font-family: var(--lm-code-font-family);
  font-size: 12px;
  font-weight: 800;
  background: var(--lm-bg-secondary);
  color: var(--lm-text-secondary);
}

.rank-badge-1 { background: #fef3c7; color: #b45309; }
.rank-badge-2 { background: #f1f5f9; color: #475569; }
.rank-badge-3 { background: #ffedd5; color: #c2410c; }

.team-dot-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--lm-bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  margin-right: 8px;
  flex-shrink: 0;
}

.avatar-rank-1 { background: #fef3c7; color: #b45309; }
.avatar-rank-2 { background: #f1f5f9; color: #475569; }
.avatar-rank-3 { background: #ffedd5; color: #c2410c; }

.team-title-text {
  font-size: 13px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-team-badge {
  font-size: 9px;
  background: #2563eb;
  color: #fff;
  padding: 1px 5px;
  border-radius: 3px;
  margin-left: 6px;
  flex-shrink: 0;
}

.score-number {
  font-size: 17px;
  font-weight: 850;
  font-family: var(--lm-code-font-family);
}

.score-unit {
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-left: 2px;
}

.wf-chip {
  font-size: 10px;
  background: #eff6ff;
  color: #1e40af;
  padding: 2px 6px;
  border-radius: 3px;
}

.date-text {
  font-size: 11px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-secondary);
}

.rt-col-action {
  gap: 4px;
}

.btn-row-action {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 24px;
  padding: 0 6px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: 3px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  text-decoration: none;
}

.btn-row-action:hover {
  background: var(--lm-bg-secondary);
  color: var(--lm-text-primary);
}

/* 多样化数据分析面板 (细览后置支撑) */
.analytics-dashboard-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
}

.panel-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--lm-text-secondary);
}

.panel-section-title h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}

.visuals-dual-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.visual-sub-card {
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  padding: 14px 18px;
  box-shadow: var(--lm-shadow-xs);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sub-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sub-card-header h4 {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
}

.sub-card-note {
  font-size: 11px;
  color: var(--lm-text-muted);
}

/* 直方图 */
.histogram-box {
  height: 100px;
  display: flex;
  align-items: flex-end;
}

.histogram-flex-track {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  border-bottom: 1px solid var(--lm-border);
  padding-bottom: 2px;
}

.hist-bar-col {
  position: relative;
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  cursor: pointer;
}

.hist-bar-wrap {
  width: 100%;
  height: calc(100% - 16px);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
}

.hist-count-num {
  font-size: 9px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-muted);
  margin-bottom: 1px;
}

.hist-bar-inner {
  width: 100%;
  max-width: 26px;
  background: #e2e8f0;
  border-radius: 3px 3px 0 0;
  transition: height 0.3s ease;
  position: relative;
}

.hist-bar-col:hover .hist-bar-inner { background: #94a3b8; }
.hist-bar-col.is-my-tier .hist-bar-inner { background: #3b82f6; }

.my-tier-dot {
  position: absolute;
  top: -3px;
  left: 50%;
  transform: translateX(-50%);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #f59e0b;
  box-shadow: 0 0 0 1px #fff;
}

.hist-x-axis {
  font-size: 9px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-muted);
  margin-top: 3px;
}

/* 直方图 Tooltip */
.hist-tooltip {
  position: absolute;
  bottom: calc(100% - 6px);
  left: 50%;
  transform: translateX(-50%);
  background: #09090b;
  color: #fff;
  padding: 5px 8px;
  border-radius: 4px;
  font-size: 10px;
  pointer-events: none;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s ease;
  white-space: nowrap;
  z-index: 20;
}

.hist-bar-col:hover .hist-tooltip {
  opacity: 1;
  visibility: visible;
}

.ht-label { font-weight: 700; }
.ht-val { color: #cbd5e1; }
.ht-my { color: #fde047; font-weight: 600; }

/* 环形 Donut 图 */
.donut-visual-container {
  display: flex;
  align-items: center;
  gap: 16px;
}

.donut-chart-box {
  position: relative;
  width: 80px;
  height: 80px;
  flex-shrink: 0;
}

.donut-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.donut-center-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  display: flex;
  flex-direction: column;
}

.donut-center-text strong {
  font-size: 14px;
  font-family: var(--lm-code-font-family);
  line-height: 1;
}

.donut-center-text small {
  font-size: 9px;
  color: var(--lm-text-muted);
}

.donut-legend-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px 8px;
  flex: 1;
}

.donut-legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
}

.legend-color-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-name { color: var(--lm-text-secondary); width: 34px; }
.legend-count { font-family: var(--lm-code-font-family); color: var(--lm-text-primary); font-weight: 600; }
.legend-percent { color: var(--lm-text-muted); margin-left: auto; }

.empty-chart-notice {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--lm-text-muted);
  border: 1px dashed var(--lm-border);
  border-radius: 4px;
}

/* 细览空状态 */
.detail-empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  background: var(--lm-surface);
  border: 1px dashed var(--lm-border);
  border-radius: var(--lm-radius-lg);
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  color: var(--lm-text-muted);
  margin-bottom: 10px;
}

.detail-empty-box h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.detail-empty-box p {
  margin: 6px 0 16px;
  font-size: 12px;
  color: var(--lm-text-secondary);
}

.btn-clear-filter {
  padding: 6px 14px;
  background: var(--lm-primary);
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

/* ==========================================================================
   赛题速览侧边抽屉 (Quick Preview Drawer)
   ========================================================================== */

.drawer-loading-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--lm-text-muted);
  font-size: 13px;
}

.drawer-body-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-tags-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.d-tag {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--lm-bg-secondary);
  border: 1px solid var(--lm-border);
  border-radius: 4px;
  color: var(--lm-text-secondary);
}

.d-tag.contest { font-weight: 700; color: var(--lm-text-primary); }

.drawer-markdown-content {
  font-size: 13px;
  line-height: 1.7;
  color: var(--lm-text-primary);
  max-height: calc(100vh - 180px);
  overflow-y: auto;
  padding-right: 6px;
}

.drawer-footer-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--lm-border);
}

.btn-to-problem-page {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  text-decoration: none;
}

.btn-to-problem-page:hover {
  text-decoration: underline;
}

/* ==========================================================================
   响应式折叠
   ========================================================================== */

@media (max-width: 900px) {
  .global-metric-strip, .overview-visuals-grid, .visuals-dual-grid {
    grid-template-columns: 1fr;
  }

  .ladder-tr {
    grid-template-columns: 1fr 100px 100px 120px;
  }

  .l-col-score:nth-child(4) {
    display: none;
  }

  .detail-summary-bar {
    grid-template-columns: repeat(2, 1fr);
  }

  .s-time-item {
    grid-column: 1 / 3;
    border-left: none;
    padding-left: 0;
  }

  .podium-strip {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .ranking-nav-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .nav-right {
    margin-left: 0;
    justify-content: space-between;
  }

  .compact-search-input {
    width: 100%;
  }

  .rt-tr {
    grid-template-columns: 46px minmax(130px, 1fr) 70px 80px;
    padding: 8px 12px;
  }

  .rt-col-workflow, .rt-col-time {
    display: none;
  }
}
</style>
