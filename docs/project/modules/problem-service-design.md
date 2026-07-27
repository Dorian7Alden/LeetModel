# problem 服务设计

> 创建日期：2026-07-26
> 最后更新：2026-07-27
> 状态：已实现（4 表 + CRUD + 分页筛选）

---

## 一、职责

题目服务（端口 8083）：题目 CRUD、标签管理、外部链接管理、分页筛选查询。

---

## 二、数据模型

4 张表，内聚于 Problem 服务数据库 `lm_problem`，不跨服务共享：

### problem（题目主表）

继承 BaseEntity（雪花 ID、create_time、update_time、逻辑删除）。

| 字段 | 类型 | 说明 |
|------|------|------|
| title | VARCHAR(255) | 题目标题 |
| content_file_id | BIGINT | 题目描述 MD 文件 ID |
| contest_type | VARCHAR(20) | MCM_ICM（美赛）/ CUMCM（国赛） |
| difficulty | TINYINT | 1=简单 2=中等 3=困难 |
| average_score | DECIMAL(5,2) | 平均得分，默认 0.00 |
| status | TINYINT | 0=草稿 1=已发布 2=已下线 3=已归档 |
| creator_id | BIGINT | 创建者用户 ID |

### tag（标签表）

扁平化设计，无分类层级。只存 name 字段。

### problem_tag（题目-标签关联）

M:N 关联表，problem_id + tag_id 唯一。

### problem_link（题目外部链接）

1:N 归属题目。字段：title、url、description、sort_order。
用于数学建模题目的数据集、视频、文档等外部资料引用。

---

## 三、API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /problems | GET | 分页查询（status/difficulty/contestType/tagId/keyword 筛选） |
| /problems/{id} | GET | 详情（含标签+链接） |
| /problems | POST | 创建 |
| /problems/{id} | PUT | 更新 |
| /problems/{id} | DELETE | 逻辑删除 |
| /tags | GET | 全部标签 |
| /tags | POST | 创建标签 |
| /tags/{id} | PUT | 更新标签 |
| /tags/{id} | DELETE | 删除标签 |
| /public/problems | GET | 公开浏览（仅返回已发布） |
| /public/problems/{id} | GET | 公开详情 |

---

## 四、设计决策

### 字段优先于标签

difficulty 和 contest_type 直接作为 problem 表字段，不通过标签间接表达。因为：
- 难度固定 3 级，赛事类型只有 2 种
- 字段查询比标签关联更高效
- 减少不必要的关联表

### 标签内聚

标签体系只服务于 Problem 业务，不设计跨服务通用标签系统。微服务数据主权独立。

### 外部链接 1:N

旧设计为 M:N（link 表独立 + problem_link 关联），新设计简化为 1:N（problem_link 归属题目）。在 Problem 领域内，一个链接只与一个题目绑定，无需 M2M 抽象。

### 依赖简化为 common-core

Problem 服务不依赖 common-security。需要鉴权的接口通过 Gateway 统一拦截，服务内部不需要 Sa-Token 注入。

---

## 五、技术依赖

- MyBatis-Plus 3.5.7（雪花 ID、分页、逻辑删除）
- Flyway（数据库版本管理，V1__init_problem_tables.sql）
- MySQL 8.0+（独立数据库 lm_problem）
- Knife4j 4.5.0（API 文档）
