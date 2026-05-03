package com.senior.leetmodelbackend.common.aspect;

import com.senior.leetmodelbackend.common.annotation.RequirePermission;
import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Set;

@Slf4j
@Aspect
@Component
@AllArgsConstructor
public class PermissionAspect {

    private final UserService userService;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            log.warn("权限校验失败：请求属性中缺少 userId");
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        if (requirePermission.selfAccess()) {
            Long targetUserId = extractTargetUserId(joinPoint);
            if (targetUserId != null && targetUserId.equals(userId)) {
                return joinPoint.proceed();
            }
        }

        Set<String> userPermissions = userService.getUserPermissionCodes(userId);

        String[] required = requirePermission.value();
        boolean hasPermission;
        if (requirePermission.mode() == RequirePermission.MatchMode.ANY) {
            hasPermission = Arrays.stream(required).anyMatch(userPermissions::contains);
        } else {
            hasPermission = userPermissions.containsAll(Arrays.asList(required));
        }

        if (!hasPermission) {
            log.warn("权限拒绝: userId={}, required={}, mode={}, userHas={}",
                    userId, Arrays.toString(required), requirePermission.mode(), userPermissions);
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        return joinPoint.proceed();
    }

    private Long extractTargetUserId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] paramValues = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            if ("userId".equals(paramNames[i]) && paramValues[i] instanceof Long) {
                return (Long) paramValues[i];
            }
        }
        return null;
    }
}
