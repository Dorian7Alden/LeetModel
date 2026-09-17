package com.leetmodel.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.assistant.entity.AssistantProductionChangeRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AssistantProductionChangeRequestMapper
        extends BaseMapper<AssistantProductionChangeRequest> {

    @Select("SELECT * FROM assistant_production_change_request "
            + "WHERE change_request_id = #{changeRequestId} AND deleted = 0 FOR UPDATE")
    AssistantProductionChangeRequest selectByRequestIdForUpdate(
            @Param("changeRequestId") String changeRequestId);
}
