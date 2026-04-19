## OSS 管理

### 文件存储管理

文件名都是 uuid。无法通过文件名来判断文件类型，因此需要在数据库中存储文件的相关信息，如文件类型、原始文件名、存储路径等，以便后续管理和使用。

### 虚拟目录管理

虚拟目录以文件后缀分类存储。由于无法通过文件名直接判断文件的作用，因此只需要通过文件后缀来进行分类存储即可。


### 上传记录管理

每次上传文件之后，都得记录日志，包括上传时间、文件类型、原始文件名、存储路径等信息，以便后续查询和管理。


### OSS 文件权限管理

### 数据表设计

oss_file()

### sql

```sql

CREATE TABLE oss_file (
    file_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_url VARCHAR(1024) NOT NULL COMMENT 'OSS访问URL',

    file_type VARCHAR(50) COMMENT '文件类型（image/pdf/md/zip等）',
    content_type VARCHAR(100) COMMENT 'MIME类型（image/png等）',

    file_size BIGINT COMMENT '文件大小（字节）',

    uploader_id BIGINT COMMENT '上传人ID',

    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除（0-未删 1-已删）',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

```
