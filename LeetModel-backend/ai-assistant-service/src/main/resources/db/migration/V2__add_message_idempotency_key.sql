ALTER TABLE `assistant_message`
  ADD COLUMN `client_request_id` VARCHAR(64) NULL AFTER `user_id`,
  ADD UNIQUE INDEX `uk_conversation_request` (`conversation_id`, `client_request_id`);
