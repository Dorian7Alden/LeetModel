package com.leetmodel.ranking.config;

import com.leetmodel.common.messaging.MessagingDomainBacklogContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/** 排行重建任务积压对接统一消息运维视图。 */
@Configuration(proxyBeanMethods = false)
public class RankingMessagingOperationsConfiguration {

    @Bean
    MessagingDomainBacklogContributor rankingTaskBacklog(JdbcTemplate jdbcTemplate) {
        return () -> Map.of(
                "ranking.waiting", count(jdbcTemplate, "WAITING"),
                "ranking.running", count(jdbcTemplate, "RUNNING"));
    }

    private long count(JdbcTemplate jdbcTemplate, String status) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ranking_rebuild_task WHERE status = ? AND deleted = 0",
                Long.class, status);
        return value == null ? 0L : value;
    }
}
