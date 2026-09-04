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

    /**
     * 清洗自由文本日志消息，执行正则脱敏与字符截断。
     *
     * @param value 原始日志文本内容，允许为 null
     * @return 脱敏并限制最大 1024 字符的安全文本；若为空串则返回 null
     */
    public static String message(String value) {
        return sanitize(value, MAX_MESSAGE_LENGTH);
    }

    /**
     * 清洗普通枚举、失败类别、路由模板等结构化字段。
     *
     * @param value 原始字段文本值，允许为 null
     * @return 脱敏并限制最大 256 字符的安全字段值；若为空串则返回 null
     */
    public static String field(String value) {
        return sanitize(value, MAX_FIELD_LENGTH);
    }

    /**
     * 清洗全链路关联标识，施加严格字符与长度限制。
     *
     * @param value 原始标识文本，允许为 null
     * @return 脱敏并限制最大 128 字符的有效标识；若为空串则返回 null
     */
    public static String identifier(String value) {
        return sanitize(value, MAX_IDENTIFIER_LENGTH);
    }

    /**
     * 清洗异常堆栈帧位置字符串，屏蔽行内敏感信息。
     *
     * @param value 原始堆栈代码位置文本，允许为 null
     * @return 脱敏并限制最大 512 字符的堆栈行文本；若为空串则返回 null
     */
    public static String stackFrame(String value) {
        return sanitize(value, MAX_STACK_FRAME_LENGTH);
    }

    /**
     * 对输入文本执行控制字符清理、敏感模式不可逆脱敏及长度截断。
     *
     * @param value     待清洗的文本，允许为 null
     * @param maxLength 允许保留的最大字符长度
     * @return 清洗脱敏后的安全字符串；空串或 null 时返回 null
     */
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

    /**
     * 清理字符串中的不可见控制字符及换行符，防止日志注入伪造。
     *
     * @param value 原始文本字符串
     * @return 消除非法控制符后的安全字符串
     */
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

    /**
     * 对超长文本施加硬截断并追加 [TRUNCATED] 标识。
     *
     * @param value     待截断的字符串
     * @param maxLength 最大长度限制
     * @return 符合长度约束且不截断 UTF-16 代理对的字符串
     */
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
