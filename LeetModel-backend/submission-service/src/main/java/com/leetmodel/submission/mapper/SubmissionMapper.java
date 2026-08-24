package com.leetmodel.submission.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.submission.entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper public interface SubmissionMapper extends BaseMapper<Submission> {
    @Select("SELECT COALESCE(MAX(version), 0) FROM submission WHERE team_id = #{teamId} AND deleted = 0")
    int selectMaxVersion(@Param("teamId") Long teamId);
}
