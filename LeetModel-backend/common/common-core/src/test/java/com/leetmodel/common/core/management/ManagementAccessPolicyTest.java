package com.leetmodel.common.core.management;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementAccessPolicyTest {

    private final ManagementAccessPolicy policy = new ManagementAccessPolicy("trusted-token");

    @Test
    void healthProbeMustRemainAvailableWithoutCredential() {
        assertThat(policy.isAllowed("/actuator/health/liveness", "203.0.113.4", null))
                .isTrue();
    }

    @Test
    void detailEndpointMustRequireLoopbackOrExactToken() {
        ManagementAccessPolicy localOnly = new ManagementAccessPolicy("");

        assertThat(localOnly.isAllowed("/actuator/prometheus", "127.0.0.1", null)).isTrue();
        assertThat(policy.isAllowed("/actuator/prometheus", "127.0.0.1", null)).isFalse();
        assertThat(policy.isAllowed("/actuator/prometheus", "203.0.113.4", "trusted-token"))
                .isTrue();
        assertThat(policy.isAllowed("/actuator/prometheus", "203.0.113.4", "wrong-token"))
                .isFalse();
        assertThat(policy.isAllowed("/actuator/prometheus", "203.0.113.4", null)).isFalse();
    }
}
