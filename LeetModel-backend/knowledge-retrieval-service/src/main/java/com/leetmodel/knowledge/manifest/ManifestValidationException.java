package com.leetmodel.knowledge.manifest;

/**
 * 知识库元数据或自描述 YAML 校验异常。
 */
public class ManifestValidationException extends RuntimeException {

    public ManifestValidationException(String message) {
        super(message);
    }

    public ManifestValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
