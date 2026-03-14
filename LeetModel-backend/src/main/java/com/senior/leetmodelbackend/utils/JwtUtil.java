package com.senior.leetmodelbackend.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * 提供生成Token、解析Token、验证Token、获取Claims信息等功能
 */
@Component
public class JwtUtil {

    // TODO: 调整配置方式
    private static final String JWT_SECRET = "6666666666666666666666666666666666666666";
    private static final long DEFAULT_EXPIRE_TIME = 1000 * 60 * 60 * 24;
    private static final Key SIGN_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());

    private JwtUtil() {}

    /**
     * 生成JWT Token（使用默认过期时间24小时）
     * @param claims 自定义载荷信息（如用户ID、用户名等）
     * @return 生成的JWT Token字符串
     */
    public static String generateToken(Map<String, Object> claims) {
        return generateToken(claims, DEFAULT_EXPIRE_TIME);
    }

    /**
     * 生成JWT Token（自定义过期时间）
     * @param claims 自定义载荷信息
     * @param expireTime 过期时间（单位：毫秒）
     * @return 生成的JWT Token字符串
     */
    public static String generateToken(Map<String, Object> claims, long expireTime) {
        // 校验过期时间合法性
        if (expireTime <= 0) {
            throw new IllegalArgumentException("过期时间必须大于0");
        }

        return Jwts.builder()
                .addClaims(claims) // 添加自定义载荷
                .setIssuedAt(new Date()) // 设置签发时间（可选，便于追踪）
                .setExpiration(new Date(System.currentTimeMillis() + expireTime)) // 设置过期时间
                .signWith(SIGN_KEY, SignatureAlgorithm.HS256) // 指定签名算法和密钥
                .compact(); // 生成Token字符串
    }

    /**
     * 解析JWT Token，获取载荷信息
     * @param token JWT Token字符串
     * @return 载荷Claims对象
     * @throws ExpiredJwtException Token过期
     * @throws SignatureException 签名错误
     * @throws MalformedJwtException Token格式错误
     * @throws IllegalArgumentException Token为空或无效
     */
    public static Claims parseToken(String token) {
        // 校验Token合法性
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT Token不能为空");
        }

        return Jwts.parserBuilder()
                .setSigningKey(SIGN_KEY) // 设置签名密钥
                .build()
                .parseClaimsJws(token) // 解析Token
                .getBody(); // 获取载荷信息
    }

    /**
     * 从Token中获取指定字段的值
     * @param token JWT Token字符串
     * @param field 要获取的字段名
     * @return 字段值（null表示字段不存在）
     */
    public static Object getFieldFromToken(String token, String field) {
        Claims claims = parseToken(token);
        return claims.get(field);
    }

    /**
     * 验证Token是否过期
     * @param token JWT Token字符串
     * @return true=已过期，false=未过期
     */
    public static boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration().before(new Date());
    }

}