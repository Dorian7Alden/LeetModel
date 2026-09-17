package com.leetmodel.problem.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端标签视图对象（含题目使用统计）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagAdminVO {

    /** 标签 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 标签名称 */
    private String name;

    /** 标签业务分类 */
    private String type;

    /** 该标签被题目引用的数量 */
    private Long problemCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
