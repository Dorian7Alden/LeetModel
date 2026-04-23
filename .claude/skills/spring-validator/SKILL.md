---
name: spring-validator
description: 当用户编写或修改 Spring Boot Controller 参数校验代码时自动触发，强制使用 ParameterValidator 链式校验替代 @Valid 注解体系
---

# Spring Boot 优雅参数校验 Skill

## 触发场景

以下任意情况均应激活本 Skill：

- 用户新建 Controller 方法，方法参数含 `@RequestBody`
- 用户粘贴或编写含 `@Valid`、`@NotBlank`、`@NotNull` 等注解的代码
- 用户描述「参数校验」「接口校验」「字段非空」相关需求
- 用户要求 review Controller 代码
- 用户要求「初始化基础设施」「scaffold」「生成校验工具类」

---

## 核心原则

**打开 Controller 方法，就能看到该接口的全部校验逻辑。**

校验逻辑只存在于一个地方：Controller 方法体顶部，通过 `ParameterValidator` 链式完成。

---

## 严禁模式（Anti-Pattern）

以下代码一经发现，必须指出并提供修改方案：

```java
// ❌ 禁止：DTO 上堆校验注解
@Data
public class SaveOrderRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
}

// ❌ 禁止：@Valid + 注解组合
@PostMapping("/save")
public Result<Long> save(@RequestBody @Valid SaveOrderRequest command) { ... }

// ❌ 禁止：if-return 地狱
@PostMapping("/save")
public Result<Long> save(@RequestBody SaveOrderRequest command) {
    if (StringUtils.isBlank(command.getOrderNo())) {
        return Result.fail("PARAM_ERROR", "订单号不能为空");
    }
    if (command.getAmount() == null) {
        return Result.fail("PARAM_ERROR", "金额不能为空");
    }
    ...
}

// ❌ 禁止：Bean Validation groups 分组
public interface PcValidation {}
public interface AppValidation {}
@Size(min = 3, groups = PcValidation.class)
@Size(min = 1, groups = AppValidation.class)
private List<String> imageUrls;
```

---

## 推荐模式（Recommended Pattern）

```java
// ✅ DTO 保持纯净，只定义数据结构
@Data
public class SaveOrderRequest {
    private String orderNo;
    private BigDecimal amount;
    private String remark;
}

// ✅ 所有校验集中在 Controller，链式调用，一次返回全部错误
@PostMapping("/save")
public Result<Long> save(@RequestBody SaveOrderRequest command) {
    ParameterValidator.init()
        .hasLength(command.getOrderNo(), "订单号不能为空")
        .notNull(command.getAmount(), "金额不能为空")
        .isTrue(
            command.getAmount() == null
                || command.getAmount().compareTo(BigDecimal.ZERO) > 0,
            "金额必须大于0")
        .validateAndThrow();

    return Result.success(orderService.save(command));
}
```

---

## 场景模板

### 场景 1：基础 CRUD 接口

```java
@PostMapping("/save")
public Result<Long> save(@RequestBody SaveXxxRequest command) {
    ParameterValidator.init()
        .hasLength(command.getFieldA(), "字段A不能为空")
        .notNull(command.getFieldB(), "字段B不能为空")
        .notEmpty(command.getItems(), "列表不能为空")
        .isTrue(
            /* 组合校验条件，前置 null 保护 */,
            "组合校验错误信息")
        .validateAndThrow();

    return Result.success(xxxService.save(command));
}
```

### 场景 2：多端差异化校验

```java
@PostMapping("/submit")
public Result<Long> submit(@RequestBody SubmitXxxRequest command,
                           HttpServletRequest request) {
    String platform = request.getHeader("X-Platform-Type");

    // 公共校验（所有端）
    ParameterValidator validator = ParameterValidator.init()
        .hasLength(command.getOrderNo(), "订单号不能为空")
        .notNull(command.getShopId(), "门店ID不能为空")
        .notEmpty(command.getImageUrls(), "凭证图片不能为空");

    // 差异化校验（按端）
    switch (platform) {
        case "PC" -> validator
            .hasLength(command.getAddress(), "PC端详细地址不能为空")
            .isTrue(command.getImageUrls().size() >= 3, "PC端至少上传3张凭证图片")
            .maxLength(command.getRemark(), 500, "PC端备注最多500字");
        case "APP" -> validator
            .isTrue(command.getImageUrls().size() >= 1, "至少上传1张凭证图片");
        case "MINI_PROGRAM", "H5" -> validator
            .isTrue(command.getImageUrls().size() >= 1, "至少上传1张凭证图片")
            .maxLength(command.getRemark(), 200, "备注最多200字");
        default -> throw new BusinessException("PARAM_ERROR", "不支持的端类型: " + platform);
    }

    validator.validateAndThrow();
    return Result.success(xxxService.submit(command));
}
```

### 场景 3：有依赖关系的组合校验

```java
// isTrue() 的条件编写规范：
// - 当前置字段为 null 时短路（避免 NPE），将 null 视为"通过"
// - null 保护由前置的 notNull() / hasLength() 兜底

ParameterValidator.init()
    .notNull(command.getOutShopId(), "调出门店ID不能为空")
    .notNull(command.getInShopId(), "调入门店ID不能为空")
    // 两字段都不为 null 时才比较
    .isTrue(
        command.getOutShopId() == null
            || command.getInShopId() == null
            || !command.getOutShopId().equals(command.getInShopId()),
        "调出门店和调入门店不能相同")
    // Boolean 字段为 true 时要求 remark 非空
    .isTrue(
        !Boolean.TRUE.equals(command.getIsAbnormal())
            || StringUtils.isNotBlank(command.getRemark()),
        "标记为异常时必须填写备注")
    .validateAndThrow();
```

---

## 动态初始化规则 (Dynamic Scaffold)

用户要求「scaffold」、「初始化基础设施」或「生成校验工具类」时：

**不要使用写死的代码代码，必须根据当前项目的实际代码环境动态生成 `ParameterValidator`：**

1. **探测现有设施**：主动使用工具搜索项目中的业务异常类（如 `BusinessException`）、错误枚举（如 `ErrorCode`）以及基础响应类（如 `Result`），确定其包路径。
2. **动态生成**：利用分析得到的类定义，在恰当的包路径（如 `{basePackage}/common/validator/`）下创建并生成 `ParameterValidator.java`。
3. **适配异常抛出**：由于项目已有完备的全局异常处理，生成的 `ParameterValidator` 其 `validateAndThrow()` 方法必须抛出项目原有的业务异常类，并引入正确的错误码（例如 `throw new BusinessException(ErrorCode.PARAM_VALIDATION_ERROR, String.join("; ", errors));`）。
4. **保留核心链式方法**：生成的类中必须包含 `init()`, `notNull()`, `hasLength()`, `notEmpty()`, `isTrue()`, `validateAndThrow()` 等方法设计。
5. 生成结束后，向用户输出适配了哪些现有项目类，提示用户确认。

---

## Code Review 输出规范

发现违规代码时，输出格式如下：

```
🚫 违规位置：XxxController.java 第 23 行
❌ 违规原因：使用了 @Valid 注解，校验逻辑分散在 DTO 注解和 Controller 两处
✅ 修改建议：
   [给出完整的替换代码]
```

---

## ParameterValidator API 速查

| 方法 | 触发条件 | 典型用途 |
|---|---|---|
| `notNull(value, msg)` | value == null | 对象、数字、枚举字段 |
| `hasLength(str, msg)` | null / 空串 / 纯空格 | 字符串必填字段 |
| `notEmpty(collection, msg)` | null 或空集合 | List、Set 必填 |
| `isTrue(condition, msg)` | condition == false | 任意组合/范围/关联校验 |
| `maxLength(str, max, msg)` | 超过最大长度 | 备注、描述字段长度限制 |
| `minLength(str, min, msg)` | 不足最小长度 | 密码、编码最短要求 |
| `range(num, min, max, msg)` | 超出数值范围 | 数量、金额范围 |
| `sizeRange(col, min, max, msg)` | 集合大小越界 | 批量提交数量限制 |
| `validateAndThrow()` | 有任意错误时 | 链式末尾必须调用 |