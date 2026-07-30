package com.leetmodel.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.dto.UserPageQuery;
import com.leetmodel.user.dto.UserRolesRequest;
import com.leetmodel.user.dto.UserStatusRequest;
import com.leetmodel.user.service.UserService;
import com.leetmodel.user.vo.UserAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "管理员-用户管理")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "分页查询用户列表")
    public Result<PageResult<UserAdminVO>> list(@Valid UserPageQuery query) {
        IPage<UserAdminVO> page = userService.listUsers(query);
        return Result.ok(PageResult.from(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查看用户详情（含角色信息）")
    public Result<UserAdminVO> detail(@PathVariable Long id) {
        UserAdminVO vo = userService.getUserDetail(id);
        return Result.ok(vo);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用用户")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(id, request.getStatus());
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "更新用户角色")
    public Result<Void> updateRoles(@PathVariable Long id,
                                    @Valid @RequestBody UserRolesRequest request) {
        userService.updateRoles(id, request.getRoleIds());
        return Result.ok();
    }
}
