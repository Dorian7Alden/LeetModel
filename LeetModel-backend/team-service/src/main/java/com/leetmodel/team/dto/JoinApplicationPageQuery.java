package com.leetmodel.team.dto;

import com.leetmodel.common.core.bean.BasePageQuery;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 入队申请分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JoinApplicationPageQuery extends BasePageQuery {

    @Pattern(regexp = "pending|approved|rejected|cancelled|closed", message = "申请状态不合法")
    private String status;
}
