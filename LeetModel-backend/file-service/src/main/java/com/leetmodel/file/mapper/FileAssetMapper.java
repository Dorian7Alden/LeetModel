package com.leetmodel.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.file.entity.FileAsset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface FileAssetMapper extends BaseMapper<FileAsset> {

    @Update("UPDATE file_asset SET deleted = 1, lifecycle_status = 'DELETED', "
            + "update_time = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND deleted = 0")
    int markPhysicallyDeleted(@Param("id") Long id);

    /**
     * 按桶内对象路径查询资产。
     *
     * @param bucketName 桶名
     * @param objectKey 对象路径
     * @return 文件资产，不存在时为 null
     */
    @Select("SELECT * FROM file_asset WHERE bucket_name = #{bucketName} AND object_key = #{objectKey} "
            + "AND deleted = 0 LIMIT 1")
    com.leetmodel.file.entity.FileAsset selectByObjectKey(
            @Param("bucketName") String bucketName,
            @Param("objectKey") String objectKey
    );
}
