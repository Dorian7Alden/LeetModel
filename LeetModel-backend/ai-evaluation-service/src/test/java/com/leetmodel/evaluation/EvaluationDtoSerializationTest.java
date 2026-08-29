package com.leetmodel.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationDtoSerializationTest {

    @Test
    void snowflakeIdsAreSerializedAsStringsForAdminFrontend() throws Exception {
        long largeId = 9_007_199_254_740_993L;
        EvaluationTaskDTO task = new EvaluationTaskDTO();
        task.setTaskId(largeId);
        task.setDatasetId(largeId + 1);
        task.setRuns(List.of());
        EvaluationDatasetDTO dataset = new EvaluationDatasetDTO(
                largeId + 2, "固定集", null, "LOCKED", 0,
                largeId + 3, null, List.of());

        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(task))
                .contains("\"taskId\":\"9007199254740993\"")
                .contains("\"datasetId\":\"9007199254740994\"");
        assertThat(mapper.writeValueAsString(dataset))
                .contains("\"datasetId\":\"9007199254740995\"")
                .contains("\"createdBy\":\"9007199254740996\"");
    }

    @Test
    void weightSchemeAuditIdsAreSerializedAsStrings() throws Exception {
        long largeId = 9_007_199_254_740_993L;
        EvaluationWeightSchemeDTO scheme = new EvaluationWeightSchemeDTO(
                largeId, "REVIEW_BALANCED", "REVIEW_BALANCED_V1", "均衡", "目标",
                "REVIEW", "METRIC_SET_V2", "INACTIVE", largeId + 1, null,
                largeId + 2, null, List.of());

        String json = new ObjectMapper().writeValueAsString(scheme);

        assertThat(json)
                .contains("\"schemeId\":\"9007199254740993\"")
                .contains("\"createdBy\":\"9007199254740994\"")
                .contains("\"deactivatedBy\":\"9007199254740995\"");
    }
}
