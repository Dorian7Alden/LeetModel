package com.leetmodel.review.parse.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscrepancyArbiterTest {

    private AiClient aiClient;
    private ObjectMapper objectMapper;
    private PaperParseV2Properties properties;
    private PaperParseV2ResponseParser responseParser;
    private DiscrepancyArbiter arbiter;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        objectMapper = new ObjectMapper();
        properties = new PaperParseV2Properties();
        responseParser = new PaperParseV2ResponseParser(objectMapper);
        arbiter = new DiscrepancyArbiter(aiClient, objectMapper, properties, responseParser);
    }

    @Test
    void shouldMergeFiguresIndependentlyWithScoreAveragingAndLongerDescription() {
        WindowBlockDTO figA = new WindowBlockDTO(
                PaperDocumentV2.BlockType.FIGURE,
                2,
                "图 1 收敛曲线",
                null, null, null,
                new PaperDocumentV2.FigurePayload(
                        "收敛曲线对比", "BOTTOM", "图 1", "DATA_VISUALIZATION",
                        "简短描述", 80.0, "线条清晰", List.of()
                ),
                null, List.of()
        );

        WindowBlockDTO figB = new WindowBlockDTO(
                PaperDocumentV2.BlockType.FIGURE,
                2,
                "图 1 收敛曲线",
                null, null, null,
                new PaperDocumentV2.FigurePayload(
                        "收敛曲线对比", "BOTTOM", "图 1", "DATA_VISUALIZATION",
                        "非常详尽的算法适应度迭代下降长描述，包含极值 0.012", 90.0, "色彩分明无遮挡", List.of()
                ),
                null, List.of()
        );

        List<WindowBlockDTO> merged = arbiter.mergeFigures(List.of(figA), List.of(figB));
        assertThat(merged).hasSize(1);
        WindowBlockDTO result = merged.get(0);
        assertThat(result.figure().aestheticScore()).isEqualTo(85.0);
        assertThat(result.figure().description()).contains("非常详尽");
    }

    @Test
    void shouldFastPathWhenTextElementsAreConsistentWithoutInvokingAi() {
        WindowBlockDTO pA = new WindowBlockDTO(
                PaperDocumentV2.BlockType.PARAGRAPH,
                2,
                "无人机在低空执行物流任务，面临风场约束。",
                null, null, null, null, null, List.of()
        );
        WindowBlockDTO pB = new WindowBlockDTO(
                PaperDocumentV2.BlockType.PARAGRAPH,
                2,
                "无人机在低空执行物流任务，面临风场约束。",
                null, null, null, null, null, List.of()
        );

        List<WindowBlockDTO> result = arbiter.arbitrate(
                List.of(pA),
                List.of(pB),
                2,
                "无人机在低空执行物流任务，面临风场约束。",
                1001L
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo(pB.text());
        verify(aiClient, never()).chat(any());
    }

    @Test
    void shouldTriggerArbitrationAiWhenTextElementsConflict() {
        WindowBlockDTO pA = new WindowBlockDTO(
                PaperDocumentV2.BlockType.PARAGRAPH,
                2,
                "版本 A 提取内容：完全不同的段落描述无人机能量消耗模型。",
                null, null, null, null, null, List.of()
        );
        WindowBlockDTO pB = new WindowBlockDTO(
                PaperDocumentV2.BlockType.TABLE,
                2,
                "版本 B 提取内容：误判为表格",
                null, null,
                new PaperDocumentV2.TablePayload("表", "TOP", "1", "<table></table>", ""),
                null, null, List.of()
        );

        String arbiterOutput = """
                [
                  {
                    "type": "PARAGRAPH",
                    "physicalPage": 2,
                    "text": "裁定正文：核对本地纯文本后采纳段落形态。"
                  }
                ]
                """;
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-arb-1",
                AiProvider.NEW_API,
                "deepseek-v4-flash",
                "p-1",
                arbiterOutput,
                null,
                "stop",
                null
        ));

        List<WindowBlockDTO> result = arbiter.arbitrate(
                List.of(pA),
                List.of(pB),
                2,
                "底层纯文本参考",
                1001L
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("裁定正文：核对本地纯文本后采纳段落形态。");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiClient).chat(captor.capture());
        assertThat(captor.getValue().messages().get(0).content().get(0).text())
                .contains("版本 A")
                .contains("版本 B")
                .contains("底层纯文本参考");
    }

    @Test
    void shouldFallbackToVersionAWhenArbitrationAiThrowsException() {
        WindowBlockDTO pA = new WindowBlockDTO(
                PaperDocumentV2.BlockType.PARAGRAPH,
                2,
                "版本 A 原始段落",
                null, null, null, null, null, List.of()
        );
        WindowBlockDTO pB = new WindowBlockDTO(
                PaperDocumentV2.BlockType.PARAGRAPH,
                2,
                "截然不同的段落 B",
                null, null, null, null, null, List.of()
        );

        when(aiClient.chat(any())).thenThrow(new RuntimeException("网关网络故障"));

        List<WindowBlockDTO> result = arbiter.arbitrate(
                List.of(pA),
                List.of(pB),
                2,
                "本地参考",
                1001L
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).text()).isEqualTo("版本 A 原始段落");
    }
}
