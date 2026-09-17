package com.leetmodel.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.file.entity.FileAsset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface FileAssetMapper extends BaseMapper<FileAsset> {

    @Update("UPDATE file_asset SET deleted = 1, lifecycle_status = 'DELETED', "
            + "update_time = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND deleted = 0")
    int markPhysicallyDeleted(@Param("id") Long id);
}
