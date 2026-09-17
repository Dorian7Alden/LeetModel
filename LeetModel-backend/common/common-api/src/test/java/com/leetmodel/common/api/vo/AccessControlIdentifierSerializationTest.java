package com.leetmodel.common.api.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlIdentifierSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeAccessControlIdentifiersWithoutJavascriptPrecisionLoss() throws Exception {
        long id = 2_098_977_987_566_616_600L;
        UserAdminVO user = UserAdminVO.builder()
                .id(id)
                .roles(List.of(UserAdminVO.RoleSimpleVO.builder().id(id + 1).build()))
                .build();
        RoleVO role = RoleVO.builder().id(id + 2).build();
        PermissionVO permission = PermissionVO.builder().id(id + 3).build();

        assertThat(objectMapper.writeValueAsString(user))
                .contains("\"id\":\"2098977987566616600\"")
                .contains("\"id\":\"2098977987566616601\"");
        assertThat(objectMapper.writeValueAsString(role))
                .contains("\"id\":\"2098977987566616602\"");
        assertThat(objectMapper.writeValueAsString(permission))
                .contains("\"id\":\"2098977987566616603\"");
    }
}
