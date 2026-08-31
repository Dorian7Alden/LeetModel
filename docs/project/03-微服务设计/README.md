# 微服务设计

> 本目录是项目微服务设计的导航入口。新增、拆分或更新设计前，必须阅读 [微服务设计文档规范](../../standards/12-microservice-design-document-spec.md)。实际模块名、接口和数据结构以当前代码为准。

---

## 服务索引

| 服务 | 职责 |
|------|------|
| [gateway-service/](gateway-service/) | 对外请求路由、认证鉴权、跨域和 API 文档聚合 |
| [ai-gateway-service/](ai-gateway-service/) | 模型路由、供应商接入、密钥、稳定性、Token 和成本治理 |
| [ai-evaluation-service/](ai-evaluation-service/) | AI 评审固定样本重复实验、评分稳定性统计和运行诊断 |
| [ai-assistant-service/](ai-assistant-service/) | AI 对话助手、平台问答和题目推荐 |
| [ai-suggestion-service/](ai-suggestion-service/) | AI 论文改善建议、建议任务和建议结果 |
| [knowledge-retrieval-service/](knowledge-retrieval-service/) | 独立知识检索服务；版本化 RAG、受控 AI 选文、来源适用性与检索快照 |
| [user-service/](user-service/) | 用户信息、登录和 RBAC 权限数据 |
| [team-service/](team-service/) | 队伍生命周期、成员和团队角色 |
| [problem-service/](problem-service/) | 预置赛事、题目、Markdown 题面、附件、标签和题库查询 |
| [submission-service/](submission-service/) | 论文上传、文件存储、提交版本和归属 |
| [ai-review-service/](ai-review-service/) | 版本化 AI 论文评审；V2 强制使用版本化 PDF 解析并输出证据说明 |
| [ranking-service/](ranking-service/) | 论文评分口径、题目排行和队伍排名查询 |
| [admin-service/](admin-service/) | 管理端聚合、AI 调用监控和 AI 测试控制 |
| [common/](common/) | 公共 Maven 模块，包含基础能力、服务调用、认证鉴权和 AI 调用客户端 |
