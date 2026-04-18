package com.senior.leetmodelbackend.controller.competition;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ErrorCode;
import com.senior.leetmodelbackend.pojo.entity.Competition;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.CompetitionService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AllArgsConstructor
@RestController
public class GetCompetitions extends CompetitionController {

    private CompetitionService competitionService;

    /**
     * 获取所有赛事
     */
    @GetMapping
    public Result<List<Competition>> getCompetitions() {

        List<Competition> allCompetitions = competitionService.getAllCompetitions();

        if (allCompetitions.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPETITION_NO_COMPETITION);
        }

        return Result.success(allCompetitions);
    }

}
