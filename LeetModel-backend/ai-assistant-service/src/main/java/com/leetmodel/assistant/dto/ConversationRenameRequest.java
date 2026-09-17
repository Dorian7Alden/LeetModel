package com.leetmodel.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重命名会话标题请求")
public class ConversationRenameRequest {

    @NotBlank(message = "会话标题不能为空")
    @Size(max = 50, message = "会话标题不能超过50个字符")
    @Schema(description = "自定义会话标题", example = "数学建模常用算法讨论")
    private String title;
}
