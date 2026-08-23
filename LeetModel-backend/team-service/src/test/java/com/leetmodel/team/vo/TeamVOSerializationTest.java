package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamVOSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeSnowflakeIdsAsStrings() throws Exception {
        TeamMemberVO member = TeamMemberVO.builder()
                .id(2091483544439365635L)
                .userId(1002L)
                .build();
        TeamVO team = TeamVO.builder()
                .id(2091483544439365634L)
                .leaderId(1002L)
                .members(java.util.List.of(member))
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(team));

        assertThat(json.get("id").asText()).isEqualTo("2091483544439365634");
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("leaderId").isIntegralNumber()).isTrue();
        assertThat(json.at("/members/0/id").isTextual()).isTrue();
        assertThat(json.at("/members/0/userId").isIntegralNumber()).isTrue();
    }
}
