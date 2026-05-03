package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.pojo.dto.UserUpdateDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.pojo.vo.UserVO;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.validator.user.UserEmailParamValidator;
import com.senior.leetmodelbackend.validator.user.UserIdParamValidator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserIdParamValidator userIdParamValidator;
    private final UserEmailParamValidator userEmailParamValidator;

    @GetMapping("/{userId}")
    public Result<UserVO> getUserById(@PathVariable Long userId) {
        userIdParamValidator.validate(userId);
        User user = userService.getUserById(userId);
        return Result.success(UserVO.createVO(user));
    }

    @GetMapping("/email/{email}")
    public Result<UserVO> getUserByEmail(@PathVariable String email) {
        userEmailParamValidator.validate(email);
        User user = userService.getUserByEmail(email);
        return Result.success(UserVO.createVO(user));
    }

    @DeleteMapping("/{userId}")
    public Result<Void> deleteUserById(@PathVariable Long userId) {
        userIdParamValidator.validate(userId);
        userService.deleteUserById(userId);
        return Result.success();
    }

    @PutMapping("/{userId}")
    public Result<Void> updateUserById(@PathVariable Long userId, @RequestBody UserUpdateDTO dto) {
        userIdParamValidator.validate(userId);
        userService.updateUserById(userId, dto);
        return Result.success();
    }
}
