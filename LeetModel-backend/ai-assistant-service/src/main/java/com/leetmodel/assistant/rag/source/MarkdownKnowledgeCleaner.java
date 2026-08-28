package com.leetmodel.assistant.rag.source;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将 Markdown 转为适合 Embedding 的稳定文本，同时保留结构和来源语义。 */
@Component
public class MarkdownKnowledgeCleaner {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("(!?)\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^]|]+)(?:\\|([^]]+))?]]");
    private static final Pattern UNIX_ABSOLUTE_PATH = Pattern.compile("(?<![\\w.:/])/(?:[^\\s/]+/)+[^\\s]*");
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("(?i)(?<![a-z0-9])[a-z]:\\\\(?:[^\\s\\\\]+\\\\)+[^\\s]*");

    public CleanKnowledgeDocument clean(KnowledgeDocument document) {
        String body = removeFrontmatter(document.content()).replace("\r\n", "\n").replace('\r', '\n');
        List<String> output = new ArrayList<>();
        boolean inCodeBlock = false;
        int blankLines = 0;
        for (String rawLine : body.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            if (line.stripLeading().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                output.add(line.strip());
                blankLines = 0;
                continue;
            }
            if (inCodeBlock) {
                output.add(line);
                continue;
            }
            line = normalizeStructure(line);
            line = normalizeLinks(line);
            line = redactAbsolutePaths(line);
            if (line.isBlank()) {
                if (blankLines++ == 0 && !output.isEmpty()) {
                    output.add("");
                }
            } else {
                output.add(line);
                blankLines = 0;
            }
        }
        String cleaned = String.join("\n", output).strip();
        return new CleanKnowledgeDocument(document, cleaned);
    }

    private String removeFrontmatter(String content) {
        String normalized = content.startsWith("\uFEFF") ? content.substring(1) : content;
        if (!normalized.startsWith("---\n") && !normalized.startsWith("---\r\n")) {
            return normalized;
        }
        int position = normalized.indexOf('\n') + 1;
        while (position > 0 && position < normalized.length()) {
            int lineEnd = normalized.indexOf('\n', position);
            int end = lineEnd < 0 ? normalized.length() : lineEnd;
            if ("---".equals(normalized.substring(position, end).stripTrailing())) {
                return lineEnd < 0 ? "" : normalized.substring(lineEnd + 1);
            }
            position = lineEnd < 0 ? -1 : lineEnd + 1;
        }
        throw new IllegalArgumentException("YAML frontmatter 缺少结束分隔符");
    }

    private String normalizeStructure(String line) {
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            return "【" + heading.group(2).trim() + "】";
        }
        if (isTableRow(line)) {
            String[] cells = line.strip().split("\\|", -1);
            List<String> normalized = new ArrayList<>();
            for (String cell : cells) {
                String value = cell.trim();
                if (!value.isEmpty()) {
                    normalized.add(value);
                }
            }
            return normalized.isEmpty() ? "" : "| " + String.join(" | ", normalized) + " |";
        }
        return line;
    }

    private boolean isTableRow(String line) {
        String stripped = line.strip();
        return stripped.startsWith("|") && stripped.endsWith("|");
    }

    private String normalizeLinks(String line) {
        Matcher wiki = WIKI_LINK.matcher(line);
        StringBuffer wikiOutput = new StringBuffer();
        while (wiki.find()) {
            String label = wiki.group(2) == null ? wiki.group(1) : wiki.group(2);
            wiki.appendReplacement(wikiOutput, Matcher.quoteReplacement(label.trim()));
        }
        wiki.appendTail(wikiOutput);

        Matcher markdown = MARKDOWN_LINK.matcher(wikiOutput.toString());
        StringBuffer output = new StringBuffer();
        while (markdown.find()) {
            String label = markdown.group(2).trim();
            String target = markdown.group(3).trim();
            String replacement;
            if (!markdown.group(1).isEmpty()) {
                replacement = label.isEmpty() ? "" : "[图：" + label + "]";
            } else if (isUnsafeLocalTarget(target)) {
                replacement = label;
            } else {
                replacement = label.isEmpty() ? target : label + "（" + target + "）";
            }
            markdown.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        markdown.appendTail(output);
        return output.toString();
    }

    private boolean isUnsafeLocalTarget(String target) {
        return target.startsWith("/") || target.startsWith("file:") || target.matches("(?i)^[a-z]:\\\\.*");
    }

    private String redactAbsolutePaths(String line) {
        String unixRedacted = UNIX_ABSOLUTE_PATH.matcher(line).replaceAll("[本地路径已省略]");
        return WINDOWS_ABSOLUTE_PATH.matcher(unixRedacted).replaceAll("[本地路径已省略]");
    }
}
