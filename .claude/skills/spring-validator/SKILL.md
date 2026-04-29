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

1. **校验与 Controller 分层** — 每个接口对应的参数校验逻辑封装在独立的 `XxxParamValidator` 类中，Controller 只负责调用，保持方法体简洁。
2. **ParameterValidator 仍是底层校验引擎** — 所有 ParamValidator 实现类内部使用 `ParameterValidator` 链式调用完成校验，校验不通过时统一抛出 `BusinessException`。

---

## 架构概览

```
common/validator/
  ParamValidator.java       ← 入参校验器接口
  ParameterValidator.java   ← 链式校验引擎
validator/                  ← 按 domain 分包
  auth/
    SendEmailCodeParamValidator.java
  order/
    SaveOrderParamValidator.java
controller/
  AuthController.java       ← 只做调度：注入 ParamValidator，一行 validate()
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

// ✅ 入参校验逻辑封装在独立 Validator 中
@Component
public class SaveOrderParamValidator implements ParamValidator<SaveOrderRequest> {

    @Override
    public void validate(SaveOrderRequest command) {
        ParameterValidator.init()
            .hasLength(command.getOrderNo(), "订单号不能为空")
            .notNull(command.getAmount(), "金额不能为空")
            .isTrue(
                command.getAmount() == null
                    || command.getAmount().compareTo(BigDecimal.ZERO) > 0,
                "金额必须大于0")
            .validateAndThrow();
    }
}

// ✅ Controller 只做调度，保持简洁
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final SaveOrderParamValidator saveOrderParamValidator;
    private final OrderService orderService;

    @PostMapping("/save")
    public Result<Long> save(@RequestBody SaveOrderRequest command) {
        saveOrderParamValidator.validate(command);
        return Result.success(orderService.save(command));
    }
}
```

---

## 场景模板

### 场景 1：基础 CRUD 接口

```java
@Component
public class SaveXxxParamValidator implements ParamValidator<SaveXxxRequest> {

    @Override
    public void validate(SaveXxxRequest command) {
        ParameterValidator.init()
            .hasLength(command.getFieldA(), "字段A不能为空")
            .notNull(command.getFieldB(), "字段B不能为空")
            .notEmpty(command.getItems(), "列表不能为空")
            .isTrue(
                /* 组合校验条件，前置 null 保护 */,
                "组合校验错误信息")
            .validateAndThrow();
    }
}

@PostMapping("/save")
public Result<Long> save(@RequestBody SaveXxxRequest command) {
    saveXxxParamValidator.validate(command);
    return Result.success(xxxService.save(command));
}
```

### 场景 2：多端差异化校验

Controller 提取 context 传入 Validator（Validator 不实现 ParamValidator 接口，因为额外参数）：

```java
@Component
public class SubmitXxxParamValidator {

    public void validate(SubmitXxxRequest command, String platform) {
        ParameterValidator validator = ParameterValidator.init()
            .hasLength(command.getOrderNo(), "订单号不能为空")
            .notNull(command.getShopId(), "门店ID不能为空")
            .notEmpty(command.getImageUrls(), "凭证图片不能为空");

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
            default -> throw new BusinessException(
                ResponseCode.GLOBAL_PARAM_VALIDATION_ERROR, "不支持的端类型: " + platform);
        }

        validator.validateAndThrow();
    }
}

@PostMapping("/submit")
public Result<Long> submit(@RequestBody SubmitXxxRequest command,
                           HttpServletRequest request) {
    String platform = request.getHeader("X-Platform-Type");
    submitXxxParamValidator.validate(command, platform);
    return Result.success(xxxService.submit(command));
}
```

### 场景 3：有依赖关系的组合校验

```java
// isTrue() 的条件编写规范：
// - 当前置字段为 null 时短路（避免 NPE），将 null 视为"通过"
// - null 保护由前置的 notNull() / hasLength() 兜底

@Component
public class TransferParamValidator implements ParamValidator<TransferRequest> {

    @Override
    public void validate(TransferRequest command) {
        ParameterValidator.init()
            .notNull(command.getOutShopId(), "调出门店ID不能为空")
            .notNull(command.getInShopId(), "调入门店ID不能为空")
            .isTrue(
                command.getOutShopId() == null
                    || command.getInShopId() == null
                    || !command.getOutShopId().equals(command.getInShopId()),
                "调出门店和调入门店不能相同")
            .isTrue(
                !Boolean.TRUE.equals(command.getIsAbnormal())
                    || StringUtils.isNotBlank(command.getRemark()),
                "标记为异常时必须填写备注")
            .validateAndThrow();
    }
}

@PostMapping("/transfer")
public Result<Void> transfer(@RequestBody TransferRequest command) {
    transferParamValidator.validate(command);
    return Result.success(transferService.transfer(command));
}
```

---

## ParamValidator 类约定

1. **类名** — 以 `ParamValidator` 结尾，如 `SendEmailCodeParamValidator`，明确表示前端入参校验。
2. **注解** — 标注 `@Component`，Controller 通过构造器注入。
3. **接口** — 实现 `ParamValidator<T>` 接口（需额外参数时可不实现，如场景 2）。
4. **单一职责** — 一个 ParamValidator 对应 Controller 中的一个方法，不同接口不共用。
5. **包路径** — `validator/{domain}/`，与 `dto/{domain}/`、`service/{domain}/` 分层一致。
6. **辅助方法** — 仅本 Validator 使用的辅助方法保留为 `private`；多处复用的工具方法提取到 `common/util/`。
7. **错误处理** — 依赖 `ParameterValidator.validateAndThrow()` 抛出 `BusinessException`，Controller 层不做 try/catch，由 `GlobalExceptionHandler` 统一处理。
8. **可测试性** — Validator 是纯 POJO，无需 Spring 上下文即可在单元测试中直接 `new` 并调用 `validate()`。

---

## 严禁模式（Anti-Pattern）

以下代码一经发现，必须指出并提供修改方案：

```java
// ❌ 禁止 1：DTO 上堆校验注解
@Data
public class SaveOrderRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
}

// ❌ 禁止 2：@Valid + 注解组合
@PostMapping("/save")
public Result<Long> save(@RequestBody @Valid SaveOrderRequest command) { ... }

// ❌ 禁止 3：if-return 地狱
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

// ❌ 禁止 4：Bean Validation groups 分组
public interface PcValidation {}
public interface AppValidation {}
@Size(min = 3, groups = PcValidation.class)
@Size(min = 1, groups = AppValidation.class)
private List<String> imageUrls;

// ❌ 禁止 5：Controller 方法体内联 ParameterValidator 链
// 校验逻辑应提取到独立的 ParamValidator 类中
@PostMapping("/save")
public Result<Long> save(@RequestBody SaveOrderRequest command) {
    ParameterValidator.init()
        .hasLength(command.getOrderNo(), "订单号不能为空")
        .notNull(command.getAmount(), "金额不能为空")
        .validateAndThrow();
    ...
}

// ❌ 禁止 6：一个 ParamValidator 类包含多个 validate 方法处理不同接口
@Component
public class OrderParamValidator {
    public void validateSave(SaveOrderRequest command) { ... }
    public void validateSubmit(SubmitOrderRequest command) { ... }
    public void validateCancel(CancelOrderRequest command) { ... }
}
```

---

## 动态初始化规则 (Dynamic Scaffold)

用户要求「scaffold」、「初始化基础设施」或「生成校验工具类」时：

**必须根据当前项目的实际代码环境动态生成：**

1. **探测现有设施** — 搜索项目中的业务异常类（如 `BusinessException`）、错误枚举（如 `ResponseCode`）以及基础响应类（如 `Result`），确定包路径。
2. **生成 ParamValidator 接口** — 在 `common/validator/` 下创建 `ParamValidator.java`：
   ```java
   @FunctionalInterface
   public interface ParamValidator<T> {
       void validate(T request);
   }
   ```
3. **生成 ParameterValidator 引擎** — 在 `common/validator/` 下创建或确认 `ParameterValidator.java` 存在，包含 `init()`、`notNull()`、`hasLength()`、`notEmpty()`、`isTrue()`、`maxLength()`、`minLength()`、`range()`、`sizeRange()`、`validateAndThrow()` 方法。其 `validateAndThrow()` 必须抛出项目真实的 `BusinessException` 类并引用正确的错误码。
4. **生成具体 ParamValidator** — 在 `validator/{domain}/` 下创建 `XxxParamValidator.java`：
   - 标注 `@Component`
   - 实现 `ParamValidator<XxxDTO>`
   - `validate()` 方法内使用 `ParameterValidator` 链式完成校验
   - 根据 DTO 字段生成占位式校验调用
5. 生成结束后，向用户输出适配了哪些现有项目类，提示用户确认。
6. 如果 Controller 中已有内联校验逻辑，主动建议提取为独立 ParamValidator。

---

## Code Review 输出规范

发现违规代码时，输出格式如下：

```
🚫 违规位置：XxxController.java 第 N 行
❌ 违规原因：...
✅ 修改建议：
   [给出完整的替换代码]
```

违规类型：
- DTO 使用 Bean Validation 注解
- Controller 参数标注 `@Valid` / `@Validated`
- if-return 地狱
- Validation groups 分组
- Controller 方法体内联 ParameterValidator 链（应提取为 XxxParamValidator）
- 单个 ParamValidator 类包含多个接口的校验方法

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
