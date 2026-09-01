package com.leetmodel.common.core.logging;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 日志输出边界的不可逆清洗策略。
 *
 * <p>该类只处理可用于技术诊断的短摘要。请求/响应正文、论文、Prompt、回答、
 * RAG 片段、Embedding 和消息 Payload 本身不应传给日志 API；即使调用点误传了
 * 带名称的敏感值，这里也会在最终 JSON 编码前再次拒绝或遮蔽。</p>
 */
public final class LogSanitizer {

    public static final String REDACTED = "[REDACTED]";
    public static final String TRUNCATED = "[TRUNCATED]";
    public static final int MAX_MESSAGE_LENGTH = 1024;
    public static final int MAX_FIELD_LENGTH = 256;
    public static final int MAX_IDENTIFIER_LENGTH = 128;
    public static final int MAX_STACK_FRAME_LENGTH = 512;

    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/=]{4,}");
    private static final Pattern BASIC = Pattern.compile(
            "(?i)\\bBasic\\s+[A-Za-z0-9+/=]{4,}");
    private static final Pattern USER_INFO = Pattern.compile(
            "(?i)(\\b(?:https?|jdbc:[a-z0-9]+)://)[^/@\\s:]+:[^/@\\s]+@");
    private static final Pattern URL_QUERY = Pattern.compile(
            "(?i)(\\b(?:https?|jdbc:[a-z0-9]+)://[^?\\s]+)\\?[^\\s]*");
    private static final Pattern GENERATED_PASSWORD = Pattern.compile(
            "(?i)(using\\s+generated\\s+security\\s+password\\s*:\\s*)\\S+");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\\b(?:authorization|proxy-authorization)\\b\\s*[:=]\\s*)"
                    + "(?:(?:Bearer|Basic|ApiKey|Token)\\s+[^\\s,;]+|[^\\s,;]+)");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:authorization|proxy-authorization|cookie|set-cookie|password|passwd|pwd|"
                    + "passcode|verification[_-]?code|captcha|session(?:id)?|jwt|token|"
                    + "access[_-]?token|refresh[_-]?token|relay[_-]?token|api[_-]?key|"
                    + "access[_-]?key|secret[_-]?key|client[_-]?secret|credential)s?\\b"
                    + "\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}&]+)");
    private static final Pattern FORBIDDEN_CONTENT = Pattern.compile(
            "(?i)(\\b(?:prompt|answer|responseBody|requestBody|paperContent|pdfText|"
                    + "ragContext|knowledgeChunk|embedding|payload)\\b\\s*[:=]\\s*)"
                    + "(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,;}]+)");

    private static final List<Replacement> REPLACEMENTS = List.of(
            new Replacement(GENERATED_PASSWORD, "$1" + REDACTED),
            new Replacement(AUTHORIZATION, "$1" + REDACTED),
            new Replacement(BEARER, "Bearer " + REDACTED),
            new Replacement(BASIC, "Basic " + REDACTED),
            new Replacement(USER_INFO, "$1" + REDACTED + "@"),
            new Replacement(URL_QUERY, "$1?" + REDACTED),
            new Replacement(SECRET_ASSIGNMENT, "$1" + REDACTED),
            new Replacement(FORBIDDEN_CONTENT, "$1" + REDACTED)
    );

    private LogSanitizer() {
    }

    /** 清洗自由文本摘要。 */
    public static String message(String value) {
        return sanitize(value, MAX_MESSAGE_LENGTH);
    }

    /** 清洗普通枚举、类别、路由模板和资源字段。 */
    public static String field(String value) {
        return sanitize(value, MAX_FIELD_LENGTH);
    }

    /** 清洗关联标识，额外使用更小的长度上限。 */
    public static String identifier(String value) {
        return sanitize(value, MAX_IDENTIFIER_LENGTH);
    }

    /** 清洗不含异常 message 的代码位置。 */
    public static String stackFrame(String value) {
        return sanitize(value, MAX_STACK_FRAME_LENGTH);
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String normalized = stripControls(value);
        for (Replacement replacement : REPLACEMENTS) {
            normalized = replacement.pattern().matcher(normalized)
                    .replaceAll(replacement.replacement());
        }
        normalized = normalized.strip();
        if (normalized.isEmpty()) return null;
        return limit(normalized, maxLength);
    }

    private static String stripControls(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        boolean lastWasSpace = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            boolean separator = Character.isISOControl(codePoint)
                    || codePoint == 0x2028 || codePoint == 0x2029;
            if (separator) {
                if (!lastWasSpace) safe.append(' ');
                lastWasSpace = true;
            } else {
                safe.appendCodePoint(codePoint);
                lastWasSpace = Character.isWhitespace(codePoint);
            }
        }
        return safe.toString();
    }

    private static String limit(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        int prefixLength = maxLength - TRUNCATED.length();
        if (prefixLength > 0 && Character.isHighSurrogate(value.charAt(prefixLength - 1))) {
            prefixLength--;
        }
        return value.substring(0, Math.max(0, prefixLength)) + TRUNCATED;
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
