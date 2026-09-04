package com.leetmodel.team.dto;

import com.leetmodel.common.core.bean.BasePageQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的队伍分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyTeamPageQuery extends BasePageQuery {

    @NotBlank(message = "练习状态不能为空")
    @Pattern(regexp = "PREPARING|IN_PROGRESS|ENDED", message = "练习状态不合法")
    private String practiceStatus;
}
