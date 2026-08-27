# DTO 与 VO 的区别与命名场景

> 日期：2026-08-22
> 相关模块：各微服务的 dto 包与 vo 包

---

## 问题

项目里的 dto 包中有 LoginResponse、AvatarUploadResponse 等 Response 对象，vo 包中有 UserVO、RoleVO 等 VO 对象。它们都是后端返回给前端的数据对象，有什么区别？什么场景该用哪一个？

## 结论

两者本质相同，都是后端向前端输出的数据对象，但关注点不同：

- dto 中的 Response：接口专属的输出对象，接口需要什么就返回什么，通常一次只服务一个接口。
- vo 中的 VO：面向展示的视图对象，通常对应某个业务实体或聚合数据，可被多个接口复用。

## 对比

| 维度 | dto 中的 Response | vo 中的 VO |
|---|---|---|
| 核心用途 | 定义接口传输契约 | 定义展示视图 |
| 生命周期 | 通常只服务一个接口 | 可被多个接口复用 |
| 与实体关系 | 可能只是实体的一部分，也可能来自多个实体 | 通常由实体或领域对象组装而来 |
| 命名特征 | LoginResponse、AvatarUploadResponse | UserVO、RoleVO、UserAdminVO |
| 是否脱敏 | 需要手动控制 | 通常专门设计为脱敏后数据 |

## 项目实例

### dto 中的 Response

```java
// 登录响应 DTO —— 只有登录接口用，字段是 token + 用户基础信息
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
}
```

```java
// 头像上传响应 —— 只有头像上传接口用，就一个 avatarUrl
public class AvatarUploadResponse {
    private String avatarUrl;
}
```

特点：

- 接口返回什么，类就定义什么，字段和某个接口一一对应。
- 换一个接口，通常不会复用这个类。
- 类和接口语义强绑定，例如 LoginResponse 一看就知道是登录接口的返回。

### vo 中的 VO

```java
// 用户信息 VO，脱敏后不含密码
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime createTime;
}
```

```java
// 管理员视角用户详情 VO，额外聚合了角色列表
public class UserAdminVO {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<RoleSimpleVO> roles;
}
```

特点：

- 从 User 实体转换而来，已经去掉 password。
- UserVO 可以被查询个人信息、更新个人信息等接口复用。
- UserAdminVO 面向管理员页面，在 User 基础上聚合了角色信息。
- 服务层完成组装，Controller 直接返回，不暴露实体。

## 命名场景

### 使用 dto 中的 Response

1. 接口返回的数据结构非常定制化，基本不会复用。
2. 返回的不是某个实体，而是多个实体拼出来的、且只为此接口服务。
3. 返回的数据和请求、查询强相关，例如登录成功后的 token、userId、username。

典型对象：

```text
LoginResponse
AvatarUploadResponse
```

### 使用 vo 中的 VO

1. 返回的是某个业务实体或领域对象的展示形态。
2. 同一个视图对象可能被多个接口复用。
3. 需要脱敏、聚合、扁平化实体关系。
4. 不同视角需要不同 VO，例如普通用户视角 UserVO，管理员视角 UserAdminVO。

典型对象：

```text
UserVO
RoleVO
PermissionVO
UserAdminVO
```

## dto 包内的完整角色

在项目的 dto 包中，实际上有四种角色：

| 类型 | 命名 | 方向 | 场景 |
|---|---|---|---|
| Request | LoginRequest、RegisterRequest | 前端到后端 | 接收请求参数，带 @Valid 校验 |
| Query | UserPageQuery | 前端到后端 | 分页查询条件 |
| Response | LoginResponse、AvatarUploadResponse | 后端到前端 | 定制化的接口返回 |
| VO | UserVO、UserAdminVO | 后端到前端 | 可复用的展示对象 |

## 面试可讲点

> "DTO 是传输层对象，关注接口契约；VO 是视图对象，关注展示。我们项目里，像登录这种一次性、定制化的返回，放在 dto 包命名成 XxxResponse；像用户信息这种需要脱敏、可复用的展示数据，放在 vo 包命名成 XxxVO。管理员视角需要额外聚合角色列表，就再拆一个 UserAdminVO。这样做的好处是：接口契约和展示视图分离，Controller 不暴露实体，前端拿到的字段稳定可控。"
