# review 服务启动报 Flyway 迁移 V9 超长失败

## 报错现象

`ai-review-service` 启动时提示 `UnsatisfiedDependencyException`，连锁导致 `internalReviewController`、`reviewService`、`reviewTaskMapper` 创建失败，根本原因定位在 `flywayInitializer` 执行迁移抛出异常，服务进程立即退出。

## 根因分析

在 `V9__add_deep_evidence_review_v3.sql` 中，向 `review_version` 表插入 V3 评审版本记录时，`final_contract_version` 字段写入了 `'DEEP_EVIDENCE_REVIEW_V3'`（长度 23）。

而该字段在 `V2__version_review_v1.sql` 中被定义为 `VARCHAR(20)`。在 MySQL 严格模式（`STRICT_TRANS_TABLES`）下，超长字符串导致执行报 `Data too long for column 'final_contract_version'`。Flyway 捕获后记录迁移失败（`flyway_schema_history` 表中留下 `version = 9, success = 0`）。后续每次启动，Flyway 发现失败迁移历史即直接中断初始化。

## 修复方案

1. 在 `V9__add_deep_evidence_review_v3.sql` 开头增加字段扩容 DDL，将 `final_contract_version` 扩容至 `VARCHAR(40)`（与 `version_code` 保持一致）：
   ```sql
   ALTER TABLE `review_version` MODIFY COLUMN `final_contract_version` VARCHAR(40) NOT NULL;
   ```
2. 清除 `flyway_schema_history` 中的失败记录：
   ```sql
   DELETE FROM lm_review.flyway_schema_history WHERE version = '9';
   ```
3. 重新启动 `ai-review-service`，Flyway 自动执行扩容、版本注册与 `review_v3_result` 建表，迁移成功标记为 `success = 1`。
