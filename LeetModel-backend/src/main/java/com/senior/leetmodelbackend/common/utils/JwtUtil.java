package com.senior.leetmodelbackend.common.utils;

import com.senior.leetmodelbackend.common.property.JwtProperties;
import com.senior.leetmodelbackend.pojo.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 提供生成Token、解析Token、验证Token、获取Claims信息等功能
 */
@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;

    private static long defaultExpireTime;
    private static Key signKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 初始化JWT工具类
     */
    @PostConstruct
    private void init() {
        String secretKey = jwtProperties.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("jwt.secret-key 配置不能为空");
        }

        defaultExpireTime = jwtProperties.getTokenExpiration();
        signKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateToken() {
        return generateToken(new HashMap<>());
    }

    /**
     * 生成包含用户载荷的 JWT Token
     * @param user 登录用户
     * @return 生成的JWT Token字符串
     */
    public static String generateToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("用户信息不能为空，且必须包含用户ID");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());

        if (user.getEmail() != null) {
            claims.put("email", user.getEmail());
        }
        if (user.getUsername() != null) {
            claims.put("username", user.getUsername());
        }
        if (user.getRole() != null) {
            claims.put("role", user.getRole());
        }

        return generateToken(claims);
    }

    /**
     * 生成JWT Token
     * @param claims 自定义载荷信息
     * @return 生成的JWT Token字符串
     */
    public static String generateToken(Map<String, Object> claims) {
        return generateToken(claims, defaultExpireTime);
    }

    /**
     * 生成JWT Token
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
                .signWith(signKey, SignatureAlgorithm.HS256) // 指定签名算法和密钥
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
            .setSigningKey(signKey) // 设置签名密钥
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