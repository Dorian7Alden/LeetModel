# modules

> 各微服务模块的功能设计文档。描述模块职责和大方向，不写过于具体的实现细节。

---

## 文档索引

| 文档 | 内容摘要 | 查看场景 |
|------|---------|---------|
| [common-core-design.md](common-core-design.md) | 公共基础模块：统一响应体、异常体系、分页封装、基类 | 开发任何服务前了解公共能力 |
| [common-api-design.md](common-api-design.md) | Feign 接口声明模块：跨服务调用契约与降级策略 | 新增服务间调用时参考 |
| [common-security-design.md](common-security-design.md) | 安全模块：认证鉴权、Sa-Token + JWT 集成方案 | 处理登录/权限相关功能时参考 |
| [user-service-design.md](user-service-design.md) | 用户服务：注册、登录、用户信息管理 | 开发用户模块功能时参考 |
| [problem-service-design.md](problem-service-design.md) | 题目服务：题目 CRUD、分类标签、全文搜索 | 开发题目模块功能时参考 |
