package com.senior.leetmodelbackend;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    private static final String jwtSecret = "6666666666666666666666666666666666666666";
    private static final Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

    @Test
    public void testGenJwt() {

        Map<String, Object> map = new HashMap<>();
        map.put("id", "1");
        map.put("username", "admin");

        String jwtToken = Jwts.builder()
                .signWith(key)
                .addClaims(map)   // 添加自定义信息
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 设置过期时间
                .compact();// compact() 方法将 JWT 生成字符串
        System.out.println(jwtToken);

    }


    @Test
    public void testParseJwt() {
        String jwtToken = "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6IjEiLCJ1c2VybmFtZSI6ImFkbWluIiwiZXhwIjoxNzczNDY2NzU5fQ.qlwf4WKmRZ-FJCtwgmTzWgrGvDMet_n_UnjEtrlT93M";
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwtToken)
                .getBody();
        System.out.println(claims);
    }

}
