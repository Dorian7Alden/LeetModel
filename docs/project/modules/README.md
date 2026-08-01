# modules

> 各微服务模块的功能设计文档。描述模块职责和大方向，不写过于具体的实现细节。

---

## 文档索引

| 文档 | 内容摘要 | 查看场景 |
|------|---------|---------|
| [common-core-design.md](common-core-design.md) | 公共基础模块：统一响应体、异常体系、分页封装、基类、MinIO 对象存储 | 开发任何服务前了解公共能力 |
| [common-api-design.md](common-api-design.md) | Feign 接口声明模块：跨服务调用契约与降级策略 | 新增服务间调用时参考 |
| [common-security-design.md](common-security-design.md) | 安全模块：认证鉴权、Sa-Token + JWT 集成方案 | 处理登录/权限相关功能时参考 |
| [user-service-design.md](user-service-design.md) | 用户服务：注册、登录、用户信息管理、RBAC 权限管理 | 开发用户模块功能时参考 |
| [gateway-design.md](gateway-design.md) | API 网关：路由转发、JWT 鉴权、跨域处理、API 文档聚合 | 了解网关的职责、路由规则和统一文档入口 |
| [problem-service-design.md](problem-service-design.md) | 题目服务：4 表 CRUD、标签管理、外部链接、分页筛选（端口 8083） | 开发题目模块功能时参考 |
| [team-service-design.md](team-service-design.md) | 团队服务：创建/解散、成员管理、权限控制（端口 8082） | 开发团队功能时参考 |
| [submission-service-design.md](submission-service-design.md) | 提交服务：PDF 分片上传、提交归属、上传权限模型、决策记录（端口 8085） | 开发论文提交功能时参考 |
| [admin-service-design.md](admin-service-design.md) | 管理后台服务：数据聚合、Feign 跨服务统计（端口 8084） | 开发管理功能时参考 |
| [pdf-parsing-design.md](pdf-parsing-design.md) | PDF 解析：多模态 LLM + PDFBox 解析路线、解析产物 schema 契约、失败表达与决策记录 | 开发解析功能或消费解析产物时参考 |
