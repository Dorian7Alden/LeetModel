package com.leetmodel.user.controller;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.user.dto.AvatarUploadResponse;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.user.dto.UserUpdateRequest;
import com.leetmodel.user.dto.UserAuthorizationResponse;
import com.leetmodel.user.service.RoleService;
import com.leetmodel.user.service.UserService;
import com.leetmodel.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客户端用户自助接口。
 *
 * <p>面向客户端，仅操作当前登录用户自己的数据。用户 ID 从 Token 中
 * 解析获得，不接收外部传入的用户 ID，防止越权。</p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "客户端-用户自助")
public class UserController {

    private final UserService userService;
    private final RoleService roleService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserVO> profile() {
        Long userId = UserContext.getUserId();
        UserVO vo = userService.getProfile(userId);
        return Result.ok(vo);
    }

    @Operation(summary = "获取当前用户角色和权限")
    @GetMapping("/me/authorization")
    public Result<UserAuthorizationResponse> authorization() {
        Long userId = UserContext.getUserId();
        UserRoleDTO authorization = roleService.getUserRoles(userId);
        UserAuthorizationResponse response = new UserAuthorizationResponse(
                authorization.getRoles(),
                authorization.getPermissions()
        );
        return Result.ok(response);
    }

    @Operation(summary = "更新个人信息（昵称、邮箱）")
    @PutMapping("/me")
    public Result<UserVO> updateProfile(@Valid @RequestBody UserUpdateRequest request) {
        Long userId = UserContext.getUserId();
        UserVO vo = userService.updateProfile(userId, request);
        return Result.ok(vo);
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        userService.changePassword(userId, request);
        return Result.ok();
    }

    @Operation(summary = "上传头像")
    @PostMapping("/me/avatar")
    public Result<AvatarUploadResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        String avatarUrl = userService.updateAvatar(userId, file);
        return Result.ok(new AvatarUploadResponse(avatarUrl));
    }
}
