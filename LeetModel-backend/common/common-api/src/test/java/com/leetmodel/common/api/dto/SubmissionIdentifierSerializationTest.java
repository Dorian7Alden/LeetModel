package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionIdentifierSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeSnowflakeIdentifiersWithoutJavascriptPrecisionLoss() throws Exception {
        long id = 2_092_649_726_601_232_386L;
        SubmissionSnapshotDTO snapshot = new SubmissionSnapshotDTO(
                id, id + 1, id + 2, id + 3, 1,
                "solution.pdf", "submissions/solution.pdf", "SUCCESS", true, null);

        String json = objectMapper.writeValueAsString(snapshot);

        assertThat(json).contains("\"id\":\"2092649726601232386\"");
        assertThat(json).contains("\"teamId\":\"2092649726601232387\"");
        assertThat(json).contains("\"problemId\":\"2092649726601232388\"");
        assertThat(json).contains("\"submitterId\":\"2092649726601232389\"");
    }

    @Test
    void shouldSerializePreviewSubmissionIdentifierAsString() throws Exception {
        SubmissionPreviewDTO preview = new SubmissionPreviewDTO(
                2_092_649_726_601_232_386L, "solution.pdf", "https://example.test/solution.pdf");

        assertThat(objectMapper.writeValueAsString(preview))
                .contains("\"submissionId\":\"2092649726601232386\"");
    }

}
