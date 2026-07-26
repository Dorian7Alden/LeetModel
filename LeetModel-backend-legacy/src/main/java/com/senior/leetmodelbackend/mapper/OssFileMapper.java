package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.OssFile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OssFileMapper {

    @Options(useGeneratedKeys = true, keyProperty = "fileId")
    @Insert("insert into oss_file (file_name, file_url, file_suffix, content_type, file_size, uploader_id, create_time, update_time) " +
            "values (#{fileName}, #{fileUrl}, #{fileSuffix}, #{contentType}, #{fileSize}, #{uploaderId}, now(), now())")
    void insertOssFile(OssFile ossFile);

    @Select("select * from oss_file where file_id = #{fileId} and is_deleted = 0")
    OssFile getOssFileById(Integer fileId);
}
