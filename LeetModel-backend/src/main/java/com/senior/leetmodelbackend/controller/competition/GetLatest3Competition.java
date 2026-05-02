//package com.senior.leetmodelbackend.controller.competition;
//
//import com.senior.leetmodelbackend.common.exception.BusinessException;
//import com.senior.leetmodelbackend.common.exception.ResponseCode;
//import com.senior.leetmodelbackend.pojo.entity.Competition;
//import com.senior.leetmodelbackend.pojo.entity.Result;
//import com.senior.leetmodelbackend.service.CompetitionService;
//import lombok.AllArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@AllArgsConstructor
//@RestController
//public class GetLatest3Competition extends CompetitionController {
//
//    private CompetitionService competitionService;
//
//    /**
//     * 获取最近 3 场比赛
//     */
//    @GetMapping("/competition/latest3")
//    public Result<List<Competition>> getLatest3Competition() {
//
//        List<Competition> allCompetitions = competitionService.getCompetitionsLatest3();
//
//        if (allCompetitions.isEmpty()) {
//            throw new BusinessException(ResponseCode.COMPETITION_NO_COMPETITION);
//        }
//        if (allCompetitions.size() < 3) {
//            return Result.success("即将举办的比赛不足 3 场", allCompetitions);
//        }
//        return Result.success("即将举办的比赛", allCompetitions);
//    }
//
//}
