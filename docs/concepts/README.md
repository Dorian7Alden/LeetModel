# concepts

> 后端开发与软件工程的通用概念知识。对技术原理的结构化讲解，不绑定某次具体报错。面试中可直接用于回答理论题。

---

## 文档索引

| 文档 | 核心问题 | 查看场景 |
|------|---------|---------|
| [认证与鉴权的区别.md](认证与鉴权的区别.md) | Authentication 和 Authorization 是什么、有什么区别 | 面试被问安全相关概念时 |
| [微服务中获取当前用户信息的方式.md](微服务中获取当前用户信息的方式.md) | 为什么普通用户查个人信息用 /me 而不是传 userId，UserContext 的作用 | 面试被问微服务认证设计或越权防护时 |
| [DTO与VO的区别与命名场景.md](DTO与VO的区别与命名场景.md) | dto 中的 Response 和 vo 中的 VO 有什么区别，什么场景用哪个 | 面试被问后端对象分层或接口设计时 |
| [微服务中面向客户端与内部Controller的区分.md](微服务中面向客户端与内部Controller的区分.md) | 对外 Controller 和 Internal Controller 的区别，以及为什么不按目录拆分 | 面试被问微服务接口分层或 Controller 组织时 |
| [SpringSecurity自动弹出HTTPBasic登录框.md](SpringSecurity自动弹出HTTPBasic登录框.md) | 为什么引入 Spring Security 后页面弹出登录框 | 遇到类似现象或面试被问 Spring Security 默认行为时 |
| [SpringBoot组件扫描机制.md](SpringBoot组件扫描机制.md) | @SpringBootApplication 为什么扫不到其他模块的 Bean | 多模块项目 Bean 注入失败时排查 |
| [YAML配置键唯一性与SpringBoot配置加载.md](YAML配置键唯一性与SpringBoot配置加载.md) | 为什么 YAML 重复键导致启动失败、配置加载顺序 | 遇到 YAML 解析错误或面试被问配置加载机制时 |
