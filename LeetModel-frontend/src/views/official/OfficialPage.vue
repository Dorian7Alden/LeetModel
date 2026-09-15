<template>
  <div class="official-page">
    <a class="skip-link" href="#official-main">跳到主要内容</a>

    <header class="official-header">
      <div class="official-header__inner">
        <router-link to="/" class="official-brand" aria-label="LeetModel 官网首页">
          <img src="@/assets/images/logo-with-en.png" alt="LeetModel" />
        </router-link>

        <nav class="official-nav" aria-label="官网主导航">
          <a href="#workflow">实训闭环</a>
          <a href="#results">真实结果</a>
          <a href="#capabilities">核心能力</a>
        </nav>

        <div class="official-header__actions">
          <router-link v-if="!userStore.isLogin" to="/login" class="header-text-action">登录</router-link>
          <router-link :to="primaryRoute" class="header-primary-action">
            {{ userStore.isLogin ? '进入工作台' : '开始实训' }}
            <ArrowUpRight :size="15" aria-hidden="true" />
          </router-link>
        </div>
      </div>
    </header>

    <main id="official-main">
      <section class="official-hero" aria-labelledby="official-heading">
        <div class="hero-copy">
          <div class="hero-kicker">
            <span></span>
            数学建模赛前全真演练场
          </div>
          <h1 id="official-heading">
            从一道真题，
            <span>到一份经得起追问的论文。</span>
          </h1>
          <p>
            在真正比赛前，把选题、三职责就绪、限时交卷与 AI 深度评审完整跑一遍。
            LeetModel 帮你提前发现格式雷区、模型断点和证据缺口。
          </p>

          <div class="hero-actions">
            <router-link :to="primaryRoute" class="official-action official-action--primary">
              {{ userStore.isLogin ? '继续我的实训' : '免费开始一次演练' }}
              <ArrowRight :size="18" aria-hidden="true" />
            </router-link>
            <router-link to="/problem" class="official-action official-action--secondary">
              先浏览历年真题
            </router-link>
          </div>

          <ul class="hero-assurances" aria-label="平台核心能力">
            <li><UsersRound :size="15" aria-hidden="true" />三职责协作</li>
            <li><UploadCloud :size="15" aria-hidden="true" />50MB 分片交卷</li>
            <li><FileCheck2 :size="15" aria-hidden="true" />版本化 AI 评审</li>
          </ul>
        </div>

        <div class="preflight-stage" aria-label="论文 AI 预审流程预览">
          <div class="stage-coordinate stage-coordinate--x">MODEL / PAPER / EVIDENCE</div>
          <div class="stage-coordinate stage-coordinate--y">PRE-FLIGHT 01</div>

          <article class="paper-preview">
            <header class="paper-preview__header">
              <div>
                <span>PAPER / PRE-FLIGHT</span>
                <strong>论文体检正在形成证据链</strong>
              </div>
              <span class="paper-status"><i></i>结构化评审</span>
            </header>

            <div class="paper-title-block">
              <span>ABSTRACT</span>
              <strong>赛题要求、模型假设与结论是否真正闭环？</strong>
              <p>不是只给一个分数，而是回到论文页码与推导证据。</p>
            </div>

            <div class="review-lanes">
              <div class="review-lane review-lane--complete">
                <span class="lane-index">01</span>
                <div>
                  <strong>结构规范审查</strong>
                  <small>摘要 · 符号 · 图表 · 引用</small>
                </div>
                <Check :size="17" aria-hidden="true" />
              </div>
              <div class="review-lane review-lane--active">
                <span class="lane-index">02</span>
                <div>
                  <strong>建模推演复核</strong>
                  <small>小题覆盖 · 假设 · 求解 · 验证</small>
                </div>
                <ScanSearch :size="17" aria-hidden="true" />
              </div>
              <div class="review-lane">
                <span class="lane-index">03</span>
                <div>
                  <strong>提分路径生成</strong>
                  <small>扣分证据 · 改写范例 · 公式建议</small>
                </div>
                <Route :size="17" aria-hidden="true" />
              </div>
            </div>

            <footer class="paper-preview__footer">
              <span>评审不是终点</span>
              <strong>每个问题都要能继续修改</strong>
            </footer>
          </article>

          <div class="annotation annotation--top">先查致命格式风险</div>
          <div class="annotation annotation--bottom">再追问模型是否自洽</div>
        </div>
      </section>

      <section id="workflow" class="workflow-section official-section" aria-labelledby="workflow-heading">
        <div class="section-heading section-heading--split">
          <div>
            <span class="section-kicker">ONE COMPLETE REHEARSAL</span>
            <h2 id="workflow-heading">一次模拟，跑通整场比赛。</h2>
          </div>
          <p>不做日常打卡，不堆无关功能。只把比赛前真正需要经历的四个动作连成闭环。</p>
        </div>

        <ol class="workflow-track">
          <li v-for="step in workflowSteps" :key="step.code">
            <div class="workflow-marker">
              <component :is="step.icon" :size="19" aria-hidden="true" />
            </div>
            <span>{{ step.code }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
          </li>
        </ol>
      </section>

      <section id="results" class="result-preview-section official-section" aria-labelledby="results-heading">
        <div class="section-heading section-heading--split">
          <div>
            <span class="section-kicker">REAL PAPER RUN / REDACTED</span>
            <h2 id="results-heading">先看 AI 到底交付什么。</h2>
          </div>
          <div class="result-preview-intro">
            <span><BadgeCheck :size="17" aria-hidden="true" />真实完整论文演练</span>
            <p>基于 2025 MCM A 题面与匹配的 25 页论文 PDF。以下内容来自真实运行结果，已脱敏与概括。</p>
          </div>
        </div>

        <div class="result-preview-shell" aria-label="真实 AI 评审与建议结果预览">
          <header class="result-preview-shell__header">
            <div>
              <span>FULL WORKFLOW / COMPLETED</span>
              <strong>题目、论文、评审与建议已完成一次端到端演练</strong>
            </div>
            <span class="result-run-status"><i></i>真实运行结果</span>
          </header>

          <div class="result-preview-grid">
            <article class="review-result-card" aria-labelledby="review-result-title">
              <header class="result-card-heading">
                <div>
                  <span>AI REVIEW / V3</span>
                  <h3 id="review-result-title">证据化评审结果</h3>
                </div>
                <span class="result-card-state"><Check :size="14" aria-hidden="true" />已完成</span>
              </header>

              <div class="review-score-summary">
                <div class="review-score-main">
                  <strong>45.0</strong>
                  <span>/ 100</span>
                  <small>平台训练评分</small>
                </div>
                <dl class="review-score-facts">
                  <div>
                    <dt>13</dt>
                    <dd>结构化发现</dd>
                  </div>
                  <div>
                    <dt>13</dt>
                    <dd>论文证据锚点</dd>
                  </div>
                </dl>
              </div>

              <div class="dimension-list" aria-label="五维评审得分">
                <div v-for="dimension in reviewDimensions" :key="dimension.label" class="dimension-row">
                  <div>
                    <span>{{ dimension.label }}</span>
                    <strong>{{ dimension.score }}<small>/{{ dimension.max }}</small></strong>
                  </div>
                  <progress
                    :value="dimension.score"
                    :max="dimension.max"
                    :aria-label="`${dimension.label} ${dimension.score} 分，满分 ${dimension.max} 分`"
                  ></progress>
                </div>
              </div>

              <div class="finding-preview">
                <span><Crosshair :size="15" aria-hidden="true" />评审结论摘要</span>
                <p>模型框架已经成形，但关键机理、参数辨识与结果验证仍缺少能够互相印证的完整证据闭环。</p>
                <small>原始论文内容、队伍信息与完整模型回答未公开。</small>
              </div>
            </article>

            <article class="suggestion-result-card" aria-labelledby="suggestion-result-title">
              <header class="result-card-heading">
                <div>
                  <span>AI COACH / V3</span>
                  <h3 id="suggestion-result-title">可执行建议结果</h3>
                </div>
                <span class="result-card-state result-card-state--dark">7 / 7 子任务完成</span>
              </header>

              <div class="suggestion-priority">
                <span>P1 · 关键修改</span>
                <small>建模与求解</small>
              </div>

              <h4>把损伤机理推进为可计算、可验证的模型</h4>
              <p class="suggestion-diagnosis">
                当前推导需要进一步明确状态变量、参数来源和边界条件，并让求解过程与验证结果形成对应关系。
              </p>

              <ol class="suggestion-actions">
                <li v-for="(action, index) in suggestionActions" :key="action">
                  <span>{{ String(index + 1).padStart(2, '0') }}</span>
                  <p>{{ action }}</p>
                </li>
              </ol>

              <div class="acceptance-preview">
                <span><ListChecks :size="16" aria-hidden="true" />验收标准</span>
                <p>方程、参数来源、求解日志与验证图表能够互相对应，关键误差指标可复算。</p>
              </div>

              <div class="evidence-chain-preview" aria-label="建议依据链">
                <span><FileSearch :size="14" aria-hidden="true" />论文第 5 页</span>
                <i></i>
                <span><MapPinned :size="14" aria-hidden="true" />评审发现</span>
                <i></i>
                <span><BookOpenCheck :size="14" aria-hidden="true" />知识依据</span>
              </div>
            </article>
          </div>

          <footer class="result-preview-shell__footer">
            <span>DEEP_EVIDENCE_REVIEW_V3</span>
            <span>GROUNDED_SUGGESTION_V3</span>
            <p>训练评分用于模拟与改进，不代表具体赛事官方结果。</p>
          </footer>
        </div>
      </section>

      <section id="review" class="review-section official-section" aria-labelledby="review-heading">
        <div class="review-section__copy">
          <span class="section-kicker section-kicker--inverse">EVIDENCE-BASED REVIEW</span>
          <h2 id="review-heading">真正有用的评审，<br />必须指出问题在哪里。</h2>
          <p>
            LeetModel 将规范性审查与建模推演分开执行，再把分数、问题、论文证据和改进方向重新关联。
            你看到的不只是结论，还能知道下一步该改什么。
          </p>

          <div class="review-principles">
            <div>
              <MapPinned :size="18" aria-hidden="true" />
              <span><strong>定位到证据</strong><small>回指页码、章节与具体观察</small></span>
            </div>
            <div>
              <ShieldCheck :size="18" aria-hidden="true" />
              <span><strong>区分确定与限制</strong><small>证据不足时明确说明，不强行下结论</small></span>
            </div>
            <div>
              <GitCompareArrows :size="18" aria-hidden="true" />
              <span><strong>从扣分走向修改</strong><small>让问题与后续改进建议保持关联</small></span>
            </div>
          </div>
        </div>

        <div class="review-blueprint" aria-label="AI 双阶段评审结构">
          <div class="blueprint-heading">
            <span>DEEP REVIEW / V3</span>
            <strong>双阶段深度评审</strong>
          </div>
          <div class="blueprint-score">
            <div>
              <strong>25</strong>
              <span>结构与学术规范</span>
            </div>
            <i>+</i>
            <div>
              <strong>75</strong>
              <span>建模与小题推演</span>
            </div>
            <i>=</i>
            <div class="blueprint-total">
              <strong>100</strong>
              <span>平台训练评分</span>
            </div>
          </div>
          <div class="blueprint-evidence">
            <span>PAGE 06</span>
            <p>模型假设给出了变量关系，但缺少参数来源与敏感性边界。</p>
            <strong>证据 → 影响 → 修改方向</strong>
          </div>
          <p class="blueprint-note">平台训练评分用于模拟与改进，不代表具体赛事官方结果。</p>
        </div>
      </section>

      <section id="capabilities" class="capability-section official-section" aria-labelledby="capability-heading">
        <div class="section-heading">
          <span class="section-kicker">BUILT FOR THE HOURS BEFORE SUBMISSION</span>
          <h2 id="capability-heading">把赛前焦虑，变成可确认的状态。</h2>
        </div>

        <div class="capability-grid">
          <article v-for="capability in capabilities" :key="capability.title">
            <component :is="capability.icon" :size="22" aria-hidden="true" />
            <span>{{ capability.label }}</span>
            <h3>{{ capability.title }}</h3>
            <p>{{ capability.description }}</p>
            <div class="capability-proof">{{ capability.proof }}</div>
          </article>
        </div>
      </section>

      <section class="final-cta official-section" aria-labelledby="final-heading">
        <div>
          <span class="section-kicker">START WITH A REAL PROBLEM</span>
          <h2 id="final-heading">下一场比赛之前，先完整交卷一次。</h2>
          <p>选择一道历年真题，建立队伍，启动属于你的全真演练。</p>
        </div>
        <div class="final-cta__actions">
          <router-link :to="primaryRoute" class="official-action official-action--primary">
            {{ userStore.isLogin ? '返回实训首页' : '创建账号并开始' }}
            <ArrowRight :size="18" aria-hidden="true" />
          </router-link>
          <router-link to="/problem" class="official-action official-action--secondary">浏览题库</router-link>
        </div>
      </section>
    </main>

    <footer class="official-footer">
      <div class="official-footer__inner">
        <router-link to="/" class="footer-brand" aria-label="返回 LeetModel 官网首页">
          <img src="@/assets/images/logo-en.png" alt="LeetModel" />
          <span>数学建模在线实训平台</span>
        </router-link>
        <nav aria-label="官网页脚导航">
          <router-link to="/problem">题库</router-link>
          <router-link to="/about">关于我们</router-link>
          <router-link to="/help">使用帮助</router-link>
          <router-link to="/contact">联系我们</router-link>
        </nav>
        <span>© 2026 LeetModel</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  ArrowRight,
  ArrowUpRight,
  BadgeCheck,
  BookOpenCheck,
  Check,
  Clock3,
  Crosshair,
  FileCheck2,
  FileSearch,
  GitCompareArrows,
  LibraryBig,
  ListChecks,
  MapPinned,
  Route,
  ScanSearch,
  ShieldCheck,
  UploadCloud,
  UsersRound,
} from '@lucide/vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const primaryRoute = computed(() => userStore.isLogin ? '/home' : '/register')

const reviewDimensions = [
  { label: '结构与表达', score: 12.3, max: 20 },
  { label: '题意与假设', score: 6.5, max: 15 },
  { label: '数学建模', score: 11.7, max: 25 },
  { label: '算法与求解', score: 6.7, max: 20 },
  { label: '结果与验证', score: 7.8, max: 20 },
]

const suggestionActions = [
  '补全状态变量、约束关系与初始边界，给出可复算的演变方程。',
  '说明参数估计方法与数据对应关系，保留关键求解过程和收敛依据。',
  '增加残差检验与全局灵敏度分析，用图表呈现模型稳定区间。',
]

const workflowSteps = [
  {
    code: '01 / PICK',
    title: '选择真题',
    description: '从国赛、美赛等历年题目中确定一次真正值得投入的模拟。',
    icon: LibraryBig,
  },
  {
    code: '02 / TEAM',
    title: '职责就绪',
    description: '建模、编程、论文三项职责覆盖后，立即启动限时实训。',
    icon: UsersRound,
  },
  {
    code: '03 / SUBMIT',
    title: '可靠交卷',
    description: '保存可追溯的 PDF 版本，在倒计时内完成稳定上传。',
    icon: Clock3,
  },
  {
    code: '04 / REVIEW',
    title: '深度体检',
    description: '从结构规范到模型推演，获得带证据的评分与改进方向。',
    icon: FileSearch,
  },
]

const capabilities = [
  {
    label: '赛程确定性',
    title: '知道还剩多久，也知道队伍还缺什么。',
    description: '倒计时、队伍职责和实训状态集中呈现，关键阶段不靠猜测。',
    proof: '三职责覆盖 · 状态化实训 · 实时倒计时',
    icon: Clock3,
  },
  {
    label: '交卷可靠性',
    title: '大文件上传过程清晰、可恢复、可追溯。',
    description: '针对包含复杂图表的论文 PDF，提供分片、校验与版本记录。',
    proof: '50MB PDF · 分片续传 · SHA-256 校验',
    icon: UploadCloud,
  },
  {
    label: '反馈可执行',
    title: '从一个扣分点，继续走到具体修改。',
    description: '评审结果保留评分理由与证据关系，为后续针对性改进提供依据。',
    proof: '结构化发现 · 论文证据 · 双轨改进建议',
    icon: GitCompareArrows,
  },
]
</script>

<style scoped>
@import './style.css';
</style>
