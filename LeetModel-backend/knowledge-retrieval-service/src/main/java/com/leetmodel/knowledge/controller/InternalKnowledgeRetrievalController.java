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

    @PostMapping("/runs")
    public Result<KnowledgeRetrievalResultDTO> retrieve(
            @Valid @RequestBody KnowledgeRetrievalRequestDTO request) {
        return Result.ok(service.retrieve(request));
    }
}
