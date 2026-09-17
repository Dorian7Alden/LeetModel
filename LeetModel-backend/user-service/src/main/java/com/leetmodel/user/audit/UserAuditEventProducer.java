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

/**
 * user-service 的语义操作审计事件生产者。
 *
 * <p>面向用户登录、密码变更、账号状态与角色权限调整等敏感操作，原子写入最小脱敏审计摘要至 Outbox。</p>
 */
@Component
@RequiredArgsConstructor
public class UserAuditEventProducer {
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;

    @Value("${spring.application.name:user-service}")
    private String sourceService;
    @Value("${APP_VERSION:dev}")
    private String serviceVersion;

    /**
     * 发布用户成功登录的操作审计事件。
     *
     * @param userId        登录成功的用户 ID
     * @param authorization 用户的角色与权限授权信息
     */
    public void loginSucceeded(Long userId, UserRoleDTO authorization) {
        publish("AUTH.LOGIN_SUCCESS", "USER", String.valueOf(userId), "USER", String.valueOf(userId),
                "SUCCEEDED", null, null,
                Map.of("authMethod", "PASSWORD", "sessionClass", "JWT"), Map.of(),
                authorization == null ? List.of() : authorization.getRoles().stream()
                        .map(value -> value.toUpperCase(Locale.ROOT)).toList());
    }

    /**
     * 发布用户登录失败被拒绝的操作审计事件。
     *
     * @param failureClass 失败归类（如 USER_NOT_FOUND, PASSWORD_INVALID, ACCOUNT_DISABLED）
     */
    public void loginRejected(String failureClass) {
        publish("AUTH.LOGIN_FAILED", "USER", "anonymous", "USER", "anonymous", "REJECTED",
                failureClass, stableFailureCode(failureClass),
                Map.of("authMethod", "PASSWORD", "failureClass", stableFailureCode(failureClass)),
                Map.of(), List.of());
    }

    /**
     * 发布用户修改密码的操作审计事件。
     *
     * @param userId 目标用户 ID
     */
    public void passwordChanged(Long userId) {
        publish("USER.PASSWORD_CHANGE", "USER", String.valueOf(userId), "USER", String.valueOf(userId),
                "SUCCEEDED", "PASSWORD_CHANGE", null,
                Map.of("credentialVersion", "ROTATED"), Map.of("credentialVersion", "ROTATED"), List.of());
    }

    /**
     * 发布管理员修改用户账号状态的操作审计事件。
     *
     * @param userId 目标用户 ID
     * @param status 修改后的目标状态（1=正常 0=禁用）
     */
    public void statusChanged(Long userId, Integer status) {
        publish("USER.STATUS_CHANGE", "ADMIN", actorId(), "USER", String.valueOf(userId),
                "SUCCEEDED", "ADMIN_REQUEST", null, Map.of(),
                Map.of("status", status == null ? "UNKNOWN" : (status == 1 ? "ACTIVE" : "DISABLED")),
                actorRoles());
    }

    /**
     * 发布管理员修改用户角色的操作审计事件。
     *
     * @param userId  目标用户 ID
     * @param roleIds 分配的角色 ID 列表
     */
    public void rolesChanged(Long userId, List<Long> roleIds) {
        publish("USER.ROLE_CHANGE", "ADMIN", actorId(), "USER", String.valueOf(userId),
                "SUCCEEDED", "ADMIN_REQUEST", null, Map.of(),
                Map.of("roleCount", String.valueOf(roleIds == null ? 0 : roleIds.size()),
                        "roleSetHash", stableSetHash(roleIds)),
                actorRoles());
    }

    /**
     * 发布管理员全量替换角色权限的操作审计事件。
     *
     * @param roleId        目标角色 ID
     * @param permissionIds 分配的权限 ID 列表
     */
    public void rolePermissionsChanged(Long roleId, List<Long> permissionIds) {
        publish("ROLE.PERMISSION_CHANGE", "ADMIN", actorId(), "ROLE", String.valueOf(roleId),
                "SUCCEEDED", "ADMIN_REQUEST", null, Map.of(),
                Map.of("permissionCount", String.valueOf(permissionIds == null ? 0 : permissionIds.size()),
                        "permissionSetHash", stableSetHash(permissionIds)),
                actorRoles());
    }

    /**
     * 构建标准操作审计载荷并投递至本地事务 Outbox。
     *
     * @param operationCode 审计操作编码
     * @param actorType     操作者主体类型（USER/ADMIN）
     * @param actorId       操作者唯一标识
     * @param targetType    操作目标实体类型
     * @param targetId      操作目标唯一标识
     * @param outcome       操作结果（SUCCEEDED/REJECTED）
     * @param reason        业务触发原因
     * @param failureCode   失败错误码
     * @param before        变更前脱敏快照
     * @param after         变更后脱敏快照
     * @param roles         操作者当时拥有的角色列表
     */
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

    /**
     * 获取当前操作执行人 ID。
     *
     * @return 当前登录操作人标识；未登录返回 admin-unknown
     */
    private String actorId() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "admin-unknown";
    }

    /**
     * 获取当前操作人拥有的角色标识列表。
     *
     * @return 角色列表
     */
    private List<String> actorRoles() {
        return List.of();
    }

    /**
     * 规范化失败分类标识为合法下划线大写错误码。
     *
     * @param value 原始失败分类
     * @return 归一化后的安全错误标识
     */
    private String stableFailureCode(String value) {
        if (value == null || value.isBlank()) return "AUTH_REJECTED";
        String normalized = value.toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
        return normalized.isBlank() ? "AUTH_REJECTED" : normalized.substring(0, Math.min(100, normalized.length()));
    }

    /**
     * 计算 ID 集合的 SHA-256 稳定散列摘要，用于审计比对而不泄露具体明细。
     *
     * @param values 待计算的 ID 列表
     * @return 64 位十六进制 SHA-256 字符串
     */
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
