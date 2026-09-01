package com.leetmodel.common.core.telemetry;

/**
 * 进程启动时确定且不允许被请求覆盖的服务资源。
 *
 * @param service Spring 应用名
 * @param environment 部署环境
 * @param serviceVersion 可发布版本
 * @param instance 实例标识
 */
public record TelemetryResource(
        String service,
        String environment,
        String serviceVersion,
        String instance
) {

    /** 校验资源字段均为稳定低基数值。 */
    public TelemetryResource {
        TelemetryFieldPolicy.requireResourceValue(service, "service");
        TelemetryFieldPolicy.requireResourceValue(environment, "environment");
        TelemetryFieldPolicy.requireResourceValue(serviceVersion, "serviceVersion");
        TelemetryFieldPolicy.requireResourceValue(instance, "instance");
    }
}
