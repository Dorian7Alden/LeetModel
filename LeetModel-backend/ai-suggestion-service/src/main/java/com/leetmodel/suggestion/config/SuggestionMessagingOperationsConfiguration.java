package com.leetmodel.suggestion.config;

import com.leetmodel.common.messaging.MessagingDomainBacklogContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/** 建议生成任务积压对接统一消息运维视图。 */
@Configuration(proxyBeanMethods = false)
public class SuggestionMessagingOperationsConfiguration {

    @Bean
    MessagingDomainBacklogContributor suggestionTaskBacklog(JdbcTemplate jdbcTemplate) {
        return () -> Map.of(
                "suggestion.waiting", count(jdbcTemplate, "WAITING"),
                "suggestion.running", count(jdbcTemplate, "RUNNING"),
                "suggestion.failed", count(jdbcTemplate, "FAILED"));
    }

    private long count(JdbcTemplate jdbcTemplate, String status) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM suggestion_task WHERE status = ? AND deleted = 0",
                Long.class, status);
        return value == null ? 0L : value;
    }
}
