package com.leetmodel.submission.service;

import com.leetmodel.submission.messaging.ReviewTaskMessageContract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将内部 Outbox 状态转换为提交接口可展示的评审派发状态。
 */
@Service
public class ReviewDispatchQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建派发状态查询服务。
     *
     * @param jdbcTemplate 提交数据库访问器
     */
    public ReviewDispatchQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询指定提交的当前评审派发状态。
     *
     * @param submissionId 提交标识
     * @return 面向 API 的稳定状态
     */
    public String status(Long submissionId) {
        List<String> statuses = jdbcTemplate.queryForList("""
                SELECT status FROM message_outbox
                WHERE event_type = ? AND idempotency_key = ?
                ORDER BY create_time DESC LIMIT 1
                """, String.class, ReviewTaskMessageContract.EVENT_TYPE,
                ReviewTaskMessageContract.idempotencyKey(
                        submissionId, ReviewTaskMessageContract.WORKFLOW_VERSION));
        if (statuses.isEmpty()) return "NOT_REQUESTED";
        return switch (statuses.get(0)) {
            case "PUBLISHED" -> "DISPATCHED";
            case "BLOCKED" -> "DISPATCH_BLOCKED";
            default -> "WAITING_DISPATCH";
        };
    }

    /**
     * Legacy Feign 成功后将同一 Outbox 事件收敛为已派发，避免历史查询永久显示等待。
     *
     * @param submissionId 提交标识
     * @param taskId 幂等创建得到的评审任务标识
     */
    public void markLegacyDispatched(Long submissionId, Long taskId) {
        jdbcTemplate.update("""
                UPDATE message_outbox
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                    broker_message_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE event_type = ? AND idempotency_key = ? AND status <> 'PUBLISHED'
                """, "legacy-feign:" + taskId, ReviewTaskMessageContract.EVENT_TYPE,
                ReviewTaskMessageContract.idempotencyKey(
                        submissionId, ReviewTaskMessageContract.WORKFLOW_VERSION));
    }
}
