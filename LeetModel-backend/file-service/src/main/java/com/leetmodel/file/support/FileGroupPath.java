package com.leetmodel.file.support;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.file.enums.FileErrorCode;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 文件逻辑分组路径规则。
 *
 * <p>只允许受控字符并限制长度，防止通过分组路径注入对象存储层级或遍历路径。</p>
 */
public final class FileGroupPath {

    private static final int MAX_LENGTH = 128;
    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*");

    private FileGroupPath() {
    }

    /**
     * 规范化并校验逻辑分组路径。
     *
     * @param groupPath 原始分组路径，可为空
     * @return 规范化后的分组路径，空值返回空字符串
     * @throws BusinessException 当分组路径非法时
     */
    public static String normalize(String groupPath) {
        if (!StringUtils.hasText(groupPath)) {
            return "";
        }
        String normalized = groupPath.trim();
        if (normalized.length() > MAX_LENGTH || !PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(FileErrorCode.GROUP_PATH_INVALID,
                    "分组仅支持字母、数字、/、_、-，且不能以 / 开头或结尾");
        }
        return normalized;
    }
}
