package com.leetmodel.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.file.entity.FileBinding;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface FileBindingMapper extends BaseMapper<FileBinding> {

    /**
     * 统计文件资产当前有效引用数量。
     *
     * @param fileId 文件资产标识
     * @return 有效引用数量
     */
    @Select("SELECT COUNT(*) FROM file_binding WHERE file_id = #{fileId} "
            + "AND binding_status = 'ACTIVE' AND deleted = 0")
    long countActiveByFileId(@Param("fileId") Long fileId);

    /**
     * 按业务引用唯一定位投影记录。
     *
     * @param fileId 文件资产标识
     * @param ownerService 业务所有者服务
     * @param resourceType 业务资源类型
     * @param resourceId 业务资源标识
     * @return 引用投影记录，不存在时为 null
     */
    @Select("SELECT * FROM file_binding WHERE file_id = #{fileId} AND owner_service = #{ownerService} "
            + "AND resource_type = #{resourceType} AND resource_id = #{resourceId} AND deleted = 0 LIMIT 1")
    FileBinding selectByReference(
            @Param("fileId") Long fileId,
            @Param("ownerService") String ownerService,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId
    );

    /**
     * 仅在事件版本更新时推进引用状态，防止旧事件覆盖新状态。
     *
     * @param id 引用投影主键
     * @param bindingStatus 目标绑定状态
     * @param eventVersion 事件版本
     * @param idempotencyKey 事件幂等键
     * @return 实际更新行数，0 表示事件版本过旧
     */
    @Update("UPDATE file_binding SET binding_status = #{bindingStatus}, event_version = #{eventVersion}, "
            + "idempotency_key = #{idempotencyKey}, update_time = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND event_version < #{eventVersion} AND deleted = 0")
    int advanceVersion(
            @Param("id") Long id,
            @Param("bindingStatus") String bindingStatus,
            @Param("eventVersion") Long eventVersion,
            @Param("idempotencyKey") String idempotencyKey
    );
}
