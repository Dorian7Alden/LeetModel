ALTER TABLE `user`
    CHANGE COLUMN `avatar_url` `avatar_path` VARCHAR(2048) DEFAULT NULL COMMENT '头像对象路径';
