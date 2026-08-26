ALTER TABLE `assistant_message`
  ADD UNIQUE INDEX `uk_reply_to_message` (`reply_to_message_id`);
