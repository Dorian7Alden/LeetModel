package com.senior.leetmodelbackend.controller;

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

    @GetMapping("/{user_id}")
    public Result<UserVO> getUserById(@PathVariable("user_id") Long userId) {
        userIdParamValidator.validate(userId);
        User user = userService.getUserById(userId);
        return Result.success("成功通过用户 id 查询到用户", UserVO.createVO(user));
    }

    @GetMapping("/email/{email}")
    public Result<UserVO> getUserByEmail(@PathVariable String email) {
        userEmailParamValidator.validate(email);
        User user = userService.getUserByEmail(email);
        return Result.success("成功通过邮箱查询到用户", UserVO.createVO(user));
    }

    @DeleteMapping("/{user_id}")
    public Result<String> deleteUserById(@PathVariable("user_id") Long userId) {
        userIdParamValidator.validate(userId);
        userService.deleteUserById(userId);
        return Result.success("已经成功删除用户 id 为 " + userId + " 的用户");
    }

    @PutMapping("/{user_id}")
    public Result<String> updateUserById(@PathVariable("user_id") Long userId, @RequestBody User user) {
        log.info("接口捕获到用户: {}", user);

        userIdParamValidator.validate(userId);

        user.setUserId(userId);
        userService.updateUserById(user);
        return Result.success("已经成功更新用户 id 为 " + userId + " 的用户");
    }
}
