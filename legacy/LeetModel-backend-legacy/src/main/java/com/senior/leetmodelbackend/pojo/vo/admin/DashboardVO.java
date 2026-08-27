package com.senior.leetmodelbackend.pojo.vo.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DashboardVO {
    private StatData stats;
    private List<TrendPoint> submissionTrend;
    private List<RecentSubmission> recentSubmissions;
    private List<StatusDistItem> problemStatusDist;

    @Data
    public static class StatData {
        private int totalProblems;
        private int totalSubmissions;
        private int todaySubmissions;
        private int pendingReviews;
    }

    @Data
    public static class TrendPoint {
        private String date;
        private int count;
    }

    @Data
    public static class RecentSubmission {
        private Long submissionId;
        private String username;
        private String title;
        private String status;
        private BigDecimal totalScore;
        private LocalDateTime submitTime;
    }

    @Data
    public static class StatusDistItem {
        private String name;
        private int value;
    }
}
