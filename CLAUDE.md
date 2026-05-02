## 参数校验规范

本项目严格执行 `/project:spring-validator` 中定义的参数校验规范：
- 每个接口对应一个独立的 `XxxParamValidator` 类，Controller 只负责调用
- 校验逻辑封装在 `validator/{domain}/` 包下，内部使用 `ParameterValidator` 链式完成
- Request DTO 禁止使用任何 Bean Validation 注解
- 详细规则见 `.claude/skills/spring-validator/SKILL.md`



## 同步开发信息

开发前，应先逐层浏览项目的目录结构，而不是一次性遍历所有目录。逐层了解后，根据任务目标，选择性地阅读必要的内容，再着手完成任务。


- **关键目录速查**：
  - 开发规范：`docs/instructions/`
  - 功能设计：`docs/system-design`


## 项目的进度

- 开发用户管理

## RBAC 权限体系

项目使用基于 RBAC 的角色-权限管理系统。初始化数据脚本位于 `sqls/v1.0.0/rbac_data.sql`，首次部署时必须执行。

### 角色体系

| 角色 | code | 说明 |
|------|------|------|
| 成员 | `MEMBER` | 普通注册用户，无管理权限 |
| 普通管理员 | `ADMIN` | 可管理题目、标签、作品等业务内容 |
| 系统管理员 | `SUPER_ADMIN` | 最高权限，可进行 RBAC 管理及所有操作 |

### 权限列表

| 权限 | code | 说明 |
|------|------|------|
| 首页概览 | `DASHBOARD_VIEW` | 查看管理端首页 |
| 题目管理 | `PROBLEM_MANAGE` | 题目 CRUD |
| 作品管理 | `SUBMISSION_MANAGE` | 作品/提交 CRUD |
| 标签管理 | `TAG_MANAGE` | 标签 CRUD |
| 角色管理 | `ROLE_MANAGE` | 角色 CRUD |
| 权限管理 | `PERMISSION_MANAGE` | 权限 CRUD |
| 授权管理 | `AUTH_MANAGE` | 用户-角色、角色-权限关联 |

### 权限矩阵

| 权限 | 成员 | 普通管理员 | 系统管理员 |
|------|:----:|:----------:|:----------:|
| DASHBOARD_VIEW | ✓ | ✓ | ✓ |
| PROBLEM_MANAGE | - | ✓ | ✓ |
| SUBMISSION_MANAGE | - | ✓ | ✓ |
| TAG_MANAGE | - | ✓ | ✓ |
| ROLE_MANAGE | - | - | ✓ |
| PERMISSION_MANAGE | - | - | ✓ |
| AUTH_MANAGE | - | - | ✓ |

### 技术实现

- **后端**: token 中携带 role claim；登录时从 `user_role` 联表查询角色，取最高角色写入 JWT
- **前端**: `userStore.isAdmin` 判断 role 为 `ADMIN` 或 `SUPER_ADMIN`；路由守卫校验 `/admin` 路径
- **数据表**: `role`、`permission`、`user_role`、`role_permission` 四张表
- **管理页面**: 角色管理、权限管理、授权管理（`/admin/auth`）

