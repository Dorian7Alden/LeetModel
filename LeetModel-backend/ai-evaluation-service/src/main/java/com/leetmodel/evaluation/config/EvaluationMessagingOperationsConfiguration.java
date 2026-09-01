package com.leetmodel.evaluation.config;

import com.leetmodel.common.messaging.MessagingDomainBacklogContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/** 批量评价任务与运行槽积压对接统一消息运维视图。 */
@Configuration(proxyBeanMethods = false)
public class EvaluationMessagingOperationsConfiguration {

    @Bean
    MessagingDomainBacklogContributor evaluationTaskBacklog(JdbcTemplate jdbcTemplate) {
        return () -> Map.of(
                "evaluation.tasks.waiting", count(jdbcTemplate, "evaluation_task", "WAITING"),
                "evaluation.tasks.running", count(jdbcTemplate, "evaluation_task", "RUNNING"),
                "evaluation.runs.waiting", count(jdbcTemplate, "evaluation_run_attempt", "WAITING"),
                "evaluation.runs.running", count(jdbcTemplate, "evaluation_run_attempt", "RUNNING"));
    }

    private long count(JdbcTemplate jdbcTemplate, String table, String status) {
        String sql = switch (table) {
            case "evaluation_task" ->
                    "SELECT COUNT(*) FROM evaluation_task WHERE status = ? AND deleted = 0";
            case "evaluation_run_attempt" ->
                    "SELECT COUNT(*) FROM evaluation_run_attempt WHERE status = ? AND deleted = 0";
            default -> throw new IllegalArgumentException("unsupported evaluation table");
        };
        Long value = jdbcTemplate.queryForObject(sql, Long.class, status);
        return value == null ? 0L : value;
    }
}
