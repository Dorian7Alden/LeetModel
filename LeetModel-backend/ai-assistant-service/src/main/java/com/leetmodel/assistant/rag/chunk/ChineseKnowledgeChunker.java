package com.leetmodel.assistant.rag.chunk;

import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.source.CleanKnowledgeDocument;
import com.leetmodel.assistant.rag.source.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 先按 Markdown 结构和中文句界切分，注入标准面包屑前缀，再按 Token 上限生成带重叠片段。 */
@Component
public class ChineseKnowledgeChunker {

    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？；!?;])|(?=【[^】]+】)");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s*(?:#{1,6}\\s+([^\\n]+)|【([^】]+)】)");
    private static final String DELIMITER_LINE = "----------------------------------------";
    private static final String DEFAULT_SECTION = "全局总览";

    private final RagProperties properties;
    private final ChineseTokenEstimator estimator;

    public ChineseKnowledgeChunker(RagProperties properties, ChineseTokenEstimator estimator) {
        this.properties = properties;
        this.estimator = estimator;
    }

    public List<KnowledgeChunk> chunk(CleanKnowledgeDocument document) {
        List<SectionUnit> units = structuralUnits(document.content());
        List<SectionChunk> baseChunks = pack(units);
        mergeSmallTail(baseChunks);
        List<KnowledgeChunk> result = new ArrayList<>();
        String previous = null;
        for (int index = 0; index < baseChunks.size(); index++) {
            SectionChunk base = baseChunks.get(index);
            String body = base.text();
            if (previous != null && properties.getChunkOverlapTokens() > 0) {
                String overlap = suffixWithin(previous, properties.getChunkOverlapTokens());
                body = fit(overlap + "\n\n" + body, properties.getChunkMaxTokens());
            }
            String breadcrumb = buildBreadcrumb(document.source(), base.section());
            String fullContent = breadcrumb + "\n" + body;
            fullContent = fitChars(fullContent, properties.getMaxEmbeddingInputChars()).strip();
            fullContent = fit(fullContent, properties.getChunkMaxTokens());
            result.add(new KnowledgeChunk(document.source(), index, fullContent, estimator.estimate(fullContent)));
            previous = base.text();
        }
        return List.copyOf(result);
    }

    private List<SectionUnit> structuralUnits(String content) {
        List<SectionUnit> units = new ArrayList<>();
        String currentSection = DEFAULT_SECTION;
        for (String paragraph : PARAGRAPH_BOUNDARY.split(content)) {
            String value = paragraph.strip();
            if (value.isEmpty()) {
                continue;
            }
            Matcher headingMatcher = HEADING_PATTERN.matcher(value);
            if (headingMatcher.find()) {
                String matched = headingMatcher.group(1) != null ? headingMatcher.group(1) : headingMatcher.group(2);
                String sanitized = sanitizeBreadcrumbField(matched);
                if (!sanitized.isBlank()) {
                    currentSection = sanitized;
                }
            }
            if (estimator.estimate(value) <= properties.getChunkTargetTokens()
                    && value.length() <= properties.getMaxEmbeddingInputChars()) {
                units.add(new SectionUnit(value, currentSection));
                continue;
            }
            for (String sentence : SENTENCE_BOUNDARY.split(value)) {
                splitHard(sentence.strip(), currentSection, units);
            }
        }
        return units;
    }

    private void splitHard(String text, String section, List<SectionUnit> output) {
        String remaining = text;
        while (!remaining.isBlank()) {
            String part = prefixWithin(remaining, properties.getChunkTargetTokens(),
                    properties.getMaxEmbeddingInputChars());
            output.add(new SectionUnit(part.strip(), section));
            remaining = remaining.substring(part.length()).stripLeading();
        }
    }

    private List<SectionChunk> pack(List<SectionUnit> units) {
        List<SectionChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String chunkSection = DEFAULT_SECTION;
        for (SectionUnit unit : units) {
            if (current.isEmpty()) {
                chunkSection = unit.section();
            }
            String candidate = current.isEmpty() ? unit.text() : current + "\n\n" + unit.text();
            if (!current.isEmpty() && estimator.estimate(candidate) > properties.getChunkTargetTokens()) {
                chunks.add(new SectionChunk(current.toString(), chunkSection));
                current.setLength(0);
                chunkSection = unit.section();
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(unit.text());
        }
        if (!current.isEmpty()) {
            chunks.add(new SectionChunk(current.toString(), chunkSection));
        }
        return chunks;
    }

    private void mergeSmallTail(List<SectionChunk> chunks) {
        if (chunks.size() < 2) {
            return;
        }
        int last = chunks.size() - 1;
        if (estimator.estimate(chunks.get(last).text()) >= properties.getChunkMinTokens()) {
            return;
        }
        String merged = chunks.get(last - 1).text() + "\n\n" + chunks.get(last).text();
        if (estimator.estimate(merged) <= properties.getChunkMaxTokens()
                && merged.length() <= properties.getMaxEmbeddingInputChars()) {
            chunks.set(last - 1, new SectionChunk(merged, chunks.get(last - 1).section()));
            chunks.remove(last);
        }
    }

    private String buildBreadcrumb(KnowledgeDocument source, String section) {
        List<String> hierarchy = source.hierarchy();
        String dirStr;
        if (hierarchy != null && !hierarchy.isEmpty()) {
            dirStr = String.join(" > ", hierarchy);
        } else if (source.relativePath() != null && source.relativePath().contains("/")) {
            int lastSlash = source.relativePath().lastIndexOf('/');
            dirStr = source.relativePath().substring(0, lastSlash).replace("/", " > ");
        } else {
            dirStr = "数学建模";
        }
        dirStr = sanitizeBreadcrumbField(dirStr);

        String title = source.title();
        if (title == null || title.isBlank()) {
            if (source.relativePath() != null) {
                String name = source.relativePath().substring(source.relativePath().lastIndexOf('/') + 1);
                title = name.replaceFirst("\\.md$", "");
            } else {
                title = "知识文档";
            }
        }
        title = sanitizeBreadcrumbField(title);

        String sec = (section != null && !section.isBlank()) ? sanitizeBreadcrumbField(section) : DEFAULT_SECTION;
        if (sec.isBlank()) {
            sec = DEFAULT_SECTION;
        }

        return "[目录: " + dirStr + "]\n"
                + "[文档: " + title + "]\n"
                + "[小节: " + sec + "]\n"
                + DELIMITER_LINE;
    }

    private String sanitizeBreadcrumbField(String text) {
        if (text == null) return "";
        return text.replace("[", "（")
                .replace("]", "）")
                .replace("【", "（")
                .replace("】", "）")
                .replaceAll("[#*$~`]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String fit(String text, int maxTokens) {
        return estimator.estimate(text) <= maxTokens ? text
                : prefixWithin(text, maxTokens, properties.getMaxEmbeddingInputChars());
    }

    private String prefixWithin(String text, int maxTokens, int maxChars) {
        int end = 0;
        int lastSafeEnd = 0;
        while (end < text.length() && end < maxChars) {
            int codePoint = text.codePointAt(end);
            end += Character.charCount(codePoint);
            if (end > maxChars || estimator.estimate(text.substring(0, end)) > maxTokens) {
                break;
            }
            lastSafeEnd = end;
        }
        if (lastSafeEnd == 0) {
            lastSafeEnd = Math.min(Character.charCount(text.codePointAt(0)), text.length());
        }
        return text.substring(0, lastSafeEnd);
    }

    private String suffixWithin(String text, int maxTokens) {
        int start = text.length();
        int best = start;
        while (start > 0) {
            int codePoint = text.codePointBefore(start);
            start -= Character.charCount(codePoint);
            if (estimator.estimate(text.substring(start)) > maxTokens) {
                break;
            }
            best = start;
        }
        return text.substring(best).stripLeading();
    }

    private String fitChars(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int end = maxChars;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private record SectionUnit(String text, String section) {}

    private record SectionChunk(String text, String section) {}
}
