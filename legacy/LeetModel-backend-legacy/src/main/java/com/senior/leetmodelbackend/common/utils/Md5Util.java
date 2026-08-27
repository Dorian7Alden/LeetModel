package com.senior.leetmodelbackend.common.utils;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

public class Md5Util {

    private static final String SALT = "leet-model";

    public static String encode(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return DigestUtils.md5DigestAsHex((SALT + raw).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean matches(String raw, String encoded) {
        if (raw == null || encoded == null) {
            return false;
        }
        return encode(raw).equals(encoded);
    }
}
