CREATE TABLE `assistant_conversation` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_user_update_time` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 客服会话';

CREATE TABLE `assistant_message` (
  `id` BIGINT NOT NULL,
  `conversation_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `reply_to_message_id` BIGINT NULL,
  `role` VARCHAR(20) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `content` TEXT NULL,
  `error_message` VARCHAR(500) NULL,
  `tool_context_json` JSON NULL,
  `model_name` VARCHAR(100) NULL,
  `ai_call_id` VARCHAR(64) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_conversation_create_time` (`conversation_id`, `create_time`),
  INDEX `idx_reply_to_message` (`reply_to_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 客服消息';
