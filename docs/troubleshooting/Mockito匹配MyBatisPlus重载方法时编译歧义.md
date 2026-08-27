# Mockito 匹配 MyBatis-Plus 重载方法时编译歧义

## 现象

测试代码使用 `insert(any())` 或 `updateById(any())` 时编译失败，提示单实体方法与集合方法匹配不明确。

## 根因

MyBatis-Plus 同时提供单实体和集合重载。Mockito 的无类型 `any()` 无法为 Java 编译器提供足够的类型信息。

## 修复

为匹配器声明明确的实体类型：

```java
verify(rolePermissionMapper).insert(any(RolePermission.class));
verify(roleMapper).updateById(any(Role.class));
```

批量参数使用集合匹配器：

```java
when(roleMapper.selectBatchIds(anyCollection())).thenReturn(roles);
```
