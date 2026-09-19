package com.leetmodel.common.api.dto;

/**
 * 业务文件绑定或解绑事件的最小载荷。
 *
 * <p>事件只承载引用关系事实，不复制附件说明、头像选择或论文版本等领域数据。</p>
 *
 * @param fileId 文件资产标识
 * @param ownerService 业务事实所有者服务
 * @param resourceType 业务资源类型
 * @param resourceId 业务资源标识
 * @param eventVersion 绑定事件版本，单调递增，用于防止旧事件覆盖新状态
 */
public record FileBindingChangedPayload(
        Long fileId,
        String ownerService,
        String resourceType,
        String resourceId,
        Long eventVersion
) {
}
