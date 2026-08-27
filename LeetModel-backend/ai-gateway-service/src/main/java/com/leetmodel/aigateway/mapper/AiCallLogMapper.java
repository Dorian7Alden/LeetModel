package com.leetmodel.aigateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    @Select("""
            SELECT COUNT(*) AS total_count,
                   COALESCE(SUM(status = 'SUCCEEDED'), 0) AS success_count,
                   COALESCE(SUM(status = 'FAILED'), 0) AS failure_count,
                   COALESCE(SUM(total_tokens), 0) AS total_tokens,
                   COALESCE(ROUND(AVG(duration_ms)), 0) AS average_duration_ms
              FROM ai_call_log
             WHERE deleted = 0
            """)
    AiCallStatsDTO selectStats();
}
