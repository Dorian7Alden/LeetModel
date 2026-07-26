package com.senior.leetmodelbackend.common.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

	/**
	 * 密钥
	 */
	private String secretKey;

	/**
	 * 过期时间（毫秒） 默认 24 h
	 */
	private long tokenExpiration = 86400000L;

}
