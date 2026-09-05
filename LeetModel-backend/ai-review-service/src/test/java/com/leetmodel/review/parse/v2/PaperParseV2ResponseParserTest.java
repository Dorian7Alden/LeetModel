package com.leetmodel.review.parse.v2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperParseV2ResponseParserTest {

    private PaperParseV2ResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new PaperParseV2ResponseParser();
    }

    @Test
    void parseWindowChunkWithCodeFencesAndLaTeX() {
        String raw = """
                ```json
                {
                  "windowIndex": 1,
                  "startPhysicalPage": 1,
                  "endPhysicalPage": 2,
                  "pageTopContinuation": false,
                  "pageBottomUnfinished": true,
                  "windowLayoutAesthetics": {
                    "score": 88.5,
                    "pageCompactness": "HIGH",
                    "comment": "两页穿插规整"
                  },
                  "blocks": [
                    {
                      "type": "HEADING",
                      "physicalPage": 1,
                      "text": "一、问题重述",
                      "heading": {
                        "level": 1,
                        "rawNumber": "一、",
                        "cleanTitle": "问题重述"
                      }
                    },
                    {
                      "type": "FORMULA",
                      "physicalPage": 1,
                      "text": "$$\\\\min f(x) = \\\\int_{0}^{1} x^2 dx$$",
                      "formula": {
                        "latex": "\\\\min f(x) = \\\\int_{0}^{1} x^2 dx",
                        "formulaNo": "(1)",
                        "isMultiLine": false
                      }
                    }
                  ]
                }
                ```
                """;

        WindowChunkDTO chunk = parser.parseWindowChunk(raw);
        assertThat(chunk).isNotNull();
        assertThat(chunk.windowIndex()).isEqualTo(1);
        assertThat(chunk.startPhysicalPage()).isEqualTo(1);
        assertThat(chunk.endPhysicalPage()).isEqualTo(2);
        assertThat(chunk.blocks()).hasSize(2);
        assertThat(chunk.blocks().get(0).type()).isEqualTo(PaperDocumentV2.BlockType.HEADING);
        assertThat(chunk.blocks().get(1).type()).isEqualTo(PaperDocumentV2.BlockType.FORMULA);
        assertThat(chunk.blocks().get(1).formula().latex()).isEqualTo("\\min f(x) = \\int_{0}^{1} x^2 dx");
    }

    @Test
    void sanitizeControlCharactersAndTrailingCommas() {
        String raw = "\uFEFF" + """
                {
                  "windowIndex": 2,
                  "startPhysicalPage": 2,
                  "endPhysicalPage": 3,
                  "pageTopContinuation": true,
                  "pageBottomUnfinished": false,
                  "windowLayoutAesthetics": {
                    "score": 75.0,
                    "pageCompactness": "MEDIUM",
                    "comment": "含控制字符\u0001与\u0008",
                  },
                  "blocks": [
                    {
                      "type": "PARAGRAPH",
                      "physicalPage": 2,
                      "text": "测试制表符\t与换行\n合法保留",
                    },
                  ],
                }
                """;

        WindowChunkDTO chunk = parser.parseWindowChunk(raw);
        assertThat(chunk).isNotNull();
        assertThat(chunk.windowIndex()).isEqualTo(2);
        assertThat(chunk.blocks()).hasSize(1);
        assertThat(chunk.blocks().get(0).text()).contains("\t");
        assertThat(chunk.blocks().get(0).text()).contains("\n");
        assertThat(chunk.windowLayoutAesthetics().comment()).isEqualTo("含控制字符与");
    }

    @Test
    void autoFixUnclosedTableHtml() {
        String raw = """
                {
                  "windowIndex": 1,
                  "startPhysicalPage": 1,
                  "endPhysicalPage": 1,
                  "pageTopContinuation": false,
                  "pageBottomUnfinished": false,
                  "windowLayoutAesthetics": {
                    "score": 80.0,
                    "pageCompactness": "HIGH",
                    "comment": "表格测试"
                  },
                  "blocks": [
                    {
                      "type": "TABLE",
                      "physicalPage": 1,
                      "text": "表 1 参数",
                      "table": {
                        "caption": "表 1",
                        "captionPosition": "TOP",
                        "tableNo": "1",
                        "html": "<table border='1'><tr><td>val</td></tr>",
                        "footnote": "注"
                      }
                    }
                  ]
                }
                """;

        WindowChunkDTO chunk = parser.parseWindowChunk(raw);
        assertThat(chunk).isNotNull();
        assertThat(chunk.blocks().get(0).table().html()).endsWith("</table>");
    }

    @Test
    void parseArbiterBlocksDirectArrayAndWrappedObject() {
        String arrayRaw = """
                [
                  {
                    "type": "PARAGRAPH",
                    "physicalPage": 2,
                    "text": "段落内容 A"
                  }
                ]
                """;
        List<WindowBlockDTO> blocks1 = parser.parseArbiterBlocks(arrayRaw);
        assertThat(blocks1).hasSize(1);
        assertThat(blocks1.get(0).text()).isEqualTo("段落内容 A");

        String objectRaw = """
                ```json
                {
                  "blocks": [
                    {
                      "type": "PARAGRAPH",
                      "physicalPage": 2,
                      "text": "段落内容 B"
                    }
                  ]
                }
                ```
                """;
        List<WindowBlockDTO> blocks2 = parser.parseArbiterBlocks(objectRaw);
        assertThat(blocks2).hasSize(1);
        assertThat(blocks2.get(0).text()).isEqualTo("段落内容 B");
    }

    @Test
    void rejectInvalidJsonWithoutBraces() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseWindowChunk("没有 JSON 内容"));
    }
}
