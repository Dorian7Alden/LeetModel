package com.leetmodel.assistant.tool;

/**
 * AI 客服白名单业务工具。
 *
 * @param <I> 经过服务端校验的输入类型
 */
public interface AssistantTool<I> {

    /**
     * 返回不可变工具描述。
     *
     * @return 工具描述
     */
    AssistantToolDescriptor descriptor();

    /**
     * 返回参数反序列化目标类型。
     *
     * @return 输入类型
     */
    Class<I> inputType();

    /**
     * 执行只读工具。
     *
     * @param input 已校验参数
     * @param context 服务端可信执行上下文
     * @return 白名单化工具结果
     */
    AssistantToolOutput execute(I input, AssistantToolExecutionContext context);
}
