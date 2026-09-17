package com.leetmodel.common.core.filter;

import com.leetmodel.common.core.management.ManagementAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementEndpointServletFilterTest {

    @Test
    void remoteMetricsRequestWithoutTokenMustBeHidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRemoteAddr("203.0.113.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new ManagementEndpointServletFilter("trusted-token").doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void trustedMetricsRequestMustContinue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRemoteAddr("203.0.113.8");
        request.addHeader(ManagementAccessPolicy.TOKEN_HEADER, "trusted-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new ManagementEndpointServletFilter("trusted-token").doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
