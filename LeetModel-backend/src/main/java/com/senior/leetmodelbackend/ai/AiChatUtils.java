package com.senior.leetmodelbackend.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;

public class AiChatUtils {

    /**
     * 通用 ai 聊天方法
     *
     * @param chatClient ai 客户端
     * @param prompt     提示词
     * @param converter  结果转换器
     * @return 解析后的实体
     */
    public static <T> T chat(ChatClient chatClient,
                      Prompt prompt,
                      StructuredOutputConverter<T> converter) {
        return chatClient
                .prompt(prompt)
                .call()
                .entity(converter);
    }

}
