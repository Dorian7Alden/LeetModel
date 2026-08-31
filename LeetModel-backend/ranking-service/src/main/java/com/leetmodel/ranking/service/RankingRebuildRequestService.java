package com.leetmodel.ranking.service;

import com.leetmodel.ranking.mapper.RankingRebuildTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 将任意数量的事件合并到每题唯一的重建任务。 */
@Service
@RequiredArgsConstructor
public class RankingRebuildRequestService {
    private final RankingRebuildTaskMapper taskMapper;

    public void request(Long problemId, String traceId) {
        taskMapper.request(problemId, traceId, LocalDateTime.now());
    }

    public void requestIfFingerprintChanged(Long problemId, String fingerprint, String traceId) {
        taskMapper.requestIfFingerprintChanged(problemId, fingerprint, traceId, LocalDateTime.now());
    }
}
