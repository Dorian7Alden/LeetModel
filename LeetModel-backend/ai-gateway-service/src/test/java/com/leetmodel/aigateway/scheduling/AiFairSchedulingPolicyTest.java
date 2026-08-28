package com.leetmodel.aigateway.scheduling;

import com.leetmodel.common.ai.model.AiCallPriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiFairSchedulingPolicyTest {

    private final AiFairSchedulingPolicy policy = new AiFairSchedulingPolicy();
    private final Instant now = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void usesExactWeightedCycleAndKeepsEveryLaneRunnable() {
        Map<AiCallPriority, Long> counts = policy.slots().stream().collect(java.util.stream.Collectors.groupingBy(
                priority -> priority, () -> new EnumMap<>(AiCallPriority.class),
                java.util.stream.Collectors.counting()));
        assertThat(policy.slots()).hasSize(31);
        assertThat(counts).containsEntry(AiCallPriority.P0, 16L)
                .containsEntry(AiCallPriority.P1, 8L)
                .containsEntry(AiCallPriority.P2, 4L)
                .containsEntry(AiCallPriority.P3, 2L)
                .containsEntry(AiCallPriority.P4, 1L);
    }

    @Test
    void agesBackgroundWorkButNeverPromotesItIntoReservedP0() {
        var oldP4 = candidate("old-p4", AiCallPriority.P4, 95);
        var freshP2 = candidate("fresh-p2", AiCallPriority.P2, 0);
        assertThat(policy.effectivePriority(oldP4, now)).isEqualTo(AiCallPriority.P1);
        assertThat(policy.effectivePriority(candidate("very-old", AiCallPriority.P4, 600), now))
                .isEqualTo(AiCallPriority.P1);

        AiFairSchedulingPolicy.SchedulingDecision result = policy.select(List.of(oldP4, freshP2), 0, now);
        assertThat(result.selected().taskId()).isEqualTo("old-p4");
    }

    @Test
    void selectsOldestWithinLaneAndSkipsExpiredTasks() {
        List<AiFairSchedulingPolicy.Candidate> candidates = new ArrayList<>();
        candidates.add(candidate("new-p0", AiCallPriority.P0, 1));
        candidates.add(candidate("old-p0", AiCallPriority.P0, 2));
        candidates.add(new AiFairSchedulingPolicy.Candidate("expired", AiCallPriority.P0,
                now.minusSeconds(10), now.minusSeconds(1)));

        assertThat(policy.select(candidates, 0, now).selected().taskId()).isEqualTo("old-p0");
    }

    @Test
    void p0FloodStillLeavesExecutionSlotsForP3AndP4Backlog() {
        List<AiFairSchedulingPolicy.Candidate> backlog = List.of(
                candidate("continuous-p0", AiCallPriority.P0, 0),
                candidate("waiting-p3", AiCallPriority.P3, 0),
                candidate("waiting-p4", AiCallPriority.P4, 0));
        List<String> selected = new ArrayList<>();
        int cursor = 0;
        for (int slot = 0; slot < 31; slot++) {
            AiFairSchedulingPolicy.SchedulingDecision decision = policy.select(backlog, cursor, now);
            selected.add(decision.selected().taskId());
            cursor = decision.nextCursor();
        }

        assertThat(selected).contains("continuous-p0", "waiting-p3", "waiting-p4");
        assertThat(selected.stream().filter("continuous-p0"::equals).count())
                .isGreaterThan(selected.stream().filter("waiting-p3"::equals).count())
                .isGreaterThan(selected.stream().filter("waiting-p4"::equals).count());
    }

    private AiFairSchedulingPolicy.Candidate candidate(String id, AiCallPriority priority, long waitedSeconds) {
        return new AiFairSchedulingPolicy.Candidate(id, priority, now.minusSeconds(waitedSeconds),
                now.plusSeconds(300));
    }
}
