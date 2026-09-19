package com.leetmodel.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件引用投影。
 *
 * <p>由业务绑定与解绑事件幂等维护，用于删除保护；不替代业务服务的领域关系主数据。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_binding")
public class FileBinding extends BaseEntity {
    private Long fileId;
    private String ownerService;
    private String resourceType;
    private String resourceId;
    private String bindingStatus;
    private Long eventVersion;
    private String idempotencyKey;
}
