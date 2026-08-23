# Spring Security 与 Sa-Token 重复认证导致空 403

## 报错现象

通过 Gateway 登录成功并取得 Token 后，请求 `/api/users/me` 仍返回 HTTP 403，响应体为空。Gateway 健康检查返回未登录，user-service 健康检查也返回 403。

## 根因分析

Gateway 已使用 Sa-Token 验证 JWT，但 user-service 中的 Spring Security 又配置了所有请求必须存在 Spring `Authentication`。Sa-Token 登录态不会自动写入 Spring SecurityContext，因此请求在业务控制器之前被第二套认证机制拦截。

空响应体来自 Spring Security 过滤器链，未进入项目的统一异常处理器，所以前端拿不到业务错误码和消息。

## 修复方案

- Gateway 负责外部请求的 Sa-Token 登录态校验。
- 业务服务通过 Sa-Token 注解执行角色和权限校验。
- Spring Security 关闭默认登录能力并放行请求，只保留密码编码等基础组件。
- Gateway 白名单加入 `/actuator/health`、其子路径和 `/actuator/info`。
- Gateway 放行浏览器 OPTIONS 预检请求，由 CORS 过滤器返回跨域响应头。
- 认证鉴权异常继续由 Sa-Token 异常处理器转换为统一 `Result`。

## 验证结果

- Gateway 与 user-service 的健康检查均返回 HTTP 200。
- 登录后可以正常访问当前用户资料和当前用户授权信息。
- 未登录访问受保护接口时由 Gateway 返回带业务错误码和消息的统一响应。
