package com.senior.leetmodelbackend.ai;

import com.senior.leetmodelbackend.pojo.entity.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReviewChatController {

    private final ChatClient chatClient;
    private final PromptTemplate sysPrompt;
    private final PromptTemplate userPrompt;

    public ReviewChatController(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/review-system-prompt.st") Resource sysPrompt,
            @Value("classpath:prompts/review-user-prompt.st") Resource userPrompt
        ) throws Exception {
        this.chatClient = chatClientBuilder.build();
        this.sysPrompt = new PromptTemplate(sysPrompt.getContentAsString(StandardCharsets.UTF_8));
        this.userPrompt = new PromptTemplate(userPrompt.getContentAsString(StandardCharsets.UTF_8));
    }

    @PostMapping("/review")
    public Result<ReviewDTO> review(@RequestBody Map<String, String> req) {

        return Result.success(AiChatUtils.chat(
                chatClient,
                new Prompt(List.of(
                        new SystemMessage(sysPrompt.render()),
                        new UserMessage(userPrompt.render(Map.of(
                                "originProblem", req.get("problem"),
                                "submission", req.get("submissionContent")))
                        )
                )),
                new BeanOutputConverter<>(ReviewDTO.class)));
    }
}
