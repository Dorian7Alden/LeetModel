package com.senior.leetmodelbackend.controller.user;

import com.senior.leetmodelbackend.common.validator.ParameterValidator;
import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ErrorCode;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
public class GetUserByEmail extends UserController {

    private final UserService userService;

    /**
     * 根据 email 获取用户信息
     */
    @GetMapping("/email/{email}")
    public Result<User> getUserByEmail(@PathVariable String email) {
        ParameterValidator.init()
                .hasLength(email, "查询的邮箱不能为空")
                .validateAndThrow();
                
        User userByEmail = userService.getUserByEmail(email);

        if (userByEmail == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "没有找到邮箱为 " + email + " 的用户");
        }

        return Result.success("成功通过邮箱查询到用户", userByEmail);
    }

}
