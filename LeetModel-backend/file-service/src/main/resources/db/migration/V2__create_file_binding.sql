ALTER TABLE `file_asset`
  ADD COLUMN `content_sha256` VARCHAR(64) NULL COMMENT '文件内容SHA-256摘要' AFTER `file_size`,
  ADD COLUMN `access_level` VARCHAR(24) NOT NULL DEFAULT 'BUSINESS_AUTHORIZED' COMMENT '访问级别' AFTER `group_path`;

CREATE TABLE `file_binding` (
  `id` BIGINT NOT NULL COMMENT '引用投影ID',
  `file_id` BIGINT NOT NULL COMMENT '文件资产ID',
  `owner_service` VARCHAR(64) NOT NULL COMMENT '业务事实所有者服务',
  `resource_type` VARCHAR(64) NOT NULL COMMENT '业务资源类型',
  `resource_id` VARCHAR(64) NOT NULL COMMENT '业务资源标识',
  `binding_status` VARCHAR(16) NOT NULL COMMENT 'ACTIVE或RELEASED',
  `event_version` BIGINT NOT NULL COMMENT '绑定事件版本',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '最近一次生效事件幂等键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_binding_ref` (`file_id`, `owner_service`, `resource_type`, `resource_id`),
  KEY `idx_file_binding_file` (`file_id`, `binding_status`),
  KEY `idx_file_binding_resource` (`owner_service`, `resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件引用投影';
