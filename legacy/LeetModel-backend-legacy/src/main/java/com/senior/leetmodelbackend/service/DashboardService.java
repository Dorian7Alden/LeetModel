package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.mapper.DashboardMapper;
import com.senior.leetmodelbackend.pojo.vo.admin.DashboardVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;

    private static final String[] PROBLEM_STATUS_NAMES = {"草稿", "已发布", "已下线", "已归档"};

    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();

        DashboardVO.StatData stats = new DashboardVO.StatData();
        stats.setTotalProblems(dashboardMapper.countProblems());
        stats.setTotalSubmissions(dashboardMapper.countSubmissions());
        stats.setTodaySubmissions(dashboardMapper.countTodaySubmissions());
        stats.setPendingReviews(dashboardMapper.countPendingReviews());
        vo.setStats(stats);

        List<DashboardVO.TrendPoint> trend = dashboardMapper.getSubmissionTrend();
        if (trend != null) {
            for (DashboardVO.TrendPoint point : trend) {
                if (point.getDate() != null && point.getDate().length() == 10) {
                    point.setDate(point.getDate().substring(5)); // "YYYY-MM-DD" -> "MM-DD"
                }
            }
        }
        vo.setSubmissionTrend(trend != null ? trend : List.of());

        List<DashboardVO.RecentSubmission> recentSubmissions = dashboardMapper.getRecentSubmissions();
        vo.setRecentSubmissions(recentSubmissions != null ? recentSubmissions : List.of());

        List<DashboardVO.StatusDistItem> statusDist = buildStatusDist();
        vo.setProblemStatusDist(statusDist);

        return vo;
    }

    private List<DashboardVO.StatusDistItem> buildStatusDist() {
        List<Map<String, Object>> rows = dashboardMapper.getProblemStatusDistribution();
        List<DashboardVO.StatusDistItem> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            Object statusObj = row.get("problem_status");
            Object cntObj = row.get("cnt");
            if (statusObj == null) continue;
            int status = ((Number) statusObj).intValue();
            int cnt = cntObj != null ? ((Number) cntObj).intValue() : 0;
            DashboardVO.StatusDistItem item = new DashboardVO.StatusDistItem();
            item.setName(status >= 0 && status < PROBLEM_STATUS_NAMES.length
                    ? PROBLEM_STATUS_NAMES[status] : "未知");
            item.setValue(cnt);
            result.add(item);
        }
        return result;
    }
}
