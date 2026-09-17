package com.leetmodel.knowledge.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalCacheServiceTest {

    private RetrievalCacheService cacheService;

    @BeforeEach
    void setUp() {
        RetrievalCacheProperties properties = new RetrievalCacheProperties();
        properties.setEnabled(true);
        properties.setSemanticThreshold(0.95);
        cacheService = new RetrievalCacheService(properties, new ObjectMapper(), null);
    }

    @Test
    @DisplayName("L1 精确结果缓存：相同请求参数命中，不同参数穿透")
    void testL1ExactCacheHitAndMiss() {
        List<KnowledgeCitationDTO> citations = List.of(new KnowledgeCitationDTO(
                "KC-001", "doc1", "chunk1", "优化模型与算法分类",
                "数学建模/模型方法/优化模型与算法分类.md", null, "hash1",
                "L4", "GENERAL_MODELING", 1.0, "正文内容"
        ));

        cacheService.putL1Exact("AI_DIRECTORY_V1", "优化", "非线性规划求解", 3, "MANIFEST_v1", citations);

        // 1. 完全相同参数查询 -> 命中
        List<KnowledgeCitationDTO> hit = cacheService.getL1Exact(
                "AI_DIRECTORY_V1", "优化", "非线性规划求解", 3, "MANIFEST_v1");
        assertThat(hit).isNotNull().hasSize(1);
        assertThat(hit.get(0).getTitle()).isEqualTo("优化模型与算法分类");

        // 2. 参数不同 (如 query 变化) -> 穿透未命中
        List<KnowledgeCitationDTO> miss = cacheService.getL1Exact(
                "AI_DIRECTORY_V1", "优化", "时序预测", 3, "MANIFEST_v1");
        assertThat(miss).isNull();
    }

    @Test
    @DisplayName("L1 自然版本隔离：manifestVersion 升级后旧缓存自然隔离穿透")
    void testL1VersionNamespaceIsolation() {
        List<KnowledgeCitationDTO> citations = List.of(new KnowledgeCitationDTO(
                "KC-001", "doc1", "chunk1", "标题", "路径", null, "h", "L4", "G", 1.0, "C"
        ));

        cacheService.putL1Exact("AI_DIRECTORY_V1", "优化", "query", 3, "MANIFEST_OLD", citations);

        // 新版本请求 -> 未命中，不造成版本污染
        List<KnowledgeCitationDTO> result = cacheService.getL1Exact(
                "AI_DIRECTORY_V1", "优化", "query", 3, "MANIFEST_NEW");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("L2 任务语义向量缓存：高相似度 (>= 0.95) 任务命中，零模型费用复用")
    void testL2TaskSemanticSimilarityHit() {
        List<KnowledgeCitationDTO> citations = List.of(new KnowledgeCitationDTO(
                "KC-002", "doc2", "chunk2", "常用模型速查-预测类",
                "数学建模/模型方法/常用模型速查-预测类.md", null, "hash2",
                "L4", "GENERAL_MODELING", 1.0, "预测内容"
        ));

        List<Float> baseVector = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        cacheService.putL2Semantic("AI_DIRECTORY_V1", "预测", "如何做时间序列预测", baseVector, citations);

        // 相似任务向量：余弦相似度 = 0.9998 >= 0.95
        List<Float> similarVector = List.of(0.999f, 0.015f, 0.0f, 0.0f);
        List<KnowledgeCitationDTO> hit = cacheService.getL2Semantic(
                "AI_DIRECTORY_V1", "预测", "时序大数据趋势预测方法", similarVector);

        assertThat(hit).isNotNull().hasSize(1);
        assertThat(hit.get(0).getTitle()).isEqualTo("常用模型速查-预测类");
    }

    @Test
    @DisplayName("L2 任务语义向量缓存：低相似度 (< 0.95) 任务不命中")
    void testL2TaskSemanticDissimilarMiss() {
        List<KnowledgeCitationDTO> citations = List.of(new KnowledgeCitationDTO(
                "KC-003", "doc3", "chunk3", "机理推导", "路径", null, "h", "L4", "G", 1.0, "C"
        ));

        List<Float> baseVector = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        cacheService.putL2Semantic("AI_DIRECTORY_V1", "机理", "微分方程建模", baseVector, citations);

        // 不相似任务向量：正交向量 (相似度 = 0.0 < 0.95)
        List<Float> orthogonalVector = List.of(0.0f, 1.0f, 0.0f, 0.0f);
        List<KnowledgeCitationDTO> miss = cacheService.getL2Semantic(
                "AI_DIRECTORY_V1", "机理", "图论最短路径", orthogonalVector);

        assertThat(miss).isNull();
    }

    @Test
    @DisplayName("按分类淘汰缓存：清除指定分类的缓存键")
    void testCategoryInvalidation() {
        List<KnowledgeCitationDTO> citations = List.of(new KnowledgeCitationDTO(
                "KC-004", "doc4", "chunk4", "评价类", "路径", null, "h", "L4", "G", 1.0, "C"
        ));
        cacheService.putL1Exact("AI_DIRECTORY_V1", "评价", "TOPSIS评价", 3, "MANIFEST_v1", citations);

        cacheService.invalidateCategory("评价");

        List<KnowledgeCitationDTO> result = cacheService.getL1Exact(
                "AI_DIRECTORY_V1", "评价", "TOPSIS评价", 3, "MANIFEST_v1");
        assertThat(result).isNull();
    }
}
