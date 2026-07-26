# problem 服务设计

> 创建日期：2026-07-26
> 状态：骨架（仅启动类，无业务代码）

---

## 一、职责

题目服务（端口 8083）：题目 CRUD、分类标签管理、全文搜索。当前仅完成服务启动骨架，业务功能待实现。

---

## 二、当前状态

- Spring Boot 应用已创建，`@EnableDiscoveryClient` 注册到 Nacos
- 依赖 `spring-boot-starter-web`、`spring-boot-starter-actuator`、Nacos Discovery
- 已配置多环境配置文件（dev / test / prod）
- 尚未引入 MyBatis-Plus、Flyway 等数据层依赖

---

## 三、未来规划

### 数据模型

- 题目表：标题、描述、分类、标签、创建时间
- 分类和标签支持筛选

### 核心功能

- 题目列表分页查询
- 按分类和标签筛选
- 题目详情查看
- 管理员后台管理题目 CRUD

### 技术选型

- MyBatis-Plus 数据访问
- Flyway 数据库版本管理
- Elasticsearch 全文检索（后续集成）
- Knife4j API 文档
