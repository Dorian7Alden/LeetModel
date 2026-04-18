package com.senior.leetmodelbackend.controller.auth;


import com.senior.leetmodelbackend.pojo.dto.LoginRequestDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.pojo.enums.error.GlobalErrorCode;
import com.senior.leetmodelbackend.pojo.enums.error.UserErrorCode;
import com.senior.leetmodelbackend.pojo.vo.LoginVO;
import com.senior.leetmodelbackend.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@AllArgsConstructor
public class Login extends AuthController {

    private final UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {

        String emailLogin = loginRequestDTO.getEmail();
        String passwordLogin = loginRequestDTO.getPassword(); // TODO: 前端密码是否已经加密？

        // 校验参数完整性
        if (emailLogin.isEmpty() || emailLogin.isBlank()) {
            log.error("邮箱登录失败 {} -----> 邮箱不能为空", emailLogin);
            return Result.error(GlobalErrorCode.PARAM_VALIDATION_ERROR, "邮箱不能为空");
        }
        if (passwordLogin.isEmpty() || passwordLogin.isBlank()) {
            log.error("密码登录失败 {} -----> 密码不能为空", passwordLogin);
            return Result.error(GlobalErrorCode.PARAM_VALIDATION_ERROR, "密码不能为空");
        }

        // TODO: 格式校验，不管
        // TODO: 适配多种登录方式？目前之后邮箱具有唯一性，所以只能支持使用邮箱登录
        // TODO: 使用 Spring Security 实现登录功能
        // TODO: 前端密码是否已经加密？
        // TODO: token 缓存机制，避免用户重复登录，保证每个用户仅唯一 token

        User userQuery = userService.getUserByEmail(emailLogin);

        // 业务异常
        if (userQuery == null) {
            log.error("邮箱登录失败 {} -----> 用户不存在", emailLogin);
            return Result.error(UserErrorCode.USER_NOT_FOUND);
        }
        if (!userQuery.getPassword().equals(passwordLogin)) {
            log.error("用户 {} 登录失败 -----> 密码错误", emailLogin);
            return Result.error(UserErrorCode.PASSWORD_INCORRECT);
        }

        // 登录成功
        log.info("用户 {} 登录成功", emailLogin);
        LoginVO loginVO = new LoginVO();

        // 生成并刷新 token
        String token = com.senior.leetmodelbackend.utils.JwtUtil.generateToken(1000 * 3600 * 24 * 3); // 24 * 3 小时的登录令牌
        loginVO.setToken(token); // 设置 token 到 loginVO
        log.info("生成用户 {} 的登录 token: {}", emailLogin, token);
        // 封装查询到的用户信息
        System.out.println("=====================================================");
        System.out.println(userQuery);
        BeanUtils.copyProperties(userQuery, loginVO);
        System.out.println(loginVO);
        System.out.println("=====================================================");

        return Result.success("登录成功", loginVO);
    }

}
