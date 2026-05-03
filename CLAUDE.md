## Git 提交规范

提交时按内容类型分次提交，不同类型不混在一次提交中：

- **代码** — 前后端代码分开提交（一次前端、一次后端）
- **文档** — 项目文档、开发规范等（`docs/`、`*.md`）
- **配置** — 依赖、构建配置、环境变量等

### 提交格式

```
<type>(<scope>): <subject>
```

| type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 bug |
| `ref` | 重构（不改变功能） |
| `docs` | 文档变更 |
| `chore` | 构建/依赖/配置 |

scope 按模块划分：`front`、`back`、`sql`、`config`

示例：
- `feat(front): 首页概览对接真实数据`
- `feat(back): 首页概览统计接口`
- `docs: 添加 git 提交规范`
- `chore(config): 更新依赖版本`

---

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
| 查看用户 | `USER_VIEW` | 查看用户列表与详情 |
| 修改用户 | `USER_UPDATE` | 修改用户信息 |
| 删除用户 | `USER_DELETE` | 删除用户 |
| 查看题目 | `PROBLEM_VIEW` | 查看题目列表与详情 |
| 管理题目 | `PROBLEM_MANAGE` | 题目 CRUD |
| 查看作品 | `SUBMISSION_VIEW` | 查看作品列表 |
| 管理作品 | `SUBMISSION_MANAGE` | 作品 CRUD |
| 查看标签 | `TAG_VIEW` | 查看标签分类与标签 |
| 管理标签 | `TAG_MANAGE` | 标签 CRUD |
| 查看帖子 | `POST_VIEW` | 查看帖子列表 |
| 查看赛事 | `CONTEST_VIEW` | 查看赛事列表 |
| 查看角色 | `ROLE_VIEW` | 查看角色列表与关联权限 |
| 管理角色 | `ROLE_MANAGE` | 角色 CRUD |
| 查看权限 | `PERMISSION_VIEW` | 查看权限列表 |
| 管理权限 | `PERMISSION_MANAGE` | 权限 CRUD |
| 授权管理 | `AUTH_MANAGE` | 用户-角色、角色-权限关联 |
| 文件上传 | `FILE_UPLOAD` | 上传文件到 OSS |

### 权限矩阵

| 权限 | 成员 | 普通管理员 | 系统管理员 |
|------|:----:|:----------:|:----------:|
| DASHBOARD_VIEW | - | ✓ | ✓ |
| USER_VIEW | - | ✓ | ✓ |
| USER_UPDATE | - | - | ✓ |
| USER_DELETE | - | - | ✓ |
| PROBLEM_VIEW | - | ✓ | ✓ |
| PROBLEM_MANAGE | - | ✓ | ✓ |
| SUBMISSION_VIEW | - | ✓ | ✓ |
| SUBMISSION_MANAGE | - | ✓ | ✓ |
| TAG_VIEW | - | ✓ | ✓ |
| TAG_MANAGE | - | ✓ | ✓ |
| POST_VIEW | - | ✓ | ✓ |
| CONTEST_VIEW | - | ✓ | ✓ |
| ROLE_VIEW | - | - | ✓ |
| ROLE_MANAGE | - | - | ✓ |
| PERMISSION_VIEW | - | - | ✓ |
| PERMISSION_MANAGE | - | - | ✓ |
| AUTH_MANAGE | - | - | ✓ |
| FILE_UPLOAD | - | ✓ | ✓ |

### 技术实现

- **后端**: token 中携带 role claim；登录时从 `user_role` 联表查询角色，取最高角色写入 JWT
- **前端**: `userStore.isAdmin` 判断 role 为 `ADMIN` 或 `SUPER_ADMIN`；路由守卫校验 `/admin` 路径
- **数据表**: `role`、`permission`、`user_role`、`role_permission` 四张表
- **管理页面**: 角色管理、权限管理、授权管理（`/admin/auth`）

