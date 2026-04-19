package com.senior.leetmodelbackend.controller.auth;

import com.senior.leetmodelbackend.pojo.dto.ResetPasswordDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.pojo.vo.LoginVO;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class ResetPassword {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;


    /***
     * 找回密码
     */
    @PostMapping("/reset-password")
    public Result<LoginVO> resetPassword(@RequestBody ResetPasswordDTO resetPasswordDTO) {
        if (resetPasswordDTO.getEmail() == null || resetPasswordDTO.getEmail().isBlank()) {
            return Result.error(ResponseCode.PARAM_VALIDATION_ERROR, "邮箱不能为空");
        } else if (resetPasswordDTO.getCode() == null || resetPasswordDTO.getCode().isBlank()) {
            return Result.error(ResponseCode.PARAM_VALIDATION_ERROR, "验证码不能为空");
        } else if (resetPasswordDTO.getPassword() == null || resetPasswordDTO.getPassword().isBlank()) {
            return Result.error(ResponseCode.PARAM_VALIDATION_ERROR, "密码不能为空");
        }
        // 验证码校验
        if (!verificationCodeService.verifyCode(resetPasswordDTO.getEmail(), resetPasswordDTO.getCode())) {
            return Result.error(ResponseCode.VERIFICATION_CODE_INCORRECT);
        }
        // 更新密码
        userService.resetPassword(resetPasswordDTO.getEmail(), resetPasswordDTO.getPassword());
        // 完成登录
        LoginVO loginVO = new LoginVO();
        User user = userService.getUserByEmail(resetPasswordDTO.getEmail());
        BeanUtils.copyProperties(user, loginVO);
        loginVO.setToken(com.senior.leetmodelbackend.utils.JwtUtil.generateToken(1000 * 3600 * 24 * 3));

        return Result.success("重置密码成功", loginVO);
    }

}
