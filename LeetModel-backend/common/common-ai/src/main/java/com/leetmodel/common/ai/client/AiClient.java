package com.leetmodel.common.ai.client;

import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;

/**
 * 统一 AI 网关调用客户端接口。
 *
 * <p>向业务服务提供统一的同步对话（chat）与向量嵌入（embed）调用契约，
 * 屏蔽底层网络通信与错误转换细节，隔离直接直连供应商与持有模型秘钥的风险。</p>
 */
public interface AiClient {

    /**
     * 发起同步 AI 对话调用。
     *
     * @param request 统一对话请求对象，包含场景编码、模型快照、Prompt 与上下文
     * @return 包含回复内容、模型快照、Token 计量与耗时的统一响应对象
     * @throws AiClientException 当网关不可用、超时或服务端报错时抛出
     */
    AiChatResponse chat(AiChatRequest request);

    /**
     * 发起同步文本向量嵌入调用。
     *
     * @param request 批量文本向量化请求对象
     * @return 包含有序向量列表、维度与用量的响应对象
     * @throws AiClientException 当网关不可用、不支持或执行失败时抛出
     */
    default AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        throw new AiClientException(50003, "当前 AI 客户端不支持 Embedding");
    }
}
