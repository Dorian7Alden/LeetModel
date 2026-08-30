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
| 11 | AI 质量评价 | 固定数据集、评价任务与依赖错误 |
| 12 | AI 网关 | 模型路由、能力校验与供应商错误 |

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

### BB=03：团队模块

| 常量 | 编码 | 说明 |
|------|------|------|
| TEAM_NOT_FOUND | 40301 | 团队不存在 |
| TEAM_FULL | 40302 | 团队已满 |
| USER_ALREADY_IN_TEAM | 40303 | 用户已在该团队中 |
| NOT_TEAM_MEMBER | 40304 | 用户不是该团队成员 |
| NOT_TEAM_LEADER | 40305 | 操作者不是队长 |
| CANNOT_REMOVE_LEADER | 40306 | 队长不能被移除 |
| LEADER_CANNOT_LEAVE | 40307 | 队长不能退出团队 |
| TEAM_ALREADY_DISBANDED | 40308 | 团队已解散 |
| USER_NOT_AVAILABLE | 40309 | 用户不存在或账号不可用 |
| TEAM_NOT_RECRUITING | 40310 | 团队未开放招募 |
| APPLICATION_ALREADY_PENDING | 40311 | 已存在待审核的入队申请 |
| APPLICATION_NOT_FOUND | 40312 | 入队申请不存在 |
| APPLICATION_ALREADY_HANDLED | 40313 | 入队申请已处理 |
| INVALID_APPLICATION_DECISION | 40314 | 审核决定不合法 |
| CANNOT_APPLY_OWN_TEAM | 40315 | 不能申请加入自己创建的团队 |
| INVALID_TEAM_STATUS | 40316 | 团队状态不合法 |

### BB=04：题目模块

| 常量 | 编码 | 说明 |
|------|------|------|
| PROBLEM_NOT_FOUND | 40401 | 题目不存在 |
| TAG_NOT_FOUND | 40402 | 标签不存在 |
| TAG_NAME_DUPLICATE | 40403 | 标签名称已存在 |
| INVALID_CONTEST_TYPE | 40404 | 赛事类型不合法 |
| INVALID_DIFFICULTY | 40405 | 难度值不合法 |
| INVALID_STATUS | 40406 | 题目状态不合法 |
| TAG_IN_USE | 40407 | 标签仍被题目使用 |

### BB=05：AI 客服

| 常量 | 编码 | 说明 |
|------|------|------|
| CONVERSATION_NOT_FOUND | 40501 | AI 客服会话不存在 |
| CONVERSATION_CLOSED | 40502 | 会话已结束，不能继续发送消息 |
| MESSAGE_NOT_FOUND | 40503 | AI 客服消息不存在 |
| MESSAGE_NOT_FAILED | 40504 | 只有失败的 AI 回复可以重试 |
| PRODUCTION_CONFIG_UNAVAILABLE | 40505 | AI 客服生产配置不存在或不可用 |
| PRODUCTION_CHANGE_INVALID | 40506 | AI 客服生产配置变更请求不合法 |
| PRODUCTION_CHANGE_CONFLICT | 40507 | 生产配置已经变化 |
| PRODUCTION_DEPENDENCY_UNAVAILABLE | 50501 | AI 客服目标配置依赖暂不可用 |

### BB=06：作品提交（SubmissionErrorCode）

| 常量 | 编码 | 说明 |
|------|------|------|
| TEAM_NOT_AVAILABLE | 40601 | 队伍不存在或暂不可用 |
| NOT_TEAM_MEMBER | 40602 | 用户不是队伍成员 |
| PRACTICE_NOT_STARTED | 40603 | 练习尚未开始 |
| DEADLINE_PASSED | 40604 | 提交截止时间已过 |
| PDF_ONLY | 40605 | 仅支持 PDF 文件 |
| SUBMISSION_NOT_FOUND | 40606 | 提交记录不存在 |
| FINAL_SUBMISSION_NOT_FOUND | 40607 | 没有可锁定的成功提交 |
| DEADLINE_NOT_REACHED | 40608 | 截止时间尚未到达 |
| SUBMISSION_PERMISSION_DENIED | 40609 | 当前成员没有作品提交权限 |
| PDF_SIZE_EXCEEDED | 40610 | 论文 PDF 超过 20MB |
| UPLOAD_NOT_FOUND | 40611 | 上传会话不存在或不属于当前用户 |
| UPLOAD_ALREADY_ACTIVE | 40612 | 队伍已有其他成员正在上传论文 |
| UPLOAD_STATE_INVALID | 40613 | 上传会话状态不允许当前操作 |
| UPLOAD_EXPIRED | 40614 | 上传会话已过期 |
| CHUNK_INDEX_INVALID | 40615 | 分片序号不正确 |
| CHUNK_SIZE_INVALID | 40616 | 分片大小不正确 |
| CHUNK_CHECKSUM_MISMATCH | 40617 | 分片摘要校验失败 |
| CHUNK_CONFLICT | 40618 | 同序号分片内容冲突 |
| CHUNK_MISSING | 40619 | 分片尚未全部上传 |
| FILE_CHECKSUM_MISMATCH | 40620 | 合并文件大小或摘要校验失败 |
| UPLOAD_COMPLETING | 40621 | 论文正在合并 |
| REVIEW_TASK_CREATE_FAILED | 50601 | 评审任务创建失败 |

### BB=11：AI 质量评价

| 常量 | 编码 | 说明 |
|------|------|------|
| DATASET_NOT_FOUND | 41101 | 评价数据集不存在 |
| DUPLICATE_SAMPLE | 41102 | 评价数据集重复引用同一提交 |
| SAMPLE_UNAVAILABLE | 41103 | 评价样本不存在或信息不完整 |
| VERSION_UNAVAILABLE | 41104 | 评审版本不存在或不可执行 |
| TASK_NOT_FOUND | 41105 | 质量评价任务不存在 |
| TASK_NOT_FAILED | 41106 | 当前任务不可重试 |
| IDEMPOTENCY_CONFLICT | 41107 | 请求标识已用于其他评价配置 |
| SCALE_LIMIT_EXCEEDED | 41108 | 评价批次规模超过服务端限制 |
| DUPLICATE_CANDIDATE | 41109 | 评价批次包含重复候选版本 |
| TASK_STATE_CONFLICT | 41110 | 评价任务状态不允许当前操作 |
| WEIGHT_SCHEME_INVALID | 41111 | 权重方案配置与指标口径不兼容 |
| WEIGHT_SCHEME_VERSION_DUPLICATE | 41112 | 权重方案版本已存在 |
| WEIGHT_SCHEME_NOT_FOUND | 41113 | 权重方案不存在 |
| SCORE_RECALCULATION_NOT_ALLOWED | 41114 | 当前评价数据不满足重新计算条件 |
| DEPENDENCY_UNAVAILABLE | 51101 | 质量评价依赖服务暂不可用 |

### BB=12：AI 网关

| 常量 | 编码 | 说明 |
|------|------|------|
| ROUTE_NOT_FOUND | 41201 | AI 调用场景未配置 |
| CAPABILITY_NOT_SUPPORTED | 41202 | 当前模型不支持请求的能力 |
| MODEL_DISABLED | 41203 | 当前模型未配置或已停用 |
| INPUT_TYPE_UNSUPPORTED | 41204 | 当前模型不支持请求的输入类型 |
| MEDIA_TYPE_UNSUPPORTED | 41205 | 当前模型不支持该媒体类型 |
| IMAGE_COUNT_EXCEEDED | 41206 | 图片数量超过当前模型上限 |
| IMAGE_BYTES_EXCEEDED | 41207 | 图片总体积超过当前模型上限 |
| CONTEXT_WINDOW_EXCEEDED | 41208 | 请求可能超过当前模型上下文窗口 |
| OUTPUT_LIMIT_EXCEEDED | 41209 | 最大输出超过当前模型上限 |
| PROVIDER_NOT_CONFIGURED | 51201 | AI 供应商未配置 |
| PROVIDER_UNAVAILABLE | 51202 | AI 供应商暂不可用 |
| RESPONSE_INVALID | 51203 | AI 供应商响应无效 |

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
