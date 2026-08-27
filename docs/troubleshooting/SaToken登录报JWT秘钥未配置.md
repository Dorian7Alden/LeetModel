# Sa-Token "请配置jwt秘钥" — 密钥未设到 SaTokenConfig

> 日期：2026-07-26 | 模块：common-security（SecuritySaTokenConfig）

---

## 报错信息

```
cn.dev33.satoken.jwt.exception.SaJwtException: 请配置jwt秘钥
    at cn.dev33.satoken.jwt.exception.SaJwtException.throwByNull(...)
    at cn.dev33.satoken.jwt.StpLogicJwtForStateless.jwtSecretKey(...)
    at cn.dev33.satoken.jwt.StpLogicJwtForStateless.createTokenValue(...)
    at cn.dev33.satoken.stp.StpLogic.login(...)
    at com.leetmodel.common.security.util.TokenUtil.login(...)
    at com.leetmodel.user.service.impl.AuthServiceImpl.login(...)
```

## 复现场景

1. 调用 `POST /api/auth/login`（注册成功，但登录时签发 Token）
2. `TokenUtil.login(userId)` → `StpLogic.createTokenValue()` → `jwtSecretKey()` → `return getConfigOrGlobal().getJwtSecretKey()` → **null** → 抛异常

## 根因

**对 `StpLogicJwtForStateless(String)` 构造参数的误解**。直觉以为传的是 JWT 密钥，实际上它是 `loginType`（传递给父类 `StpLogic` 的构造函数）。

通过反编译字节码确认：
```java
// 构造参数 → 传给父类作为 loginType，不是密钥
public StpLogicJwtForStateless(String loginType) {
    super(loginType);
}

// 密钥从 SaTokenConfig 读取，不由构造参数决定
public String jwtSecretKey() {
    return getConfigOrGlobal().getJwtSecretKey();
}
```

## 修复

```java
// ❌ 错误：把构造参数当密钥
StpLogicJwtForStateless stpLogic = new StpLogicJwtForStateless(jwtSecretKey);
cn.dev33.satoken.config.SaTokenConfig config = new cn.dev33.satoken.config.SaTokenConfig();
config.setTimeout(timeout);
// ← 遗漏了 config.setJwtSecretKey(jwtSecretKey)
stpLogic.setConfig(config);

// ✅ 正确：密钥必须设到 SaTokenConfig
StpLogicJwtForStateless stpLogic = new StpLogicJwtForStateless("login");
cn.dev33.satoken.config.SaTokenConfig config = new cn.dev33.satoken.config.SaTokenConfig();
config.setJwtSecretKey(jwtSecretKey);  // ← 关键行
config.setTimeout(timeout);
config.setIsConcurrent(false);
config.setIsLog(true);
stpLogic.setConfig(config);
```

## 排查时间线

| 尝试 | 假设 | 结果 |
|------|------|------|
| 1 | `@Value` 未注入 | 加 null 兜底 → 仍然报错 |
| 2 | `@Value` 不支持 hyphen | 改 camelCase → 仍然报错 |
| 3 | 反编译 `.class` 字节码 | 发现 `jwtSecretKey()` 从 `SaTokenConfig` 读 → **定位根因** |

## 反思

- **API 直觉不可靠**：不能"猜"构造参数含义，遇到问题直接看源码/反编译
- **框架 API 随版本变化**：Sa-Token 1.38.0 的 API 与旧版 `SaTokenForJwt` 完全不同
- **三层排除法有效**：配置注入 → 参数传递 → 源码验证，每次缩小范围

## 面试可讲点

> "遇到一个 Sa-Token JWT 密钥配置的坑。直觉以为构造参数是密钥，传进去没报错但运行时抛异常。反编译了 StpLogicJwtForStateless 的字节码才发现构造参数是 loginType，密钥必须通过 SaTokenConfig.setJwtSecretKey() 设置。这个过程体现了阅读框架源码定位问题的能力——不能靠猜 API，遇到问题直接看源码是最快的方式。"

