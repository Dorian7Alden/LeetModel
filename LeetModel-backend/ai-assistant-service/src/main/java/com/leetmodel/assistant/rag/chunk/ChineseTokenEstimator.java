package com.leetmodel.assistant.rag.chunk;

import org.springframework.stereotype.Component;

/** 面向中文知识库的保守 Token 估算器，不依赖供应商私有 tokenizer。 */
@Component
public class ChineseTokenEstimator {

    public int estimate(String text) {
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                tokens += asciiTokens(asciiRun);
                asciiRun = 0;
            } else if (codePoint < 128 && Character.isLetterOrDigit(codePoint)) {
                asciiRun++;
            } else {
                tokens += asciiTokens(asciiRun) + 1;
                asciiRun = 0;
            }
        }
        return tokens + asciiTokens(asciiRun);
    }

    private int asciiTokens(int characters) {
        return (characters + 3) / 4;
    }
}
