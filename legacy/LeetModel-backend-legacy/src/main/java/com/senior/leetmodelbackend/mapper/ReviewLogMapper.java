package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.ReviewLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewLogMapper {

    void insertReviewLog(ReviewLog reviewLog);
}
