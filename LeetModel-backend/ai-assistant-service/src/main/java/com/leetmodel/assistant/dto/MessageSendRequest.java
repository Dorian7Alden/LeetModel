package com.leetmodel.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "单条消息不能超过2000个字符")
    private String content;

    @NotBlank(message = "请求标识不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "请求标识格式不正确")
    private String clientRequestId;
}
