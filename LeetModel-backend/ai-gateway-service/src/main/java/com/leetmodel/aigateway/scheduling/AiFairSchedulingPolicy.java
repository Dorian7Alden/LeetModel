package com.leetmodel.aigateway.scheduling;

import com.leetmodel.common.ai.model.AiCallPriority;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 31 槽加权轮转 + 30 秒等待老化；纯策略便于数据库领取前确定性验证。 */
@Component
public class AiFairSchedulingPolicy {

    static final Duration AGING_STEP = Duration.ofSeconds(30);
    private static final List<AiCallPriority> FIRST_HALF = List.of(
            AiCallPriority.P0, AiCallPriority.P1, AiCallPriority.P0, AiCallPriority.P2,
            AiCallPriority.P0, AiCallPriority.P1, AiCallPriority.P0, AiCallPriority.P3,
            AiCallPriority.P0, AiCallPriority.P1, AiCallPriority.P0, AiCallPriority.P2,
            AiCallPriority.P0, AiCallPriority.P1, AiCallPriority.P0);
    private static final List<AiCallPriority> SLOTS = java.util.stream.Stream.concat(
            FIRST_HALF.stream(), java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(AiCallPriority.P4), FIRST_HALF.stream())).toList();

    public SchedulingDecision select(List<Candidate> candidates, int cursor, Instant now) {
        List<RankedCandidate> available = candidates.stream()
                .filter(candidate -> candidate.deadline() == null || candidate.deadline().isAfter(now))
                .map(candidate -> new RankedCandidate(candidate, effectivePriority(candidate, now)))
                .toList();
        if (available.isEmpty()) return new SchedulingDecision(null, normalize(cursor));
        int start = normalize(cursor);
        for (int offset = 0; offset < SLOTS.size(); offset++) {
            int slot = (start + offset) % SLOTS.size();
            AiCallPriority lane = SLOTS.get(slot);
            Candidate selected = available.stream()
                    .filter(candidate -> candidate.effectivePriority() == lane)
                    .map(RankedCandidate::candidate)
                    .min(Comparator.comparing(Candidate::queuedAt).thenComparing(Candidate::taskId))
                    .orElse(null);
            if (selected != null) return new SchedulingDecision(selected, (slot + 1) % SLOTS.size());
        }
        return new SchedulingDecision(null, start);
    }

    public AiCallPriority effectivePriority(Candidate candidate, Instant now) {
        if (candidate.priority() == AiCallPriority.P0) return AiCallPriority.P0;
        long steps = Math.max(0, Duration.between(candidate.queuedAt(), now).toSeconds()
                / AGING_STEP.toSeconds());
        int original = candidate.priority().ordinal();
        int promoted = Math.max(AiCallPriority.P1.ordinal(), original - Math.toIntExact(Math.min(steps, 4)));
        return AiCallPriority.values()[promoted];
    }

    public List<AiCallPriority> slots() {
        return SLOTS;
    }

    private int normalize(int cursor) {
        return Math.floorMod(cursor, SLOTS.size());
    }

    public record Candidate(String taskId, AiCallPriority priority, Instant queuedAt, Instant deadline) {}

    public record SchedulingDecision(Candidate selected, int nextCursor) {}

    private record RankedCandidate(Candidate candidate, AiCallPriority effectivePriority) {}
}
