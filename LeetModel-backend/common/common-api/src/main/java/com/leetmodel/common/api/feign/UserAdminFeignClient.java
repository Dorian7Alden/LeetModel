package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.UserPageQuery;
import com.leetmodel.common.api.dto.UserRolesRequest;
import com.leetmodel.common.api.dto.UserStatusRequest;
import com.leetmodel.common.api.vo.UserAdminVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户管理内部 Feign 客户端 —— 供 admin-service 调用 user-service 完成管理员操作。
 */
@FeignClient(
        name = "user-service",
        contextId = "userAdminFeignClient",
        path = "/internal/admin/users",
        fallbackFactory = UserAdminFeignFallback.class
)
public interface UserAdminFeignClient {

    @GetMapping
    Result<PageResult<UserAdminVO>> page(@SpringQueryMap UserPageQuery query);

    @GetMapping("/{userId}")
    Result<UserAdminVO> detail(@PathVariable("userId") Long userId);

    @PutMapping("/{userId}/status")
    Result<Void> updateStatus(@PathVariable("userId") Long userId,
                              @RequestBody UserStatusRequest request);

    @PutMapping("/{userId}/roles")
    Result<Void> updateRoles(@PathVariable("userId") Long userId,
                             @RequestBody UserRolesRequest request);
}
