package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.entity.pojo.Competition;
import com.senior.leetmodelbackend.entity.enums.error.CompetitionErrorCode;
import com.senior.leetmodelbackend.entity.pojo.Result;
import com.senior.leetmodelbackend.service.CompetitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/competitions")
public class CompetitionController {

    @Autowired
    private CompetitionService competitionService;


    @GetMapping
    public Result<List<Competition>> getCompetitionList() {

        List<Competition> allCompetitions = competitionService.getAllCompetitions();

        if (allCompetitions.isEmpty()) {
            return Result.error(CompetitionErrorCode.COMPETITION_NOT_FOUND, "没有比赛");
        }
        return Result.success(allCompetitions);
    }


    @GetMapping("/competition/latest3")
    public Result<List<Competition>> getLatest3Competition() {

        List<Competition> allCompetitions = competitionService.getCompetitionsLatest3();

        if (allCompetitions.isEmpty()) {
            return Result.error(CompetitionErrorCode.COMPETITION_NOT_FOUND, "没有比赛");
        }
        if (allCompetitions.size() < 3) {
            return Result.success("即将举办的比赛不足 3 场", allCompetitions);
        }
        return Result.success("即将举办的比赛", allCompetitions);
    }


}
