package com.leetmodel.assistant.rag.chunk;

import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.source.CleanKnowledgeDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 先按 Markdown 结构和中文句界切分，再按 Token 上限生成带重叠片段。 */
@Component
public class ChineseKnowledgeChunker {

    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？；!?;])|(?=【[^】]+】)");

    private final RagProperties properties;
    private final ChineseTokenEstimator estimator;

    public ChineseKnowledgeChunker(RagProperties properties, ChineseTokenEstimator estimator) {
        this.properties = properties;
        this.estimator = estimator;
    }

    public List<KnowledgeChunk> chunk(CleanKnowledgeDocument document) {
        List<String> units = structuralUnits(document.content());
        List<String> baseChunks = pack(units);
        mergeSmallTail(baseChunks);
        List<KnowledgeChunk> result = new ArrayList<>();
        String previous = null;
        for (int index = 0; index < baseChunks.size(); index++) {
            String content = baseChunks.get(index);
            if (previous != null && properties.getChunkOverlapTokens() > 0) {
                String overlap = suffixWithin(previous, properties.getChunkOverlapTokens());
                content = fit(overlap + "\n\n" + content, properties.getChunkMaxTokens());
            }
            content = fitChars(content, properties.getMaxEmbeddingInputChars()).strip();
            result.add(new KnowledgeChunk(document.source(), index, content, estimator.estimate(content)));
            previous = baseChunks.get(index);
        }
        return List.copyOf(result);
    }

    private List<String> structuralUnits(String content) {
        List<String> units = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BOUNDARY.split(content)) {
            String value = paragraph.strip();
            if (value.isEmpty()) {
                continue;
            }
            if (estimator.estimate(value) <= properties.getChunkTargetTokens()
                    && value.length() <= properties.getMaxEmbeddingInputChars()) {
                units.add(value);
                continue;
            }
            for (String sentence : SENTENCE_BOUNDARY.split(value)) {
                splitHard(sentence.strip(), units);
            }
        }
        return units;
    }

    private void splitHard(String text, List<String> output) {
        String remaining = text;
        while (!remaining.isBlank()) {
            String part = prefixWithin(remaining, properties.getChunkTargetTokens(),
                    properties.getMaxEmbeddingInputChars());
            output.add(part.strip());
            remaining = remaining.substring(part.length()).stripLeading();
        }
    }

    private List<String> pack(List<String> units) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            String candidate = current.isEmpty() ? unit : current + "\n\n" + unit;
            if (!current.isEmpty() && estimator.estimate(candidate) > properties.getChunkTargetTokens()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(unit);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private void mergeSmallTail(List<String> chunks) {
        if (chunks.size() < 2) {
            return;
        }
        int last = chunks.size() - 1;
        if (estimator.estimate(chunks.get(last)) >= properties.getChunkMinTokens()) {
            return;
        }
        String merged = chunks.get(last - 1) + "\n\n" + chunks.get(last);
        if (estimator.estimate(merged) <= properties.getChunkMaxTokens()
                && merged.length() <= properties.getMaxEmbeddingInputChars()) {
            chunks.set(last - 1, merged);
            chunks.remove(last);
        }
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
}
