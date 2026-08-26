package com.leetmodel.assistant.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssistantReplyVO {
    private AssistantMessageVO userMessage;
    private AssistantMessageVO assistantMessage;
}
