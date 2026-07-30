package com.leetmodel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 头像上传响应。
 *
 * @author LeetModel
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarUploadResponse {

    /** 头像访问 URL */
    private String avatarUrl;
}
