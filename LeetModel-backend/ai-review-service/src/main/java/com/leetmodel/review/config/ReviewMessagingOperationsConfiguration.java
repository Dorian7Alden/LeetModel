package com.leetmodel.review.config;

import com.leetmodel.common.messaging.MessagingDomainBacklogContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/** 评审领域任务积压对接统一消息运维视图。 */
@Configuration(proxyBeanMethods = false)
public class ReviewMessagingOperationsConfiguration {

    @Bean
    MessagingDomainBacklogContributor reviewTaskBacklog(JdbcTemplate jdbcTemplate) {
        return () -> Map.of(
                "review.waiting", count(jdbcTemplate, "WAITING"),
                "review.running", count(jdbcTemplate, "RUNNING"),
                "review.failed", count(jdbcTemplate, "FAILED"));
    }

    private long count(JdbcTemplate jdbcTemplate, String status) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE status = ? AND deleted = 0", Long.class, status);
        return value == null ? 0L : value;
    }
}
