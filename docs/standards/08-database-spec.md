## 数据库设计规范


### 数据库设计规范


> 【强制】：必须遵守的内容
>
> 【推荐】：最好遵守的内容，特殊条件下可以自行调整


1. 【强制】数据库名统一使用 `lm_{service}` 格式，全小写、下划线分隔。`lm` 是 LeetModel 的缩写。每个业务微服务独占一个数据库。
   示例：用户服务 → `lm_user`，题目服务 → `lm_problem`，团队服务 → `lm_team`。

2. 【强制】独立数据库，数据主权隔离。每个业务微服务拥有自己独立的数据库，不允许跨服务直接访问其他服务的数据库表。
   服务间数据交换必须通过 Feign（同步）或 MQ（异步）进行，不能绕过服务边界直接读写对方数据库。

3. 【强制】表名使用全小写 snake_case，单数名词命名。关联表（junction table）以两个关联表名组合，按字母序排列。
   示例：`user`、`role`、`problem`、`tag`；关联表：`user_role`、`role_permission`、`problem_tag`。

4. 【强制】主键统一使用 MyBatis-Plus `IdType.ASSIGN_ID`（雪花算法，`BaseEntity` 提供默认实现）。字段名为 `id`，类型为 `BIGINT`。

5. 【强制】唯一约束命名格式：`uk_{column}`（单列）或 `uk_{table}_{column}`（多列时避免歧义）。
   示例：`uk_username`（user 表 username 列）、`uk_problem_tag`（problem_tag 表的 problem_id + tag_id 组合唯一约束）。

6. 【强制】索引命名格式：普通索引 `idx_{column}`；复合索引按列顺序 `idx_{col1}_{col2}`。
   示例：`idx_status`、`idx_contest_difficulty`（contest_type + difficulty 的复合索引）。

7. 【强制】存储引擎统一使用 InnoDB，字符集统一使用 utf8mb4。DDL 建表语句必须显式声明 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`。

8. 【推荐】核心实体（需要逻辑删除和审计追溯的）继承 `BaseEntity`，获得四个标准字段：
   - `id`：BIGINT，雪花算法生成
   - `create_time`：DATETIME，创建时间
   - `update_time`：DATETIME，更新时间
   - `deleted`：TINYINT，逻辑删除标记（`@TableLogic`）
   非核心的关联表或纯配置表（如 `tag`、`user_role`）可以不继承 BaseEntity，按需定义自己的 id 和时间戳。

9. 【强制】数据库迁移使用 Flyway，迁移脚本统一放在 `resources/db/migration/` 目录下。
   命名格式：`V{version}__{description}.sql`（版本号与描述之间是两个下划线）。
   每个微服务独立管理自己数据库的迁移脚本，不跨服务共用迁移。

10. 【推荐】能字段化就不标签化。如果一种分类属性可以通过固定枚举值或范围字段来表达，优先使用数据库字段直接存储，避免引入标签关联表增加查询复杂度。
    示例：题目难度用 `difficulty TINYINT`（1-3），题目类型用 `contest_type VARCHAR`（MCM_ICM/CUMCM），都不走标签表。
