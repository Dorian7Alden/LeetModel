package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.vo.admin.DashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM problem")
    int countProblems();

    @Select("SELECT COUNT(*) FROM submission")
    int countSubmissions();

    @Select("SELECT COUNT(*) FROM submission WHERE DATE(submit_time) = CURDATE()")
    int countTodaySubmissions();

    @Select("SELECT COUNT(*) FROM submission WHERE status = 'PENDING'")
    int countPendingReviews();

    @Select("SELECT DATE_FORMAT(submit_time, '%Y-%m-%d') AS `date`, COUNT(*) AS `count` " +
            "FROM submission " +
            "WHERE submit_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
            "GROUP BY DATE_FORMAT(submit_time, '%Y-%m-%d') " +
            "ORDER BY DATE(submit_time)")
    List<DashboardVO.TrendPoint> getSubmissionTrend();

    @Select("SELECT s.submission_id AS submissionId, u.username, s.title, s.status, " +
            "s.total_score AS totalScore, s.submit_time AS submitTime " +
            "FROM submission s " +
            "LEFT JOIN user u ON s.user_id = u.user_id " +
            "ORDER BY s.submit_time DESC LIMIT 5")
    List<DashboardVO.RecentSubmission> getRecentSubmissions();

    @Select("SELECT problem_status, COUNT(*) AS cnt FROM problem GROUP BY problem_status")
    List<Map<String, Object>> getProblemStatusDistribution();
}
