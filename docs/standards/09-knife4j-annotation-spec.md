## Knife4j 接口注解规范

> 创建日期：2026-08-22
> 影响范围：所有 Controller 接口方法

---

### 强制规则

1. 【强制】使用 Knife4j 的项目中，所有 Controller 接口方法必须写 `@Operation(summary = "xxx")`，summary 使用简短中文描述接口用途。

2. 【强制】`@Operation` 注解必须写在 `@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping` 等请求映射注解之前。

3. 【强制】同一个文件内注解顺序保持一致，统一先写 `@Operation`，再写请求映射注解，不混用不同顺序。

### 正例

```java
@Operation(summary = "获取当前用户信息")
@GetMapping("/me")
public Result<UserVO> profile() {
    Long userId = UserContext.getUserId();
    UserVO vo = userService.getProfile(userId);
    return Result.ok(vo);
}
```

### 反例

```java
@GetMapping("/me")
@Operation(summary = "获取当前用户信息")
public Result<UserVO> profile() {
    ...
}
```

```java
@GetMapping("/me")
public Result<UserVO> profile() {
    ...
}
```

### 说明

- `@Operation` 的 summary 是 Knife4j 接口列表中的显示名称，缺失时文档可读性差。
- 固定注解顺序便于检索、阅读和 code review，也便于 AI 按统一模式生成代码。
