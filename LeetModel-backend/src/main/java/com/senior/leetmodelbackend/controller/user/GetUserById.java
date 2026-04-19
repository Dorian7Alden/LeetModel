package com.senior.leetmodelbackend.controller.user;

import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
public class GetUserById extends UserController {

    private final UserService userService;

    /**
     * 根据 user_id 获取用户信息
     */
    @GetMapping("/{user_id}")
    public Result<User> getUserById(@PathVariable("user_id") Integer userId) {
        ParameterValidator.init()
                .notNull(userId, "用户ID不能为空")
                .isTrue(userId > 0, "用户ID必须大于0")
                .validateAndThrow();
                
        User userById = userService.getUserById(userId);

        if (userById == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "没有找到 id 为 " + userId + " 的用户");
        }

        return Result.success("成功通过用户 id 查询到用户", userById);
    }
}
