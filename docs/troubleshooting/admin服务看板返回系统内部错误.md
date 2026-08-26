# admin 服务看板返回“系统内部错误”

> 创建日期：2026-08-26
> 影响范围：admin-service、common-core（PageResult）、前端 `src/api/dashboard.js`

## 报错现象

管理端首页跑通后访问“首页概览”，红条提示 `系统内部错误`，指标全部显示「暂不可用」。

- 直接访问 `http://localhost:8084/api/admin/dashboard/stats`（带 satoken）返回：
  `{"code":50001,"message":"系统内部错误"}`
- 通过网关 `http://localhost:8080/api/admin/dashboard/stats` 时结果不稳定：短路径能返回真实指标，长路径首包/跨源场景返回 `50001`。
- admin 日志出现两类错误：
  1. `NoClassDefFoundError: com/baomidou/mybatisplus/core/metadata/IPage`
  2. `NoResourceFoundException: No static resource api/admin/dashboard.`

## 根因分析

这是两个独立问题叠加：

### 1. admin-service 缺 `IPage` 类链接失败

`common-core` 的 `PageResult` 定义了 `public static <T> PageResult<T> from(IPage<T> page)`，方法签名引用了 MyBatis-Plus 的 `IPage`。

admin-service 按“纯聚合层”设计，在 `admin-service/pom.xml` 显式排除了 `mybatis-plus-spring-boot3-starter`，于是运行时 classpath 上没有 `IPage`。一旦 Feign/Jackson 反射 `PageResult`（管理端分页、看板聚合都会触发），JVM 解析方法签名时找不到 `IPage`，抛出 `NoClassDefFoundError`，被全局异常处理器兜底为 `50001 系统内部错误`。

> 同款“排除 MyBatis-Plus 后仍引用其类”的体系问题，可参考
> [admin服务启动报MybatisPlusConfig条件装配失败.md](admin服务启动报MybatisPlusConfig条件装配失败.md)。

### 2. 前端看板端点漏掉 `/stats` 子路径

`src/api/dashboard.js` 的 `getDashboard()` 请求 `/admin/dashboard`，但后端真实端点是 `/api/admin/dashboard/stats`。

请求落到 admin-service 后没有匹配的 Controller 方法，被当作静态资源，返回 `NoResourceFoundException` 并被兜底为 `50001`。所以即使管理员角色通过，看板依然报“系统内部错误”。

## 修复方案

### 后端：为 admin-service 提供 `IPage` 类，但不引入 MyBatis-Plus 自动配置

在 `admin-service/pom.xml` 显式声明仅包含 `IPage` 等核心类的 `mybatis-plus-core`（版本沿用父 POM 的 `mybatis-plus.version`）：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-core</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
```

`mybatis-plus-core` 只提供 `IPage` 等 ORM 核心类型，不含 `mybatis-plus-spring-boot3-starter`，不会触发 MyBatis-Plus 自动配置，也不会引入 DataSource。保留了“admin 不连数据库”的原则，同时让 `PageResult` 的方法签名能完成链接。

修改后需要重建并重启 admin-service：

```bash
cd LeetModel-backend
mvn -pl admin-service -am -DskipTests package
```

### 前端：补齐真实端点

```js
export function getDashboard() {
  return request({ url: "/admin/dashboard/stats", method: "get" });
}
```

## 验证

1. 带 satoken 访问 `/api/admin/dashboard/stats`，返回 `code:20000` 且 `metrics` 各项为真实数值或独立的 `available:false` 说明。
2. 管理端“首页概览”展示真实统计（本机：用户 13、队伍 9、题目 11、提交 5、评审 5、客服会话 2、AI 调用 1），无“系统内部错误”提示。
3. `npm run build` 通过，真实浏览器截图无控制台错误。

## 关联设计约束

- `PageResult` 与 MyBatis-Plus 的强耦合是跨服务契约的“隐式依赖”：任何不使用 MyBatis-Plus 的服务，只要引用 `PageResult` 就需要 `IPage` 在 classpath。后续若再新增纯聚合服务，要么保持 `mybatis-plus-core`，要么把 `PageResult.from` 与 `IPage` 解耦到单独工具类。
