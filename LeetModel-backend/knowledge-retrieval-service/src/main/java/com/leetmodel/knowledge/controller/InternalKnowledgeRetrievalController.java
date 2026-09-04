package com.leetmodel.knowledge.controller;

import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.knowledge.service.KnowledgeRetrievalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/knowledge-retrieval")
@RequiredArgsConstructor
public class InternalKnowledgeRetrievalController {
    private final KnowledgeRetrievalService service;

    /**
     * 执行基于 Elasticsearch 向量/文本检索的 RAG 知识检索运行。
     *
     * @param request 包含题目 ID、问题文本与 TopK 的检索请求对象，不能为 null
     * @return 匹配的证据快照与文本知识条目结果
     */
    @PostMapping("/runs")
    public Result<KnowledgeRetrievalResultDTO> retrieve(
            @Valid @RequestBody KnowledgeRetrievalRequestDTO request) {
        return Result.ok(service.retrieve(request));
    }
}
