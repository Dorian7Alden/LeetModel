package com.leetmodel.assistant.tool.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** explain_modeling_knowledge 的模型可提交参数。 */
public record ExplainModelingKnowledgeInput(
        @NotBlank @Size(min = 2, max = 100) String topic,
        KnowledgeLevel level,
        @Size(max = 100) String focus) {

    /** 可选关注点一旦出现就不能只有空白。 */
    @JsonIgnore
    @AssertTrue(message = "focus 不能是空白字符串")
    public boolean isFocusValid() {
        return focus == null || !focus.trim().isEmpty();
    }
}
