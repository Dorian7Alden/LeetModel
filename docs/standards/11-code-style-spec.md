## Service 层 Java 代码风格规范

> 创建日期：2026-08-22
> 影响范围：LeetModel-backend 各微服务的 Service 层 Java 代码，即 service 包与 service.impl 包
> 参照样本：UserServiceImpl

---

### 一、总体原则

1. 【强制】每个方法只做一件事，复杂逻辑拆成小方法。
2. 【强制】方法内用 `//` 注释划分步骤，先看注释就能看懂流程。
3. 【强制】简单边界判断使用早返回，保持主流程平坦。
4. 【强制】业务异常统一使用 `BusinessException.throwIf`。
5. 【强制】集合组装优先使用普通 for 循环，避免嵌套过深的 Stream。
6. 【推荐】Stream 链式调用每行只放一个操作，便于阅读和断点。

---

### 二、代码注释分类与格式规范

注释的唯一目标是充当高信噪比的地图速查卡，防止代码长时间未看时遗忘核心逻辑。代码注释不是设计文档，不承载大篇幅架构论证。

#### 2.1 类注释（无固定标签，自由正文）

类注释不规定生硬标签，使用 1~3 句话干练说明：
1. 核心定位：该类在系统全链路中充当什么角色、提供什么能力。
2. 关键约束与防坑提示：非显而易见的注意事项（如时区要求、精度丢失转换、防 OOM 上限等）。

#### 2.2 字段与变量注释（轻量单行）

采用标准单行 Javadoc，清晰说明业务含义即可，不增加额外标签：

```java
/** 业务状态码：0 为成功，非 0 为错误 */
private int code;
```

#### 2.3 方法注释（强制固定格式标准）

方法注释具有严格的固定结构，必须标准化声明输入、输出与异常约束：

- **首行**：动宾短语一句话概括方法的核心行为。
- **输入 `@param`**：明确说明参数业务含义，若有约束须标明（如“是否允许为 null”、“取值范围”）。
- **输出 `@return`**：明确说明返回值含义，特别须注明异常或空值情况下的返回行为（如“未查到返回 null”、“空结果返回空集合而非 null”）。
- **异常 `@throws`**：若方法显式抛出特定业务异常，必须说明触发该异常的明确边界条件。

正例：

```java
/**
 * 将 MyBatis-Plus 的分页结果转换为统一领域分页模型。
 *
 * @param page MyBatis-Plus 的 IPage 分页源对象，不能为 null
 * @param <T>  列表项元素类型
 * @return 转换后的 PageResult 实例；若原记录集为 null 则自动置为空集合
 */
public static <T> PageResult<T> from(IPage<T> page) {
    ...
}
```

公有与私有方法均须遵守此格式。方法名已完全自解释且无入参出参的微型方法可适当简略。

---

### 三、方法内步骤注释

方法内部按逻辑步骤用 `//` 注释分段：

```java
public String updateAvatar(Long userId, MultipartFile file) {
    // 检查存储服务
    if (storageService == null) {
        throw new BusinessException(UserErrorCode.STORAGE_NOT_ENABLED);
    }

    // 获取用户
    User user = getById(userId);
    BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

    // 上传文件，获取头像 URL
    String objectName = storageService.upload(file, "avatars");
    String avatarUrl = storageService.getUrl(objectName);

    // 更新用户头像 URL
    user.setAvatarUrl(avatarUrl);
    updateById(user);
    return avatarUrl;
}
```

---

### 四、早返回

简单的边界判断使用一行早返回，不加花括号：

```java
if (userIds.isEmpty()) return Map.of();
if (userRoles.isEmpty()) return buildEmptyRoleMap(userIds);
if (role == null) continue;
```

较复杂或需要解释的判断使用多行 `throwIf`。

---

### 五、业务异常

统一使用 `BusinessException.throwIf`，条件与错误码分两行：

```java
BusinessException.throwIf(
        !passwordEncoder.matches(request.getOldPassword(), user.getPassword()),
        UserErrorCode.PASSWORD_OLD_INVALID
);
```

禁止手写 if-throw：

```java
if (!passwordEncoder.matches(...)) {
    throw new BusinessException(UserErrorCode.PASSWORD_OLD_INVALID);
}
```

---

### 六、Stream 使用

每行只放一个操作：

```java
List<Long> roleIds = userRoles.stream()
        .map(UserRole::getRoleId)
        .distinct()
        .toList();
```

简单转换可以保持紧凑：

```java
List<Long> userIds = userPage.getRecords().stream().map(User::getId).toList();
```

禁止一长串操作堆在一行或嵌套过深的 Collector。

---

### 七、Builder 风格

Builder 每个属性单独一行：

```java
return UserAdminVO.RoleSimpleVO.builder()
        .id(role.getId())
        .code(role.getCode())
        .name(role.getName())
        .build();
```

---

### 八、小方法拆分

当返回值需要大量操作才能得出时，拆成独立私有方法：

```java
private Map<Long, List<UserAdminVO.RoleSimpleVO>> buildEmptyRoleMap(List<Long> userIds) {
    return userIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
}

private UserAdminVO.RoleSimpleVO toRoleSimpleVO(Role role) {
    return UserAdminVO.RoleSimpleVO.builder()
            .id(role.getId())
            .code(role.getCode())
            .name(role.getName())
            .build();
}
```

---

### 九、分区注释

同一类内不同职责区域用分隔注释：

```java
// ==================== 管理员方法 ====================

// ==================== 私有方法 ====================
```

---

### 十、禁止事项

1. 【禁止】方法内出现一大坨嵌套 Stream 或 Collector。
2. 【禁止】用 if-throw 代替 `BusinessException.throwIf`。
3. 【禁止】简单边界判断写多行花括号。
4. 【禁止】Builder 属性挤在一行。
5. 【禁止】方法缺少 Javadoc 或步骤注释。
6. 【禁止】在代码注释、Javadoc 中出现“面试”、“面试考点”、“考点”等求职元信息。代码注释必须保持严肃、纯粹的生产级工程与架构视角，仅说明职责、设计权衡、技术约束、线程安全与业务规则。
