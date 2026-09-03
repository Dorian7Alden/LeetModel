package com.leetmodel.user.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.leetmodel.common.api.audit.OperationAuditContract;
import com.leetmodel.common.api.audit.OperationAuditCatalog;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.common.messaging.OperationAuditMessageCodec;
import com.leetmodel.common.messaging.OperationAuditResources;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** user-service 的语义审计生产者；只写最小摘要，不接触密码、Token 或请求正文。 */
@Component
@RequiredArgsConstructor
public class UserAuditEventProducer {
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;

    @Value("${spring.application.name:user-service}")
    private String sourceService;
    @Value("${APP_VERSION:dev}")
    private String serviceVersion;

    public void loginSucceeded(Long userId, UserRoleDTO authorization) {
        publish("AUTH.LOGIN_SUCCESS", "USER", String.valueOf(userId), "USER", String.valueOf(userId),
                "SUCCEEDED", null, null,
                Map.of("authMethod", "PASSWORD", "sessionClass", "JWT"), Map.of(),
                authorization == null ? List.of() : authorization.getRoles().stream()
                        .map(value -> value.toUpperCase(Locale.ROOT)).toList());
    }

    public void loginRejected(String failureClass) {
        publish("AUTH.LOGIN_FAILED", "USER", "anonymous", "USER", "anonymous", "REJECTED",
                failureClass, stableFailureCode(failureClass),
                Map.of("authMethod", "PASSWORD", "failureClass", stableFailureCode(failureClass)),
                Map.of(), List.of());
    }

    public void passwordChanged(Long userId) {
        publish("USER.PASSWORD_CHANGE", "USER", String.valueOf(userId), "USER", String.valueOf(userId),
                "SUCCEEDED", "PASSWORD_CHANGE", null,
                Map.of("credentialVersion", "ROTATED"), Map.of("credentialVersion", "ROTATED"), List.of());
    }

    public void statusChanged(Long userId, Integer status) {
        publish("USER.STATUS_CHANGE", "ADMIN", actorId(), "USER", String.valueOf(userId),
                "SUCCEEDED", "ADMIN_REQUEST", null, Map.of(),
                Map.of("status", status == null ? "UNKNOWN" : (status == 1 ? "ACTIVE" : "DISABLED")),
                actorRoles());
    }

    public void rolesChanged(Long userId, List<Long> roleIds) {
        publish("USER.ROLE_CHANGE", "ADMIN", actorId(), "USER", String.valueOf(userId),
                "SUCCEEDED", "ADMIN_REQUEST", null, Map.of(),
                Map.of("roleCount", String.valueOf(roleIds == null ? 0 : roleIds.size()),
                        "roleSetHash", stableSetHash(roleIds)),
                actorRoles());
    }

    public void rolePermissionsChanged(Long roleId, List<Long> permissionIds) {
        publish("ROLE.PERMISSION_CHANGE", "ADMIN", actorId(), "ROLE", String.valueOf(roleId),
                "SUCCEEDED", "ADMIN_REQUEST", null, Map.of(),
                Map.of("permissionCount", String.valueOf(permissionIds == null ? 0 : permissionIds.size()),
                        "permissionSetHash", stableSetHash(permissionIds)),
                actorRoles());
    }

    private void publish(String operationCode, String actorType, String actorId, String targetType,
                         String targetId, String outcome, String reason, String failureCode,
                         Map<String, String> before, Map<String, String> after, List<String> roles) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String operationId = CorrelationContext.ensureOperationId();
        String traceId = TraceIdUtil.getTraceId();
        if (traceId == null || traceId.isBlank()) traceId = CorrelationContext.newId();
        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(operationCode);
        OperationAuditPayloadV1 payload = new OperationAuditPayloadV1(
                OperationAuditPayloadV1.VERSION, eventId, operationId, "COMPLETED", now,
                sourceService, serviceVersion, spec.category(), operationCode, spec.riskLevel(), outcome, reason, failureCode,
                actorType, actorId, roles, targetType, targetId, null, before, after,
                traceId, null, null, null, null, null, null);
        OperationAuditContract.validate(payload);
        outbox.enqueue(OperationAuditResources.TOPIC, OperationAuditResources.TAG, codec.envelope(payload));
    }

    private String actorId() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "admin-unknown";
    }

    private List<String> actorRoles() {
        return List.of();
    }

    private String stableFailureCode(String value) {
        if (value == null || value.isBlank()) return "AUTH_REJECTED";
        String normalized = value.toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
        return normalized.isBlank() ? "AUTH_REJECTED" : normalized.substring(0, Math.min(100, normalized.length()));
    }

    private String stableSetHash(List<Long> values) {
        String canonical = (values == null ? List.<Long>of() : values).stream()
                .filter(Objects::nonNull).distinct().sorted().map(String::valueOf)
                .collect(Collectors.joining(","));
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
