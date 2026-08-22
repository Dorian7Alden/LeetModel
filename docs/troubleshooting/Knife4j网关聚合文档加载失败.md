# Knife4j 网关聚合文档加载失败

## 现象

Knife4j 网关聚合页面 /doc.html 能打开，但左侧分组加载接口文档失败。网关 /v3/api-docs/swagger-config 返回的文档地址形如 /api/problems/v3/api-docs，请求该地址时被网关 Sa-Token 拦截返回 40100，或转发到下游服务后因路径不存在返回 404 或 500。

## 根因

1. Knife4j 网关聚合在 discover 模式下，读取 Spring Cloud Gateway 中每个 lb:// 路由的 Path 前缀，拼接 /v3/api-docs 生成各服务的文档地址。
2. 网关路由只做转发不做前缀剥离，请求 /api/problems/v3/api-docs 会以完整路径到达下游服务。
3. 下游服务默认只在 /v3/api-docs 暴露 OpenAPI 文档，完整路径没有对应端点。
4. 网关 Sa-Token 白名单只放行了 /v3/api-docs/**，没有放行带路由前缀的文档路径。

## 修复

1. 各服务将 springdoc.api-docs.path 配置为网关路由前缀加 /v3/api-docs：
   - user-service：/api/auth/v3/api-docs
   - problem-service：/api/problems/v3/api-docs
   - team-service：/api/teams/v3/api-docs
   - admin-service：/api/admin/dashboard/v3/api-docs
2. 网关 SaTokenConfig 白名单放行上述路径。
3. common-security 的 SecurityConfig 对上述路径 permitAll。
