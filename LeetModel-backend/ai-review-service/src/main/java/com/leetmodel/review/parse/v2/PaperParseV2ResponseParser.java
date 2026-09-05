package com.leetmodel.review.parse.v2;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第二代 PDF 解析大模型响应结构化鲁棒解析器。
 *
 * <p>严格遵循《提示词管理.md》实践：剥离 Markdown 围栏、清洗非法控制字符、
 * 修复尾随逗号、兜底闭合 HTML 表格标签并宽容反序列化。</p>
 */
@Component
public class PaperParseV2ResponseParser {

    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)\\s*```",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper;

    public PaperParseV2ResponseParser() {
        this.objectMapper = configureMapper(new ObjectMapper());
    }

    public PaperParseV2ResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = configureMapper(objectMapper.copy());
    }

    private static ObjectMapper configureMapper(ObjectMapper mapper) {
        return mapper
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
                .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature());
    }

    /**
     * 解析单次双页滑窗输出的 WindowChunkDTO。
     *
     * @param rawContent 大模型返回的原始字符串
     * @return 经过清洗校验的窗口分块 DTO
     */
    public WindowChunkDTO parseWindowChunk(String rawContent) {
        String json = extractJsonObject(rawContent);
        try {
            WindowChunkDTO chunk = objectMapper.readValue(json, WindowChunkDTO.class);
            return sanitizeChunk(chunk);
        } catch (Exception firstEx) {
            // 尝试轻量修复尾随逗号后重试反序列化
            String repaired = repairTrailingCommas(json);
            try {
                WindowChunkDTO chunk = objectMapper.readValue(repaired, WindowChunkDTO.class);
                return sanitizeChunk(chunk);
            } catch (Exception retryEx) {
                throw new IllegalArgumentException(
                        "WindowChunkDTO 反序列化失败: " + firstEx.getMessage()
                                + ", 原始片段截取: " + truncate(json, 200),
                        firstEx
                );
            }
        }
    }

    /**
     * 解析仲裁 AI 裁决返回的内容块列表。
     *
     * @param rawContent 仲裁模型原始返回字符串
     * @return 经过清洗的内容块列表
     */
    public List<WindowBlockDTO> parseArbiterBlocks(String rawContent) {
        String sanitized = sanitizeRaw(rawContent);
        String jsonArrayCandidate = extractJsonArrayOrFromObject(sanitized);
        try {
            List<WindowBlockDTO> blocks = objectMapper.readValue(
                    jsonArrayCandidate,
                    new TypeReference<List<WindowBlockDTO>>() {}
            );
            return sanitizeBlocks(blocks);
        } catch (Exception firstEx) {
            String repaired = repairTrailingCommas(jsonArrayCandidate);
            try {
                List<WindowBlockDTO> blocks = objectMapper.readValue(
                        repaired,
                        new TypeReference<List<WindowBlockDTO>>() {}
                );
                return sanitizeBlocks(blocks);
            } catch (Exception retryEx) {
                throw new IllegalArgumentException(
                        "仲裁内容块反序列化失败: " + firstEx.getMessage()
                                + ", 原始片段截取: " + truncate(jsonArrayCandidate, 200),
                        firstEx
                );
            }
        }
    }

    /**
     * 清洗不可见字符与代码围栏，提取最外层 JSON 对象字符串。
     *
     * @param raw 原始字符串
     * @return 规范化后的 JSON 对象报文
     */
    public String extractJsonObject(String raw) {
        String sanitized = sanitizeRaw(raw);
        int firstBrace = sanitized.indexOf('{');
        int lastBrace = sanitized.lastIndexOf('}');
        if (firstBrace == -1 || lastBrace == -1 || firstBrace >= lastBrace) {
            throw new IllegalArgumentException("返回内容未包含合法的 JSON 对象边界 {...}");
        }
        return sanitized.substring(firstBrace, lastBrace + 1).trim();
    }

    /**
     * 从原始文本中提取 JSON 数组，兼容直接输出数组或包裹在 {"blocks": [...]} 中的结构。
     *
     * @param sanitized 已清洗的基础文本
     * @return 纯净的 JSON 数组文本
     */
    private String extractJsonArrayOrFromObject(String sanitized) {
        int firstBracket = sanitized.indexOf('[');
        int lastBracket = sanitized.lastIndexOf(']');
        int firstBrace = sanitized.indexOf('{');
        int lastBrace = sanitized.lastIndexOf('}');

        // 若为对象包裹形态（如 {"blocks": [...]}）
        if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
            try {
                String objJson = sanitized.substring(firstBrace, lastBrace + 1);
                JsonNode root = objectMapper.readTree(objJson);
                if (root.has("blocks") && root.get("blocks").isArray()) {
                    return root.get("blocks").toString();
                }
            } catch (Exception ignored) {
                // 降级尝试直接抠中括号
            }
        }

        if (firstBracket == -1 || lastBracket == -1 || firstBracket >= lastBracket) {
            throw new IllegalArgumentException("仲裁返回未包含合法的 JSON 数组边界 [...]");
        }
        return sanitized.substring(firstBracket, lastBracket + 1).trim();
    }

    /**
     * 基础文本清洗：剥离 Markdown 围栏、去 BOM、剔除除制表符和换行外的非法控制字符。
     *
     * @param raw 原始文本
     * @return 纯净文本
     */
    public String sanitizeRaw(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("\uFEFF", "");
        // 剥离代码围栏
        Matcher matcher = CODE_FENCE_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1);
        }
        // 剔除非法控制字符，保留 \t, \n, \r
        return cleaned.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").trim();
    }

    private String repairTrailingCommas(String json) {
        if (json == null) {
            return "";
        }
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }

    private WindowChunkDTO sanitizeChunk(WindowChunkDTO chunk) {
        if (chunk == null) {
            return null;
        }
        List<WindowBlockDTO> safeBlocks = sanitizeBlocks(chunk.blocks());
        return new WindowChunkDTO(
                chunk.windowIndex(),
                chunk.startPhysicalPage(),
                chunk.endPhysicalPage(),
                chunk.pageTopContinuation(),
                chunk.pageBottomUnfinished(),
                chunk.windowLayoutAesthetics(),
                safeBlocks
        );
    }

    private List<WindowBlockDTO> sanitizeBlocks(List<WindowBlockDTO> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<WindowBlockDTO> result = new ArrayList<>(input.size());
        for (WindowBlockDTO block : input) {
            if (block == null) {
                continue;
            }
            WindowBlockDTO fixed = block;
            // 兜底补齐缺失的 </table> 标签
            if (block.table() != null && block.table().html() != null) {
                String html = block.table().html().trim();
                String lower = html.toLowerCase();
                if (lower.contains("<table") && !lower.contains("</table>")) {
                    html = html + "</table>";
                    PaperDocumentV2.TablePayload table = new PaperDocumentV2.TablePayload(
                            block.table().caption(),
                            block.table().captionPosition(),
                            block.table().tableNo(),
                            html,
                            block.table().footnote()
                    );
                    fixed = new WindowBlockDTO(
                            block.type(),
                            block.physicalPage(),
                            block.text(),
                            block.heading(),
                            block.formula(),
                            table,
                            block.figure(),
                            block.code(),
                            block.references()
                    );
                }
            }
            result.add(fixed);
        }
        return List.copyOf(result);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
