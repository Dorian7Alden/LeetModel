package com.senior.leetmodelbackend.controller.user;

import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ErrorCode;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
public class DeleteUserById extends UserController {

    private final UserService userService;

    /**
     * 根据 user_id 删除用户信息
     */
    @DeleteMapping("/{user_id}")
    public Result<String> deleteUserById(@PathVariable("user_id") Integer userId) {

        ParameterValidator.init()
                .notNull(userId, "用户ID不能为空")
                .isTrue(userId > 0, "用户ID必须大于0")
                .validateAndThrow();

        if (userService.getUserById(userId) == null)
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "删除失败，没有找到 id 为 " + userId + " 的用户");

        userService.deleteUserById(userId);
        return Result.success("已经成功删除用户 id 为 " + userId + " 的用户");
    }

}
