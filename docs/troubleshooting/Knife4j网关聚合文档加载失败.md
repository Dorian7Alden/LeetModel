# Knife4j 网关聚合文档加载失败

## 现象

Knife4j 网关聚合页面 /doc.html 能打开，但存在两类问题：

1. 左侧分组加载接口文档失败，请求 /api/problems/v3/api-docs 被网关 Sa-Token 拦截返回 40100，或转发到下游服务后因路径不存在返回 404 或 500。
2. 接口路径显示或调试时出现 /api/xxx/api/xxx 的重复前缀。

## 根因

1. Knife4j 网关聚合在 discover 模式下，读取 Spring Cloud Gateway 中每个 lb:// 路由的 Path 前缀，拼接 /v3/api-docs 生成各服务的文档地址。
2. 网关路由只做转发不做前缀剥离，请求 /api/problems/v3/api-docs 会以完整路径到达下游服务。
3. 下游服务默认只在 /v3/api-docs 暴露 OpenAPI 文档，完整路径没有对应端点。
4. 网关 Sa-Token 白名单只放行了 /v3/api-docs/**，没有放行带路由前缀的文档路径。
5. discover 模式生成的 swagger-config 会带上 contextPath 路由前缀，Knife4j 前端会把 contextPath 再拼到 OpenAPI 已有路径上，形成 /api/xxx/api/xxx 重复前缀。

## 修复

1. 各服务将 springdoc.api-docs.path 配置为网关路由前缀加 /v3/api-docs：
   - user-service：/api/auth/v3/api-docs
   - problem-service：/api/problems/v3/api-docs
   - team-service：/api/teams/v3/api-docs
   - admin-service：/api/admin/dashboard/v3/api-docs
2. 网关 SaTokenConfig 白名单放行上述路径。
3. common-security 的 SecurityConfig 对上述路径 permitAll。
4. 网关 Knife4j 聚合从 discover 模式改为 manual 模式，手动声明各服务的文档地址，并将 context-path 设为 /，使 swagger-config 中 contextPath 为空，避免前端重复拼接前缀。
