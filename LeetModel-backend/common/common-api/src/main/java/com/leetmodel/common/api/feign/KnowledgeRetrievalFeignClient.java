package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "knowledge-retrieval-service")
public interface KnowledgeRetrievalFeignClient {
    @PostMapping("/internal/knowledge-retrieval/runs")
    Result<KnowledgeRetrievalResultDTO> retrieve(
            @Valid @RequestBody KnowledgeRetrievalRequestDTO request);
}
