# 为什么 Knife4j 文档页弹 HTTP Basic 登录框？

> 日期：2026-07-26
> 相关模块：common-security

---

## 问题

启动 user 服务后，访问 `http://localhost:8081/doc.html`，浏览器弹出 HTTP Basic Auth 登录框，要求输入用户名密码。

## 根因

Spring Security 默认启用 **HTTP Basic 认证**。即使我们在 `SecurityConfig` 中配置了：

```java
.requestMatchers("/api/auth/**", "/doc.html", "/v3/api-docs/**").permitAll()
```

`permitAll()` 只意味着"通过认证的用户都能访问"，但 Spring Security 仍然会走认证流程——`WWW-Authenticate: Basic` 头被返回给浏览器，浏览器自动弹出登录框。

### 流程

```
请求 /doc.html
  → Spring Security Filter Chain
    → HTTP Basic 认证过滤器检测到无认证信息
    → 返回 401 + WWW-Authenticate: Basic 头
    → 浏览器自动弹框
```

`permitAll()` 对公开路径来说是不够的——需要彻底关闭不需要的认证机制。

## 修复

在 `SecurityConfig` 中显式关闭 HTTP Basic 和 Form Login：

```java
http
    .csrf(AbstractHttpConfigurer::disable)
    .httpBasic(AbstractHttpConfigurer::disable)   // ← 关键：关掉 Basic Auth
    .formLogin(AbstractHttpConfigurer::disable)   // ← 关掉默认登录页
    .logout(AbstractHttpConfigurer::disable)      // ← Sa-Token 接管登出
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**", "/doc.html", "/v3/api-docs/**").permitAll()
        .anyRequest().authenticated()
    )
    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

## 面试可讲点

> "Spring Security 默认启用了 HTTP Basic 认证和 Form Login。在 JWT 无状态架构下，这两种机制都不需要——认证由 Gateway 层的 JWT 验签完成，鉴权由 Sa-Token 的注解处理。我们显式关闭它们，只保留 Spring Security 的 Filter Chain 做请求拦截和路径放行，避免浏览器弹框这种不符合前后端分离架构的行为。"
