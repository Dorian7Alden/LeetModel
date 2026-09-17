package com.leetmodel.audit.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuditInternalAccessFilterTest {

    @Test
    void allowsMatchingTokenRegardlessOfRemoteAddress() throws Exception {
        AuditInternalAccessFilter filter = new AuditInternalAccessFilter("lm-audit-internal-local-only-change-me");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/audit/events");
        request.setRemoteAddr("10.135.189.84");
        request.addHeader("X-LeetModel-Audit-Token", "lm-audit-internal-local-only-change-me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsMismatchedToken() throws Exception {
        AuditInternalAccessFilter filter = new AuditInternalAccessFilter("lm-audit-internal-local-only-change-me");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/audit/events");
        request.setRemoteAddr("10.135.189.84");
        request.addHeader("X-LeetModel-Audit-Token", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void allowsLocalInterfaceAddressWhenTokenIsNull() throws Exception {
        AuditInternalAccessFilter filter = new AuditInternalAccessFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/audit/events");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsExternalAddressWhenTokenIsNull() throws Exception {
        AuditInternalAccessFilter filter = new AuditInternalAccessFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/audit/events");
        request.setRemoteAddr("203.0.113.195");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void passesNonInternalRequestsDirectly() throws Exception {
        AuditInternalAccessFilter filter = new AuditInternalAccessFilter("lm-audit-internal-local-only-change-me");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("203.0.113.195");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
