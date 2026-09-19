package com.leetmodel.file.enums;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageContentTypes;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 业务文件用途到命名空间、来源类型、访问级别与文件类型的映射。
 *
 * <p>调用方只声明用途，物理 objectKey、命名空间与访问策略由 file-service 决定，
 * 避免业务服务自行拼接对象路径或扩大可用文件类型。</p>
 */
public enum FilePurpose {

    /** 题目附件：允许文档、表格、图片、文本与常见压缩包。 */
    PROBLEM_ATTACHMENT("problem", "PROBLEM_ATTACHMENT", "BUSINESS_AUTHORIZED", "problem-service",
            problemAttachmentTypes()),

    /** 用户头像：只允许图片。 */
    USER_AVATAR("avatar", "USER_AVATAR", "BUSINESS_AUTHORIZED", "user-service",
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp")),

    /** 正式论文：只允许 PDF。 */
    SUBMISSION_PAPER("submission", "SUBMISSION_PAPER", "BUSINESS_AUTHORIZED", "submission-service",
            Set.of("application/pdf"));

    private final String namespaceCode;
    private final String sourceType;
    private final String accessLevel;
    private final String ownerService;
    private final Set<String> allowedContentTypes;

    FilePurpose(String namespaceCode, String sourceType, String accessLevel, String ownerService,
                Set<String> allowedContentTypes) {
        this.namespaceCode = namespaceCode;
        this.sourceType = sourceType;
        this.accessLevel = accessLevel;
        this.ownerService = ownerService;
        this.allowedContentTypes = Set.copyOf(allowedContentTypes);
    }

    public String namespaceCode() {
        return namespaceCode;
    }

    public String sourceType() {
        return sourceType;
    }

    public String accessLevel() {
        return accessLevel;
    }

    /**
     * 该用途对应的业务事实所有者服务。
     *
     * @return 业务服务名
     */
    public String ownerService() {
        return ownerService;
    }

    /**
     * 判断媒体类型是否在该用途允许范围内。
     *
     * @param contentType 客户端声明的媒体类型
     * @return 允许时为 true
     */
    public boolean allowsContentType(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase(Locale.ROOT));
    }

    /**
     * 计算在对象存储基础白名单之外需要额外放行的媒体类型。
     *
     * <p>当前只有题目附件允许压缩包，其余用途不能因为该策略同步放宽。</p>
     *
     * @return 额外媒体类型集合
     */
    public Set<String> additionalContentTypes() {
        return allowedContentTypes.stream()
                .filter(StorageContentTypes.ARCHIVE::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 解析用途编码。
     *
     * @param code 用途编码，忽略大小写
     * @return 匹配的文件用途
     * @throws BusinessException 当编码为空或不受支持时
     */
    public static FilePurpose fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(FileErrorCode.FILE_PURPOSE_INVALID);
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (FilePurpose purpose : values()) {
            if (purpose.name().equals(normalized)) {
                return purpose;
            }
        }
        throw new BusinessException(FileErrorCode.FILE_PURPOSE_INVALID);
    }

    /**
     * 按文件来源类型解析业务用途。
     *
     * @param sourceType 文件来源类型
     * @return 匹配的文件用途，未托管来源返回 null
     */
    public static FilePurpose fromSourceType(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        for (FilePurpose purpose : values()) {
            if (purpose.sourceType.equals(sourceType)) {
                return purpose;
            }
        }
        return null;
    }

    private static Set<String> problemAttachmentTypes() {
        Set<String> types = new HashSet<>(StorageContentTypes.ARCHIVE);
        types.add("application/pdf");
        types.add("text/plain");
        types.add("text/markdown");
        types.add("text/csv");
        types.add("application/msword");
        types.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        types.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        types.add("image/jpeg");
        types.add("image/png");
        types.add("image/gif");
        types.add("image/webp");
        return Set.copyOf(types);
    }
}
