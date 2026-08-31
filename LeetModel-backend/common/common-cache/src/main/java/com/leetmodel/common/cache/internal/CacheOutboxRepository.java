package com.leetmodel.common.cache.internal;

import com.leetmodel.common.cache.config.CacheNamespace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 使用数据所有者本地数据库保存失效 Outbox。
 */
public final class CacheOutboxRepository {

    private static final String INSERT_SQL = """
            INSERT INTO cache_invalidation_outbox
                (id, owner_service, region, scope_key, schema_version, occurred_at,
                 retry_count, next_attempt_at, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CacheNamespace namespace;

    /**
     * 创建 Outbox 仓库。
     *
     * @param jdbcTemplate 本服务数据源
     * @param namespace 缓存命名空间
     */
    public CacheOutboxRepository(JdbcTemplate jdbcTemplate, CacheNamespace namespace) {
        this.jdbcTemplate = jdbcTemplate;
        this.namespace = namespace;
    }

    /**
     * 在当前事务中新增失效事件。
     *
     * @param region 缓存区域
     * @param scopeKey 作用域
     * @param schemaVersion 读模型版本
     * @return 带数据库单调版本的事件
     */
    public CacheInvalidationEvent insert(String region, String scopeKey, String schemaVersion) {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_SQL,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, eventId);
            statement.setString(2, namespace.ownerService());
            statement.setString(3, region);
            statement.setString(4, scopeKey);
            statement.setString(5, schemaVersion);
            statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setTimestamp(7, Timestamp.valueOf(now));
            statement.setTimestamp(8, Timestamp.valueOf(now));
            statement.setTimestamp(9, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Number revision = keyHolder.getKey();
        if (revision == null) throw new IllegalStateException("outbox revision was not generated");
        return new CacheInvalidationEvent(
                eventId,
                namespace.ownerService(),
                region,
                scopeKey,
                revision.longValue(),
                schemaVersion,
                now,
                null
        );
    }

    /**
     * 读取到期的待投递事件。
     *
     * @param limit 最大数量
     * @return 待投递事件
     */
    public List<CacheInvalidationEvent> findPending(int limit) {
        return jdbcTemplate.query("""
                        SELECT id, owner_service, region, scope_key, revision, schema_version, occurred_at
                        FROM cache_invalidation_outbox
                        WHERE delivered_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                        ORDER BY revision
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new CacheInvalidationEvent(
                        resultSet.getString("id"),
                        resultSet.getString("owner_service"),
                        resultSet.getString("region"),
                        resultSet.getString("scope_key"),
                        resultSet.getLong("revision"),
                        resultSet.getString("schema_version"),
                        resultSet.getTimestamp("occurred_at").toLocalDateTime(),
                        null
                ),
                limit
        );
    }

    /**
     * 标记事件投递成功。
     *
     * @param eventId 事件标识
     */
    public void markDelivered(String eventId) {
        jdbcTemplate.update("""
                UPDATE cache_invalidation_outbox
                SET delivered_at = CURRENT_TIMESTAMP, last_error = NULL, update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND delivered_at IS NULL
                """, eventId);
    }

    /**
     * 记录投递失败并安排下一秒重试。
     *
     * @param eventId 事件标识
     * @param error 脱敏错误类型
     */
    public void markFailed(String eventId, String error) {
        jdbcTemplate.update("""
                UPDATE cache_invalidation_outbox
                SET retry_count = retry_count + 1,
                    next_attempt_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 SECOND),
                    last_error = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND delivered_at IS NULL
                """, error, eventId);
    }

    /**
     * 返回待投递数量。
     *
     * @return 待投递数量
     */
    public long pendingCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cache_invalidation_outbox WHERE delivered_at IS NULL
                """, Long.class);
        return count == null ? 0L : count;
    }
}
