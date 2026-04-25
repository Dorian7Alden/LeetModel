package leet.model.leetmodelbackend.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import leet.model.leetmodelbackend.property.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类，负责基于项目配置创建、解析和校验 JWT。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    /**
     * 创建 JWT，并把业务自定义 claims 写入 token。
     *
     * @param subject JWT 的主题，一般用于用户标识；允许为空。
     * @param claims JWT 中要携带的自定义声明。
     * @return 生成后的 JWT 字符串。
     */
    public String createToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration());

        JwtBuilder builder = Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey());

        // subject 允许为空，空值时只写入 claims，不额外设置主题字段。
        if (subject != null) {
            builder.subject(subject);
        }

        return builder.compact();
    }

    /**
     * 创建一个只有 claims 的 JWT。
     *
     * @param claims JWT 中要携带的自定义声明。
     * @return 生成后的 JWT 字符串。
     */
    public String createToken(Map<String, Object> claims) {
        return createToken(null, claims);
    }

    /**
     * 解析并返回 token 中的完整声明信息。
     *
     * @param token 待解析的 JWT 字符串。
     * @return JWT 中的声明对象。
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 读取 token 的 subject，通常用于标识用户主键或登录标识。
     *
     * @param token 待解析的 JWT 字符串。
     * @return JWT 的 subject。
     */
    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 读取 token 中指定名称的自定义字段。
     *
     * @param token 待解析的 JWT 字符串。
     * @param claimName 自定义声明名。
     * @param requiredType 期望返回的类型。
     * @param <T> 声明值的类型。
     * @return 指定声明名对应的值。
     */
    public <T> T getClaim(String token, String claimName, Class<T> requiredType) {
        return parseClaims(token).get(claimName, requiredType);
    }

    /**
     * 判断 token 是否已经过期。
     *
     * @param token 待解析的 JWT 字符串。
     * @return true 表示已过期，false 表示未过期。
     */
    public boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    /**
     * 判断 token 是否可用，解析失败或已过期都会返回 false。
     *
     * @param token 待校验的 JWT 字符串。
     * @return true 表示 token 有效，false 表示 token 无效或已过期。
     */
    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration() == null || claims.getExpiration().after(new Date());
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 把配置中的 Base64 密钥转换成 JJWT 可直接使用的签名密钥。
     *
     * @return 签名密钥对象。
     */
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }
}