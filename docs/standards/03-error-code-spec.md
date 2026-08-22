# 统一错误码规范

> 创建日期：2026-07-26
> 影响范围：common-core（Result, ErrorCodeEnum）、common-security（AuthExceptionHandler）、各业务模块 ErrorCode 枚举

---

## 一、编码规范：A-BB-CC 五段式

```
A - BB - CC
│   │    └── 具体错误序号，从 01 递增
│   └─────── 业务模块，00=全局 01=认证鉴权 02=用户 03=团队...
└─────────── 响应场景，2=成功 4=客户端错误/业务阻断 5=服务端错误
```

### A 位（场景类型）

| A | 含义 | 说明 |
|----|------|------|
| 2 | 成功响应 | 所有正常返回 |
| 4 | 客户端错误 / 业务阻断 | 参数校验失败、资源不存在、业务逻辑拒绝 |
| 5 | 服务端错误 / 第三方异常 | 系统内部异常、外部服务不可用 |

### BB 位（业务模块）

| BB | 模块 | 示例 |
|----|------|------|
| 00 | 全局通用 | 参数校验、404、系统异常 |
| 01 | 认证鉴权 | 未登录、权限不足、角色不匹配 |
| 02 | 用户管理 | 用户不存在、用户名重复、密码错误 |
| 03 | 团队管理 | 预留 |
| 04 | 题目管理 | 预留 |
| 05 | AI 客服 | 预留 |
| 06 | 作品提交 | 预留 |
| 07 | 评审打分 | 预留 |
| 08 | 改进建议 | 预留 |
| 09 | 排行榜 | 预留 |
| 10 | 管理后台 | 预留 |

---

## 二、已定义错误码

### BB=00：全局通用（ErrorCodeEnum）

| 常量 | 编码 | 说明 |
|------|------|------|
| SUCCESS | 20000 | 操作成功 |
| PARAM_INVALID | 40001 | 参数校验失败 |
| NOT_FOUND | 40002 | 资源不存在 |
| METHOD_NOT_ALLOWED | 40003 | 请求方法不允许 |
| RATE_LIMITED | 40004 | 请求过于频繁 |
| SYSTEM_ERROR | 50001 | 系统内部错误 |

### BB=01：认证鉴权（AuthExceptionHandler）

| 常量 | 编码 | 说明 |
|------|------|------|
| (NotLoginException) | 40101 | 未登录 |
| (NotPermissionException) | 40103 | 权限不足 |
| (NotRoleException) | 40104 | 角色不满足 |

### BB=02：用户模块（UserErrorCode）

| 常量 | 编码 | 说明 |
|------|------|------|
| USER_NOT_FOUND | 40201 | 用户不存在 |
| USERNAME_DUPLICATE | 40202 | 用户名已被占用 |
| PASSWORD_INVALID | 40203 | 密码错误 |
| TOKEN_EXPIRED | 40204 | 登录已过期 |
| ACCOUNT_DISABLED | 40205 | 账号已被禁用 |
| PASSWORD_OLD_INVALID | 40206 | 旧密码错误 |
| PASSWORD_SAME_AS_OLD | 40207 | 新密码不能与旧密码相同 |
| STORAGE_NOT_ENABLED | 40208 | 对象存储未启用 |
| ROLE_NOT_FOUND | 40209 | 角色不存在 |
| ROLE_CODE_DUPLICATE | 40210 | 角色编码已存在 |
| PERMISSION_NOT_FOUND | 40211 | 权限不存在 |
| PERMISSION_CODE_DUPLICATE | 40212 | 权限编码已存在 |
| PERMISSION_IN_USE | 40213 | 权限仍被角色使用 |
| SYSTEM_ROLE_PROTECTED | 40214 | 系统预设角色受保护 |
| DEFAULT_ROLE_NOT_FOUND | 40215 | 系统默认角色不存在 |

---

## 三、新增模块错误码规范

1. 在服务的 `enums/` 包下新建 `XxxErrorCode.java`
2. 实现 `com.leetmodel.common.core.exception.ErrorCode` 接口
3. 按 BB 位选取号段，CC 从 01 递增
4. 错误消息面向用户，不应暴露内部实现细节
5. 服务端 5xxxx 错误统一不暴露给客户端，使用 `ErrorCodeEnum.SYSTEM_ERROR` 兜底

```java
// 示例：团队模块 BB=03
@Getter
@AllArgsConstructor
public enum TeamErrorCode implements ErrorCode {
    TEAM_NOT_FOUND(40301, "队伍不存在"),
    TEAM_FULL(40302, "队伍已满（最多3人）"),
    NOT_TEAM_LEADER(40303, "只有队长可以执行此操作"),
    ;
    private final int code;
    private final String message;
}
```

---

## 四、关键设计决策

- **20000 而不是 0**：0 缺乏语义，20000 明确属于"成功"区间，便于监控和日志过滤
- **4xxxx 业务阻断 vs 5xxxx 系统异常**：前者是"用户的问题"（用户名重复），后者是"我们的问题"（数据库挂了），日志级别和处理策略不同
- **异常详情不暴露**：`SYSTEM_ERROR(50001, "系统内部错误")` — 不向前端返回堆栈信息，只记日志。面试可讲"防止信息泄露"
