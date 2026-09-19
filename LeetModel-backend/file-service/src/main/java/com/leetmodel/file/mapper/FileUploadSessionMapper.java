package com.leetmodel.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.file.entity.FileUploadSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface FileUploadSessionMapper extends BaseMapper<FileUploadSession> {

    /**
     * 仅在会话仍处于待上传状态时抢占完成处理权。
     *
     * @param id 会话主键
     * @param status 目标状态
     * @return 实际更新行数
     */
    @Update("UPDATE file_upload_session SET status = #{status}, update_time = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'UPLOADING' AND deleted = 0")
    int claim(@Param("id") Long id, @Param("status") String status);

    /**
     * 推进会话到终态并记录完成时间。
     *
     * @param id 会话主键
     * @param status 目标状态
     * @param recordComplete 是否记录完成时间
     * @return 实际更新行数
     */
    @Update("UPDATE file_upload_session SET status = #{status}, "
            + "complete_time = IF(#{recordComplete} = 1, CURRENT_TIMESTAMP(3), complete_time), "
            + "update_time = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND deleted = 0")
    int finish(@Param("id") Long id,
               @Param("status") String status,
               @Param("recordComplete") int recordComplete);
}
