package com.leetmodel.team.dto;

import com.leetmodel.common.core.bean.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公共队伍分页查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TeamPublicPageQuery extends BasePageQuery {

    private Long problemId;
    private String keyword;
    private Boolean availableOnly;
    private Boolean recruitingOnly;
    private Boolean excludeJoined;
    private Boolean needModeler;
    private Boolean needProgrammer;
    private Boolean needWriter;
    private String sortBy;
}
