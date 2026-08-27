package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户分页查询请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPageQuery {

    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    private int pageSize = 20;

    /** 搜索关键词（匹配用户名或昵称） */
    private String keyword;

    /** 账号状态筛选：null=全部 1=正常 0=禁用 */
    private Integer status;
}
