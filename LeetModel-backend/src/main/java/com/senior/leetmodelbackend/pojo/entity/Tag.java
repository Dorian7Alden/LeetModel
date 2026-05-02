package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Tag {

    /**
     * 标签ID
     */
    private Long tagId;
    
    /**
     * 所属分类ID
     */
    private Long categoryId;
    
    /**
     * 标签名称
     */
    private String name;
    

    /**
     * 标签描述
     */
    private String description;
    
    /**
     * 排序权重（升序）
     */
    private Integer sortOrder;
    
    /**
     * 引用次数（冗余计数）
     */
    private Integer usageCount;
    
    /**
     * 状态：0-禁用 1-启用
     */
    private Boolean status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
