package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsIdentifierSerializationTest {

    private static final long SNOWFLAKE_ID = 2_098_977_963_310_931_988L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeTeamIdentifiersWithoutJavascriptPrecisionLoss() throws Exception {
        TeamDTO team = new TeamDTO();
        team.setId(SNOWFLAKE_ID);
        team.setLeaderId(SNOWFLAKE_ID + 1);
        team.setProblemId(SNOWFLAKE_ID + 2);

        String json = objectMapper.writeValueAsString(team);

        assertIdentifiers(json);
    }

    @Test
    void shouldSerializeReviewIdentifiersWithoutJavascriptPrecisionLoss() throws Exception {
        ReviewSummaryDTO review = new ReviewSummaryDTO();
        review.setTaskId(SNOWFLAKE_ID);
        review.setSubmissionId(SNOWFLAKE_ID + 1);
        review.setTeamId(SNOWFLAKE_ID + 2);
        review.setProblemId(SNOWFLAKE_ID + 3);

        String json = objectMapper.writeValueAsString(review);

        assertOperationIdentifiers(json);
    }

    @Test
    void shouldSerializeSuggestionIdentifiersWithoutJavascriptPrecisionLoss() throws Exception {
        SuggestionTaskSummaryDTO suggestion = new SuggestionTaskSummaryDTO();
        suggestion.setTaskId(SNOWFLAKE_ID);
        suggestion.setSubmissionId(SNOWFLAKE_ID + 1);
        suggestion.setTeamId(SNOWFLAKE_ID + 2);
        suggestion.setProblemId(SNOWFLAKE_ID + 3);

        String json = objectMapper.writeValueAsString(suggestion);

        assertOperationIdentifiers(json);
    }

    private void assertIdentifiers(String json) {
        assertThat(json).contains("\"id\":\"2098977963310931988\"");
        assertThat(json).contains("\"leaderId\":\"2098977963310931989\"");
        assertThat(json).contains("\"problemId\":\"2098977963310931990\"");
    }

    private void assertOperationIdentifiers(String json) {
        assertThat(json).contains("\"taskId\":\"2098977963310931988\"");
        assertThat(json).contains("\"submissionId\":\"2098977963310931989\"");
        assertThat(json).contains("\"teamId\":\"2098977963310931990\"");
        assertThat(json).contains("\"problemId\":\"2098977963310931991\"");
    }
}
