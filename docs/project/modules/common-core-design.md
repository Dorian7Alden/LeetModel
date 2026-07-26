# common-core 公共模块设计

> 创建日期：2026-07-26
> 状态：已确认

---

## 一、目标

构建所有微服务共同依赖的基础模块 `common-core`（jar 包，非独立服务），提供统一响应体、异常体系、分页封装、公共实体基类和工具类。

---

## 二、组件设计

### 2.1 Result\<T\> — 统一响应体

- 成功 code = 0，非 0 为业务错误
- 携带 `timestamp` 方便按时间定位日志
- 提供 `ok()` / `fail()` 工厂方法

### 2.2 PageResult\<T\> — 分页响应

- 封装 `total` / `page` / `size` / `rows`
- 提供 `from(IPage<T>)` 从 MyBatis-Plus 分页结果转换

### 2.3 异常体系

| 类 | 角色 |
|----|------|
| `ErrorCode`（接口） | 所有错误码枚举的统一契约，`getCode()` + `getMessage()` |
| `ErrorCodeEnum`（枚举） | 通用错误码，按万位分段：0xxxx = 系统级 |
| `BusinessException` | 运行时异常，携带 code + message，支持覆盖消息 |

各业务模块可定义自己的错误码枚举（如 `UserErrorCode` 使用 1xxxx 段），实现 `ErrorCode` 接口即可。

错误码分段规则：
- `0xxxx`：通用/系统级
- `1xxxx`：用户模块
- `2xxxx`：团队模块
- `3xxxx`：题目模块
- 后续模块递增万位

### 2.4 GlobalExceptionHandler

`@RestControllerAdvice` 全局异常拦截，处理三类异常：
- `BusinessException` → 返回业务错误码 + 消息
- `MethodArgumentNotValidException` → 提取字段校验失败信息
- `Exception` → 兜底，返回 `SYSTEM_ERROR`，打印完整堆栈

### 2.5 BaseEntity — 实体基类

抽象基类，提供 `id`（雪花算法主键）、`createTime`、`updateTime`、`deleted`（逻辑删除）四个公共字段。

### 2.6 工具类

- **TraceIdUtil**：封装 MDC 读写，网关写入 → 各服务透传 → 日志自动带 traceId
- **AssertUtil**：参数校验语法糖，`notNull` / `isTrue` / `notBlank`，一行断言代替 if-throw
