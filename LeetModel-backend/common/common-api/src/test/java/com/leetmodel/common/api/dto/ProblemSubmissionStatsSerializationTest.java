package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemSubmissionStatsSerializationTest {

    @Test
    void shouldSerializeProblemIdentifierAsStringButKeepCountNumeric() throws Exception {
        ProblemSubmissionStatsDTO stats = new ProblemSubmissionStatsDTO(
                2_092_421_535_190_147_074L, 4L);

        String json = new ObjectMapper().writeValueAsString(stats);

        assertThat(json).contains("\"problemId\":\"2092421535190147074\"");
        assertThat(json).contains("\"submissionCount\":4");
    }
}
