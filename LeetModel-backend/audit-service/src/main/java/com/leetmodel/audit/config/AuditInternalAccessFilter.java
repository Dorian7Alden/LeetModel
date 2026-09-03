package com.leetmodel.audit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** audit-service 内部查询的独立令牌边界；不信任转发地址。 */
@Component
public class AuditInternalAccessFilter extends OncePerRequestFilter {
    static final String TOKEN_HEADER = "X-LeetModel-Audit-Token";
    private final byte[] token;

    public AuditInternalAccessFilter(@Value("${AUDIT_INTERNAL_TOKEN:lm-audit-internal-local-only-change-me}") String configuredToken) {
        this.token = configuredToken == null || configuredToken.isBlank()
                ? null : configuredToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/audit/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader(TOKEN_HEADER);
        boolean allowed = token == null
                ? isLoopback(request.getRemoteAddr())
                : presented != null && MessageDigest.isEqual(token,
                presented.getBytes(StandardCharsets.UTF_8));
        if (!allowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "audit internal access denied");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isLoopback(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        if (address.startsWith("127.") || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address)) {
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(address);
            return addr.isLoopbackAddress() || NetworkInterface.getByInetAddress(addr) != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
