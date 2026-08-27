## 统一项目异常处理规范

> 创建日期：2026-08-22
> 影响范围：所有微服务的 Controller、Service、Feign 降级与自定义异常

---

### 一、总体原则

1. 【强制】所有接口的异常响应必须返回统一结构 `Result`，不允许出现 Spring Boot 默认错误 JSON 或 HTML。
2. 【强制】业务异常统一抛出 `BusinessException`，不允许在 Controller 或 Service 中手动 try-catch 后返回 `Result.fail`。
3. 【强制】错误码使用 `ErrorCode` 枚举定义，不允许在代码中直接写魔法数字。
4. 【强制】异常消息面向用户，不暴露堆栈、SQL、类名等系统内部信息。
5. 【强制】全局异常处理器负责把异常转换为 `Result`，业务代码只负责抛异常。

---

### 二、统一响应结构

所有接口返回：

```json
{
  "code": 20000,
  "message": "success",
  "data": null,
  "timestamp": 1787367978725
}
```

- `code` 采用 A-BB-CC 五段式编码，见 `03-error-code-spec.md`
- `message` 是面向用户的提示
- `data` 是业务数据，异常时为 null

---

### 三、业务异常统一抛出

业务层发现异常时，优先使用 `BusinessException.throwIf`：

```java
BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);
```

等价于：

```java
if (user == null) {
    throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
}
```

【强制】项目中统一使用 `throwIf` 风格，不直接手写 if-throw。

---

### 四、错误码定义

1. 每个业务模块定义自己的 `XxxErrorCode` 枚举，实现 `ErrorCode` 接口。
2. 通用错误码定义在 `ErrorCodeEnum`。
3. 认证鉴权错误码由 `AuthExceptionHandler` 使用，号段为 401xx。
4. 新增错误码时按模块号段递增，不允许重复。

示例：

```java
@Getter
@AllArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(40201, "用户不存在"),
    USERNAME_DUPLICATE(40202, "用户名已被占用"),
    PASSWORD_OLD_INVALID(40206, "旧密码错误");
}
```

---

### 五、全局异常处理器

项目有两个全局异常处理器，职责不同：

| 处理器 | 所在模块 | 职责 |
|---|---|---|
| `GlobalExceptionHandler` | common-core | 处理 `BusinessException`、参数校验异常、兜底系统异常 |
| `AuthExceptionHandler` | common-security | 处理 Sa-Token 的未登录、无权限、角色不匹配异常 |

业务模块只需依赖对应公共模块，不需要自行编写异常处理器。

---

### 六、参数校验异常

请求参数使用 `@Valid` 校验：

```java
public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
    ...
}
```

校验失败时由 `GlobalExceptionHandler` 统一处理，返回：

```json
{
  "code": 40001,
  "message": "username: 用户名不能为空; password: 密码不能为空",
  "data": null
}
```

业务代码不需要手动处理 `BindingResult`。

---

### 七、Feign 降级异常

Feign 调用失败时，降级工厂返回统一错误结果：

```java
public Result<UserAdminVO> detail(Long userId) {
    return Result.fail(50001, "用户服务暂不可用");
}
```

不允许降级返回 null 或空数据对象，否则调用方无法区分正常空数据和服务不可用。

---

### 八、禁止事项

1. 【禁止】在 Controller 中 try-catch 业务异常后返回 `Result.fail`。
2. 【禁止】在代码中写 `throw new RuntimeException("...")` 代替业务异常。
3. 【禁止】在 Service 中直接返回 `Result`，Service 只返回业务数据或抛异常。
4. 【禁止】把 `e.getMessage()` 原样返回给客户端，内部错误信息只记录日志。
5. 【禁止】在错误码枚举中定义重复码值。

---

### 九、正例与反例

正例：

```java
public UserVO getProfile(Long userId) {
    User user = getById(userId);
    BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);
    return toVO(user);
}
```

反例：

```java
public Result<UserVO> getProfile(Long userId) {
    User user = getById(userId);
    if (user == null) {
        return Result.fail(UserErrorCode.USER_NOT_FOUND);
    }
    return Result.ok(toVO(user));
}
```

反例：

```java
if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
    throw new BusinessException(UserErrorCode.PASSWORD_OLD_INVALID);
}
```

统一改为：

```java
BusinessException.throwIf(
        !passwordEncoder.matches(request.getOldPassword(), user.getPassword()),
        UserErrorCode.PASSWORD_OLD_INVALID
);
```
