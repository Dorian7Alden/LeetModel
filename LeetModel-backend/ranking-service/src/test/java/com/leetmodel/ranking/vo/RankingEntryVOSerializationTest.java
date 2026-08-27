package com.leetmodel.ranking.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingEntryVOSerializationTest {

    @Test
    void serializesSnowflakeIdentifiersAsStrings() throws Exception {
        RankingEntryVO entry = RankingEntryVO.builder()
                .teamId(9007199254740993L)
                .submissionId(9007199254740995L)
                .build();

        String json = new ObjectMapper().writeValueAsString(entry);

        assertThat(json).contains("\"teamId\":\"9007199254740993\"");
        assertThat(json).contains("\"submissionId\":\"9007199254740995\"");
    }
}
