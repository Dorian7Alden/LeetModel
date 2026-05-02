package com.senior.leetmodelbackend.pojo.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table(name = "tag")
public class Tag {
    
    /**
     * 标签ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
