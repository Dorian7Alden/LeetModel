package leet.model.leetmodelbackend.dto.auth;

import lombok.Data;

/**
 * 发送邮箱验证码的请求体（预留扩展字段）。
 */
@Data
public class SendEmailCodeRequestDTO {

    /** 收件人邮箱地址 */
    private String email;
}