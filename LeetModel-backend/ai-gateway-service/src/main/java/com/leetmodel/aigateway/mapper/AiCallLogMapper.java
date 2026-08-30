package com.leetmodel.aigateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiModelCallStatsDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    AiCallStatsDTO selectStats(AiCallQueryDTO query);

    List<AiModelCallStatsDTO> selectModelStats(AiCallQueryDTO query);

    @Select("""
            SELECT * FROM ai_call_log
             WHERE deleted = 0 AND status = 'SUCCEEDED' AND cost_source = 'UNKNOWN'
               AND cost_enrichment_status IN ('PENDING', 'RETRY_WAIT')
               AND (cost_next_retry_at IS NULL OR cost_next_retry_at <= #{now})
             ORDER BY create_time ASC
             LIMIT #{limit}
            """)
    List<AiCallLog> selectCostEnrichmentDue(@Param("now") LocalDateTime now,
                                            @Param("limit") int limit);

    @Update("""
            UPDATE ai_call_log
               SET cost_amount = #{amount}, cost_currency = #{currency},
                   cost_source = 'PRICE_SNAPSHOT_ESTIMATED',
                   price_snapshot_version = #{snapshotVersion}, cost_completeness = 'COMPLETE',
                   cost_enrichment_status = 'COMPLETED',
                   cost_enrichment_attempts = cost_enrichment_attempts + 1,
                   cost_last_attempt_at = #{attemptedAt}, cost_next_retry_at = NULL
             WHERE id = #{id} AND deleted = 0 AND cost_source = 'UNKNOWN'
               AND cost_enrichment_status IN ('PENDING', 'RETRY_WAIT')
            """)
    int completeEstimatedCost(@Param("id") Long id, @Param("amount") BigDecimal amount,
                              @Param("currency") String currency,
                              @Param("snapshotVersion") String snapshotVersion,
                              @Param("attemptedAt") LocalDateTime attemptedAt);

    @Update("""
            UPDATE ai_call_log
               SET cost_enrichment_status = #{status},
                   cost_enrichment_attempts = cost_enrichment_attempts + 1,
                   cost_last_attempt_at = #{attemptedAt}, cost_next_retry_at = #{nextRetryAt}
             WHERE id = #{id} AND deleted = 0 AND cost_source = 'UNKNOWN'
               AND cost_enrichment_status IN ('PENDING', 'RETRY_WAIT')
            """)
    int recordCostEnrichmentMiss(@Param("id") Long id, @Param("status") String status,
                                 @Param("attemptedAt") LocalDateTime attemptedAt,
                                 @Param("nextRetryAt") LocalDateTime nextRetryAt);
}
