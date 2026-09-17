# admin 服务查询审计报 403 导致中央审计暂不可用

> 创建日期：2026-09-03  
> 影响范围：`admin-service`、`audit-service`、前端 `AuditPage.vue`

---

## 报错现象

在前端管理端页面中，访问“审计”板块时，界面上方弹出黄色/橙色警示栏：
> **中央审计暂不可用**  
> `系统内部错误`（或 `audit-service 查询失败`）  
> 页面不会用空集合伪装成功；请按 Runbook 检查 audit-service、Broker 和本地 Outbox。

对应日志与网络请求表现如下：

1. **浏览器网络请求**：
   - 请求 `GET /api/admin/audit/events` 返回 HTTP 200，但响应体为：
     ```json
     {"code": 50001, "message": "系统内部错误", "data": null}
     ```
2. **`admin-service` 日志**：
   - 抛出 `feign.FeignException$Forbidden: [403 Forbidden] during [GET] to [http://audit-service/internal/audit/events?limit=50] [AuditFeignClient#search(...)]`；
   - 未被业务层捕获，最终由 `GlobalExceptionHandler` 兜底转换为 `50001 系统内部错误`。
3. **`audit-service` 控制台/日志**：
   - 收到来自 `admin-service` 的请求，但直接在过滤器层被拦截，响应 `403 Forbidden ("audit internal access denied")`。

---

## 根因分析

这是微服务内部只读接口安全边界与本地服务发现机制之间的冲突所导致的：

### 1. audit-service 内部只读接口的安全令牌边界

按照架构设计，`audit-service` 独占不可变审计库 `lm_audit`，对外不直接暴露公网端点，仅向受信的 `admin-service` 提供只读查询接口（`/internal/audit/**`）。

为防止外部伪造请求或未授权微服务横向越权探测，`audit-service` 挂载了 `AuditInternalAccessFilter`：
- 要求外部请求必须在 HTTP Header 中携带 `X-LeetModel-Audit-Token`，并使用恒定时间比较 `MessageDigest.isEqual` 与系统配置的 `${AUDIT_INTERNAL_TOKEN:}` 进行比对。
- **回退机制**：当 `AUDIT_INTERNAL_TOKEN` 为空（`token == null`）时，系统降级为地址检查：
  ```java
  boolean allowed = token == null
          ? isLoopback(request.getRemoteAddr())
          : presented != null && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
  ```
  最初实现的 `isLoopback` 仅硬编码匹配了回环地址：
  ```java
  private boolean isLoopback(String address) {
      return address != null && (address.startsWith("127.") || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address));
  }
  ```

### 2. 本地开发环境未配置默认内部令牌

在 `admin-service` 和 `audit-service` 的 `application.yml` 与 `application-dev.yml` 中，均未为 `AUDIT_INTERNAL_TOKEN` 声明默认值。本地直接运行微服务时，两端获取到的 `token` 均为 `null`/空字符串。

- `admin-service` 端：`AuditFeignConfig` 判断 `token == null || token.isBlank()` 为真，因此**不向请求注入 `X-LeetModel-Audit-Token` 请求头**。
- `audit-service` 端：`token == null` 生效，进入 `isLoopback(request.getRemoteAddr())` 兜底检查。

### 3. Nacos 服务发现解析 LAN IP 导致 loopback 判定失效

在 Spring Cloud Alibaba 体系下，服务启动后自动向 Nacos 注册本机的物理/局域网网卡 IP（例如 `10.135.189.84`），而不是 `127.0.0.1`。

当 `admin-service` 通过 Feign 调用 `lb://audit-service` 时：
1. Spring Cloud LoadBalancer 从 Nacos 服务列表中获取到 `10.135.189.84:8094`；
2. HTTP TCP 连接从本机网卡 `10.135.189.84` 发往 `10.135.189.84:8094`；
3. `audit-service` 端获取到的 `request.getRemoteAddr()` 为 `10.135.189.84`；
4. `isLoopback("10.135.189.84")` 判定为 `false`；
5. 请求被无条件返回 `403 Forbidden`，导致本地微服务即使全量启动，管理端审计也永远无法连通。

---

## 修复方案

采取**“服务令牌安全默认值 + 本机网卡回退防护”**的双层防御策略：

### 1. 配置安全默认令牌（主链路）

遵循项目现有本地安全配置模式（如 `AUDIT_DB_APP_PASSWORD:lm-audit-app-local-only-change-me`），在代码和配置中为 `AUDIT_INTERNAL_TOKEN` 提供本地开发安全默认值：

- **`AuditFeignConfig.java`**：
  ```java
  @Bean
  RequestInterceptor auditInternalTokenInterceptor(
          @Value("${AUDIT_INTERNAL_TOKEN:lm-audit-internal-local-only-change-me}") String token) {
      return template -> {
          if (token != null && !token.isBlank()) {
              template.header("X-LeetModel-Audit-Token", token);
          }
      };
  }
  ```
- **`AuditInternalAccessFilter.java`**：
  ```java
  public AuditInternalAccessFilter(@Value("${AUDIT_INTERNAL_TOKEN:lm-audit-internal-local-only-change-me}") String configuredToken) {
      this.token = configuredToken == null || configuredToken.isBlank()
              ? null : configuredToken.getBytes(StandardCharsets.UTF_8);
  }
  ```
- **`application-dev.yml`**：在 `admin-service` 与 `audit-service` 中显式配置：
  ```yaml
  audit:
    internal-token: ${AUDIT_INTERNAL_TOKEN:lm-audit-internal-local-only-change-me}
  ```
- **`start-mvp.sh`**：在脚本启动环境变量中追加 `AUDIT_INTERNAL_TOKEN`。

在生产环境中，部署平台（如 K8s Secret 或环境变量）直接注入高强度 `AUDIT_INTERNAL_TOKEN` 即可实现安全覆盖。

### 2. 增强 `isLoopback` 本机网卡识别（兜底链路）

若在某些特殊测试场景下未配置令牌，重构 `AuditInternalAccessFilter.isLoopback`，引入 `NetworkInterface.getByInetAddress(addr) != null` 检查请求 IP 是否属于宿主机上的任一网络接口：

```java
private boolean isLoopback(String address) {
    if (address == null || address.isBlank()) {
        return false;
    }
    if (address.startsWith("127.") || "::1".equals(address)
            || "0:0:0:0:0:0:0:1".equals(address)) {
        return true;
    }
    try {
        InetAddress addr = InetAddress.getByName(address);
        return addr.isLoopbackAddress() || NetworkInterface.getByInetAddress(addr) != null;
    } catch (Exception ignored) {
        return false;
    }
}
```

即使在没有令牌的极端情况下，同一个宿主机上跑的多微服务也能通过局域网 IP 成功通过本地访问核验。

### 3. 补充单元测试防护

新增 `AuditInternalAccessFilterTest`，完整验证 5 种场景：
- 令牌匹配时：即使来自外部或局域网 IP 也成功放行；
- 令牌不匹配时：直接返回 403 Forbidden；
- 令牌为空时：来自本机网卡 IP / 回环地址成功放行；
- 令牌为空时：来自外网 IP 直接返回 403；
- 非 `/internal/audit/` 路径：无需内部认证直接放行。

---

## 验证

1. **静态契约测试**：运行 `mvn test -Dtest="AuditQueryContractTest,AuditInternalAccessFilterTest"`，全部通过。
2. **契约验证脚本**：运行 `./scripts/verify-audit-contract.sh`，审计静态契约校验通过。
3. **接口连通性验证**：带管理员 Token 访问 `http://127.0.0.1:8080/api/admin/audit/events`，返回 HTTP 200 且 `code: 20000`，`data.events` 正常加载。
4. **前端管理端渲染**：访问管理端 `/admin/audit`，原“中央审计暂不可用”警示条完全消失，操作事件列表与时间线正常渲染。

---

## 经验与避坑建议

1. **微服务内部调用不等于“127.0.0.1 本机调用”**：
   在 Spring Cloud / K8s / Docker 容器化或注册中心环境下，服务发现返回给客户端的永远是局域网虚拟网卡 IP（Pod IP、宿主机 LAN IP）。如果服务间权限完全依赖 `remoteAddr == 127.0.0.1`，会在接入注册中心或容器化部署的第一天全部瘫痪。
2. **跨服务内部安全必须显式声明服务间凭证**：
   微服务内部接口（如 `/internal/**`）与对外开放的业务接口必须物理或逻辑解耦。通过自定义 Header（如 `X-*-Token`）传递专有内部服务令牌是标准且轻量的跨服务访问控制实践。
3. **安全配置的“开发开箱即用”与“生产强制覆盖”兼顾**：
   本地开发时，应当为必须的服务间密码、密钥、内部令牌提供明确标注的默认值（如 `-local-only-change-me`），避免每次克隆仓库或新增开发者时都要手动配置十几项未公开环境变量才能跑通主链；同时在生产 profile 或 CI 流程中校验并强制覆盖这些默认值。
