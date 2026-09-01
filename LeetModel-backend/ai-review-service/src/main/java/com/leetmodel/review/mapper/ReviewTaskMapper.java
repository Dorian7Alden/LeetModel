package com.leetmodel.review.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.review.entity.ReviewTask;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {
    /** 查询下一个到期等待任务或租约过期任务。 */
    @Select("""
            SELECT * FROM review_task
            WHERE deleted = 0
              AND ((status = 'WAITING' AND next_run_at <= #{now})
                OR (status IN ('LEASED', 'RUNNING') AND lease_expires_at < #{now}))
            ORDER BY priority DESC, next_run_at, create_time
            LIMIT 1
            """)
    ReviewTask selectNextClaimable(@Param("now") LocalDateTime now);

    /** 使用条件更新领取任务并为本次执行生成 fencing token。 */
    @Update("""
            UPDATE review_task
            SET recovery_count = recovery_count + IF(status IN ('LEASED', 'RUNNING'), 1, 0),
                status = 'LEASED', lease_owner = #{owner}, lease_token = #{token},
                lease_expires_at = #{leaseExpiresAt}, heartbeat_at = #{now},
                update_time = #{now}
            WHERE id = #{id} AND deleted = 0
              AND ((status = 'WAITING' AND next_run_at <= #{now})
                OR (status IN ('LEASED', 'RUNNING') AND lease_expires_at < #{now}))
            """)
    int claim(@Param("id") Long id,
              @Param("owner") String owner,
              @Param("token") String token,
              @Param("now") LocalDateTime now,
              @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /** 将已租用任务推进为运行态并持久化稳定 AI 幂等键。 */
    @Update("""
            UPDATE review_task
            SET status = 'RUNNING', started_at = COALESCE(started_at, #{now}),
                heartbeat_at = #{now}, lease_expires_at = #{leaseExpiresAt},
                ai_idempotency_key = #{aiIdempotencyKey}, update_time = #{now}
            WHERE id = #{id} AND status = 'LEASED' AND lease_owner = #{owner} AND lease_token = #{token}
            """)
    int markRunning(@Param("id") Long id,
                    @Param("owner") String owner,
                    @Param("token") String token,
                    @Param("aiIdempotencyKey") String aiIdempotencyKey,
                    @Param("now") LocalDateTime now,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /** 只延长仍由本地执行器跟踪且 token 匹配的活动租约。 */
    @Update("""
            UPDATE review_task
            SET heartbeat_at = #{now}, lease_expires_at = #{leaseExpiresAt}, update_time = #{now}
            WHERE id = #{id} AND lease_owner = #{owner} AND lease_token = #{token}
              AND status IN ('LEASED', 'RUNNING') AND deleted = 0
            """)
    int heartbeat(@Param("id") Long id,
                  @Param("owner") String owner,
                  @Param("token") String token,
                  @Param("now") LocalDateTime now,
                  @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /** 执行器拒绝任务时立即释放尚未开始的租约。 */
    @Update("""
            UPDATE review_task
            SET status = 'WAITING', lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW()
            WHERE id = #{id} AND status = 'LEASED' AND lease_token = #{token}
            """)
    int releaseClaim(@Param("id") Long id, @Param("token") String token);

    /** 对 AI 派发前的安全失败创建新业务 attempt。 */
    @Update("""
            UPDATE review_task
            SET status = 'WAITING', attempt_no = attempt_no + 1, retry_count = retry_count + 1,
                next_run_at = #{nextRunAt}, started_at = NULL, finished_at = NULL,
                failure_type = #{failureType}, error_message = #{errorMessage},
                ai_idempotency_key = #{nextIdempotencyKey}, lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW()
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int scheduleRetry(@Param("id") Long id,
                      @Param("token") String token,
                      @Param("nextRunAt") LocalDateTime nextRunAt,
                      @Param("failureType") String failureType,
                      @Param("errorMessage") String errorMessage,
                      @Param("nextIdempotencyKey") String nextIdempotencyKey);

    /** AI 网关仍处理中时保留同一 attempt 与幂等键再查询。 */
    @Update("""
            UPDATE review_task
            SET status = 'WAITING', next_run_at = #{nextRunAt}, failure_type = #{failureType},
                error_message = #{errorMessage}, lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW()
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int scheduleSameAttempt(@Param("id") Long id,
                            @Param("token") String token,
                            @Param("nextRunAt") LocalDateTime nextRunAt,
                            @Param("failureType") String failureType,
                            @Param("errorMessage") String errorMessage);

    /** 由当前 fencing token 写入 FAILED 或 UNKNOWN 终态。 */
    @Update("""
            UPDATE review_task
            SET status = #{status}, failure_type = #{failureType}, error_message = #{errorMessage},
                finished_at = NOW(), lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW()
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int markTerminalFailure(@Param("id") Long id,
                            @Param("token") String token,
                            @Param("status") String status,
                            @Param("failureType") String failureType,
                            @Param("errorMessage") String errorMessage);

    /** 由当前 fencing token 原子提交任务完成状态。 */
    @Update("""
            UPDATE review_task
            SET status = 'COMPLETED', finished_at = #{finishedAt}, failure_type = NULL,
                error_message = NULL, lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = #{finishedAt}
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int markCompleted(@Param("id") Long id,
                      @Param("token") String token,
                      @Param("finishedAt") LocalDateTime finishedAt);

    /** 用户显式重试失败任务并清理旧租约。 */
    @Update("UPDATE review_task SET status=#{status}, retry_count=#{retryCount}, attempt_no=#{attemptNo}, " +
            "next_run_at=#{nextRunAt}, started_at=NULL, finished_at=NULL, failure_type=NULL, error_message=NULL, " +
            "ai_idempotency_key=#{aiIdempotencyKey}, lease_owner=NULL, lease_token=NULL, " +
            "lease_expires_at=NULL, heartbeat_at=NULL, update_time=NOW() " +
            "WHERE id=#{id} AND status='FAILED'")
    int resetForRetry(ReviewTask task);
}
