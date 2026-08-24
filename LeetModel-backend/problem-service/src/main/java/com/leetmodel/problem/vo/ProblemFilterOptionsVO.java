package com.leetmodel.problem.vo;

import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 公开题库可用筛选项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemFilterOptionsVO {
    private List<Contest> contests;
    private List<Tag> tags;
}
