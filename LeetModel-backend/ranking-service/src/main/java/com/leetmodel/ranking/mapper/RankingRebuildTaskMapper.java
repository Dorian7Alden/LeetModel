package com.leetmodel.ranking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.ranking.entity.RankingRebuildTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 排行重建任务的原子合并、领取与 fencing 操作。 */
public interface RankingRebuildTaskMapper extends BaseMapper<RankingRebuildTask> {

    @Insert("""
            INSERT IGNORE INTO ranking_rebuild_task
              (problem_id, status, requested_revision, completed_revision, trace_id,
               retry_count, next_run_at, recovery_count, create_time, update_time)
            VALUES (#{problemId}, 'IDLE', 0, 0, 'manual-rebuild', 0, #{now}, 0, #{now}, #{now})
            """)
    int ensureProblemRow(@Param("problemId") Long problemId, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO ranking_rebuild_task
              (problem_id, status, requested_revision, completed_revision, trace_id,
               retry_count, next_run_at, recovery_count, create_time, update_time)
            VALUES (#{problemId}, 'WAITING', 1, 0, #{traceId}, 0, #{now}, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
              requested_revision = requested_revision + 1,
              trace_id = #{traceId},
              status = IF(status = 'RUNNING', 'RUNNING', 'WAITING'),
              next_run_at = IF(status = 'RUNNING', next_run_at, LEAST(next_run_at, #{now})),
              update_time = #{now}
            """)
    int request(@Param("problemId") Long problemId,
                @Param("traceId") String traceId,
                @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO ranking_rebuild_task
              (problem_id, status, requested_revision, completed_revision, source_fingerprint,
               trace_id, retry_count, next_run_at, recovery_count, create_time, update_time)
            VALUES (#{problemId}, 'WAITING', 1, 0, #{fingerprint}, #{traceId},
                    0, #{now}, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
              requested_revision = requested_revision
                  + IF(source_fingerprint IS NULL OR source_fingerprint <> #{fingerprint}, 1, 0),
              status = IF(source_fingerprint IS NULL OR source_fingerprint <> #{fingerprint},
                          IF(status = 'RUNNING', 'RUNNING', 'WAITING'), status),
              next_run_at = IF(source_fingerprint IS NULL OR source_fingerprint <> #{fingerprint},
                               IF(status = 'RUNNING', next_run_at, LEAST(next_run_at, #{now})),
                               next_run_at),
              trace_id = IF(source_fingerprint IS NULL OR source_fingerprint <> #{fingerprint},
                            #{traceId}, trace_id),
              source_fingerprint = #{fingerprint},
              update_time = #{now}
            """)
    int requestIfFingerprintChanged(@Param("problemId") Long problemId,
                                    @Param("fingerprint") String fingerprint,
                                    @Param("traceId") String traceId,
                                    @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM ranking_rebuild_task
            WHERE (status = 'WAITING' AND next_run_at <= #{now})
               OR (status = 'RUNNING' AND lease_expires_at < #{now})
            ORDER BY next_run_at, update_time, id
            LIMIT 1
            """)
    RankingRebuildTask selectNextClaimable(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE ranking_rebuild_task
            SET recovery_count = recovery_count + IF(status = 'RUNNING', 1, 0),
                status = 'RUNNING', running_revision = requested_revision,
                lease_owner = #{owner}, lease_token = #{token},
                lease_expires_at = #{leaseExpiresAt}, heartbeat_at = #{now}, update_time = #{now}
            WHERE id = #{id}
              AND ((status = 'WAITING' AND next_run_at <= #{now})
                OR (status = 'RUNNING' AND lease_expires_at < #{now}))
            """)
    int claim(@Param("id") Long id,
              @Param("owner") String owner,
              @Param("token") String token,
              @Param("now") LocalDateTime now,
              @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("""
            UPDATE ranking_rebuild_task
            SET heartbeat_at = #{now}, lease_expires_at = #{leaseExpiresAt}, update_time = #{now}
            WHERE id = #{id} AND status = 'RUNNING'
              AND lease_owner = #{owner} AND lease_token = #{token}
            """)
    int heartbeat(@Param("id") Long id,
                  @Param("owner") String owner,
                  @Param("token") String token,
                  @Param("now") LocalDateTime now,
                  @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Select("SELECT * FROM ranking_rebuild_task WHERE id = #{id} FOR UPDATE")
    RankingRebuildTask selectForCompletion(@Param("id") Long id);

    @Select("SELECT * FROM ranking_rebuild_task WHERE problem_id = #{problemId} FOR UPDATE")
    RankingRebuildTask selectProblemForUpdate(@Param("problemId") Long problemId);

    @Update("""
            UPDATE ranking_rebuild_task
            SET completed_revision = GREATEST(completed_revision, #{revision}),
                status = IF(requested_revision > #{revision}, 'WAITING', 'IDLE'),
                retry_count = 0, next_run_at = #{now}, last_error = NULL,
                lease_owner = NULL, lease_token = NULL, lease_expires_at = NULL,
                heartbeat_at = NULL, update_time = #{now}
            WHERE id = #{id} AND status = 'RUNNING'
              AND lease_token = #{token} AND running_revision = #{revision}
            """)
    int complete(@Param("id") Long id,
                 @Param("token") String token,
                 @Param("revision") Long revision,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ranking_rebuild_task
            SET status = 'WAITING', retry_count = retry_count + 1,
                next_run_at = #{nextRunAt}, last_error = #{error},
                lease_owner = NULL, lease_token = NULL, lease_expires_at = NULL,
                heartbeat_at = NULL, update_time = #{now}
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int scheduleRetry(@Param("id") Long id,
                      @Param("token") String token,
                      @Param("nextRunAt") LocalDateTime nextRunAt,
                      @Param("error") String error,
                      @Param("now") LocalDateTime now);
}
