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

### 二、方法注释

每个方法必须有 Javadoc，包含一句话说明、`@param` 和 `@return`。

正例：

```java
/**
 * 根据用户名查找用户。
 * @param username 用户名
 * @return 用户实体
 */
public User findByUsername(String username) {
    ...
}
```

私有方法也要写 Javadoc。

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
