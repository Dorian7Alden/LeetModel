<template>
  <div class="official-page">
    <a class="skip-link" href="#official-main">跳到主要内容</a>

    <header class="official-header">
      <div class="official-header__inner">
        <router-link to="/" class="official-brand" aria-label="LeetModel 官网首页">
          <img src="@/assets/images/logo-with-en.png" alt="LeetModel" />
        </router-link>

        <nav class="official-nav" aria-label="官网主导航">
          <a href="#results-review">AI 评审结果</a>
          <a href="#results-suggestion">AI 修改建议</a>
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
            <h2 id="results-heading">真实 AI 论文评审与修改建议示例</h2>
          </div>
          <div class="result-preview-intro">
            <span><BadgeCheck :size="17" aria-hidden="true" />完整论文评审与建议已跑通</span>
            <p>基于 2025 MCM Problem A 与匹配的 25 页论文，并列呈现系统真实跑通的“五维论文评分”与“有依据的改进建议”。</p>
          </div>
        </div>

        <div class="feature-slice-stage">
          <div class="stage-telemetry-strip">
            <div class="telemetry-status">
              <span class="telemetry-pulse"></span>
              <strong>SYSTEM LIVE RUNTIME</strong>
              <span class="telemetry-divider">/</span>
              <span>真实生产执行环境产物</span>
            </div>
            <div class="telemetry-tags">
              <span class="telemetry-tag">ENGINE: V4 DUAL-PIPELINE</span>
              <span class="telemetry-tag">MODEL: GEMINI-3.8-FLASH-HIGH</span>
              <span class="telemetry-tag">DATA: 2025 MCM PROBLEM A (25P)</span>
            </div>
          </div>

          <div class="feature-slice-container">
            <!-- 功能一：真实 AI 论文评审结果示例 -->
            <div id="results-review" class="slice-panel slice-panel--featured">
              <div class="slice-panel__title-bar">
                <div class="panel-title-group">
                  <div class="panel-num-tag">FEATURE 01</div>
                  <div class="panel-main-title">
                    <FileCheck2 :size="20" aria-hidden="true" />
                    <h3>核心功能一：AI 论文评审实际结果示例</h3>
                  </div>
                </div>
                <div class="slice-context-badge">
                  <ShieldCheck :size="13" aria-hidden="true" />
                  <span>真实已跑通产物 · 2025 MCM Problem A</span>
                </div>
              </div>

          <!-- 切面一：AI 论文评审结果切面 -->
          
            <div class="slice-panel__meta">
              <div class="slice-meta-item">
                <span class="meta-kicker">SUBMISSION SPEC</span>
                <strong>2025 MCM Problem A · submission-01.pdf</strong>
                <small>25 页完整建模论文 · DEEP_EVIDENCE_REVIEW_V4 专业证据化评审已完成</small>
              </div>
              <div class="slice-score-seal">
                <div class="seal-number">
                  <strong>73.8</strong>
                  <span>/ 100</span>
                </div>
                <div class="seal-label">
                  <span class="seal-badge">平台训练评分</span>
                  <small>稳定分位 · 算法与推导完备</small>
                </div>
              </div>
            </div>

            <div class="slice-summary-card">
              <div class="card-title">
                <BookOpenCheck :size="15" aria-hidden="true" />
                <span>综合评审评估</span>
              </div>
              <p>
                本次平台训练评分为 <strong>73.8 / 100</strong>。宏观踏步磨损体积与微观单步摩擦滑移距离通过 Archard 动力学方程耦合的反演思路清晰且贴合题意；但在数据自洽性、量纲严密性与欠定反演逻辑上存在显著改进空间。
              </p>
            </div>

            <div class="slice-grid slice-grid--review">
              <!-- 左侧：真实五维量表与亮点 -->
              <div class="slice-col">
                <div class="slice-subhead">
                  <span>SCORING DIMENSIONS</span>
                  <h3>论文五维量表得分</h3>
                </div>

                <div class="dimension-bars">
                  <div v-for="dim in realReviewDimensions" :key="dim.label" class="dimension-bar-item">
                    <div class="bar-info">
                      <span class="bar-label">{{ dim.label }}</span>
                      <span class="bar-score"><strong>{{ dim.score }}</strong> / {{ dim.max }}</span>
                    </div>
                    <div class="bar-track" aria-hidden="true">
                      <div class="bar-fill" :style="{ width: (dim.score / dim.max * 100) + '%' }"></div>
                    </div>
                    <p class="bar-eval">{{ dim.eval }}</p>
                  </div>
                </div>

                <div class="strengths-box">
                  <div class="strengths-title">
                    <BadgeCheck :size="14" aria-hidden="true" />
                    <span>已验证论文亮点（3 项）</span>
                  </div>
                  <ul class="strengths-list">
                    <li>
                      <strong>Archard 方程解构动力学耦合：</strong>
                      <span>在 B133-B137 将累积摩擦距离解构为人流频次 n、单步滑动距离 d 与服役周期 t 线性累积，切中题目核心要求。</span>
                    </li>
                    <li>
                      <strong>Delaunay 三角网格微元积分：</strong>
                      <span>针对点云无解析曲面构建离散微元求和模型 V_total = ∑ A_i |h_i|，为不规则磨损数字化提供标准化计算通路。</span>
                    </li>
                  </ul>
                </div>
              </div>

              <!-- 右侧：真实优先问题与证据链 -->
              <div class="slice-col">
                <div class="slice-subhead">
                  <span>EVIDENCE FINDINGS</span>
                  <h3>需要优先处理的问题与证据</h3>
                </div>

                <div class="finding-cards">
                  <article class="slice-finding-card slice-finding-card--p1">
                    <div class="finding-top">
                      <span class="priority-tag priority-tag--p1">P1 严重硬伤</span>
                      <span class="impact-tag">-12.0 分</span>
                      <span class="page-anchor"><MapPinned :size="12" aria-hidden="true" />论文第 11 页 · 数据矛盾</span>
                    </div>
                    <h4>表 11 相同材料与使用时间的台阶估算人流量与理论公式反向变动</h4>
                    <div class="finding-desc">
                      <p><strong>问题成因：</strong>根据推导公式 (B137)，日人流 n 严格正比于磨损体积 V。但样本 6 磨损体积高达 13495 mm³ 对应日人流 6399.1，而体积仅 10617 mm³ 的样本 5 日人流反向激增至 8390.6。</p>
                      <p class="finding-consequence"><strong>影响与判定：</strong>数据与核心理论模型发生直接冲突，存在严重人为拼接或程序计算错误，削弱人流量估算可信度。</p>
                    </div>
                  </article>

                  <article class="slice-finding-card slice-finding-card--p1">
                    <div class="finding-top">
                      <span class="priority-tag priority-tag--p1">P1 量纲遗漏</span>
                      <span class="impact-tag">-10.0 分</span>
                      <span class="page-anchor"><MapPinned :size="12" aria-hidden="true" />论文第 10 页 · 理论公式</span>
                    </div>
                    <h4>磨料磨损简化公式 (B130) 存在确定性量纲缺失</h4>
                    <div class="finding-desc">
                      <p><strong>公式分析：</strong>标准 Archard 公式为 V = (K P L)/(3H)，论文简写为 V = K_abr · L / (3H)，漏乘法向载荷 P，导致等式两端量纲不守恒（[L³] 与 [L³]/[F]）。</p>
                      <p class="finding-consequence"><strong>影响与判定：</strong>理论推导缺乏严密物理基础，混淆无量纲几何系数与力学量纲，导致后续模型迁移断层。</p>
                    </div>
                  </article>

                  <article class="slice-finding-card slice-finding-card--p2">
                    <div class="finding-top">
                      <span class="priority-tag priority-tag--p2">P2 逻辑循环</span>
                      <span class="impact-tag">-8.0 分</span>
                      <span class="page-anchor"><MapPinned :size="12" aria-hidden="true" />论文第 11 页 · 算法设计</span>
                    </div>
                    <h4>贝叶斯参数反演在未知日人流前提下直接估计使用时间</h4>
                    <div class="finding-desc">
                      <p><strong>算法逻辑：</strong>磨损量取决于人流 n 与时间 t 乘积 (nt)，在无日人流先验时属于欠定系统，无法通过一维网格搜索得到最大后验估计值。</p>
                    </div>
                  </article>
                </div>
              </div>
            </div>
          </div>

            <!-- 功能二：真实 AI 修改建议报告示例 -->
            <div id="results-suggestion" class="slice-panel slice-panel--featured slice-panel--separator">
              <div class="slice-panel__title-bar">
                <div class="panel-title-group">
                  <div class="panel-num-tag panel-num-tag--accent">FEATURE 02</div>
                  <div class="panel-main-title">
                    <Sparkles :size="20" aria-hidden="true" />
                    <h3>核心功能二：AI 论文修改建议实际结果示例</h3>
                  </div>
                </div>
                <div class="slice-context-badge">
                  <BadgeCheck :size="13" aria-hidden="true" />
                  <span>V4 动态流水线 · 证据闭环实测</span>
                </div>
              </div>
            <div class="slice-panel__meta">
              <div class="slice-meta-item">
                <span class="meta-kicker">GROUNDED SUGGESTION V4</span>
                <strong>有依据的论文改进建议与实施标准</strong>
                <small>涵盖 5 项必要修正、4 项可选探索 · 严格对应评审证据与原文位置</small>
              </div>
              <div class="slice-strategy-stat">
                <div class="stat-box">
                  <span class="stat-num">5</span>
                  <span class="stat-desc">必要修正</span>
                </div>
                <div class="stat-box">
                  <span class="stat-num stat-num--blue">4</span>
                  <span class="stat-desc">可选探索</span>
                </div>
                <div class="stat-box">
                  <span class="stat-num stat-num--green">100%</span>
                  <span class="stat-desc">证据链覆盖</span>
                </div>
              </div>
            </div>

            <div class="slice-strategy-alert">
              <div class="strategy-badge"><Sparkles :size="14" aria-hidden="true" />本轮论文完善主线</div>
              <p>优先处理有明确论文与评审依据的确定性问题（如量纲、自相矛盾、欠定系统），再补充解释、验证与适用边界。可选探索仅用于提供拓展思考，不代表现有模型必须被推翻。</p>
            </div>

            <div class="suggestion-items-deck">
              <article class="suggestion-deck-card">
                <div class="deck-card__header">
                  <div class="deck-card__tags">
                    <span class="guidance-badge guidance-badge--required">必要修正</span>
                    <span class="type-badge">数学建模</span>
                    <span class="priority-badge">P1 优先级</span>
                  </div>
                  <span class="deck-card__location"><FileSearch :size="13" aria-hidden="true" />论文第 11、12 页 · 3.2.3 Bayesian Inversion Framework</span>
                </div>
                <h4 class="deck-card__title">S-1 · 闭合贝叶斯参数反演的未知量约束与可辨识性链条</h4>
                
                <div class="deck-card__body">
                  <div class="deck-section">
                    <b>针对问题：</b>
                    <p>在未知日人流量的前提下直接估计使用时间，数学上属于欠定系统，一维网格搜索得到的 MAP 估计值缺乏约束。</p>
                  </div>
                  <div class="deck-section deck-section--guidance">
                    <b>建议补充的方面（可执行操作）：</b>
                    <p>补充对反演关系的未知量、约束条件与误差传播说明：引入日人流量先验分布（基于周边场景人流统计或双向分布假设），形成 (n, t) 联合后验估计；核对输入、参数、约束与输出闭合性，明确当前结论的适用条件。</p>
                  </div>
                  <div class="deck-section deck-section--acceptance">
                    <b>完成标准（验收准则）：</b>
                    <ul>
                      <li>明确定义先验分布设定依据，反演算法能够输出包含人流与时间置信区间的后验分布。</li>
                      <li>正文中对欠定性消除路径与误差传递上界做出数学阐述，与附录算法伪代码保持严格一致。</li>
                    </ul>
                  </div>
                </div>
              </article>

              <article class="suggestion-deck-card">
                <div class="deck-card__header">
                  <div class="deck-card__tags">
                    <span class="guidance-badge guidance-badge--required">必要修正</span>
                    <span class="type-badge">公式推导</span>
                    <span class="priority-badge">P1 优先级</span>
                  </div>
                  <span class="deck-card__location"><FileSearch :size="13" aria-hidden="true" />论文第 10 页 · 3.1 磨损模型</span>
                </div>
                <h4 class="deck-card__title">S-2 · 修正磨料磨损简化公式 (B130) 的量纲不守恒与参数定义</h4>
                
                <div class="deck-card__body">
                  <div class="deck-section">
                    <b>针对问题：</b>
                    <p>磨料磨损简化公式遗漏法向载荷 P，强行将无量纲几何系数 tan θ 与带牛顿量纲的载荷乘积等同化简，导致物理量纲断层。</p>
                  </div>
                  <div class="deck-section deck-section--guidance">
                    <b>建议补充的方面（可执行操作）：</b>
                    <p>恢复 Archard 理论的标准量纲表达式 V = (K · P · L) / (3H)，明确符号说明表中 P 的物理意义（标准单人自重分力）及单位 [N]；规范载荷与摩擦系数的化简假设，消除正文与附录代码中的量纲冲突。</p>
                  </div>
                  <div class="deck-section deck-section--acceptance">
                    <b>完成标准（验收准则）：</b>
                    <ul>
                      <li>公式等号两侧物理量纲严格平衡为 [L³]，并在正文符号表中补充载荷量纲说明。</li>
                    </ul>
                  </div>
                </div>
              </article>

              <article class="suggestion-deck-card deck-card--exploration">
                <div class="deck-card__header">
                  <div class="deck-card__tags">
                    <span class="guidance-badge guidance-badge--optional">可选探索</span>
                    <span class="type-badge">高分升华</span>
                    <span class="priority-badge">P2 思考方向</span>
                  </div>
                  <span class="deck-card__location"><FileSearch :size="13" aria-hidden="true" />论文第 16 页 · 3.3 Wear Distribution Model</span>
                </div>
                <h4 class="deck-card__title">S-7 · 完善横向多峰磨损模型中高斯分量权重与通行通道映射</h4>
                
                <div class="deck-card__body">
                  <div class="deck-section">
                    <b>思考拓展：</b>
                    <p>多峰横向磨损反映了不同通道（靠扶手侧 vs 中央侧）频率加权混合。将等权求和拓展为带归一化混合权重系数的高斯混合模型，可进一步拟合双向流量不对称与偏好侧行走的真实复杂场景。</p>
                  </div>
                  <div class="deck-section deck-section--acceptance">
                    <b>完成标准：</b>
                    <ul>
                      <li>公式中包含表征各通道通行相对频率的加权系数，给出非线性拟合与参数分解路径。</li>
                    </ul>
                  </div>
                </div>
              </article>
            </div>
          </div>

            <div class="slice-footer-note">
              <div class="footer-live-badge">
                <span class="pulse-dot"></span>
                <span>端到端流水线已跑通</span>
              </div>
              <span>本切面数据抽取自后台实际生成的 JSON 报告实体，包含完整量表、具体数值推导及行号证据；平台训练评分用于论文模拟实训，不代表赛事官方成绩。</span>
            </div>
          </div>
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
import { computed, ref } from 'vue'
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

const activeSlice = ref('review')

const realReviewDimensions = [
  { label: '结构规范与排版可读性', score: 9.3, max: 20, eval: '结构完整，但缺少定量摘要闭环与充分的附录代码说明。' },
  { label: '题意理解与假设符号规范', score: 12.5, max: 15, eval: '假设合理且有行为学解释；磨料磨损公式有确定性量纲遗漏。' },
  { label: '数学形式化建模推导', score: 20.0, max: 25, eval: 'Archard 动力学解构切中题意；表 11 数据反向变动存在严重冲突。' },
  { label: '求解算法与程序复现', score: 16.0, max: 20, eval: 'Delaunay 网格微元求和科学实用；贝叶斯反演存在欠定性。' },
  { label: '结果合理性与灵敏度检验', score: 16.0, max: 20, eval: '算例分析完备，但缺少关键扰动步长下的定量灵敏度表格。' },
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
