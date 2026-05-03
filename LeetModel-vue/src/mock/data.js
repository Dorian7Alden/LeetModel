/**
 * Mock data for LeetModel frontend development.
 * All data shapes mirror the database table structures from sqls/.
 * Import and wrap in ref() as needed in components.
 */

// ==================== Users ====================
export const mockUsers = [
  { userId: 1001, username: '建模学者', email: 'scholar@tsinghua.edu.cn', school: '清华大学', phone: '13800001001', status: 1, role: 'SUPER_ADMIN', createTime: '2025-09-01' },
  { userId: 1002, username: '数模达人', email: 'master@pku.edu.cn', school: '北京大学', phone: '13800001002', status: 1, role: 'ADMIN', createTime: '2025-09-15' },
  { userId: 1003, username: '算法新手', email: 'newbie@zju.edu.cn', school: '浙江大学', phone: '13800001003', status: 1, role: 'MEMBER', createTime: '2025-10-01' },
  { userId: 1004, username: '优化专家', email: 'optimizer@sjtu.edu.cn', school: '上海交通大学', phone: '13800001004', status: 1, role: 'MEMBER', createTime: '2025-10-15' },
  { userId: 1005, username: '数据行者', email: 'datawh@nju.edu.cn', school: '南京大学', phone: '13800001005', status: 1, role: 'MEMBER', createTime: '2025-11-01' },
  { userId: 1006, username: '模型工匠', email: 'modeler@hit.edu.cn', school: '哈尔滨工业大学', phone: '13800001006', status: 1, role: 'MEMBER', createTime: '2025-11-15' },
  { userId: 1007, username: '统计高手', email: 'stats@fudan.edu.cn', school: '复旦大学', phone: '13800001007', status: 1, role: 'MEMBER', createTime: '2025-12-01' },
  { userId: 1008, username: '编程达人', email: 'coder@ustc.edu.cn', school: '中国科学技术大学', phone: '13800001008', status: 1, role: 'MEMBER', createTime: '2025-12-15' },
]

// ==================== Tag Categories ====================
export const mockTagCategories = [
  { categoryId: 1, name: '模型类型', code: 'MODEL_TYPE', description: '数学建模所使用的模型分类', sortOrder: 1, isMultiple: true, isRequired: true, status: 1 },
  { categoryId: 2, name: '算法方法', code: 'ALGORITHM', description: '求解模型的算法方法', sortOrder: 2, isMultiple: true, isRequired: false, status: 1 },
  { categoryId: 3, name: '应用领域', code: 'DOMAIN', description: '模型应用领域', sortOrder: 3, isMultiple: true, isRequired: true, status: 1 },
  { categoryId: 4, name: '难度等级', code: 'DIFFICULTY', description: '题目难度分级', sortOrder: 4, isMultiple: false, isRequired: true, status: 1 },
  { categoryId: 5, name: '编程语言', code: 'LANGUAGE', description: '编程实现语言', sortOrder: 5, isMultiple: false, isRequired: false, status: 1 },
  { categoryId: 6, name: '工具平台', code: 'TOOL', description: '使用的工具软件', sortOrder: 6, isMultiple: true, isRequired: false, status: 1 },
]

// ==================== Tags ====================
export const mockTags = [
  // Model types
  { tagId: 101, categoryId: 1, name: '线性回归', description: '线性回归模型', sortOrder: 1, usageCount: 245, status: 1 },
  { tagId: 102, categoryId: 1, name: '时间序列', description: '时间序列分析与预测', sortOrder: 2, usageCount: 189, status: 1 },
  { tagId: 103, categoryId: 1, name: '神经网络', description: '人工神经网络模型', sortOrder: 3, usageCount: 312, status: 1 },
  { tagId: 104, categoryId: 1, name: '决策树', description: '决策树与随机森林', sortOrder: 4, usageCount: 156, status: 1 },
  { tagId: 105, categoryId: 1, name: '支持向量机', description: 'SVM模型', sortOrder: 5, usageCount: 134, status: 1 },
  { tagId: 106, categoryId: 1, name: '聚类分析', description: 'K-Means、层次聚类等', sortOrder: 6, usageCount: 198, status: 1 },
  { tagId: 107, categoryId: 1, name: '图论模型', description: '图论与网络流模型', sortOrder: 7, usageCount: 167, status: 1 },
  { tagId: 108, categoryId: 1, name: '微分方程', description: '常微分与偏微分方程', sortOrder: 8, usageCount: 143, status: 1 },
  // Algorithms
  { tagId: 201, categoryId: 2, name: '梯度下降', description: '梯度下降优化算法', sortOrder: 1, usageCount: 278, status: 1 },
  { tagId: 202, categoryId: 2, name: '遗传算法', description: '遗传算法与进化计算', sortOrder: 2, usageCount: 201, status: 1 },
  { tagId: 203, categoryId: 2, name: '模拟退火', description: '模拟退火优化', sortOrder: 3, usageCount: 89, status: 1 },
  { tagId: 204, categoryId: 2, name: '动态规划', description: '动态规划算法', sortOrder: 4, usageCount: 234, status: 1 },
  { tagId: 205, categoryId: 2, name: '蒙特卡洛', description: '蒙特卡洛模拟方法', sortOrder: 5, usageCount: 156, status: 1 },
  { tagId: 206, categoryId: 2, name: '蚁群算法', description: '蚁群优化算法', sortOrder: 6, usageCount: 67, status: 1 },
  // Domains
  { tagId: 301, categoryId: 3, name: '交通物流', description: '交通运输与物流优化', sortOrder: 1, usageCount: 210, status: 1 },
  { tagId: 302, categoryId: 3, name: '金融经济', description: '金融建模与经济分析', sortOrder: 2, usageCount: 189, status: 1 },
  { tagId: 303, categoryId: 3, name: '环境科学', description: '环境建模与生态分析', sortOrder: 3, usageCount: 145, status: 1 },
  { tagId: 304, categoryId: 3, name: '生物医学', description: '生物信息与医学建模', sortOrder: 4, usageCount: 132, status: 1 },
  { tagId: 305, categoryId: 3, name: '社会科学', description: '社会网络与行为建模', sortOrder: 5, usageCount: 98, status: 1 },
  { tagId: 306, categoryId: 3, name: '能源电力', description: '能源系统与电力优化', sortOrder: 6, usageCount: 167, status: 1 },
  // Difficulties
  { tagId: 401, categoryId: 4, name: '入门', description: '适合初学者', sortOrder: 1, usageCount: 89, status: 1 },
  { tagId: 402, categoryId: 4, name: '中等', description: '需要一定基础', sortOrder: 2, usageCount: 234, status: 1 },
  { tagId: 403, categoryId: 4, name: '困难', description: '综合应用能力', sortOrder: 3, usageCount: 156, status: 1 },
  { tagId: 404, categoryId: 4, name: '挑战', description: '竞赛级别难度', sortOrder: 4, usageCount: 78, status: 1 },
  // Languages
  { tagId: 501, categoryId: 5, name: 'Python', description: 'Python编程', sortOrder: 1, usageCount: 456, status: 1 },
  { tagId: 502, categoryId: 5, name: 'MATLAB', description: 'MATLAB编程', sortOrder: 2, usageCount: 312, status: 1 },
  { tagId: 503, categoryId: 5, name: 'R', description: 'R语言', sortOrder: 3, usageCount: 89, status: 1 },
  { tagId: 504, categoryId: 5, name: 'C++', description: 'C++编程', sortOrder: 4, usageCount: 67, status: 1 },
  // Tools
  { tagId: 601, categoryId: 6, name: 'Jupyter', description: 'Jupyter Notebook', sortOrder: 1, usageCount: 345, status: 1 },
  { tagId: 602, categoryId: 6, name: 'SPSS', description: 'SPSS统计分析', sortOrder: 2, usageCount: 123, status: 1 },
  { tagId: 603, categoryId: 6, name: 'LINGO', description: 'LINGO优化求解', sortOrder: 3, usageCount: 89, status: 1 },
  { tagId: 604, categoryId: 6, name: 'Tableau', description: 'Tableau可视化', sortOrder: 4, usageCount: 56, status: 1 },
]

// ==================== Problems ====================
export const mockProblems = [
  {
    problemId: 1, problemTitle: '城市交通流量预测模型', aveScore: 87.5, problemStatus: 1, creatorId: 1001,
    difficulty: '中等', language: 'Python',
    tags: ['时间序列', '神经网络', '交通物流'],
    description: '基于历史交通数据，建立城市主干道交通流量预测模型，要求考虑早晚高峰、节假日等影响因素，预测未来24小时流量变化趋势。',
    createTime: '2026-01-15', updateTime: '2026-03-20',
    submissionCount: 234, passRate: 0.72
  },
  {
    problemId: 2, problemTitle: '投资组合优化问题', aveScore: 82.3, problemStatus: 1, creatorId: 1001,
    difficulty: '困难', language: 'Python',
    tags: ['线性回归', '动态规划', '金融经济'],
    description: '在给定风险约束下，构建最优投资组合模型。要求使用Markowitz均值-方差模型，考虑交易成本和最小持有量约束。',
    createTime: '2026-01-20', updateTime: '2026-04-01',
    submissionCount: 189, passRate: 0.65
  },
  {
    problemId: 3, problemTitle: '图像去噪与增强', aveScore: 90.1, problemStatus: 1, creatorId: 1002,
    difficulty: '中等', language: 'MATLAB',
    tags: ['神经网络', '聚类分析', '生物医学'],
    description: '对医学CT图像进行去噪和增强处理。要求实现多种滤波算法对比（均值、中值、高斯、小波变换），评估PSNR和SSIM指标。',
    createTime: '2026-02-01', updateTime: '2026-04-10',
    submissionCount: 156, passRate: 0.78
  },
  {
    problemId: 4, problemTitle: '共享单车需求预测', aveScore: 85.7, problemStatus: 1, creatorId: 1002,
    difficulty: '中等', language: 'Python',
    tags: ['时间序列', '梯度下降', '交通物流'],
    description: '利用城市共享单车历史骑行数据，预测各区域未来一小时的单车需求量。需考虑天气、节假日、地铁站距离等因素。',
    createTime: '2026-02-10', updateTime: '2026-04-05',
    submissionCount: 278, passRate: 0.81
  },
  {
    problemId: 5, problemTitle: '多目标路径规划', aveScore: 78.4, problemStatus: 1, creatorId: 1001,
    difficulty: '挑战', language: 'C++',
    tags: ['图论模型', '遗传算法', '交通物流'],
    description: '在考虑距离、时间、能耗的多目标约束下，设计城市物流配送最优路径。使用NSGA-II多目标优化算法求解Pareto最优解集。',
    createTime: '2026-02-20', updateTime: '2026-04-15',
    submissionCount: 112, passRate: 0.52
  },
  {
    problemId: 6, problemTitle: '时间序列异常检测', aveScore: 88.2, problemStatus: 1, creatorId: 1003,
    difficulty: '中等', language: 'Python',
    tags: ['时间序列', '支持向量机', '金融经济'],
    description: '对金融交易时间序列数据进行异常检测，识别潜在的欺诈交易模式。使用Isolation Forest和LOF算法进行对比分析。',
    createTime: '2026-03-01', updateTime: '2026-04-20',
    submissionCount: 201, passRate: 0.76
  },
  {
    problemId: 7, problemTitle: '空气质量预测模型', aveScore: 83.6, problemStatus: 1, creatorId: 1003,
    difficulty: '中等', language: 'Python',
    tags: ['时间序列', '神经网络', '环境科学'],
    description: '基于历史气象数据和污染物浓度数据，建立PM2.5浓度预测模型。使用LSTM网络进行多步预测，评估RMSE和MAE指标。',
    createTime: '2026-03-10', updateTime: '2026-04-25',
    submissionCount: 167, passRate: 0.71
  },
  {
    problemId: 8, problemTitle: '社区发现算法对比', aveScore: 86.9, problemStatus: 1, creatorId: 1002,
    difficulty: '困难', language: 'Python',
    tags: ['聚类分析', '图论模型', '社会科学'],
    description: '在社交网络数据上实现多种社区发现算法（Louvain、Label Propagation、Girvan-Newman），对比模块度、运行效率等指标。',
    createTime: '2026-03-15', updateTime: '2026-05-01',
    submissionCount: 89, passRate: 0.68
  },
  {
    problemId: 9, problemTitle: '电力负荷预测', aveScore: 84.1, problemStatus: 1, creatorId: 1004,
    difficulty: '困难', language: 'MATLAB',
    tags: ['时间序列', '支持向量机', '能源电力'],
    description: '对区域电网负荷进行短期预测（未来48小时）。考虑温度、湿度、日期类型等特征，对比SVR和XGBoost模型性能。',
    createTime: '2026-03-20', updateTime: '2026-05-05',
    submissionCount: 134, passRate: 0.69
  },
  {
    problemId: 10, problemTitle: '蛋白质结构预测入门', aveScore: 91.3, problemStatus: 1, creatorId: 1004,
    difficulty: '入门', language: 'Python',
    tags: ['神经网络', '聚类分析', '生物医学'],
    description: '使用简化的蛋白质序列数据，预测其二级结构类型（α螺旋、β折叠、无规卷曲）。入门级题目，适合学习基本的序列特征提取方法。',
    createTime: '2026-04-01', updateTime: '2026-05-10',
    submissionCount: 312, passRate: 0.89
  },
  {
    problemId: 11, problemTitle: '水库调度优化', aveScore: 79.8, problemStatus: 1, creatorId: 1001,
    difficulty: '挑战', language: 'Python',
    tags: ['微分方程', '遗传算法', '能源电力'],
    description: '建立水库群联合调度优化模型，平衡发电、防洪、灌溉等多目标需求。使用动态规划与遗传算法的混合策略。',
    createTime: '2026-04-10', updateTime: '2026-05-15',
    submissionCount: 67, passRate: 0.45
  },
  {
    problemId: 12, problemTitle: '推荐系统算法实现', aveScore: 88.7, problemStatus: 1, creatorId: 1002,
    difficulty: '中等', language: 'Python',
    tags: ['聚类分析', '梯度下降', '社会科学'],
    description: '基于协同过滤和矩阵分解技术，实现电影推荐系统。要求使用SVD和NMF两种方法，对比RMSE和Top-N推荐准确率。',
    createTime: '2026-04-15', updateTime: '2026-05-20',
    submissionCount: 245, passRate: 0.83
  },
]

// ==================== Posts (Community) ====================
export const mockPosts = [
  {
    postId: 1, publisherId: 1002, publisherName: '数模达人', publisherAvatar: '',
    type: 'experience', title: '2026美赛F题经验分享：从选题到论文',
    content: `## 赛前准备\n\n我们团队在赛前做了充分准备，主要包括：\n\n1. **文献调研**：提前阅读了近3年美赛O奖论文\n2. **工具准备**：Python + MATLAB + LaTeX\n3. **分工明确**：建模、编程、写作各司其职\n\n## 选题分析\n\nF题通常涉及政策分析，需要建立综合评价模型。我们选择了AHP-TOPSIS组合方法...\n\n## 建模过程\n\n### 第一阶段：问题分析\n花了大约4小时深入理解问题背景，识别关键变量和约束条件。\n\n### 第二阶段：模型构建\n使用层次分析法确定指标权重，结合TOPSIS进行方案排序...`,
    likeCnt: 234, commentCnt: 45, viewCnt: 1890, heat: 458, isTop: 1, status: 'published',
    tags: ['#经验分享', '#美赛', '#建模技巧'],
    createTime: '2026-04-20'
  },
  {
    postId: 2, publisherId: 1003, publisherName: '算法新手', publisherAvatar: '',
    type: 'discuss', title: 'LSTM与Transformer在时间序列预测中的选择？',
    content: `最近在做时间序列预测相关的题目，发现LSTM和Transformer各有优劣。\n\nLSTM在长序列上训练较快，但容易遗忘早期信息。Transformer的注意力机制能捕捉全局依赖，但计算量大。\n\n大家在实践中更倾向哪种？有没有结合两者优势的混合架构推荐？`,
    likeCnt: 156, commentCnt: 32, viewCnt: 1230, heat: 312, isTop: 0, status: 'published',
    tags: ['#技术讨论', '#深度学习', '#时间序列'],
    createTime: '2026-04-22'
  },
  {
    postId: 3, publisherId: 1001, publisherName: '建模学者', publisherAvatar: '',
    type: 'skill', title: '数学建模中常用的数据处理技巧（Python版）',
    content: `## 缺失值处理\n\n\`\`\`python\n# 多重插补\nfrom sklearn.impute import KNNImputer\nimputer = KNNImputer(n_neighbors=5)\ndata_filled = imputer.fit_transform(data)\n\`\`\`\n\n## 异常值检测\n\n使用IQR方法：\n\n\`\`\`python\nQ1 = df['col'].quantile(0.25)\nQ3 = df['col'].quantile(0.75)\nIQR = Q3 - Q1\nlower = Q1 - 1.5 * IQR\nupper = Q3 + 1.5 * IQR\n\`\`\`\n\n## 特征工程\n\n- 标准化/归一化\n- 对数变换处理偏态分布\n- 独热编码与标签编码\n- 多项式特征生成\n\n更多技巧持续更新中...`,
    likeCnt: 312, commentCnt: 28, viewCnt: 2560, heat: 567, isTop: 0, status: 'published',
    tags: ['#技巧教程', '#Python', '#数据处理'],
    createTime: '2026-04-18'
  },
  {
    postId: 4, publisherId: 1005, publisherName: '数据行者', publisherAvatar: '',
    type: 'experience', title: '从零开始拿国奖：一个大二学生的建模之路',
    content: `大一暑假才开始接触数学建模，到现在拿下国赛一等奖，分享一些心得。\n\n## 我的学习路线\n\n1. **基础阶段（2个月）**：姜启源《数学模型》+ MOOC课程\n2. **编程阶段（1个月）**：Python数据分析三件套 + MATLAB入门\n3. **实战阶段（2个月）**：刷往年赛题 + 模拟训练\n4. **冲刺阶段（1个月）**：限时模拟 + 论文写作\n\n## 几点建议\n\n- 团队很重要，找志同道合的队友\n- 不要贪多，深入掌握几种模型即可\n- 论文写作要提前练习，LaTeX是必备技能`,
    likeCnt: 478, commentCnt: 67, viewCnt: 3890, heat: 823, isTop: 1, status: 'published',
    tags: ['#经验分享', '#国赛', '#学习路线'],
    createTime: '2026-04-15'
  },
  {
    postId: 5, publisherId: 1006, publisherName: '模型工匠', publisherAvatar: '',
    type: 'discuss', title: '大家怎么看今年国赛可能增加的AI辅助限制？',
    content: `听说今年国赛可能会出台新规定，限制使用大语言模型等AI工具辅助论文写作。\n\n我觉得这是必然趋势，但也有些矛盾：\n- 一方面，过度依赖AI确实有违竞赛初衷\n- 另一方面，AI工具已成为科研常态，完全禁止是否合理？\n\n大家怎么看？有没有了解具体政策的同学分享一下？`,
    likeCnt: 198, commentCnt: 89, viewCnt: 2100, heat: 445, isTop: 0, status: 'published',
    tags: ['#讨论交流', '#国赛', '#AI工具'],
    createTime: '2026-04-24'
  },
  {
    postId: 6, publisherId: 1004, publisherName: '优化专家', publisherAvatar: '',
    type: 'skill', title: '运筹优化模型求解工具对比：Gurobi vs CPLEX vs SCIP',
    content: `## 商业求解器\n\n### Gurobi\n- 优点：速度最快，Python接口友好\n- 缺点：价格昂贵（学术免费）\n- 适合：大规模线性/整数规划\n\n### CPLEX\n- 优点：稳定性好，功能全面\n- 缺点：学习曲线陡峭\n- 适合：企业级应用\n\n## 开源替代\n\n### SCIP\n- 优点：完全免费，支持约束规划\n- 缺点：速度稍慢\n- 适合：学术研究、中小规模问题\n\n## 总结推荐\n\n学术用途首选Gurobi（申请学术许可），备选SCIP。`,
    likeCnt: 267, commentCnt: 34, viewCnt: 1980, heat: 398, isTop: 0, status: 'published',
    tags: ['#技巧教程', '#优化求解', '#工具推荐'],
    createTime: '2026-04-19'
  },
  {
    postId: 7, publisherId: 1007, publisherName: '统计高手', publisherAvatar: '',
    type: 'experience', title: '全国大学生统计建模大赛经验复盘',
    content: `## 比赛流程\n\n统计建模大赛与数模竞赛有些不同：\n1. 主题自选（有参考主题）\n2. 时间跨度长（约2个月）\n3. 更注重数据分析和统计方法的正确使用\n\n## 我们的选题\n\n选择了"数字经济对区域发展的影响分析"，使用了面板数据回归模型...\n\n## 踩过的坑\n\n1. 数据收集阶段花了太多时间\n2. 统计方法选择不够严谨\n3. 论文格式细节被扣分\n\n希望对准备参赛的同学有帮助！`,
    likeCnt: 189, commentCnt: 23, viewCnt: 1450, heat: 298, isTop: 0, status: 'published',
    tags: ['#经验分享', '#统计建模', '#比赛复盘'],
    createTime: '2026-04-17'
  },
  {
    postId: 8, publisherId: 1008, publisherName: '编程达人', publisherAvatar: '',
    type: 'discuss', title: '数学建模中的可视化：ECharts vs Plotly vs Matplotlib？',
    content: `在数学建模论文中，可视化效果直接影响评委的第一印象。大家更推荐哪个可视化工具？\n\n- **Matplotlib**：功能强大但默认样式丑\n- **Plotly**：交互式图表，支持Web展示\n- **ECharts**：前端渲染，适合Web应用\n- **Tableau**：拖拽式操作，快速出图\n\n我目前主力是Plotly，但排版到LaTeX中有时会有像素问题...`,
    likeCnt: 145, commentCnt: 41, viewCnt: 1670, heat: 334, isTop: 0, status: 'published',
    tags: ['#讨论交流', '#可视化', '#工具选择'],
    createTime: '2026-04-21'
  },
]

// ==================== Comments ====================
export const mockComments = [
  { commentId: 1, postId: 1, userId: 1003, userName: '算法新手', parentId: null, content: '非常感谢分享！请问AHP的一致性检验你们是怎么处理的？', likeCnt: 12, status: 'normal', createTime: '2026-04-21' },
  { commentId: 2, postId: 1, userId: 1002, userName: '数模达人', parentId: 1, content: '我们使用了CR<0.1作为通过标准，不通过时会手动调整判断矩阵。推荐用yaanp软件辅助计算。', likeCnt: 8, status: 'normal', createTime: '2026-04-21' },
  { commentId: 3, postId: 1, userId: 1005, userName: '数据行者', parentId: null, content: '同问，我们队也是F题，但在模型评价部分做得不够好，可以详细说说吗？', likeCnt: 5, status: 'normal', createTime: '2026-04-22' },
  { commentId: 4, postId: 4, userId: 1003, userName: '算法新手', parentId: null, content: '大二就拿国一，太强了！请问数学基础需要到什么程度？', likeCnt: 15, status: 'normal', createTime: '2026-04-16' },
  { commentId: 5, postId: 4, userId: 1005, userName: '数据行者', parentId: 4, content: '个人感觉高数、线代、概率论的基础就够了，重要的是建模思维。', likeCnt: 10, status: 'normal', createTime: '2026-04-16' },
  { commentId: 6, postId: 2, userId: 1006, userName: '模型工匠', parentId: null, content: '可以试试Informer，专门为长序列时间序列设计的Transformer变体。', likeCnt: 18, status: 'normal', createTime: '2026-04-23' },
]

// ==================== Contests ====================
export const mockContests = [
  {
    id: 1, title: '2026年全国大学生数学建模竞赛', shortTitle: '国赛',
    type: '国赛', typeColor: '#d97706',
    signUpStartTime: '2026-05-01', signUpEndTime: '2026-06-15',
    startTime: '2026-09-10', endTime: '2026-09-13',
    status: '报名中', statusColor: '#16a34a',
    participantCount: 15234,
    introduction: '全国大学生数学建模竞赛（CUMCM）是中国规模最大的数学建模赛事，每年吸引数万支队伍参赛。竞赛题目涵盖工程、经济、管理、环境等多个领域。',
    organizer: '中国工业与应用数学学会',
    prizeLevels: ['国家级一等奖', '国家级二等奖', '省级一等奖', '省级二等奖'],
    phases: [
      { name: '报名阶段', start: '2026-05-01', end: '2026-06-15' },
      { name: '竞赛阶段', start: '2026-09-10', end: '2026-09-13' },
      { name: '评审阶段', start: '2026-09-20', end: '2026-11-15' },
      { name: '结果公布', start: '2026-11-20', end: '2026-11-30' },
    ]
  },
  {
    id: 2, title: '2027年美国大学生数学建模竞赛', shortTitle: '美赛',
    type: '美赛', typeColor: '#2563eb',
    signUpStartTime: '2026-11-01', signUpEndTime: '2027-01-15',
    startTime: '2027-02-17', endTime: '2027-02-21',
    status: '未开始', statusColor: '#64748b',
    participantCount: 28500,
    introduction: '美国大学生数学建模竞赛（MCM/ICM）是全球最具影响力的数学建模赛事之一。MCM侧重数学建模，ICM侧重交叉学科建模。',
    organizer: 'COMAP',
    prizeLevels: ['Outstanding Winner', 'Finalist', 'Meritorious Winner', 'Honorable Mention', 'Successful Participant'],
    phases: [
      { name: '报名阶段', start: '2026-11-01', end: '2027-01-15' },
      { name: '竞赛阶段', start: '2027-02-17', end: '2027-02-21' },
      { name: '评审阶段', start: '2027-03-01', end: '2027-04-30' },
    ]
  },
  {
    id: 3, title: '2026年五一数学建模联赛', shortTitle: '五一赛',
    type: '校赛', typeColor: '#16a34a',
    signUpStartTime: '2026-04-01', signUpEndTime: '2026-04-28',
    startTime: '2026-05-01', endTime: '2026-05-04',
    status: '即将开始', statusColor: '#d97706',
    participantCount: 3420,
    introduction: '五一数学建模联赛是由中国矿业大学发起的全国性赛事，旨在为大学生提供数学建模实践平台。',
    organizer: '中国矿业大学',
    prizeLevels: ['一等奖', '二等奖', '三等奖', '优秀奖'],
    phases: [
      { name: '竞赛阶段', start: '2026-05-01', end: '2026-05-04' },
      { name: '评审阶段', start: '2026-05-10', end: '2026-06-10' },
    ]
  },
  {
    id: 4, title: '2026年"深圳杯"数学建模挑战赛', shortTitle: '深圳杯',
    type: '校赛', typeColor: '#8b5cf6',
    signUpStartTime: '2026-06-01', signUpEndTime: '2026-07-31',
    startTime: '2026-08-15', endTime: '2026-08-20',
    status: '未开始', statusColor: '#64748b',
    participantCount: 2100,
    introduction: '深圳杯数学建模挑战赛以实际应用为导向，赛题紧密联系深圳及大湾区发展需求。',
    organizer: '深圳市科学技术协会',
    prizeLevels: ['特等奖', '一等奖', '二等奖', '三等奖'],
    phases: [
      { name: '报名阶段', start: '2026-06-01', end: '2026-07-31' },
      { name: '竞赛阶段', start: '2026-08-15', end: '2026-08-20' },
    ]
  },
  {
    id: 5, title: '2026年APMCM亚太地区数学建模竞赛', shortTitle: 'APMCM',
    type: '国际赛', typeColor: '#0891b2',
    signUpStartTime: '2026-09-01', signUpEndTime: '2026-11-20',
    startTime: '2026-11-25', endTime: '2026-11-29',
    status: '未开始', statusColor: '#64748b',
    participantCount: 8900,
    introduction: 'APMCM亚太地区大学生数学建模竞赛面向亚太地区高校，旨在促进数学建模方法的跨学科应用。',
    organizer: '亚太数学建模学会',
    prizeLevels: ['Grand Prize', 'First Prize', 'Second Prize', 'Third Prize'],
    phases: [
      { name: '报名阶段', start: '2026-09-01', end: '2026-11-20' },
      { name: '竞赛阶段', start: '2026-11-25', end: '2026-11-29' },
    ]
  },
  {
    id: 6, title: '2026年全国大学生统计建模大赛', shortTitle: '统计建模',
    type: '国赛', typeColor: '#d97706',
    signUpStartTime: '2026-03-01', signUpEndTime: '2026-04-30',
    startTime: '2026-05-15', endTime: '2026-07-15',
    status: '进行中', statusColor: '#2563eb',
    participantCount: 6780,
    introduction: '全国大学生统计建模大赛由中国统计教育学会主办，聚焦统计学方法在实际问题中的创新应用。',
    organizer: '中国统计教育学会',
    prizeLevels: ['一等奖', '二等奖', '三等奖', '优秀奖'],
    phases: [
      { name: '报名与选题', start: '2026-03-01', end: '2026-04-30' },
      { name: '作品提交', start: '2026-05-15', end: '2026-07-15' },
      { name: '评审阶段', start: '2026-08-01', end: '2026-10-01' },
    ]
  },
]

// ==================== Submissions ====================
export const mockSubmissions = [
  { submissionId: 1, problemId: 1, userId: 1003, userName: '算法新手', title: '基于LSTM的交通流量预测', status: 'COMPLETED', totalScore: 88.5, submitTime: '2026-03-15', completeTime: '2026-03-15' },
  { submissionId: 2, problemId: 1, userId: 1005, userName: '数据行者', title: '融合多源数据的流量预测方法', status: 'COMPLETED', totalScore: 91.2, submitTime: '2026-03-20', completeTime: '2026-03-20' },
  { submissionId: 3, problemId: 2, userId: 1004, userName: '优化专家', title: '基于NSGA-II的投资组合优化', status: 'COMPLETED', totalScore: 85.7, submitTime: '2026-04-01', completeTime: '2026-04-01' },
  { submissionId: 4, problemId: 3, userId: 1006, userName: '模型工匠', title: '小波变换结合深度学习的图像去噪', status: 'COMPLETED', totalScore: 93.4, submitTime: '2026-04-10', completeTime: '2026-04-10' },
  { submissionId: 5, problemId: 10, userId: 1003, userName: '算法新手', title: 'CNN蛋白质二级结构预测', status: 'COMPLETED', totalScore: 90.8, submitTime: '2026-04-15', completeTime: '2026-04-15' },
  { submissionId: 6, problemId: 4, userId: 1007, userName: '统计高手', title: '时空图卷积共享单车需求预测', status: 'EVALUATING', totalScore: null, submitTime: '2026-05-01', completeTime: null },
  { submissionId: 7, problemId: 12, userId: 1008, userName: '编程达人', title: 'SVD++推荐系统实现', status: 'COMPLETED', totalScore: 87.3, submitTime: '2026-04-25', completeTime: '2026-04-25' },
]

// ==================== Review Dimensions (matching review table) ====================
export const mockReviews = [
  { reviewId: 1, submissionId: 1, dimensionCode: 'MODEL_DESIGN', dimensionName: '模型设计', score: 86, weight: 0.30, feedback: '模型选择合理，LSTM适用于时序预测。但缺少与传统方法的对比分析，建议补充ARIMA基线模型。', status: 'COMPLETED' },
  { reviewId: 2, submissionId: 1, dimensionCode: 'ALGORITHM', dimensionName: '算法实现', score: 90, weight: 0.25, feedback: '代码结构清晰，超参数调优过程完整。使用了Dropout和EarlyStopping防止过拟合。', status: 'COMPLETED' },
  { reviewId: 3, submissionId: 1, dimensionCode: 'DATA_PROCESS', dimensionName: '数据处理', score: 85, weight: 0.15, feedback: '数据预处理步骤完整，但异常值处理部分可以更详细。特征工程维度可进一步丰富。', status: 'COMPLETED' },
  { reviewId: 4, submissionId: 1, dimensionCode: 'RESULT_ANALYSIS', dimensionName: '结果分析', score: 92, weight: 0.20, feedback: '结果可视化清晰，误差分析全面。对不同时间粒度的预测效果进行了详细对比。', status: 'COMPLETED' },
  { reviewId: 5, submissionId: 1, dimensionCode: 'PAPER_QUALITY', dimensionName: '论文质量', score: 88, weight: 0.10, feedback: '论文结构规范，图表清晰。摘要部分可以更简洁地概括主要贡献。', status: 'COMPLETED' },
]

export const mockReviewDimensions = [
  { code: 'MODEL_DESIGN', name: '模型设计', weight: 0.30, description: '评估模型选择合理性、创新性与问题匹配度' },
  { code: 'ALGORITHM', name: '算法实现', weight: 0.25, description: '评估算法实现质量、效率与代码规范性' },
  { code: 'DATA_PROCESS', name: '数据处理', weight: 0.15, description: '评估数据预处理、特征工程的质量' },
  { code: 'RESULT_ANALYSIS', name: '结果分析', weight: 0.20, description: '评估结果可视化、分析与模型评估的完整性' },
  { code: 'PAPER_QUALITY', name: '论文质量', weight: 0.10, description: '评估论文结构、表达清晰度与格式规范性' },
]

// ==================== Profile / User Stats ====================
export const mockUserProfile = {
  userId: 1003,
  username: '算法新手',
  email: 'newbie@zju.edu.cn',
  school: '浙江大学',
  joinDate: '2025-10-01',
  ranking: 1234,
  totalSolved: 47,
  easySolved: 12,
  mediumSolved: 28,
  hardSolved: 7,
  weeklySolved: 5,
  streak: 12,
  totalSubmissions: 89,
  acceptanceRate: 0.53,
  followers: 128,
  following: 56,
  badges: [
    { id: 1, name: '初出茅庐', description: '完成第一道题目', icon: 'CircleCheck', color: '#16a34a', unlocked: true, unlockDate: '2025-10-15' },
    { id: 2, name: '持之以恒', description: '连续打卡7天', icon: 'Calendar', color: '#2563eb', unlocked: true, unlockDate: '2025-11-01' },
    { id: 3, name: '解题达人', description: '完成20道题目', icon: 'Trophy', color: '#d97706', unlocked: true, unlockDate: '2026-01-10' },
    { id: 4, name: '精益求精', description: '单题得分超过95分', icon: 'Star', color: '#8b5cf6', unlocked: false, unlockDate: null },
    { id: 5, name: '社区之星', description: '发布帖子获得100个点赞', icon: 'ChatLineSquare', color: '#0891b2', unlocked: false, unlockDate: null },
    { id: 6, name: '竞赛达人', description: '参加3次竞赛', icon: 'Medal', color: '#dc2626', unlocked: true, unlockDate: '2026-03-20' },
  ],
  skillScores: [
    { dimension: '模型设计', score: 78, fullScore: 100 },
    { dimension: '算法实现', score: 85, fullScore: 100 },
    { dimension: '数据处理', score: 72, fullScore: 100 },
    { dimension: '结果分析', score: 80, fullScore: 100 },
    { dimension: '论文写作', score: 68, fullScore: 100 },
  ],
  recentActivity: [
    { type: 'submission', problemTitle: '蛋白质结构预测入门', score: 90.8, status: 'completed', time: '2026-05-02T14:30' },
    { type: 'submission', problemTitle: '社区发现算法对比', score: 86.9, status: 'completed', time: '2026-04-28T10:15' },
    { type: 'post', title: 'LSTM与Transformer在时间序列预测中的选择？', likes: 156, time: '2026-04-22T16:00' },
    { type: 'submission', problemTitle: '图像去噪与增强', score: 85.0, status: 'completed', time: '2026-04-18T09:45' },
    { type: 'badge', badgeName: '竞赛达人', time: '2026-03-20T12:00' },
    { type: 'submission', problemTitle: '投资组合优化问题', score: 82.3, status: 'completed', time: '2026-03-15T15:30' },
  ],
  historyData: [
    { date: '2026-05-02', count: 3 },
    { date: '2026-05-01', count: 1 },
    { date: '2026-04-30', count: 2 },
    { date: '2026-04-29', count: 0 },
    { date: '2026-04-28', count: 4 },
    { date: '2026-04-27', count: 1 },
    { date: '2026-04-26', count: 2 },
  ]
}

// ==================== Teams ====================
export const mockTeams = [
  { teamId: 1, name: '建模先锋队', description: '专注数学建模竞赛，已有2年合作经验', memberCount: 3, maxMembers: 3, members: ['建模学者', '数模达人', '算法新手'], missingRoles: [], createTime: '2025-10-01' },
  { teamId: 2, name: '数据分析小组', description: '数据分析与机器学习方向', memberCount: 2, maxMembers: 3, members: ['数据行者', '统计高手'], missingRoles: ['编程'], createTime: '2025-11-15' },
  { teamId: 3, name: '优化研究组', description: '运筹优化与组合算法方向', memberCount: 2, maxMembers: 3, members: ['优化专家', '模型工匠'], missingRoles: ['写作'], createTime: '2025-12-01' },
  { teamId: 4, name: '深度学习工作室', description: '深度学习与计算机视觉', memberCount: 3, maxMembers: 4, members: ['编程达人', '模型工匠', '算法新手'], missingRoles: [], createTime: '2026-01-10' },
  { teamId: 5, name: '统计学习联盟', description: '统计学习与时间序列分析', memberCount: 2, maxMembers: 4, members: ['统计高手', '数据行者'], missingRoles: ['建模', '编程'], createTime: '2026-02-20' },
  { teamId: 6, name: '竞赛冲刺组', description: '2026美赛F题冲奖队', memberCount: 3, maxMembers: 3, members: ['数模达人', '优化专家', '编程达人'], missingRoles: [], createTime: '2026-03-01' },
]

// ==================== Links ====================
export const mockLinks = [
  { linkId: 1, linkTitle: 'Python官方文档', linkUrl: 'https://docs.python.org/zh-cn/3/', description: 'Python 3 中文文档', status: 1 },
  { linkId: 2, linkTitle: 'MATLAB中文论坛', linkUrl: 'https://www.ilovematlab.cn/', description: 'MATLAB技术交流社区', status: 1 },
  { linkId: 3, linkTitle: 'Kaggle竞赛平台', linkUrl: 'https://www.kaggle.com/', description: '数据科学竞赛平台', status: 1 },
  { linkId: 4, linkTitle: '全国大学生数学建模竞赛官网', linkUrl: 'http://www.mcm.edu.cn/', description: '国赛官方信息发布', status: 1 },
]

// ==================== Dashboard Stats ====================
export const mockDashboardStats = [
  { title: '总题目数', value: 124, icon: 'DocumentCopy', color: '#2563eb', bgColor: '#eff6ff', trend: '+12%', trendUp: true, subtitle: '较上月' },
  { title: '总作品数', value: 890, icon: 'DataLine', color: '#16a34a', bgColor: '#f0fdf4', trend: '+23%', trendUp: true, subtitle: '较上月' },
  { title: '今日提交', value: 12, icon: 'Upload', color: '#d97706', bgColor: '#fffbeb', trend: '-5%', trendUp: false, subtitle: '较昨日' },
  { title: '待审核', value: 5, icon: 'Warning', color: '#dc2626', bgColor: '#fef2f2', trend: '-2', trendUp: true, subtitle: '较昨日' },
]

export const mockSubmissionTrend = [
  { date: '04-03', count: 45 }, { date: '04-06', count: 52 }, { date: '04-09', count: 38 },
  { date: '04-12', count: 61 }, { date: '04-15', count: 55 }, { date: '04-18', count: 48 },
  { date: '04-21', count: 72 }, { date: '04-24', count: 65 }, { date: '04-27', count: 58 },
  { date: '04-30', count: 80 }, { date: '05-03', count: 76 },
]

export const mockProblemStatusDist = [
  { name: '已发布', value: 89, color: '#16a34a' },
  { name: '草稿', value: 18, color: '#d97706' },
  { name: '已下线', value: 12, color: '#64748b' },
  { name: '已归档', value: 5, color: '#dc2626' },
]

// ==================== Role & Permission (RBAC) ====================
export const mockRoles = [
  { roleId: 1, name: '系统管理员', code: 'SUPER_ADMIN', description: '最高权限，可进行RBAC管理及所有操作', status: 1, createTime: '2025-01-01' },
  { roleId: 2, name: '普通管理员', code: 'ADMIN', description: '可管理题目、标签、作品等业务内容', status: 1, createTime: '2025-01-01' },
  { roleId: 3, name: '成员', code: 'MEMBER', description: '普通注册用户，无管理权限', status: 1, createTime: '2025-01-01' },
]

export const mockPermissions = [
  { permissionId: 1, name: '首页概览', code: 'DASHBOARD_VIEW', description: '查看管理端首页', status: 1 },
  { permissionId: 2, name: '查看用户', code: 'USER_VIEW', description: '查看用户列表与详情', status: 1 },
  { permissionId: 3, name: '修改用户', code: 'USER_UPDATE', description: '修改用户信息', status: 1 },
  { permissionId: 4, name: '删除用户', code: 'USER_DELETE', description: '删除用户', status: 1 },
  { permissionId: 5, name: '查看题目', code: 'PROBLEM_VIEW', description: '查看题目列表与详情', status: 1 },
  { permissionId: 6, name: '管理题目', code: 'PROBLEM_MANAGE', description: '题目CRUD', status: 1 },
  { permissionId: 7, name: '查看作品', code: 'SUBMISSION_VIEW', description: '查看作品列表', status: 1 },
  { permissionId: 8, name: '管理作品', code: 'SUBMISSION_MANAGE', description: '作品CRUD', status: 1 },
  { permissionId: 9, name: '查看标签', code: 'TAG_VIEW', description: '查看标签分类与标签', status: 1 },
  { permissionId: 10, name: '管理标签', code: 'TAG_MANAGE', description: '标签CRUD', status: 1 },
  { permissionId: 11, name: '查看帖子', code: 'POST_VIEW', description: '查看帖子列表', status: 1 },
  { permissionId: 12, name: '查看赛事', code: 'CONTEST_VIEW', description: '查看赛事列表', status: 1 },
  { permissionId: 13, name: '查看角色', code: 'ROLE_VIEW', description: '查看角色列表与关联权限', status: 1 },
  { permissionId: 14, name: '管理角色', code: 'ROLE_MANAGE', description: '角色CRUD', status: 1 },
  { permissionId: 15, name: '查看权限', code: 'PERMISSION_VIEW', description: '查看权限列表', status: 1 },
  { permissionId: 16, name: '管理权限', code: 'PERMISSION_MANAGE', description: '权限CRUD', status: 1 },
  { permissionId: 17, name: '授权管理', code: 'AUTH_MANAGE', description: '用户-角色、角色-权限关联', status: 1 },
  { permissionId: 18, name: '文件上传', code: 'FILE_UPLOAD', description: '上传文件到OSS', status: 1 },
]
