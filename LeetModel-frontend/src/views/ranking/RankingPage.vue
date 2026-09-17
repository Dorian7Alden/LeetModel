<template>
  <div class="ranking-workspace">
    <!-- ====================================================================
         1. 顶部工作台主控制栏 (Header Control Strip)
         ==================================================================== -->
    <header class="workspace-header-strip">
      <div class="header-left">
        <!-- 视图模式切换分段控制器 -->
        <div class="segmented-control" role="tablist">
          <button
            type="button"
            class="segment-item"
            :class="{ active: viewMode === 'overview' }"
            role="tab"
            :aria-selected="viewMode === 'overview'"
            @click="switchViewMode('overview')"
          >
            <el-icon><DataAnalysis /></el-icon>
            <span>赛题天梯总览</span>
          </button>
          <button
            type="button"
            class="segment-item"
            :class="{ active: viewMode === 'detail' }"
            role="tab"
            :aria-selected="viewMode === 'detail'"
            @click="switchViewMode('detail')"
          >
            <el-icon><Trophy /></el-icon>
            <span>单题成果细览</span>
          </button>
        </div>

        <!-- 细览模式：当前赛题胶囊 (点击唤起服务端按需筛选中心) -->
        <div v-if="viewMode === 'detail'" class="current-problem-pill-group">
          <button
            type="button"
            class="btn-trigger-picker"
            title="点击打开赛题可视化筛选中心 (按需检索海量题库)"
            @click="openProblemSelectorModal"
          >
            <span class="pill-code">#{{ currentProblemCode }}</span>
            <span class="pill-title">{{ currentProblemTitle }}</span>
            <el-icon class="pill-icon-switch"><Switch /></el-icon>
          </button>

          <!-- 临时看一眼题目详情 (带内存缓存的轻量抽屉，免跳出) -->
          <button
            type="button"
            class="btn-peek-drawer"
            title="免跳转侧边抽屉速览题面 (秒级缓存呈现)"
            @click="openProblemDrawer(selectedProblemId)"
          >
            <el-icon><View /></el-icon>
            <span>题面速览</span>
          </button>
        </div>

        <!-- 总览模式：可视化赛题筛选器入口 -->
        <button
          v-else
          type="button"
          class="btn-trigger-picker overview-picker-btn"
          @click="openProblemSelectorModal"
        >
          <el-icon><Filter /></el-icon>
          <span>多维赛题筛选器</span>
        </button>
      </div>

      <div class="header-right">
        <!-- 细览模式：队伍即时防抖搜索 -->
        <div v-if="viewMode === 'detail'" class="search-field-box">
          <el-input
            v-model="keyword"
            placeholder="搜索上榜队伍..."
            clearable
            size="small"
            class="search-input"
            @input="onSearchInputDebounced"
            @clear="onSearchClear"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <!-- 细览模式：我的队伍定位徽章 -->
        <button
          v-if="viewMode === 'detail' && myTeamInCurrentProblem"
          type="button"
          class="my-team-anchor-badge"
          :class="{ 'has-rank': myTeamRankingItem }"
          title="点击在下方榜单中定位我的队伍"
          @click="locateMyTeamAction"
        >
          <el-icon><UserFilled /></el-icon>
          <span class="my-team-name">{{ myTeamInCurrentProblem.name }}</span>
          <strong v-if="myTeamRankingItem" class="my-team-rank-val">
            #{{ myTeamRankingItem.rank }} ({{ formatScore(myTeamRankingItem.score) }}分)
          </strong>
          <span v-else class="my-team-pending-tag">实训中</span>
        </button>

        <!-- 刷新数据 -->
        <button
          type="button"
          class="btn-refresh-circle"
          :disabled="isAnyLoading"
          title="刷新数据"
          @click="handleManualRefresh"
        >
          <el-icon :class="{ 'spin-anim': isAnyLoading }"><Refresh /></el-icon>
        </button>
      </div>
    </header>

    <!-- 错误异常提示 -->
    <div v-if="rankingError" class="ranking-error-card">
      <el-icon class="error-warn-icon"><Warning /></el-icon>
      <div class="error-texts">
        <h4>数据加载受阻</h4>
        <p>{{ rankingError }}</p>
      </div>
      <button type="button" class="btn-error-retry" @click="handleManualRefresh">重新拉取</button>
    </div>

    <!-- ====================================================================
         2. 模式一：全平台赛题总览天梯 (Global Overview)
         ==================================================================== -->
    <main v-if="viewMode === 'overview'" class="overview-content">
      <!-- 2.1 全局数据指标大盘 -->
      <section class="overview-metrics-grid">
        <div class="stat-card">
          <div class="stat-icon-wrapper blue-grad">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-meta">
            <span class="stat-title">已纳入赛题</span>
            <div class="stat-number-row">
              <span class="stat-num">{{ globalStats?.problemCount || 0 }}</span>
              <span class="stat-unit">道</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper gold-grad">
            <el-icon><UserFilled /></el-icon>
          </div>
          <div class="stat-meta">
            <span class="stat-title">上榜参赛队伍</span>
            <div class="stat-number-row">
              <span class="stat-num">{{ globalStats?.rankedTeams || 0 }}</span>
              <span class="stat-unit">支</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper green-grad">
            <el-icon><Finished /></el-icon>
          </div>
          <div class="stat-meta">
            <span class="stat-title">完成最终评审</span>
            <div class="stat-number-row">
              <span class="stat-num">{{ globalStats?.reviewedSubmissions || 0 }}</span>
              <span class="stat-unit">份</span>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper amber-grad">
            <el-icon><Trophy /></el-icon>
          </div>
          <div class="stat-meta">
            <span class="stat-title">全平台平均得分</span>
            <div class="stat-number-row">
              <span class="stat-num stat-highlight">{{ formatScore(globalStats?.overallAverageScore) }}</span>
              <span class="stat-unit">分</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 2.2 全平台多维对比可视化看板 (图表渐进渲染) -->
      <section v-if="globalStats?.items?.length" class="overview-charts-grid">
        <div class="visual-card">
          <div class="visual-header">
            <el-icon class="vh-icon"><Histogram /></el-icon>
            <h4>各赛题队伍参与规模对比</h4>
          </div>
          <div class="h-bar-chart-container">
            <div
              v-for="item in topRankedProblemsByTeams"
              :key="`hbar-${item.problemId}`"
              class="h-bar-item"
              @click="enterDetail(item.problemId)"
            >
              <div class="h-bar-meta" :title="item.problemTitle">
                <span class="h-bar-code">#{{ item.problemCode || item.problemId }}</span>
                <span class="h-bar-title">{{ item.problemTitle }}</span>
              </div>
              <div class="h-bar-progress-track">
                <div
                  class="h-bar-fill-strip"
                  :style="{ width: `${getBarPercent(item.rankedTeamCount, maxGlobalRankedTeams)}%` }"
                ></div>
                <span class="h-bar-count-badge">{{ item.rankedTeamCount || 0 }} 支</span>
              </div>
            </div>
          </div>
        </div>

        <div class="visual-card">
          <div class="visual-header">
            <el-icon class="vh-icon"><TrendCharts /></el-icon>
            <h4>赛题得分竞技区间 (均分 ~ 最高分)</h4>
          </div>
          <div class="range-spectrum-container">
            <div
              v-for="item in topRankedProblemsByTeams"
              :key="`range-${item.problemId}`"
              class="spectrum-row"
              @click="enterDetail(item.problemId)"
            >
              <div class="spectrum-labels">
                <span class="sp-title">#{{ item.problemCode || item.problemId }} {{ item.problemTitle }}</span>
                <span class="sp-scores">
                  均分 <b>{{ formatScore(item.averageScore) }}</b> · 最高 <b>{{ formatScore(item.highestScore) }}</b>
                </span>
              </div>
              <div class="spectrum-track">
                <div
                  class="spectrum-span"
                  :style="{
                    left: `${Math.max(0, Math.min(Number(item.averageScore) || 0, 100))}%`,
                    width: `${Math.max(5, Math.min((Number(item.highestScore) || 0) - (Number(item.averageScore) || 0), 100))}%`
                  }"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 2.3 赛题天梯数据表 (带数据分页与即时多维排序) -->
      <section class="ladder-card-wrapper">
        <div class="ladder-toolbar">
          <div class="ladder-title-wrap">
            <h3>赛题竞赛天梯榜</h3>
            <span class="ladder-subtitle">共 {{ sortedGlobalProblems.length }} 道建模赛题</span>
          </div>

          <div class="ladder-actions">
            <!-- 快速筛选按钮 -->
            <button type="button" class="btn-open-filter-modal" @click="openProblemSelectorModal">
              <el-icon><Filter /></el-icon>
              <span>快速赛题筛选</span>
            </button>

            <!-- 排序切换 -->
            <div class="sort-segmented-group">
              <span class="sort-tag-label">排序:</span>
              <button
                type="button"
                class="sort-pill"
                :class="{ active: globalSortBy === 'teams' }"
                @click="globalSortBy = 'teams'"
              >
                队伍数
              </button>
              <button
                type="button"
                class="sort-pill"
                :class="{ active: globalSortBy === 'avgScore' }"
                @click="globalSortBy = 'avgScore'"
              >
                平均分
              </button>
              <button
                type="button"
                class="sort-pill"
                :class="{ active: globalSortBy === 'maxScore' }"
                @click="globalSortBy = 'maxScore'"
              >
                最高分
              </button>
            </div>
          </div>
        </div>

        <!-- 局部骨架加载态 -->
        <div v-if="loadingOverview" class="ladder-skeleton-box">
          <div v-for="i in 5" :key="`sk-lad-${i}`" class="sk-row-strip skeleton-pulse"></div>
        </div>

        <!-- 赛题天梯表格 -->
        <div v-else class="ladder-table-responsive">
          <table class="ladder-table">
            <thead>
              <tr>
                <th class="col-ladder-prob">赛题信息</th>
                <th class="col-ladder-teams">上榜队伍</th>
                <th class="col-ladder-max">最高得分</th>
                <th class="col-ladder-avg">平均得分</th>
                <th class="col-ladder-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in paginatedGlobalProblems"
                :key="`ladder-${item.problemId}`"
                class="ladder-row"
                @click="enterDetail(item.problemId)"
              >
                <td class="col-ladder-prob">
                  <div class="prob-title-cell">
                    <span class="badge-code">#{{ item.problemCode || item.problemId }}</span>
                    <span class="text-title" :title="item.problemTitle">{{ item.problemTitle }}</span>
                  </div>
                </td>
                <td class="col-ladder-teams">
                  <span class="team-count-text">
                    <strong>{{ item.rankedTeamCount || 0 }}</strong> 支队伍
                  </span>
                </td>
                <td class="col-ladder-max">
                  <span class="score-gold-pill">{{ formatScore(item.highestScore) }} 分</span>
                </td>
                <td class="col-ladder-avg">
                  <span class="score-plain-pill">{{ formatScore(item.averageScore) }} 分</span>
                </td>
                <td class="col-ladder-actions" @click.stop>
                  <div class="action-btn-cluster">
                    <button
                      type="button"
                      class="btn-mini-preview"
                      title="免跳转侧边抽屉速览"
                      @click="openProblemDrawer(item.problemId)"
                    >
                      <el-icon><View /></el-icon>
                      <span>速览</span>
                    </button>
                    <button
                      type="button"
                      class="btn-mini-enter"
                      @click="enterDetail(item.problemId)"
                    >
                      <span>细览</span>
                      <el-icon><ArrowRight /></el-icon>
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 天梯列表底部分页器 -->
        <div v-if="sortedGlobalProblems.length > globalPageSize" class="ladder-pagination-bar">
          <span class="pagination-info">
            显示第 {{ (globalPage - 1) * globalPageSize + 1 }}–{{ Math.min(globalPage * globalPageSize, sortedGlobalProblems.length) }} 条，共 {{ sortedGlobalProblems.length }} 道题目
          </span>
          <el-pagination
            v-model:current-page="globalPage"
            v-model:page-size="globalPageSize"
            :total="sortedGlobalProblems.length"
            :page-sizes="[10, 20, 50]"
            layout="sizes, prev, pager, next"
            background
          />
        </div>
      </section>
    </main>

    <!-- ====================================================================
         3. 模式二：单题成果细览 (Problem Detail Ranking)
         ==================================================================== -->
    <main v-else class="detail-content">
      <!-- 3.1 赛题核心指标横条 -->
      <section class="detail-summary-strip">
        <div class="summary-metric">
          <span class="sm-label">上榜参赛队伍</span>
          <div class="sm-val-box">
            <strong class="sm-num">{{ rankingItems.length }}</strong>
            <span class="sm-unit">支</span>
          </div>
        </div>
        <div class="summary-metric">
          <span class="sm-label">全榜最高得分</span>
          <div class="sm-val-box">
            <strong class="sm-num score-highlight-gold">{{ highestScore }}</strong>
            <span v-if="highestScore !== '-'" class="sm-unit">分</span>
          </div>
        </div>
        <div class="summary-metric">
          <span class="sm-label">平均评审得分</span>
          <div class="sm-val-box">
            <strong class="sm-num">{{ averageScore }}</strong>
            <span v-if="averageScore !== '-'" class="sm-unit">分</span>
          </div>
        </div>
        <div class="summary-metric">
          <span class="sm-label">前25%高分线 (P75)</span>
          <div class="sm-val-box">
            <strong class="sm-num">{{ p75Score }}</strong>
            <span v-if="p75Score !== '-'" class="sm-unit">分</span>
          </div>
        </div>
        <div class="summary-metric time-metric-box">
          <span class="sm-label">快照生成时间</span>
          <span class="sm-time-text">{{ formatSnapshotTime(overview?.computedAt) }}</span>
        </div>
      </section>

      <!-- 细览主榜单局部骨架加载态 -->
      <div v-if="loadingRankTable" class="detail-skeleton-box">
        <div class="sk-podium-row skeleton-pulse"></div>
        <div class="sk-table-box skeleton-pulse"></div>
      </div>

      <!-- 3.2 若有上榜队伍：主次分明优先呈现榜单 -->
      <template v-else-if="rankingItems.length">
        <!-- 3.2.1 Top 3 荣誉领奖台 -->
        <section v-if="!hasActiveKeyword && podiumItems.length" class="podium-card-deck" aria-label="荣誉前三名领奖台">
          <div
            v-for="item in podiumItems"
            :key="`podium-${item.teamId}`"
            class="podium-card-unit"
            :class="[`rank-tier-${item.rank}`, { 'is-my-team-podium': isMyTeam(item.teamId) }]"
            @click="scrollToTeamRow(item.teamId)"
          >
            <div class="podium-card-top">
              <span class="tier-medal-tag">{{ item.rank === 1 ? '🥇 冠军' : item.rank === 2 ? '🥈 亚军' : '🥉 季军' }}</span>
              <span class="tier-rank-number">#{{ item.rank }}</span>
              <span v-if="isMyTeam(item.teamId)" class="my-team-flag">我的队伍</span>
            </div>

            <div class="podium-card-middle">
              <div class="podium-avatar-circle">
                {{ teamInitial(item.teamName) }}
              </div>
              <h4 class="podium-team-title" :title="item.teamName">{{ item.teamName }}</h4>
              <div class="podium-score-display">
                <span class="score-large">{{ formatScore(item.score) }}</span>
                <span class="score-unit-small">分</span>
              </div>
            </div>

            <div class="podium-card-bottom">
              <span class="wf-version-tag">{{ workflowLabel(item.workflowVersion) }}</span>
              <span class="submit-time-text">{{ formatDate(item.submittedAt) }}</span>
            </div>
          </div>
        </section>

        <!-- 3.2.2 核心成果排名数据表格 (支持客户端数据分页) -->
        <section class="ranking-table-board">
          <div class="board-caption-bar">
            <div class="caption-left">
              <h3>成果排名表</h3>
              <span class="badge-total-count">共 {{ filteredRankingItems.length }} 支队伍</span>
              <span v-if="hasActiveKeyword" class="badge-filter-notice">筛选 “{{ appliedKeyword }}”</span>
            </div>
            <div class="caption-right">
              <span class="sorting-rule-text">口径：最终稿 AI 评审客观综合得分</span>
            </div>
          </div>

          <div class="board-table-responsive">
            <table class="board-table">
              <thead>
                <tr>
                  <th class="col-th-rank">排名</th>
                  <th class="col-th-team">参赛队伍</th>
                  <th class="col-th-score">最终得分</th>
                  <th class="col-th-wf">AI 评审版本</th>
                  <th class="col-th-time">最终稿提交时间</th>
                  <th class="col-th-action">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in paginatedRankingItems"
                  :id="`team-row-${item.teamId}`"
                  :key="`row-${item.teamId}`"
                  class="board-row"
                  :class="{
                    'is-my-team-row': isMyTeam(item.teamId),
                    'row-highlighted': highlightedTeamId === item.teamId
                  }"
                >
                  <td class="col-td-rank">
                    <span class="rank-badge-box" :class="`rank-val-${item.rank}`">
                      {{ item.rank }}
                    </span>
                  </td>
                  <td class="col-td-team">
                    <div class="team-cell-wrap">
                      <span class="team-avatar-mini" :class="`av-rank-${item.rank <= 3 ? item.rank : 'normal'}`">
                        {{ teamInitial(item.teamName) }}
                      </span>
                      <strong class="team-name-text" :title="item.teamName">{{ item.teamName }}</strong>
                      <span v-if="isMyTeam(item.teamId)" class="my-team-pill-badge">我的队伍</span>
                    </div>
                  </td>
                  <td class="col-td-score">
                    <div class="score-cell-flex">
                      <strong class="score-emphasis">{{ formatScore(item.score) }}</strong>
                      <span class="unit-text">分</span>
                    </div>
                  </td>
                  <td class="col-td-wf">
                    <span class="wf-pill-badge">{{ workflowLabel(item.workflowVersion) }}</span>
                  </td>
                  <td class="col-td-time">
                    <span class="time-stamp-text">{{ formatDate(item.submittedAt) }}</span>
                  </td>
                  <td class="col-td-action">
                    <div class="row-actions-group">
                      <button
                        type="button"
                        class="btn-table-opt"
                        title="复制队伍名称"
                        @click.stop="copyTeamName(item.teamName)"
                      >
                        <el-icon><CopyDocument /></el-icon>
                        <span>复制</span>
                      </button>
                      <router-link
                        v-if="item.teamId"
                        :to="`/team/detail/${item.teamId}`"
                        class="btn-table-opt"
                        title="查看队伍工作台"
                      >
                        <span>队伍</span>
                      </router-link>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 细览榜单底部分页控制条 -->
          <div v-if="filteredRankingItems.length > detailPageSize" class="board-pagination-bar">
            <span class="pagination-info">
              显示第 {{ (detailPage - 1) * detailPageSize + 1 }}–{{ Math.min(detailPage * detailPageSize, filteredRankingItems.length) }} 条，共 {{ filteredRankingItems.length }} 支队伍
            </span>
            <el-pagination
              v-model:current-page="detailPage"
              v-model:page-size="detailPageSize"
              :total="filteredRankingItems.length"
              :page-sizes="[10, 20, 50]"
              layout="sizes, prev, pager, next"
              background
            />
          </div>
        </section>

        <!-- 3.2.3 赛题多维数据分析看板 (渐进式懒加载，不阻断核心榜单首屏) -->
        <section class="detail-analytics-deck">
          <div class="deck-header">
            <el-icon><DataAnalysis /></el-icon>
            <h3>赛题成果多维数据分析</h3>
            <span v-if="loadingAnalytics" class="loading-tag-inline">
              <el-icon class="spin-anim"><Refresh /></el-icon>
              <span>正在计算分布样本...</span>
            </span>
          </div>

          <!-- 分析图表局部骨架 -->
          <div v-if="loadingAnalytics" class="deck-skeleton-row">
            <div class="sk-deck-card skeleton-pulse"></div>
            <div class="sk-deck-card skeleton-pulse"></div>
          </div>

          <div v-else class="deck-cards-grid">
            <!-- 分析图表 A: 分数梯队直方图 (Histogram) -->
            <div class="analytics-card">
              <div class="ac-head">
                <h4>分数梯队直方分布 (0–100 分)</h4>
                <span class="ac-sub">统计 {{ totalDistributionTeams }} 支作品</span>
              </div>
              <div v-if="distributionBuckets.length" class="histogram-viewport">
                <div class="histogram-bars-strip">
                  <div
                    v-for="(bucket, idx) in distributionBuckets"
                    :key="`b-${idx}`"
                    class="hist-col-group"
                    :class="{ 'is-my-score-tier': isTeamInBucket(bucket) }"
                  >
                    <div class="hist-popover">
                      <div class="hp-tier-name">{{ bucket.label }}</div>
                      <div class="hp-count">{{ bucket.count }} 支队伍 ({{ bucket.percentage }}%)</div>
                      <div v-if="isTeamInBucket(bucket)" class="hp-my-mark">★ 我的队伍在此区间</div>
                    </div>
                    <div class="hist-bar-outer">
                      <span v-if="bucket.count > 0" class="hist-val-top">{{ bucket.count }}</span>
                      <div
                        class="hist-bar-core"
                        :style="{ height: `${Math.max(bucket.heightRatio * 100, bucket.count > 0 ? 8 : 2)}%` }"
                      >
                        <div v-if="isTeamInBucket(bucket)" class="my-score-pip"></div>
                      </div>
                    </div>
                    <span class="hist-x-mark">{{ bucket.shortLabel }}</span>
                  </div>
                </div>
              </div>
              <div v-else class="empty-vis-notice">暂无足够样本生成分布直方图</div>
            </div>

            <!-- 分析图表 B: 竞技实力层级构成 (Donut Breakdown) -->
            <div class="analytics-card">
              <div class="ac-head">
                <h4>竞技实力层级构成</h4>
                <span class="ac-sub">全榜正态分层比例</span>
              </div>
              <div v-if="tierBreakdownList.length" class="donut-chart-flex">
                <div class="donut-svg-wrapper">
                  <svg viewBox="0 0 120 120" class="donut-svg-element">
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
                  <div class="donut-center-badge">
                    <strong>{{ totalDistributionTeams }}</strong>
                    <small>作品样本</small>
                  </div>
                </div>

                <div class="donut-legend-grid">
                  <div
                    v-for="tier in tierBreakdownList"
                    :key="tier.name"
                    class="legend-row-item"
                  >
                    <span class="legend-color-dot" :style="{ background: tier.color }"></span>
                    <span class="legend-tier-name">{{ tier.name }}</span>
                    <span class="legend-count-val">{{ tier.count }} 支</span>
                    <span class="legend-ratio-val">{{ tier.percentage }}%</span>
                  </div>
                </div>
              </div>
              <div v-else class="empty-vis-notice">暂无足够样本生成构成分析图</div>
            </div>
          </div>
        </section>
      </template>

      <!-- 3.3 细览空状态：搜索无结果 -->
      <section v-else-if="hasActiveKeyword && !loadingRankTable" class="empty-state-card">
        <el-icon class="empty-watermark-icon"><Search /></el-icon>
        <h4>未找到匹配队伍</h4>
        <p>在当前赛题下，未匹配到包含 “{{ appliedKeyword }}” 的上榜队伍。</p>
        <button type="button" class="btn-clear-empty" @click="onSearchClear">清空搜索条件</button>
      </section>

      <!-- 3.4 细览空状态：题目暂无队伍上榜 -->
      <section v-else-if="!loadingRankTable" class="empty-state-card">
        <el-icon class="empty-watermark-icon"><Trophy /></el-icon>
        <h4>该赛题尚无队伍上榜</h4>
        <p>参赛队伍完成建模学术作品提交并通过系统 AI 评审后，榜单将自动计算呈现。</p>
        <button type="button" class="btn-clear-empty" @click="openProblemDrawer(selectedProblemId)">
          查看本题详情与建模要求
        </button>
      </section>
    </main>

    <!-- ====================================================================
         4. 模态窗口：海量赛题服务端按需分页与防抖懒加载筛选中心
         ==================================================================== -->
    <el-dialog
      v-model="pickerModalVisible"
      title="赛题可视化筛选中心"
      width="840px"
      destroy-on-close
      class="problem-picker-dialog"
    >
      <div class="picker-dialog-body">
        <!-- 检索框与重置快捷项 -->
        <div class="picker-search-bar">
          <el-input
            v-model="pickerKeyword"
            placeholder="输入题号、赛题关键词即时按需检索..."
            clearable
            @input="onPickerSearchInputDebounced"
            @clear="fetchPickerProblems(true)"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <button
            v-if="hasActiveProblemFilters"
            type="button"
            class="btn-reset-filters"
            @click="resetPickerFilters"
          >
            <el-icon><Refresh /></el-icon>
            <span>重置筛选</span>
          </button>
        </div>

        <!-- 多维可视化矩阵胶囊 (Filter Matrix Chips) -->
        <div class="picker-matrix-filters">
          <!-- 赛事体系 -->
          <div class="matrix-filter-row">
            <span class="mf-label">赛事体系:</span>
            <div class="mf-chips-scroll">
              <button
                type="button"
                class="mf-chip"
                :class="{ active: pickerContestId === null }"
                @click="setPickerContest(null)"
              >
                全部赛事
              </button>
              <button
                v-for="contest in availableContests"
                :key="`c-${contest.id}`"
                type="button"
                class="mf-chip"
                :class="{ active: pickerContestId === contest.id }"
                @click="setPickerContest(contest.id)"
              >
                {{ contest.name }}
              </button>
            </div>
          </div>

          <!-- 年份筛选 -->
          <div class="matrix-filter-row">
            <span class="mf-label">赛题年份:</span>
            <div class="mf-chips-scroll">
              <button
                type="button"
                class="mf-chip"
                :class="{ active: pickerYear === null }"
                @click="setPickerYear(null)"
              >
                全部年份
              </button>
              <button
                v-for="year in availableYears"
                :key="`y-${year}`"
                type="button"
                class="mf-chip"
                :class="{ active: pickerYear === year }"
                @click="setPickerYear(year)"
              >
                {{ year }} 年
              </button>
            </div>
          </div>

          <!-- 难度梯度 -->
          <div class="matrix-filter-row">
            <span class="mf-label">难度梯度:</span>
            <div class="mf-chips-scroll">
              <button
                type="button"
                class="mf-chip"
                :class="{ active: pickerDifficulty === null }"
                @click="setPickerDifficulty(null)"
              >
                全部难度
              </button>
              <button
                type="button"
                class="mf-chip diff-1-chip"
                :class="{ active: pickerDifficulty === 1 }"
                @click="setPickerDifficulty(1)"
              >
                简单 (入门友好)
              </button>
              <button
                type="button"
                class="mf-chip diff-2-chip"
                :class="{ active: pickerDifficulty === 2 }"
                @click="setPickerDifficulty(2)"
              >
                中等 (标准挑战)
              </button>
              <button
                type="button"
                class="mf-chip diff-3-chip"
                :class="{ active: pickerDifficulty === 3 }"
                @click="setPickerDifficulty(3)"
              >
                困难 (综合深水)
              </button>
            </div>
          </div>
        </div>

        <!-- 匹配的赛题卡片列表与服务端分页 -->
        <div class="picker-results-section">
          <div class="results-header">
            <span class="results-count-text">
              服务端匹配到 <strong>{{ pickerTotal }}</strong> 道赛题
            </span>
            <span v-if="loadingPicker" class="loading-tag-inline">
              <el-icon class="spin-anim"><Refresh /></el-icon>
              <span>正在拉取最新切片...</span>
            </span>
          </div>

          <!-- 弹窗卡片骨架 -->
          <div v-if="loadingPicker && !pickerProblems.length" class="picker-skeleton-grid">
            <div v-for="i in 6" :key="`sk-pp-${i}`" class="sk-picker-card skeleton-pulse"></div>
          </div>

          <!-- 真实服务端分页赛题卡片流 -->
          <div v-else-if="pickerProblems.length" class="picker-cards-grid">
            <div
              v-for="prob in pickerProblems"
              :key="`pick-${prob.id}`"
              class="picker-problem-card"
              :class="{ 'is-active-problem': String(prob.id) === String(selectedProblemId) }"
              @click="chooseProblemFromPicker(prob)"
            >
              <div class="pp-card-head">
                <span class="pp-code">#{{ prob.code || prob.id }}</span>
                <span class="pp-tag contest">{{ prob.contestName || '数模赛题' }}</span>
                <span v-if="prob.year" class="pp-tag">{{ prob.year }}</span>
                <span class="pp-tag" :class="`diff-${prob.difficulty}`">{{ difficultyLabel(prob.difficulty) }}</span>
              </div>
              <h5 class="pp-title" :title="prob.title">{{ prob.title }}</h5>
              <div class="pp-card-footer">
                <span class="pp-teams-hint">
                  <strong>{{ getProblemTeamCount(prob.id) }}</strong> 支队伍上榜
                </span>
                <div class="pp-actions" @click.stop>
                  <button
                    type="button"
                    class="btn-pp-peek"
                    title="免跳转速览"
                    @click="openProblemDrawer(prob.id)"
                  >
                    <el-icon><View /></el-icon>
                    <span>速览</span>
                  </button>
                  <button
                    type="button"
                    class="btn-pp-select"
                    @click="chooseProblemFromPicker(prob)"
                  >
                    <span>进入细览</span>
                    <el-icon><ArrowRight /></el-icon>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="picker-empty-results">
            <el-icon><Search /></el-icon>
            <p>未检索到符合条件的赛题，请调整筛选条件或重置。</p>
          </div>

          <!-- 弹窗内的服务端分页器 -->
          <div v-if="pickerTotal > pickerPageSize" class="picker-pagination-wrap">
            <el-pagination
              v-model:current-page="pickerPage"
              :page-size="pickerPageSize"
              :total="pickerTotal"
              layout="prev, pager, next"
              background
              @current-change="handlePickerPageChange"
            />
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- ====================================================================
         5. 侧边抽屉：临时看一眼题目详情 (带前端内存级 LRU 缓存，免跳出)
         ==================================================================== -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerProblem ? `#${drawerProblem.code || drawerProblem.id} · ${drawerProblem.title}` : '赛题速览'"
      direction="rtl"
      size="560px"
      destroy-on-close
      class="quick-peek-drawer"
    >
      <div v-if="loadingDrawer" class="drawer-loading-center">
        <el-icon class="spin-anim"><Refresh /></el-icon>
        <span>正在加载题面详情...</span>
      </div>
      <div v-else-if="drawerProblem" class="drawer-inner-content">
        <div class="drawer-meta-badges">
          <span class="dm-tag contest">{{ drawerProblem.contestName || '建模公开赛题' }}</span>
          <span v-if="drawerProblem.year" class="dm-tag">{{ drawerProblem.year }} 年</span>
          <span class="dm-tag">{{ languageLabel(drawerProblem.statementLanguage) }}</span>
          <span class="dm-tag" :class="`diff-${drawerProblem.difficulty}`">
            {{ difficultyLabel(drawerProblem.difficulty) }}
          </span>
        </div>

        <div class="drawer-markdown-render" v-html="drawerMarkdownHtml"></div>

        <div class="drawer-bottom-bar">
          <router-link :to="`/problem/${drawerProblem.id}`" class="link-open-full">
            <span>在做题工作台打开完整页面</span>
            <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  CopyDocument,
  DataAnalysis,
  Document,
  Filter,
  Finished,
  Histogram,
  Refresh,
  Search,
  Switch,
  TrendCharts,
  Trophy,
  UserFilled,
  View,
  Warning
} from '@element-plus/icons-vue'
import { getPublicProblemDetail, getPublicProblemFilterOptions, getPublicProblemList } from '@/api/problem'
import { getGlobalRankingStats, getProblemScoreDistribution, getRanking } from '@/api/ranking'
import { getAllMyTeams } from '@/api/team'
import { useUserStore } from '@/store/user'
import { renderSafeMarkdown } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 视图模式: 'overview' (全平台总览天梯) | 'detail' (单题成果细览)
const viewMode = ref('overview')

// 细粒度异步加载控制状态 (杜绝一刀切白屏)
const loadingOverview = ref(false)
const loadingRankTable = ref(false)
const loadingAnalytics = ref(false)
const loadingPicker = ref(false)
const loadingDrawer = ref(false)

const isAnyLoading = computed(() =>
  loadingOverview.value || loadingRankTable.value || loadingAnalytics.value
)

// 数据状态
const problems = ref([]) // 赛题基础池
const currentProblemMeta = ref(null) // 当前细览赛题对象
const filterOptions = ref(null)
const selectedProblemId = ref(null)
const keyword = ref('')
const appliedKeyword = ref('')
const overview = ref(null)
const distribution = ref(null)
const globalStats = ref(null)
const rankingError = ref('')
const myTeams = ref([])
const highlightedTeamId = ref(null)

// SWR 本地会话缓存 Key
const CACHE_KEY_GLOBAL_STATS = 'leetmodel_ranking_global_stats_cache'

// 内存级缓存字典：题目详情缓存，二次打开免重复网络请求
const problemDetailCache = new Map()

// 排序与客户端分页 (总览 & 细览)
const globalSortBy = ref('teams')
const globalPage = ref(1)
const globalPageSize = ref(10)
const detailPage = ref(1)
const detailPageSize = ref(10)

// 模态筛选中心 (Server-side Paginated Lazy Load) 状态
const pickerModalVisible = ref(false)
const pickerKeyword = ref('')
const pickerContestId = ref(null)
const pickerYear = ref(null)
const pickerDifficulty = ref(null)
const pickerPage = ref(1)
const pickerPageSize = ref(6)
const pickerProblems = ref([])
const pickerTotal = ref(0)
let pickerSearchTimer = null
let teamSearchTimer = null

// 抽屉临时速览状态
const drawerVisible = ref(false)
const drawerProblem = ref(null)
const drawerMarkdownHtml = ref('')

// 计算属性：当前选中赛题
const currentProblemCode = computed(() => {
  if (currentProblemMeta.value?.code) return currentProblemMeta.value.code
  const found = problems.value.find((it) => String(it.id) === String(selectedProblemId.value))
  return found?.code || selectedProblemId.value || '?'
})

const currentProblemTitle = computed(() => {
  if (currentProblemMeta.value?.title) return currentProblemMeta.value.title
  const found = problems.value.find((it) => String(it.id) === String(selectedProblemId.value))
  return found?.title || '点击选择赛题'
})

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
    return [top[1], top[0], top[2]]
  }
  return top
})

// 队伍模糊筛选后结果
const filteredRankingItems = computed(() => {
  if (!hasActiveKeyword.value) return rankingItems.value
  const kw = appliedKeyword.value.toLowerCase()
  return rankingItems.value.filter((item) => String(item.teamName || '').toLowerCase().includes(kw))
})

// 细览榜单分页切片
const paginatedRankingItems = computed(() => {
  const start = (detailPage.value - 1) * detailPageSize.value
  return filteredRankingItems.value.slice(start, start + detailPageSize.value)
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

// 全局赛题排序与分页
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

const paginatedGlobalProblems = computed(() => {
  const start = (globalPage.value - 1) * globalPageSize.value
  return sortedGlobalProblems.value.slice(start, start + globalPageSize.value)
})

const topRankedProblemsByTeams = computed(() => {
  return [...(globalStats.value?.items || [])].sort(
    (a, b) => (b.rankedTeamCount || 0) - (a.rankedTeamCount || 0)
  ).slice(0, 5)
})

const maxGlobalRankedTeams = computed(() => {
  const counts = (globalStats.value?.items || []).map((it) => it.rankedTeamCount || 0)
  return Math.max(...counts, 1)
})

function getBarPercent(val, max) {
  if (!max || !val) return 0
  return Math.round((val / max) * 100)
}

function getProblemTeamCount(probId) {
  const item = (globalStats.value?.items || []).find((it) => String(it.problemId) === String(probId))
  return item?.rankedTeamCount || 0
}

// 可视化筛选中心筛选项提取
const availableContests = computed(() => {
  if (filterOptions.value?.contests?.length) {
    return filterOptions.value.contests
  }
  const map = new Map()
  for (const p of problems.value) {
    if (p.contestId && p.contestName && !map.has(p.contestId)) {
      map.set(p.contestId, { id: p.contestId, name: p.contestName })
    }
  }
  return Array.from(map.values())
})

const availableYears = computed(() => {
  const years = [2026, 2025, 2024, 2023, 2022, 2021]
  for (const p of problems.value) {
    if (p.year && !years.includes(p.year)) years.push(p.year)
  }
  return years.sort((a, b) => b - a)
})

const hasActiveProblemFilters = computed(() => {
  return Boolean(
    pickerKeyword.value.trim() ||
    pickerContestId.value !== null ||
    pickerYear.value !== null ||
    pickerDifficulty.value !== null
  )
})

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
    '95-100': '#eab308',
    '90-94': '#3b82f6',
    '85-89': '#10b981',
    '80-84': '#6366f1',
    '70-79': '#94a3b8',
    '60-69': '#cbd5e1',
    '<60': '#f87171'
  }

  return distributionBuckets.value.map((b) => ({
    name: b.shortLabel,
    count: b.count,
    percentage: b.percentage,
    color: colors[b.shortLabel] || '#94a3b8'
  }))
})

const donutSegments = computed(() => {
  const list = tierBreakdownList.value
  if (!list.length) return []
  const circumference = 2 * Math.PI * 45
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

// 格式化辅助
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
  if (ver === 'DEEP_EVIDENCE_REVIEW_V4') return '专业证据评审 V4'
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

async function copyTeamName(name) {
  if (!name) return
  try {
    await navigator.clipboard.writeText(name)
    ElMessage.success(`队伍 “${name}” 已复制`)
  } catch {
    ElMessage.info(`队伍名称：${name}`)
  }
}

// 细览队伍搜索防抖
function onSearchInputDebounced() {
  clearTimeout(teamSearchTimer)
  teamSearchTimer = setTimeout(() => {
    appliedKeyword.value = keyword.value.trim()
    detailPage.value = 1
  }, 250)
}

function onSearchClear() {
  keyword.value = ''
  appliedKeyword.value = ''
  detailPage.value = 1
}

// 跨页平滑定位高亮
function scrollToTeamRow(teamId) {
  const targetIndex = filteredRankingItems.value.findIndex((it) => String(it.teamId) === String(teamId))
  if (targetIndex !== -1) {
    const targetPage = Math.floor(targetIndex / detailPageSize.value) + 1
    if (detailPage.value !== targetPage) {
      detailPage.value = targetPage
    }
  }

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
    if (hasActiveKeyword.value) onSearchClear()
    scrollToTeamRow(myTeamRankingItem.value.teamId)
  } else {
    ElMessage.info(`队伍 “${myTeamInCurrentProblem.value.name}” 正在实训中，尚未完成最终稿评审`)
  }
}

// 免跳转题面速览抽屉 (内存缓存优先，杜绝重复网络请求)
async function openProblemDrawer(problemId) {
  if (!problemId) return
  drawerVisible.value = true

  // 1. 检查内存缓存
  if (problemDetailCache.has(problemId)) {
    const cached = problemDetailCache.get(problemId)
    drawerProblem.value = cached
    drawerMarkdownHtml.value = cached._html
    loadingDrawer.value = false
    return
  }

  // 2. 无缓存时异步拉取
  loadingDrawer.value = true
  drawerProblem.value = null
  drawerMarkdownHtml.value = ''
  try {
    const res = await getPublicProblemDetail(problemId)
    const data = res.data
    const html = renderSafeMarkdown(data?.contentMarkdown || '暂无题目说明')
    data._html = html
    problemDetailCache.set(problemId, data)
    drawerProblem.value = data
    drawerMarkdownHtml.value = html
  } catch (error) {
    ElMessage.error(error.message || '赛题内容加载失败')
  } finally {
    loadingDrawer.value = false
  }
}

// ==========================================================================
// 4. 赛题筛选中心：纯服务端按需懒加载 (Server-side Paginated Lazy Query)
// ==========================================================================
function openProblemSelectorModal() {
  pickerModalVisible.value = true
  if (!filterOptions.value) {
    getPublicProblemFilterOptions()
      .then((res) => {
        filterOptions.value = res.data
      })
      .catch(() => {})
  }
  // 仅在首次打开或列表为空时触发服务端按需拉取
  if (!pickerProblems.value.length) {
    fetchPickerProblems(true)
  }
}

function onPickerSearchInputDebounced() {
  clearTimeout(pickerSearchTimer)
  pickerSearchTimer = setTimeout(() => {
    fetchPickerProblems(true)
  }, 300)
}

function setPickerContest(id) {
  if (pickerContestId.value === id) return
  pickerContestId.value = id
  fetchPickerProblems(true)
}

function setPickerYear(year) {
  if (pickerYear.value === year) return
  pickerYear.value = year
  fetchPickerProblems(true)
}

function setPickerDifficulty(diff) {
  if (pickerDifficulty.value === diff) return
  pickerDifficulty.value = diff
  fetchPickerProblems(true)
}

function resetPickerFilters() {
  pickerKeyword.value = ''
  pickerContestId.value = null
  pickerYear.value = null
  pickerDifficulty.value = null
  fetchPickerProblems(true)
}

function handlePickerPageChange(page) {
  pickerPage.value = page
  fetchPickerProblems(false)
}

// 服务端按需分页请求：绝不一次性拉取整个题库
async function fetchPickerProblems(resetPage = false) {
  if (resetPage) pickerPage.value = 1
  loadingPicker.value = true

  const params = {
    page: pickerPage.value,
    pageSize: pickerPageSize.value
  }
  if (pickerKeyword.value.trim()) params.keyword = pickerKeyword.value.trim()
  if (pickerContestId.value !== null) params.contestId = pickerContestId.value
  if (pickerYear.value !== null) params.year = pickerYear.value
  if (pickerDifficulty.value !== null) params.difficulty = pickerDifficulty.value

  try {
    const res = await getPublicProblemList(params)
    pickerProblems.value = res.data?.rows || []
    pickerTotal.value = res.data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '检索赛题失败')
    pickerProblems.value = []
    pickerTotal.value = 0
  } finally {
    loadingPicker.value = false
  }
}

function chooseProblemFromPicker(prob) {
  pickerModalVisible.value = false
  currentProblemMeta.value = prob
  enterDetail(prob.id)
}

// ==========================================================================
// 模式与数据生命周期控制
// ==========================================================================
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
      loadDetailData(selectedProblemId.value)
    }
  }
}

function enterDetail(problemId) {
  selectedProblemId.value = problemId
  viewMode.value = 'detail'
  detailPage.value = 1
  router.replace({ query: { problemId: String(problemId) } })
  loadDetailData(problemId)
}

// 加载赛题基础简明元数据 (轻量首屏分页，不加载大内容)
async function loadProblemsMeta() {
  try {
    const [listRes, filterRes] = await Promise.all([
      getPublicProblemList({ page: 1, pageSize: 20 }), // 只拉取前20道用于细览快速回落，不拉200道
      getPublicProblemFilterOptions().catch(() => null)
    ])
    problems.value = listRes.data?.rows || []
    filterOptions.value = filterRes?.data || null
  } catch {
    // 允许静默失败
  }
}

// 总览数据懒加载：仅加载全局指标，不拉取单题数据
async function loadGlobalStats() {
  // 1. 优先使用 SWR 缓存瞬时上屏 (0ms 渲染)
  if (!globalStats.value) {
    try {
      const cached = sessionStorage.getItem(CACHE_KEY_GLOBAL_STATS)
      if (cached) {
        globalStats.value = JSON.parse(cached)
      }
    } catch {
      // 静默忽略解析异常
    }
  }

  if (!globalStats.value) {
    loadingOverview.value = true
  }
  rankingError.value = ''
  try {
    const res = await getGlobalRankingStats()
    globalStats.value = res.data
    try {
      sessionStorage.setItem(CACHE_KEY_GLOBAL_STATS, JSON.stringify(res.data))
    } catch {
      // 静默忽略存储限制
    }
  } catch {
    if (!globalStats.value) {
      globalStats.value = {
        problemCount: 0,
        rankedTeams: 0,
        reviewedSubmissions: 0,
        overallAverageScore: null,
        items: []
      }
    }
  } finally {
    loadingOverview.value = false
  }
}

// 细览数据按需与渐进式加载 (Progressive Loading)
async function loadDetailData(problemId) {
  if (!problemId) return
  rankingError.value = ''
  detailPage.value = 1

  // 阶段 1: 优先秒级出榜单表格与队伍状态
  loadingRankTable.value = true
  const p1 = getRanking(problemId, '')
    .then((res) => {
      overview.value = res.data
      appliedKeyword.value = ''
    })
    .catch((err) => {
      rankingError.value = err.message || '排行榜加载失败'
      overview.value = null
    })
    .finally(() => {
      loadingRankTable.value = false
    })

  // 我的队伍状态并发同步
  loadMyTeams()

  // 阶段 2: 渐进式异步拉取多维分析分布 (不阻断核心榜单首屏显示)
  loadingAnalytics.value = true
  const p2 = getProblemScoreDistribution(problemId)
    .then((res) => {
      distribution.value = res.data
    })
    .catch(() => {
      distribution.value = null
    })
    .finally(() => {
      loadingAnalytics.value = false
    })

  await Promise.all([p1, p2])
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

async function handleManualRefresh() {
  if (viewMode.value === 'overview') {
    await loadGlobalStats()
  } else if (selectedProblemId.value) {
    await loadDetailData(selectedProblemId.value)
  }
  ElMessage.success('最新数据已同步')
}

// 路由监听
watch(
  () => route.query.problemId,
  (newId) => {
    if (newId) {
      if (String(newId) !== String(selectedProblemId.value) || viewMode.value !== 'detail') {
        selectedProblemId.value = Number(newId) || newId
        viewMode.value = 'detail'
        loadDetailData(selectedProblemId.value)
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
  const qProblemId = route.query.problemId

  if (qProblemId) {
    selectedProblemId.value = Number(qProblemId) || qProblemId
    viewMode.value = 'detail'
    loadDetailData(selectedProblemId.value)
  } else {
    viewMode.value = 'overview'
    loadGlobalStats()
  }
})

onUnmounted(() => {
  clearTimeout(pickerSearchTimer)
  clearTimeout(teamSearchTimer)
})
</script>

<style scoped>
/* ==========================================================================
   LeetModel 成果榜单 - 顶级设计感、渐进式懒加载学术工作台
   ========================================================================== */

.ranking-workspace {
  width: min(100%, 1180px);
  margin: 0 auto;
  padding: 4px 16px 48px;
  color: var(--lm-text-primary);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ==========================================================================
   1. 顶部工作台主控制栏
   ========================================================================== */
.workspace-header-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 18px;
  background: var(--lm-surface);
  border: 1px solid var(--lm-border);
  border-radius: var(--lm-radius);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 12px rgba(0, 0, 0, 0.02);
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.segmented-control {
  display: inline-flex;
  padding: 3px;
  background: #f1f5f9;
  border-radius: 8px;
  gap: 2px;
}

.segment-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: var(--lm-text-secondary);
  font-size: 12px;
  font-weight: 650;
  border-radius: 6px;
  cursor: pointer;
  transition: all var(--lm-transition);
}

.segment-item:hover {
  color: var(--lm-text-primary);
}

.segment-item.active {
  background: #ffffff;
  color: var(--lm-text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.04);
}

.current-problem-pill-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.btn-trigger-picker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: var(--lm-text-primary);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  transition: all var(--lm-transition);
  max-width: 380px;
}

.btn-trigger-picker:hover {
  border-color: #94a3b8;
  background: #f8fafc;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}

.overview-picker-btn {
  color: var(--lm-text-secondary);
}

.pill-code {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 700;
}

.pill-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pill-icon-switch {
  font-size: 12px;
  color: var(--lm-text-muted);
}

.btn-peek-drawer {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 10px;
  background: #f8fafc;
  border: 1px solid var(--lm-border);
  border-radius: 8px;
  color: var(--lm-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--lm-transition);
  white-space: nowrap;
}

.btn-peek-drawer:hover {
  background: #e2e8f0;
  color: var(--lm-text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.search-input {
  width: 170px;
}

.my-team-anchor-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  background: #f8fafc;
  border: 1px solid var(--lm-border);
  border-radius: 999px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
  white-space: nowrap;
}

.my-team-anchor-badge.has-rank {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.my-team-name {
  font-weight: 600;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-team-pending-tag {
  font-size: 10px;
  color: var(--lm-text-muted);
}

.btn-refresh-circle {
  width: 30px;
  height: 30px;
  border: 1px solid var(--lm-border);
  background: #ffffff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-refresh-circle:hover:not(:disabled) {
  background: #f8fafc;
  color: var(--lm-text-primary);
  border-color: #cbd5e1;
}

.spin-anim {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 局部与整页流光占位动画 */
.skeleton-pulse {
  background: linear-gradient(90deg, #f4f4f5 25%, #e4e4e7 37%, #f4f4f5 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

@keyframes skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

.ladder-skeleton-box, .detail-skeleton-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}

.sk-row-strip { height: 42px; border-radius: 6px; }
.sk-podium-row { height: 160px; border-radius: 12px; }
.sk-table-box { height: 280px; border-radius: 12px; }
.sk-deck-card { height: 140px; border-radius: 12px; flex: 1; }
.deck-skeleton-row { display: flex; gap: 12px; }
.loading-tag-inline { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; color: var(--lm-text-muted); margin-left: auto; }

/* 错误卡片 */
.ranking-error-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 12px;
  color: #991b1b;
}

.error-warn-icon {
  font-size: 26px;
  color: #ef4444;
}

.error-texts h4 { margin: 0; font-size: 14px; font-weight: 700; }
.error-texts p { margin: 2px 0 0; font-size: 12px; color: #b91c1c; }

.btn-error-retry {
  margin-left: auto;
  padding: 6px 12px;
  background: #dc2626;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

/* ==========================================================================
   2. 模式一：全平台赛题总览天梯 (Overview)
   ========================================================================== */
.overview-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.overview-metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  display: flex;
  align-items: center;
  gap: 14px;
  transition: transform var(--lm-transition), box-shadow var(--lm-transition);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}

.stat-icon-wrapper {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.blue-grad { background: #eff6ff; color: #2563eb; }
.gold-grad { background: #fefce8; color: #ca8a04; }
.green-grad { background: #f0fdf4; color: #16a34a; }
.amber-grad { background: #fff7ed; color: #ea580c; }

.stat-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-text-muted);
}

.stat-number-row {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.stat-num {
  font-size: 26px;
  font-weight: 850;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-primary);
  line-height: 1.1;
}

.stat-num.stat-highlight {
  color: #d97706;
}

.stat-unit {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.overview-charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.visual-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.visual-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.visual-header h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 750;
}

.vh-icon {
  font-size: 16px;
  color: #2563eb;
}

.h-bar-chart-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.h-bar-item {
  display: grid;
  grid-template-columns: 160px 1fr;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
  transition: background var(--lm-transition);
}

.h-bar-item:hover {
  background: #f8fafc;
}

.h-bar-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.h-bar-code {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
}

.h-bar-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.h-bar-progress-track {
  height: 20px;
  background: #f1f5f9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.h-bar-fill-strip {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa 0%, #2563eb 100%);
  border-radius: 6px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.h-bar-count-badge {
  position: absolute;
  right: 8px;
  font-size: 10px;
  font-weight: 750;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-secondary);
}

.range-spectrum-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.spectrum-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
  transition: background var(--lm-transition);
}

.spectrum-row:hover {
  background: #f8fafc;
}

.spectrum-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
}

.sp-title {
  font-weight: 650;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 250px;
}

.sp-scores b {
  color: var(--lm-text-primary);
}

.spectrum-track {
  position: relative;
  height: 8px;
  background: #f1f5f9;
  border-radius: 999px;
}

.spectrum-span {
  position: absolute;
  height: 100%;
  background: linear-gradient(90deg, #93c5fd 0%, #3b82f6 100%);
  border-radius: 999px;
}

.ladder-card-wrapper {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.ladder-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--lm-border);
  flex-wrap: wrap;
  gap: 12px;
}

.ladder-title-wrap h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 750;
}

.ladder-subtitle {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.ladder-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-open-filter-modal {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-open-filter-modal:hover {
  background: #e2e8f0;
  color: var(--lm-text-primary);
}

.sort-segmented-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sort-tag-label {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.sort-pill {
  border: 1px solid var(--lm-border);
  background: #ffffff;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.sort-pill.active {
  background: #f1f5f9;
  color: var(--lm-text-primary);
  border-color: #cbd5e1;
  font-weight: 700;
}

.ladder-table-responsive {
  overflow-x: auto;
}

.ladder-table {
  width: 100%;
  border-collapse: collapse;
}

.ladder-table th {
  background: #f8fafc;
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
  padding: 10px 18px;
  text-align: left;
  border-bottom: 1px solid var(--lm-border);
}

.ladder-table td {
  padding: 12px 18px;
  border-bottom: 1px solid var(--lm-border-light);
  font-size: 13px;
}

.ladder-row {
  cursor: pointer;
  transition: background var(--lm-transition);
}

.ladder-row:hover {
  background: #f8fafc;
}

.prob-title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.badge-code {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  font-weight: 750;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

.text-title {
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-gold-pill {
  font-family: var(--lm-code-font-family);
  font-weight: 800;
  color: #b45309;
}

.score-plain-pill {
  font-family: var(--lm-code-font-family);
  font-weight: 700;
  color: var(--lm-text-secondary);
}

.action-btn-cluster {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-mini-preview, .btn-mini-enter {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 26px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-mini-preview {
  border: 1px solid var(--lm-border);
  background: #ffffff;
  color: var(--lm-text-secondary);
}

.btn-mini-preview:hover {
  background: #f1f5f9;
  color: var(--lm-text-primary);
}

.btn-mini-enter {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 650;
}

.btn-mini-enter:hover {
  background: #dbeafe;
}

.ladder-pagination-bar, .board-pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #ffffff;
  border-top: 1px solid var(--lm-border);
  flex-wrap: wrap;
  gap: 12px;
}

.pagination-info {
  font-size: 12px;
  color: var(--lm-text-muted);
}

/* ==========================================================================
   3. 模式二：单题成果细览 (Detail)
   ========================================================================== */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr) minmax(180px, 1.2fr);
  gap: 12px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  padding: 14px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}

.summary-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sm-label {
  font-size: 11px;
  color: var(--lm-text-muted);
  font-weight: 600;
}

.sm-val-box {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.sm-num {
  font-size: 20px;
  font-family: var(--lm-code-font-family);
  font-weight: 850;
  color: var(--lm-text-primary);
}

.score-highlight-gold {
  color: #b45309;
}

.sm-unit {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.time-metric-box {
  border-left: 1px solid var(--lm-border-light);
  padding-left: 16px;
}

.sm-time-text {
  font-size: 12px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-secondary);
  margin-top: 2px;
}

.podium-card-deck {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.podium-card-unit {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: transform var(--lm-transition), box-shadow var(--lm-transition);
}

.podium-card-unit:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.06);
}

.rank-tier-1 {
  border-color: rgba(217, 119, 6, 0.35);
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.28) 0%, #ffffff 45%);
}

.rank-tier-2 {
  border-color: rgba(148, 163, 184, 0.4);
}

.rank-tier-3 {
  border-color: rgba(202, 138, 4, 0.3);
}

.podium-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tier-medal-tag {
  font-size: 11px;
  font-weight: 750;
}

.tier-rank-number {
  font-size: 16px;
  font-family: var(--lm-code-font-family);
  font-weight: 850;
  color: var(--lm-text-primary);
}

.my-team-flag {
  font-size: 9px;
  background: #2563eb;
  color: #fff;
  padding: 1px 6px;
  border-radius: 999px;
  font-weight: 700;
}

.podium-card-middle {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 6px;
}

.podium-avatar-circle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #f1f5f9;
  border: 1px solid var(--lm-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 800;
}

.rank-tier-1 .podium-avatar-circle {
  background: #fef3c7;
  color: #b45309;
  border-color: #fcd34d;
}

.podium-team-title {
  margin: 0;
  font-size: 14px;
  font-weight: 750;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-large {
  font-size: 24px;
  font-family: var(--lm-code-font-family);
  font-weight: 850;
  line-height: 1;
}

.rank-tier-1 .score-large { color: #b45309; }

.score-unit-small {
  font-size: 11px;
  color: var(--lm-text-muted);
  margin-left: 2px;
}

.podium-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--lm-border-light);
  font-size: 10px;
}

.wf-version-tag {
  background: #f1f5f9;
  padding: 1px 5px;
  border-radius: 3px;
  color: var(--lm-text-secondary);
}

.submit-time-text {
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-muted);
}

.ranking-table-board {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.board-caption-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--lm-border);
  flex-wrap: wrap;
  gap: 10px;
}

.caption-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.caption-left h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 750;
}

.badge-total-count {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.badge-filter-notice {
  font-size: 11px;
  color: #2563eb;
  background: #eff6ff;
  padding: 1px 6px;
  border-radius: 4px;
}

.sorting-rule-text {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.board-table-responsive {
  overflow-x: auto;
}

.board-table {
  width: 100%;
  border-collapse: collapse;
}

.board-table th {
  background: #f8fafc;
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
  padding: 10px 18px;
  text-align: left;
  border-bottom: 1px solid var(--lm-border);
}

.board-table td {
  padding: 11px 18px;
  border-bottom: 1px solid var(--lm-border-light);
  font-size: 13px;
}

.board-row:hover {
  background: #f8fafc;
}

.board-row.is-my-team-row {
  background: rgba(37, 99, 235, 0.03);
  border-left: 3px solid #2563eb;
}

.board-row.row-highlighted {
  animation: pulse-glow 2.5s ease-out;
}

@keyframes pulse-glow {
  0% { background: rgba(254, 240, 138, 0.55); box-shadow: 0 0 12px rgba(234, 179, 8, 0.35); }
  100% { background: transparent; }
}

.rank-badge-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  height: 26px;
  border-radius: 6px;
  font-family: var(--lm-code-font-family);
  font-size: 12px;
  font-weight: 800;
  background: #f1f5f9;
  color: var(--lm-text-secondary);
}

.rank-val-1 { background: #fef3c7; color: #b45309; }
.rank-val-2 { background: #f1f5f9; color: #475569; }
.rank-val-3 { background: #ffedd5; color: #c2410c; }

.team-cell-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.team-avatar-mini {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 750;
  flex-shrink: 0;
}

.av-rank-1 { background: #fef3c7; color: #b45309; }
.av-rank-2 { background: #f1f5f9; color: #475569; }
.av-rank-3 { background: #ffedd5; color: #c2410c; }

.team-name-text {
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-team-pill-badge {
  font-size: 9px;
  background: #2563eb;
  color: #fff;
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.score-cell-flex {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.score-emphasis {
  font-size: 18px;
  font-family: var(--lm-code-font-family);
  font-weight: 850;
}

.unit-text {
  font-size: 11px;
  color: var(--lm-text-muted);
}

.wf-pill-badge {
  font-size: 11px;
  background: #eff6ff;
  color: #1e40af;
  padding: 2px 7px;
  border-radius: 4px;
}

.time-stamp-text {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  color: var(--lm-text-secondary);
}

.row-actions-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-table-opt {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 24px;
  padding: 0 6px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 4px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  text-decoration: none;
}

.btn-table-opt:hover {
  background: #f1f5f9;
  color: var(--lm-text-primary);
}

/* 3.2.3 赛题多维数据分析看板 */
.detail-analytics-deck {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 6px;
}

.deck-header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--lm-text-secondary);
}

.deck-header h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 750;
}

.deck-cards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.analytics-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ac-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ac-head h4 { margin: 0; font-size: 12px; font-weight: 750; }
.ac-sub { font-size: 11px; color: var(--lm-text-muted); }

.histogram-viewport {
  height: 106px;
  display: flex;
  align-items: flex-end;
}

.histogram-bars-strip {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  border-bottom: 1px solid var(--lm-border);
  padding-bottom: 2px;
}

.hist-col-group {
  position: relative;
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  cursor: pointer;
}

.hist-bar-outer {
  width: 100%;
  height: calc(100% - 18px);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
}

.hist-val-top {
  font-size: 9px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-muted);
  margin-bottom: 1px;
}

.hist-bar-core {
  width: 100%;
  max-width: 28px;
  background: #e2e8f0;
  border-radius: 4px 4px 0 0;
  transition: height 0.3s ease;
  position: relative;
}

.hist-col-group:hover .hist-bar-core { background: #94a3b8; }
.hist-col-group.is-my-score-tier .hist-bar-core { background: #3b82f6; }

.my-score-pip {
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

.hist-x-mark {
  font-size: 9px;
  font-family: var(--lm-code-font-family);
  color: var(--lm-text-muted);
  margin-top: 3px;
}

.hist-popover {
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

.hist-col-group:hover .hist-popover {
  opacity: 1;
  visibility: visible;
}

.hp-tier-name { font-weight: 700; }
.hp-count { color: #cbd5e1; }
.hp-my-mark { color: #fde047; font-weight: 600; }

.donut-chart-flex {
  display: flex;
  align-items: center;
  gap: 16px;
}

.donut-svg-wrapper {
  position: relative;
  width: 84px;
  height: 84px;
  flex-shrink: 0;
}

.donut-svg-element {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.donut-center-badge {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  display: flex;
  flex-direction: column;
}

.donut-center-badge strong {
  font-size: 15px;
  font-family: var(--lm-code-font-family);
  line-height: 1;
}

.donut-center-badge small {
  font-size: 9px;
  color: var(--lm-text-muted);
}

.donut-legend-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px 8px;
  flex: 1;
}

.legend-row-item {
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

.legend-tier-name { color: var(--lm-text-secondary); width: 34px; }
.legend-count-val { font-family: var(--lm-code-font-family); color: var(--lm-text-primary); font-weight: 600; }
.legend-ratio-val { color: var(--lm-text-muted); margin-left: auto; }

.empty-vis-notice {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--lm-text-muted);
  border: 1px dashed var(--lm-border);
  border-radius: 6px;
}

.empty-state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 50px 20px;
  background: #ffffff;
  border: 1px dashed var(--lm-border);
  border-radius: 12px;
  text-align: center;
}

.empty-watermark-icon {
  font-size: 34px;
  color: var(--lm-text-muted);
  margin-bottom: 10px;
}

.empty-state-card h4 { margin: 0; font-size: 15px; font-weight: 750; }
.empty-state-card p { margin: 6px 0 16px; font-size: 12px; color: var(--lm-text-secondary); }

.btn-clear-empty {
  padding: 6px 14px;
  background: var(--lm-primary);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

/* ==========================================================================
   4. 模态窗口：赛题可视化筛选中心 (Visual Problem Selector Modal)
   ========================================================================= */
.picker-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.picker-search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-reset-filters {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  background: #f8fafc;
  border: 1px solid var(--lm-border);
  border-radius: 6px;
  font-size: 12px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  white-space: nowrap;
}

.btn-reset-filters:hover {
  background: #f1f5f9;
  color: var(--lm-text-primary);
}

.picker-matrix-filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid var(--lm-border);
  border-radius: 8px;
}

.matrix-filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mf-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--lm-text-muted);
  width: 62px;
  flex-shrink: 0;
}

.mf-chips-scroll {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.mf-chip {
  padding: 2px 8px;
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 4px;
  font-size: 11px;
  color: var(--lm-text-secondary);
  cursor: pointer;
  transition: all var(--lm-transition);
}

.mf-chip:hover {
  background: #f1f5f9;
  color: var(--lm-text-primary);
}

.mf-chip.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
  font-weight: 600;
}

.results-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.results-count-text {
  font-size: 12px;
  color: var(--lm-text-muted);
}

.results-count-text strong {
  color: var(--lm-text-primary);
}

.picker-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.sk-picker-card {
  height: 88px;
  border-radius: 8px;
}

.picker-cards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.picker-problem-card {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  border-radius: 8px;
  padding: 12px 14px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all var(--lm-transition);
}

.picker-problem-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
}

.picker-problem-card.is-active-problem {
  border-color: #2563eb;
  background: #eff6ff;
}

.pp-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.pp-code {
  font-family: var(--lm-code-font-family);
  font-size: 11px;
  font-weight: 750;
  color: #2563eb;
  background: #eff6ff;
  padding: 1px 5px;
  border-radius: 3px;
}

.pp-tag {
  font-size: 10px;
  padding: 1px 5px;
  background: #f1f5f9;
  border-radius: 3px;
  color: var(--lm-text-secondary);
}

.pp-tag.contest { font-weight: 600; color: var(--lm-text-primary); }

.pp-title {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--lm-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pp-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 6px;
  border-top: 1px solid var(--lm-border-light);
  font-size: 11px;
}

.pp-teams-hint {
  color: var(--lm-text-muted);
}

.pp-teams-hint strong {
  color: var(--lm-text-primary);
}

.pp-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-pp-peek, .btn-pp-select {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 24px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all var(--lm-transition);
}

.btn-pp-peek {
  background: #ffffff;
  border: 1px solid var(--lm-border);
  color: var(--lm-text-secondary);
}

.btn-pp-peek:hover {
  background: #f1f5f9;
  color: var(--lm-text-primary);
}

.btn-pp-select {
  background: #2563eb;
  border: none;
  color: #ffffff;
  font-weight: 600;
}

.btn-pp-select:hover {
  background: #1d4ed8;
}

.picker-empty-results {
  padding: 36px 0;
  text-align: center;
  color: var(--lm-text-muted);
  font-size: 12px;
}

.picker-pagination-wrap {
  display: flex;
  justify-content: center;
  padding-top: 10px;
}

/* ==========================================================================
   5. 侧边抽屉：题面速览
   ========================================================================== */
.drawer-loading-center {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--lm-text-muted);
  font-size: 13px;
}

.drawer-inner-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-meta-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.dm-tag {
  font-size: 11px;
  padding: 2px 8px;
  background: #f1f5f9;
  border: 1px solid var(--lm-border);
  border-radius: 4px;
  color: var(--lm-text-secondary);
}

.dm-tag.contest { font-weight: 700; color: var(--lm-text-primary); }

.diff-1 { color: #059669; }
.diff-2 { color: #d97706; }
.diff-3 { color: #dc2626; }

.drawer-markdown-render {
  font-size: 13px;
  line-height: 1.7;
  color: var(--lm-text-primary);
  max-height: calc(100vh - 180px);
  overflow-y: auto;
  padding-right: 6px;
}

.drawer-bottom-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--lm-border);
}

.link-open-full {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  text-decoration: none;
}

.link-open-full:hover {
  text-decoration: underline;
}

/* ==========================================================================
   响应式断点
   ========================================================================== */
@media (max-width: 992px) {
  .overview-metrics-grid, .overview-charts-grid, .deck-cards-grid, .picker-cards-grid {
    grid-template-columns: 1fr;
  }

  .detail-summary-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .time-metric-box {
    grid-column: 1 / 3;
    border-left: none;
    padding-left: 0;
  }

  .podium-card-deck {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .workspace-header-strip {
    flex-direction: column;
    align-items: stretch;
  }

  .header-right {
    margin-left: 0;
    justify-content: space-between;
  }

  .search-input {
    width: 100%;
  }

  .col-th-wf, .col-th-time, .col-td-wf, .col-td-time {
    display: none;
  }
}
</style>
