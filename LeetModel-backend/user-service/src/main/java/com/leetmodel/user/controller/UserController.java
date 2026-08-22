package com.leetmodel.user.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.user.dto.AvatarUploadResponse;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.user.dto.UserUpdateRequest;
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
 * 用户个人信息管理接口（需要登录）。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理")
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserVO> profile() {
        Long userId = UserContext.getUserId();
        UserVO vo = userService.getProfile(userId);
        return Result.ok(vo);
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
