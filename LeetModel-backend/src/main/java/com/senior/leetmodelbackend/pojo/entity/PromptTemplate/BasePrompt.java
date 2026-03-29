package com.senior.leetmodelbackend.pojo.entity.PromptTemplate;

import com.senior.leetmodelbackend.pojo.enums.PromptEnums;
import lombok.Getter;

import java.util.Map;

@Getter
public abstract class BasePrompt {

    protected PromptEnums promptEnum;

    public BasePrompt(PromptEnums promptEnum) {
        this.promptEnum = promptEnum;
    }

    /**
     * 将对象中的变量字段映射为 Map
     */
    public abstract Map<String, String> buildVariablesMap();
}
