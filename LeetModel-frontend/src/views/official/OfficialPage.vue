<template>
  <div class="official-page">
    <a class="skip-link" href="#official-main">跳到主要内容</a>

    <header class="official-header">
      <div class="official-header__inner">
        <router-link to="/" class="official-brand" aria-label="LeetModel 官网首页">
          <img src="@/assets/images/logo-with-en.png" alt="LeetModel" />
        </router-link>

        <nav class="official-nav" aria-label="官网主导航">
          <a href="#results">AI 评审结果</a>
          <a href="#review">评审维度</a>
          <a href="#workflow">使用流程</a>
        </nav>

        <div class="official-header__actions">
          <router-link v-if="!userStore.isLogin" to="/login" class="header-text-action">登录</router-link>
          <router-link :to="primaryRoute" class="header-primary-action">
            {{ userStore.isLogin ? '进入评审工作台' : '开始论文 AI 评审' }}
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
            数学建模论文 AI 评审与修改建议
          </div>
          <h1 id="official-heading">
            AI 评审数学建模论文，
            <span>定位问题并给出修改建议。</span>
          </h1>
          <p>
            上传完整的数学建模竞赛论文，LeetModel 从结构表达、题意假设、数学建模、算法求解和结果验证五个维度评分，
            定位论文问题与证据，并生成可执行的修改步骤和验收标准。
          </p>

          <div class="hero-actions">
            <router-link :to="primaryRoute" class="official-action official-action--primary">
              {{ userStore.isLogin ? '进入评审工作台' : '开始论文 AI 评审' }}
              <ArrowRight :size="18" aria-hidden="true" />
            </router-link>
            <a href="#results" class="official-action official-action--secondary">查看真实评审结果</a>
          </div>

          <ul class="hero-assurances" aria-label="AI 论文评审核心能力">
            <li><FileCheck2 :size="15" aria-hidden="true" />五维论文评分</li>
            <li><MapPinned :size="15" aria-hidden="true" />问题定位到论文证据</li>
            <li><ListChecks :size="15" aria-hidden="true" />生成可执行修改建议</li>
          </ul>
        </div>

        <div class="preflight-stage" aria-label="数学建模论文 AI 评审流程预览">
          <div class="stage-coordinate stage-coordinate--x">PAPER / MODEL / EVIDENCE</div>
          <div class="stage-coordinate stage-coordinate--y">AI REVIEW 01</div>

          <article class="paper-preview">
            <header class="paper-preview__header">
              <div>
                <span>MATHEMATICAL MODELING PAPER</span>
                <strong>AI 正在评审你的数学建模论文</strong>
              </div>
              <span class="paper-status"><i></i>论文 AI 评审</span>
            </header>

            <div class="paper-title-block">
              <span>REVIEW SCOPE</span>
              <strong>从模型假设到结果验证，逐项定位论文问题</strong>
              <p>输出五维评分、证据化评语和可执行修改建议。</p>
            </div>

            <div class="review-lanes">
              <div class="review-lane review-lane--complete">
                <span class="lane-index">01</span>
                <div>
                  <strong>论文结构与规范评审</strong>
                  <small>摘要 · 符号 · 图表 · 引用</small>
                </div>
                <Check :size="17" aria-hidden="true" />
              </div>
              <div class="review-lane review-lane--active">
                <span class="lane-index">02</span>
                <div>
                  <strong>模型与算法求解评审</strong>
                  <small>题意 · 假设 · 建模 · 算法</small>
                </div>
                <ScanSearch :size="17" aria-hidden="true" />
              </div>
              <div class="review-lane">
                <span class="lane-index">03</span>
                <div>
                  <strong>结果验证与修改建议</strong>
                  <small>误差检验 · 修改步骤 · 验收标准</small>
                </div>
                <Route :size="17" aria-hidden="true" />
              </div>
            </div>

            <footer class="paper-preview__footer">
              <span>评审结果可追溯</span>
              <strong>每条问题关联论文证据与修改方案</strong>
            </footer>
          </article>

          <div class="annotation annotation--top">定位论文问题</div>
          <div class="annotation annotation--bottom">生成修改建议</div>
        </div>
      </section>

      <section id="results" class="result-preview-section official-section" aria-labelledby="results-heading">
        <div class="section-heading section-heading--split">
          <div>
            <span class="section-kicker">REAL AI PAPER REVIEW RESULT</span>
            <h2 id="results-heading">真实数学建模论文 AI 评审结果</h2>
          </div>
          <div class="result-preview-intro">
            <span><BadgeCheck :size="17" aria-hidden="true" />完整论文评审与建议已跑通</span>
            <p>基于 2025 MCM A 题面与匹配的 25 页论文 PDF，展示真实五维评分、论文问题与改进建议；内容已脱敏。</p>
          </div>
        </div>

        <div class="product-shot-stage">
          <div class="product-shot-label"><span></span>LeetModel 产品界面实景预览</div>

          <figure class="product-shot" aria-label="LeetModel AI 论文评审详情页面预览">
            <div class="product-shot__chrome" aria-hidden="true">
              <div class="product-shot__traffic"><i></i><i></i><i></i></div>
              <div class="product-shot__address"><ShieldCheck :size="12" />LeetModel / AI 论文评审结果</div>
              <div class="product-shot__privacy">真实结果 · 已脱敏</div>
            </div>

            <div class="product-shot__app">
              <aside class="product-shot__sidebar" aria-label="产品导航预览">
                <div class="shot-brand"><img src="@/assets/images/logo-en.png" alt="LeetModel" /></div>
                <nav>
                  <span><LayoutDashboard :size="15" aria-hidden="true" />实训概览</span>
                  <span><LibraryBig :size="15" aria-hidden="true" />赛题与队伍</span>
                  <span><FileText :size="15" aria-hidden="true" />论文提交</span>
                  <span class="is-active"><FileCheck2 :size="15" aria-hidden="true" />AI 评审</span>
                </nav>
                <div class="shot-profile"><i>LM</i><span>演练队伍<small>三职责已就绪</small></span></div>
              </aside>

              <div class="product-shot__workspace">
                <header class="shot-appbar">
                  <span>实训工作台&nbsp; / &nbsp;提交记录&nbsp; / &nbsp;<strong>AI 评审详情</strong></span>
                  <div><span class="shot-appbar__status"><i></i>服务正常</span><i class="shot-avatar">A</i></div>
                </header>

                <div class="shot-page">
                  <header class="shot-report-heading">
                    <div class="shot-report-file">
                      <span><FileText :size="18" aria-hidden="true" /></span>
                      <div>
                        <p>2025 MCM Problem A</p>
                        <h3>论文 AI 深度评审</h3>
                        <small>submission.pdf · 25 页 · 完整论文演练</small>
                      </div>
                    </div>
                    <span class="shot-completed"><Check :size="14" aria-hidden="true" />评审已完成</span>
                  </header>

                  <div class="shot-tabs" aria-label="结果页面标签预览">
                    <span class="is-active">评审结果</span>
                    <span>改进建议 <i>5</i></span>
                    <span>论文证据 <i>13</i></span>
                  </div>

                  <div class="shot-dashboard">
                    <section class="shot-review-panel" aria-labelledby="review-result-title">
                      <div class="shot-panel-heading">
                        <div>
                          <span>DEEP EVIDENCE REVIEW · V3</span>
                          <h3 id="review-result-title">综合评审结果</h3>
                        </div>
                        <span>13 条发现</span>
                      </div>

                      <div class="shot-score-row">
                        <div class="shot-score">
                          <strong>45.0</strong><span>/ 100</span>
                          <small>平台训练评分</small>
                        </div>
                        <p>模型框架已经成形，但关键机理、参数辨识与结果验证仍需补全证据闭环。</p>
                      </div>

                      <div class="shot-dimensions" aria-label="五维评审得分">
                        <div v-for="dimension in reviewDimensions" :key="dimension.label" class="shot-dimension">
                          <span>{{ dimension.label }}</span>
                          <progress
                            :value="dimension.score"
                            :max="dimension.max"
                            :aria-label="`${dimension.label} ${dimension.score} 分，满分 ${dimension.max} 分`"
                          ></progress>
                          <strong>{{ dimension.score }}<small>/{{ dimension.max }}</small></strong>
                        </div>
                      </div>

                      <div class="shot-findings-heading">
                        <h4>需要优先处理的问题</h4>
                        <span>按影响程度排序</span>
                      </div>

                      <div class="shot-finding-list">
                        <article>
                          <span class="shot-severity">HIGH</span>
                          <div>
                            <h4>关键机理与参数来源缺少完整论证</h4>
                            <p>现有推导尚未把状态变量、边界条件和参数辨识过程串成可复算链路。</p>
                            <small><MapPinned :size="12" aria-hidden="true" />论文第 4 页 · 数学建模</small>
                          </div>
                          <span class="shot-impact">影响 8.0 分</span>
                        </article>
                        <article>
                          <span class="shot-severity shot-severity--medium">MEDIUM</span>
                          <div>
                            <h4>验证结果与核心结论的对应关系不足</h4>
                            <p>需要补充可复算的误差指标，并说明验证结果如何支持最终结论。</p>
                            <small><MapPinned :size="12" aria-hidden="true" />论文证据锚点 · 结果与验证</small>
                          </div>
                          <span class="shot-impact">影响 5.0 分</span>
                        </article>
                      </div>
                    </section>

                    <aside class="shot-suggestion-panel" aria-labelledby="suggestion-result-title">
                      <div class="shot-suggestion-heading">
                        <span><Sparkles :size="14" aria-hidden="true" />AI 改进建议</span>
                        <small>7 / 7 子任务完成</small>
                      </div>

                      <span class="shot-priority">P1 · 关键修改</span>
                      <h3 id="suggestion-result-title">把损伤机理推进为可计算、可验证的模型</h3>
                      <p>针对当前评审问题，优先补齐模型定义、求解依据与验证闭环。</p>

                      <ol class="shot-action-list">
                        <li v-for="(action, index) in suggestionActions" :key="action">
                          <span>{{ index + 1 }}</span>
                          <p>{{ action }}</p>
                        </li>
                      </ol>

                      <div class="shot-acceptance">
                        <span><ListChecks :size="14" aria-hidden="true" />验收标准</span>
                        <p>方程、参数来源、求解日志与验证图表能够互相对应，关键误差指标可复算。</p>
                      </div>

                      <div class="shot-evidence">
                        <span>建议依据链</span>
                        <div><FileSearch :size="13" aria-hidden="true" />论文第 5 页</div>
                        <i></i>
                        <div><Crosshair :size="13" aria-hidden="true" />评审发现</div>
                        <i></i>
                        <div><BookOpenCheck :size="13" aria-hidden="true" />知识依据</div>
                      </div>
                    </aside>
                  </div>

                  <footer class="shot-disclaimer">
                    <BadgeCheck :size="14" aria-hidden="true" />
                    基于真实完整论文运行结果生成；展示内容已脱敏与概括，训练评分不代表赛事官方结果。
                  </footer>
                </div>
              </div>
            </div>
          </figure>
        </div>
      </section>

      <section id="review" class="review-section official-section" aria-labelledby="review-heading">
        <div class="review-section__copy">
          <span class="section-kicker section-kicker--inverse">EVIDENCE-BASED PAPER REVIEW</span>
          <h2 id="review-heading">每条论文评审意见，<br />都有证据和修改方案。</h2>
          <p>
            AI 分别评审结构表达、题意假设、数学建模、算法求解和结果验证，不只给出总分；
            每个问题关联论文证据，建议包含修改步骤和验收标准。
          </p>

          <div class="review-principles">
            <div>
              <FileCheck2 :size="18" aria-hidden="true" />
              <span><strong>五维论文评分</strong><small>覆盖结构、假设、建模、算法与验证</small></span>
            </div>
            <div>
              <MapPinned :size="18" aria-hidden="true" />
              <span><strong>证据化问题定位</strong><small>关联论文页码、评审发现与分数影响</small></span>
            </div>
            <div>
              <ListChecks :size="18" aria-hidden="true" />
              <span><strong>可执行修改建议</strong><small>给出修改步骤、依据链与验收标准</small></span>
            </div>
          </div>
        </div>

        <div class="review-blueprint" aria-label="AI 双阶段评审结构">
          <div class="blueprint-heading">
            <span>MATHEMATICAL MODELING PAPER / V3</span>
            <strong>数学建模论文评分结构</strong>
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

      <section id="workflow" class="workflow-section official-section" aria-labelledby="workflow-heading">
        <div class="section-heading section-heading--split">
          <div>
            <span class="section-kicker">AI PAPER REVIEW WORKFLOW</span>
            <h2 id="workflow-heading">数学建模论文 AI 评审流程</h2>
          </div>
          <p>选择对应赛题并上传完整论文 PDF，系统完成五维评审、问题定位和修改建议生成。</p>
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

      <section class="final-cta official-section" aria-labelledby="final-heading">
        <div>
          <span class="section-kicker">START AI PAPER REVIEW</span>
          <h2 id="final-heading">上传数学建模论文，开始一次完整 AI 评审。</h2>
          <p>获得五维评分、论文问题证据、修改步骤与验收标准。</p>
        </div>
        <div class="final-cta__actions">
          <router-link :to="primaryRoute" class="official-action official-action--primary">
            {{ userStore.isLogin ? '进入评审工作台' : '开始论文 AI 评审' }}
            <ArrowRight :size="18" aria-hidden="true" />
          </router-link>
          <router-link to="/problem" class="official-action official-action--secondary">查看数学建模赛题</router-link>
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
  Crosshair,
  FileCheck2,
  FileSearch,
  FileText,
  LayoutDashboard,
  LibraryBig,
  ListChecks,
  MapPinned,
  Route,
  ScanSearch,
  ShieldCheck,
  Sparkles,
  UploadCloud,
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
    title: '选择数学建模赛题',
    description: '确定论文对应的赛事题目，让 AI 按具体任务要求进行评审。',
    icon: LibraryBig,
  },
  {
    code: '02 / UPLOAD',
    title: '上传完整论文 PDF',
    description: '提交包含模型、算法、结果和验证过程的完整数学建模论文。',
    icon: UploadCloud,
  },
  {
    code: '03 / REVIEW',
    title: 'AI 五维论文评审',
    description: '获得结构表达、题意假设、数学建模、算法求解和结果验证评分。',
    icon: ScanSearch,
  },
  {
    code: '04 / IMPROVE',
    title: '获取可执行修改建议',
    description: '根据论文证据和评审问题，查看修改步骤、依据链与验收标准。',
    icon: ListChecks,
  },
]

</script>

<style scoped>
@import './style.css';
</style>
