package com.leetmodel.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
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
}
