package com.leetmodel.review.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.review.entity.ReviewTask;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {
    @Select("SELECT * FROM review_task WHERE deleted = 0 AND status = 'WAITING' AND next_run_at <= NOW() ORDER BY create_time LIMIT 1")
    ReviewTask selectNextWaiting();
    @Update("UPDATE review_task SET status='RUNNING', started_at=#{now}, update_time=#{now} WHERE id=#{id} AND status='WAITING'")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);
    @Update("UPDATE review_task SET status=#{status}, retry_count=#{retryCount}, attempt_no=#{attemptNo}, " +
            "next_run_at=#{nextRunAt}, started_at=NULL, finished_at=NULL, error_message=NULL, update_time=NOW() " +
            "WHERE id=#{id} AND status='FAILED'")
    int resetForRetry(ReviewTask task);
}
