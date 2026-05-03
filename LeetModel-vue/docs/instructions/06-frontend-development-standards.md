## 前端开发规范


### 适用范围

- LeetModel-vue 前端项目的日常功能开发、重构优化
- AI 协作前端开发



### 目录结构规范



#### 顶层目录

```
src/
├── api/              # HTTP 请求层，按业务领域拆分文件
├── assets/           # 静态资源（图片、图标）
├── components/       # 跨模块共享的通用组件
├── composables/      # 可复用的组合式逻辑（Vue Composables）
├── mock/             # Mock 数据（开发阶段使用）
├── router/           # 路由配置
├── store/            # Pinia 状态管理
├── styles/           # 全局样式（可选，当前使用 style.css）
├── views/            # 页面视图，按功能模块分包
├── App.vue           # 根组件
├── main.js           # 入口文件
└── style.css         # 全局 CSS token 与工具类
```



#### views/ 模块内结构

每个功能模块内部遵循统一的子目录结构：

```
views/[module]/
├── [Module]Page.vue       # 模块首页（路由入口）
├── [Module]Layout.vue     # 模块布局（如需嵌套路由）
├── style.css              # 模块内共享样式
├── components/            # 模块内组件（仅供该模块使用）
│   ├── ComponentA.vue
│   └── ComponentB.vue
└── pages/                 # 子路由页面
    ├── ChildPageA.vue
    └── ChildPageB.vue
```

- **`components/`**：存放模块内部使用的子组件，不被其他模块直接引用
- **`pages/`**：存放该模块的二级路由页面
- **`style.css`**：提取该模块页面的 `<style scoped>` 样式，通过 `@import` 引用

> 【强制】跨模块共享的组件放在 `src/components/`，模块内部组件放在 `views/[module]/components/`



### 命名规范



#### 文件命名

| 类型 | 规范 | 示例 |
|------|------|------|
| 页面组件 | PascalCase + `Page` 后缀 | `HomePage.vue`、`ProblemListPage.vue` |
| 布局组件 | PascalCase + `Layout` 后缀 | `ProblemLayout.vue`、`AdminLayout.vue` |
| 通用组件 | PascalCase | `StatCard.vue`、`DataCard.vue` |
| 路由模块文件 | kebab-case | `problem.js`、`contest.js` |
| CSS 文件 | 统一名称 `style.css` | `views/home/style.css` |
| 目录 | kebab-case 或 camelCase | `views/`、`composables/` |

> 【强制】Vue 组件文件名必须使用 PascalCase
>
> 【强制】作为路由入口的页面组件必须添加 `Page` 后缀
>
> 【强制】作为嵌套路由容器的布局组件必须添加 `Layout` 后缀

#### 变量与方法命名

- Vue 组件内：`ref` 变量用 camelCase，方法用 camelCase
- Composables：`use[Feature]` 命名，如 `useAuth`、`usePagination`
- Store：`use[Name]Store` 命名，如 `useUserStore`
- 事件处理函数：`handle[Event]` 命名，如 `handleLogout`、`handleSubmit`



### 组件分层规范



```
src/
├── components/        # 层级 1: 全局共享组件
│   ├── common/        #   通用 UI 组件 (DataCard, PageHeader, StatCard)
│   ├── charts/        #   图表组件 (GrowthChart)
│   └── layout/        #   全局布局 (AppLayout)
│
└── views/
    └── [module]/
        ├── components/  # 层级 2: 模块内组件
        └── pages/       # 层级 3: 子路由页面
```

> 【强制】组件引用遵循层级规则：pages 可引用 components 和全局组件；components 可引用全局组件；禁止全局组件引用 views 下的组件
>
> 【推荐】组件功能单一，避免一个组件承担过多职责。页面组件负责组装布局，子组件负责具体的 UI 交互



### CSS 规范



#### 样式组织方式

1. **全局样式**：设计 token（CSS 自定义属性）、reset、工具类统一写在 `src/style.css`
2. **模块样式**：从页面组件的 `<style scoped>` 中提取到 `views/[module]/style.css`，通过 `@import` 引入
3. **组件样式**：简单组件的样式保留在 `<style scoped>` 中，复杂样式提取到独立 `.css` 文件

#### 样式引用方式

```vue
<!-- Page-level component -->
<style scoped>
@import './style.css';
</style>

<!-- Nested page under pages/ -->
<style scoped>
@import '../style.css';
</style>
```

> 【强制】页面组件（通过路由直接加载的组件）的样式必须提取到独立的 `style.css` 文件
>
> 【强制】全局 token 统一在 `src/style.css` 中以 CSS 自定义属性形式定义，命名前缀统一使用 `--lm-`
>
> 【推荐】优先使用全局 token 变量而非硬编码色值

#### Design Token 命名规范

| 类别 | 前缀 | 示例 |
|------|------|------|
| 颜色 | `--lm-` | `--lm-primary`、`--lm-bg`、`--lm-border` |
| 圆角 | `--lm-radius-` | `--lm-radius-sm`、`--lm-radius-lg` |
| 阴影 | `--lm-shadow-` | `--lm-shadow`、`--lm-shadow-lg` |
| 过渡 | `--lm-transition` | `--lm-transition` |



### 路由规范



#### 路由文件组织

路由按功能模块拆分，每个模块一个独立文件：

```
router/
├── index.js            # 入口：组装所有路由模块 + beforeEach 守卫
└── modules/
    ├── home.js         # 首页路由
    ├── problem.js      # 题库路由
    ├── contest.js      # 赛事路由
    ├── community.js    # 社区路由
    ├── team.js         # 组队路由
    ├── profile.js      # 个人中心路由
    ├── auth.js         # 认证路由（登录/注册）
    ├── about.js        # 关于/帮助路由
    └── admin.js        # 后台管理路由
```

> 【强制】每个功能模块的路由定义在独立文件中，通过 `router/modules/[module].js` 导出
>
> 【强制】路由入口文件 `router/index.js` 只负责导入各模块路由并组装，不直接定义具体路由
>
> 【强制】路由守卫（`beforeEach`）统一在 `router/index.js` 中定义

#### 路由配置规范

- 使用 `() => import("@/views/...")` 实现路由懒加载
- 路由命名使用 PascalCase
- 需要认证的路由添加 `meta: { requiresAuth: true }`



### 状态管理规范



- 使用 Pinia 进行状态管理
- Store 按业务领域拆分（如 `user.js`、`problem.js`）
- 可复用的逻辑通过 composables 封装（如 `useAuth.js`）
- localStorage 的读写通过 store actions 统一管理

> 【强制】localStorage 的读写操作集中在 store 或 composable 中，禁止在组件中直接操作 localStorage
>
> 【推荐】跨组件复用的逻辑封装为 composable，避免代码重复



### API 规范



#### 文件组织

```
api/
├── request.js         # Axios 实例 + 拦截器
├── user.js            # 用户相关 API
├── problem.js         # 题目相关 API
├── contest.js         # 赛事相关 API
└── ...
```

#### 使用规范

- 所有请求统一使用 `request.js` 中创建的 axios 实例
- 请求拦截器统一注入 token
- 响应拦截器统一处理 401/403 异常
- 后端返回格式统一为 `{ code, msg, data }`，拦截器根据 code 判断业务状态

> 【强制】所有 API 调用必须通过 `request.js` 导出的 axios 实例，禁止直接使用 axios



### Import 路径规范



| 引用场景 | 路径方式 | 示例 |
|----------|---------|------|
| 跨模块引用 | `@/` 绝对路径 | `import { useUserStore } from "@/store/user"` |
| 同模块内部 | 相对路径 | `import TeamCard from "./components/TeamCard.vue"` |
| 关联模块引用 | `@/` 绝对路径 | `import PageHeader from "@/components/common/PageHeader.vue"` |

> 【强制】跨目录（跨功能模块）引用必须使用 `@/` 别名绝对路径
>
> 【推荐】同一功能模块内部的组件引用使用相对路径，保持模块内聚性
