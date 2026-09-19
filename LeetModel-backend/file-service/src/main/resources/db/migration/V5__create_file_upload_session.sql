CREATE TABLE `file_upload_session` (
  `id` BIGINT NOT NULL COMMENT '上传会话ID',
  `session_token` VARCHAR(64) NOT NULL COMMENT '客户端上传会话标识',
  `file_id` BIGINT NOT NULL COMMENT '预创建的文件资产ID',
  `purpose_code` VARCHAR(32) NOT NULL COMMENT '业务用途编码',
  `object_key` VARCHAR(512) NOT NULL COMMENT '合并后的最终对象路径',
  `part_prefix` VARCHAR(512) NOT NULL COMMENT '分片对象路径前缀',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `content_type` VARCHAR(127) NOT NULL COMMENT '媒体类型',
  `file_size` BIGINT NOT NULL COMMENT '声明文件字节数',
  `part_size` BIGINT NOT NULL COMMENT '服务端约定的分片字节数',
  `part_count` INT NOT NULL COMMENT '分片总数',
  `status` VARCHAR(20) NOT NULL COMMENT 'UPLOADING、COMPLETING、COMPLETED、ABORTED或EXPIRED',
  `creator_id` BIGINT NULL COMMENT '发起人ID',
  `expire_time` DATETIME(3) NOT NULL COMMENT '会话过期时间',
  `complete_time` DATETIME(3) NULL COMMENT '完成时间',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_upload_session_token` (`session_token`),
  UNIQUE KEY `uk_file_upload_session_file` (`file_id`),
  KEY `idx_file_upload_session_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预签名分片直传会话';

ALTER TABLE `file_asset`
  MODIFY COLUMN `lifecycle_status` VARCHAR(32) NOT NULL COMMENT 'UPLOADING、ACTIVE、AVAILABLE_UNBOUND、DISCOVERED、PENDING_DELETE、DELETE_FAILED或DELETED';
