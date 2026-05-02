---
name: backend-controller-spec
description: 当用户编写或修改 Spring Boot Controller、VO、统一响应结构（Result）时自动触发，强制遵循 Controller 层开发规范
---

# 后端 Controller 开发规范 Skill

## 触发场景

以下任意情况均应激活本 Skill：

- 用户新建或修改 Controller 类及其方法
- 用户新建或修改 VO（View Object）类
- 用户新建或修改统一响应结构 `Result`
- 用户编写涉及 `@RequestBody`、`@RequestPart` 的接口
- 用户编写文件上传接口
- 用户描述「接口返回格式」「统一响应」「VO 转换」相关需求
- 用户要求 review Controller 层代码

---

## 核心原则

1. **Controller 仅作为请求入口与响应出口** — 不承载业务逻辑，只做调度。
2. **校验与 Controller 分层** — 每个接口对应独立的 Validator，Controller 只负责调用。
3. **VO 显式转换** — 通过静态工厂方法 `createVO(Entity)` 逐字段赋值，禁用任何自动拷贝工具。
4. **统一响应** — 所有接口返回值类型固定为 `Result<T>`。

---

## 项目结构约束（最高优先级）

- **本文档中所有包结构、目录结构均为推荐示意，非强制要求。**
- **当项目已有既定的目录结构、包划分、类命名习惯时，沿用项目现有结构，不做任何调整。**
- **仅规范编码模式（Controller/Validator/VO/Result 的职责边界与写法），不改变项目既有的物理结构。**
- 新增文件时，遵循项目现有分包方式放置到对应位置即可。

---

## 架构概览（推荐示意）

```
controller/
  UserController.java        ← 只做调度：组装 DTO → 调 Validator → 调 Service → 转 VO → 返回 Result
validator/
  user/
    UserCreateValidator.java  ← 链式校验，继承 BaseValidator
    UserQueryValidator.java
vo/
  UserVO.java                ← 含静态工厂方法 createVO(Entity)
dto/
  UserCreateDTO.java         ← 纯净 DTO，禁止校验注解
common/
  Result.java                ← 统一响应结构
  ValidationException.java   ← 校验异常（含 List<FieldError>）
  FieldError.java            ← 字段错误载体
```

---

## 推荐模式

### 场景 1：纯 JSON 请求

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserCreateValidator userCreateValidator;

    public UserController(UserService userService, UserCreateValidator userCreateValidator) {
        this.userService = userService;
        this.userCreateValidator = userCreateValidator;
    }

    @PostMapping
    public Result<Void> createUser(@RequestBody UserCreateDTO dto) {
        userCreateValidator.validate(dto);
        userService.createUser(dto);
        return Result.success();
    }
}
```

### 场景 2：文件 + JSON 混合请求（统一 DTO）

```java
// DTO 定义（包含文件字段）
public class UserAttachmentDTO {
    private String title;
    private MultipartFile file;
    // getter/setter
}

@RestController
public class AttachmentController {

    private final UserAttachmentValidator userAttachmentValidator;
    private final AttachmentService attachmentService;

    @PostMapping("/upload")
    public Result<String> uploadFile(
            @RequestPart("meta") UserAttachmentDTO meta,
            @RequestPart("file") MultipartFile file) {

        meta.setFile(file);                     // 统一封装到一个 DTO
        userAttachmentValidator.validate(meta);
        String url = attachmentService.save(meta);
        return Result.success(url);
    }
}
```

### 场景 3：条件查询 + VO 转换

```java
@GetMapping("/search")
public Result<List<UserVO>> search(UserQueryDTO dto) {
    userQueryValidator.validate(dto);
    List<User> users = userService.query(dto);

    List<UserVO> voList = new ArrayList<>();
    for (User user : users) {
        UserVO vo = UserVO.createVO(user);      // 调用 createVO 完成逐字段转换
        voList.add(vo);
    }
    return Result.success(voList);
}
```

---

## VO 转换规范

### createVO 静态工厂方法

所有 VO 类必须提供静态工厂方法 `createVO`，接收对应的领域对象并返回 VO 实例：

```java
public class UserVO {
    private Long id;
    private String username;
    private String email;

    // 不暴露 password 等敏感字段

    public static UserVO createVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        // 故意不设置 password、手机号等字段，按需暴露
        return vo;
    }

    // getter/setter ...
}
```

**使用方式**（在 Controller 中）：
```java
User user = userService.getById(id);
UserVO vo = UserVO.createVO(user);
return Result.success(vo);
```

### VO 禁止事项

- 严禁使用 `BeanUtils.copyProperties` 等反射工具进行 VO 转换。
- 严禁在 VO 类中直接返回 `Result` 或其他响应结构。
- VO 中不可包含业务逻辑，仅作为数据的视图容器。

---

## Validator 层规范

### 设计原则

- **唯一校验入口**：所有校验经由 Validator，不依赖注解。
- **一次校验、全量报错**：收集全部错误后统一抛出。
- **完整链式调用**：校验从 `init()` 开始，依次调用基础方法与自定义方法，最后以 `validateAndThrow()` 收尾。
- **自定义校验方法化**：复杂校验逻辑封装为当前 Validator 的私有方法，返回 `this`，无缝融入链式。
- **按业务模块分包**：`validator.user`、`validator.order` 等，绝不按参数类型（如 `json`、`file`）分包。

### 基础校验器（BaseValidator）

```java
public abstract class BaseValidator<T extends BaseValidator<T>> {

    protected List<FieldError> errors = new ArrayList<>();

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    public T init() {
        return self();
    }

    public T notNull(Object value, String errorMessage) {
        if (value == null) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T notBlank(String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T hasLength(String value, int min, int max, String errorMessage) {
        if (value != null && (value.length() < min || value.length() > max)) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T isTrue(Boolean condition, String errorMessage) {
        if (condition != null && !condition) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T maxLength(String value, int max, String errorMessage) {
        if (value != null && value.length() > max) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T minValue(Integer value, int min, String errorMessage) {
        if (value != null && value < min) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T fileNotEmpty(MultipartFile file, String errorMessage) {
        if (file == null || file.isEmpty()) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T fileMaxSize(MultipartFile file, long maxBytes, String errorMessage) {
        if (file != null && file.getSize() > maxBytes) {
            errors.add(new FieldError(null, errorMessage));
        }
        return self();
    }

    public T addError(String errorMessage) {
        errors.add(new FieldError(null, errorMessage));
        return self();
    }

    public T exec(Runnable block) {
        block.run();
        return self();
    }

    public void validateAndThrow() {
        if (!errors.isEmpty()) {
            throw new ValidationException(new ArrayList<>(errors));
        }
    }
}
```

### 具体校验器实现示例

```java
@Component
public class UserCreateValidator extends BaseValidator<UserCreateValidator> {

    public void validate(UserCreateDTO dto) {
        init()
            .notBlank(dto.getUsername(), "请输入用户名")
            .hasLength(dto.getUsername(), 3, 20, "用户名长度需在3到20个字符之间")
            .notBlank(dto.getPassword(), "请输入密码")
            .passwordComplexity(dto.getPassword())   // 自定义校验方法融入链式
            .validateAndThrow();
    }

    private UserCreateValidator passwordComplexity(String password) {
        if (password != null && (password.length() < 6 || !password.matches(".*[A-Z].*"))) {
            addError("密码需至少6位且包含大写字母");
        }
        return self();
    }
}
```

### Validator 类约定

1. **类名** — 以 `Validator` 结尾，如 `UserCreateValidator`。
2. **注解** — 标注 `@Component`，Controller 通过构造器注入。
3. **继承** — 继承 `BaseValidator<T>`，获得链式校验能力。
4. **方法命名** — 统一命名 `validate(DTO)`。
5. **链式末尾** — 必须调用 `validateAndThrow()`，禁止在链外单独处理错误。
6. **预留扩展** — 即使接口暂无校验规则，也要保留 Validator 并调用 `validateAndThrow()`。

---

## 统一响应结构（Result）

| 字段    | 类型   | 说明               |
| ------- | ------ | ------------------ |
| code    | int    | 业务状态码         |
| message | String | 提示信息           |
| data    | T      | 业务数据           |

```java
public class Result<T> {
    private int code;
    private String message;
    private T data;

    private Result() {}

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = StatusCode.SUCCESS;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(int code, String message, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.data = data;
        return r;
    }
}
```

---

## 异常处理说明

- Validator 抛出的 `ValidationException`（含 `List<FieldError>`）由全局异常处理器统一处理为 `Result` 响应。
- Controller 层严禁使用 `try-catch` 捕获业务异常。
- `ValidationException` 继承 `RuntimeException`，`FieldError` 包含 `field`（可为 null）与 `message`（中文提示信息）。

---

## Service 层规范（引用）

- Service 只返回领域对象，不包含视图信息。
- 视图转换全部交由 Controller 侧通过 `createVO()` 完成。

---

## 完整调用流程

```
Client 请求 (JSON / 文件+JSON)
      │
      ▼
Controller 组装 DTO（JSON 部分 + 文件部分统一封装）
      │
      ▼
Validator 链式校验
      │  init() → 基础校验 → 自定义方法 → ... → validateAndThrow()
      │
      ├── 存在错误 → throw ValidationException → 全局异常处理器 → Result(code, message, List<FieldError>)
      │
      └── 校验通过 → Service 执行业务 → 返回领域对象
                              │
                              ▼
                      Controller: xxVO.createVO(entity)
                              │
                              ▼
                       Result.success(vo)
```

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

// ❌ 禁止 2：@Valid / @Validated + 注解组合
@PostMapping("/save")
public Result<Long> save(@RequestBody @Valid SaveOrderRequest command) { ... }

// ❌ 禁止 3：Controller 中 try-catch 处理业务异常
@PostMapping("/save")
public Result<Long> save(@RequestBody SaveOrderRequest command) {
    try {
        ...
    } catch (Exception e) {
        return Result.fail(...);
    }
}

// ❌ 禁止 4：使用 BeanUtils.copyProperties 转换 VO
UserVO vo = new UserVO();
BeanUtils.copyProperties(user, vo);

// ❌ 禁止 5：在 Controller 中直接 set 构造 VO（应使用 createVO）
UserVO vo = new UserVO();
vo.setId(user.getId());
vo.setUsername(user.getUsername());
// 应在 UserVO.createVO() 中完成

// ❌ 禁止 6：Controller 方法体内联校验逻辑（应提取到独立 Validator）
@PostMapping("/save")
public Result<Long> save(@RequestBody SaveOrderRequest command) {
    if (StringUtils.isBlank(command.getOrderNo())) {
        return Result.fail("PARAM_ERROR", "订单号不能为空");
    }
    ...
}

// ❌ 禁止 7：文件参数未封装到 DTO（MultipartFile 作为独立参数传到 Service）
public Result<String> upload(@RequestPart MultipartFile file) {
    return Result.success(fileService.save(file));  // file 应与 meta 封装为一个 DTO
}
```

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
- Controller 中 `try-catch` 处理业务异常
- 使用 `BeanUtils.copyProperties` 或类似工具转换 VO
- Controller 中直接 `set` 构造 VO（应提取为 `createVO()`）
- Controller 方法体内联校验逻辑（应提取为独立 Validator）
- 文件参数未封装到 DTO
- Controller 返回值不是 `Result<T>`

---

## 附加约定

- 文件参数统一作为 DTO 的一个字段，传输时通过多个 `@RequestPart` 组合拼装。
- DTO 内部严禁使用任何校验注解，校验逻辑全部集中在 Validator 中。
- VO 必须提供静态工厂方法 `createVO(Entity)`，转换时逐字段显式赋值。
- 链式末尾必须调用 `validateAndThrow()`，禁止在链外单独处理错误。
- 即使接口暂无校验规则，也要保留 Validator 并调用 `validateAndThrow()` 以预留扩展点。
- 业务状态码统一定义、全局异常处理的具体实现请参照项目已有相关规范。
