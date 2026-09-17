INSERT INTO `assistant_workflow_version`
(`id`, `workflow_version`, `name`, `status`, `prompt_version`,
 `model_execution_config_version`, `toolset_version`, `rag_mode`,
 `input_schema`, `output_schema`, `compatibility`, `impact_scope`, `experiment_candidate`)
VALUES
(5, 'ASSISTANT_TOOLS_RETRIEVAL_V1', '客服工具版跨服务检索RAG V1', 'ENABLED',
 'PROMPT_ASSISTANT_TOOLS_0001', 'MODEL_CFG_ASSISTANT_TOOLS_0001',
 'ASSISTANT_TOOLSET_0001', 'RETRIEVAL_SERVICE', 'ASSISTANT_QUESTION_V1', 'ASSISTANT_REPLY_V1',
 '委托中央 knowledge-retrieval-service 进行向量与BM25混合检索，结合RRF重排',
 '仅影响激活后新创建的客服回复；历史版本快照不变', 0);
