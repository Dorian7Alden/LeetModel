package com.senior.leetmodelbackend.controller.auth;


import com.senior.leetmodelbackend.common.utils.JwtUtil;
import com.senior.leetmodelbackend.common.utils.Md5Util;
import com.senior.leetmodelbackend.pojo.dto.LoginRequestDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.pojo.vo.LoginVO;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.validator.auth.LoginParamValidator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
public class Login extends AuthController {

    private final UserService userService;
    private final LoginParamValidator loginParamValidator;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequestDTO request) {

        loginParamValidator.validate(request);

        String email = request.getEmail();
        String password = request.getPassword();

        User userQuery = userService.getUserByEmail(email);

        if (userQuery == null) {
            log.error("邮箱登录失败 {} -----> 用户不存在", email);
            return Result.error(ResponseCode.USER_NOT_FOUND);
        }
        if (!Md5Util.matches(password, userQuery.getPassword())) {
            log.error("用户 {} 登录失败 -----> 密码错误", email);
            return Result.error(ResponseCode.USER_PASSWORD_WRONG);
        }

        // 从 DB 查询用户角色（若未分配角色则默认为 MEMBER）
        List<Role> roles = userService.getUserRoles(Long.valueOf(userQuery.getUserId()));
        if (roles != null && !roles.isEmpty()) {
            boolean isSuperAdmin = roles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
            boolean isAdmin = roles.stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
            if (isSuperAdmin) {
                userQuery.setRole("SUPER_ADMIN");
            } else if (isAdmin) {
                userQuery.setRole("ADMIN");
            } else {
                userQuery.setRole("MEMBER");
            }
        } else {
            userQuery.setRole("MEMBER");
        }

        log.info("用户 {} 登录成功", email);
        LoginVO loginVO = new LoginVO();

        String token = JwtUtil.generateToken(userQuery);
        loginVO.setToken(token);
        log.info("生成用户 {} 的登录 token: {}", email, token);

        BeanUtils.copyProperties(userQuery, loginVO);
        loginVO.setId(userQuery.getUserId());

        return Result.success("登录成功", loginVO);
    }

}
