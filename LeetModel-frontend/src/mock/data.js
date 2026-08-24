// 仍被未对接页面使用的临时数据。完成相应后端接口后应逐项移除。

export const mockTags = [
  { tagId: 1, name: '时间序列', usageCount: 128 },
  { tagId: 2, name: '神经网络', usageCount: 115 },
  { tagId: 3, name: '遗传算法', usageCount: 96 },
  { tagId: 4, name: '动态规划', usageCount: 82 },
  { tagId: 5, name: '线性回归', usageCount: 78 },
  { tagId: 6, name: '聚类分析', usageCount: 71 },
  { tagId: 7, name: '图论模型', usageCount: 63 },
  { tagId: 8, name: '微分方程', usageCount: 58 },
  { tagId: 9, name: '蒙特卡洛', usageCount: 52 },
  { tagId: 10, name: '梯度下降', usageCount: 47 },
]

export const mockSubmissions = [
  { submissionId: 1, problemId: 1, problemTitle: '城市交通流量预测', version: 3, status: 'COMPLETED', totalScore: 88.5, submitTime: '2026-03-15' },
  { submissionId: 2, problemId: 2, problemTitle: '生态环境综合评价', version: 2, status: 'COMPLETED', totalScore: 91.2, submitTime: '2026-03-20' },
  { submissionId: 3, problemId: 3, problemTitle: '供应链库存优化', version: 1, status: 'PENDING', totalScore: null, submitTime: '2026-04-02' },
]

export const mockUserProfile = {
  totalSolved: 47,
  easySolved: 12,
  mediumSolved: 28,
  hardSolved: 7,
  weeklySolved: 5,
  totalSubmissions: 89,
  acceptanceRate: 0.53,
  skillScores: [
    { dimension: '模型设计', score: 78, fullScore: 100 },
    { dimension: '算法实现', score: 85, fullScore: 100 },
    { dimension: '数据处理', score: 72, fullScore: 100 },
    { dimension: '结果分析', score: 80, fullScore: 100 },
    { dimension: '论文写作', score: 68, fullScore: 100 },
  ],
}
