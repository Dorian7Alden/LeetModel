package com.leetmodel.file.service;

import com.leetmodel.common.api.dto.FileBindingChangedPayload;

/**
 * 业务文件引用投影服务。
 *
 * <p>引用关系的事实所有者是业务服务，本服务只维护用于删除保护的治理投影。</p>
 */
public interface FileBindingService {

    /**
     * 记录一个有效业务引用，并把文件资产置为已绑定。
     *
     * @param payload 绑定事件载荷
     */
    void bind(FileBindingChangedPayload payload);

    /**
     * 解除一个业务引用；最后一个引用解除后进入未绑定待清理状态。
     *
     * @param payload 解绑事件载荷
     */
    void release(FileBindingChangedPayload payload);

    /**
     * 统计文件资产当前有效引用数量。
     *
     * @param fileId 文件资产标识
     * @return 有效引用数量
     */
    long activeCount(Long fileId);
}
