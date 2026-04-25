package leet.model.leetmodelbackend.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

	/** JWT 签名密钥，需与签发和校验逻辑保持一致 */
	private String secret;

	/** JWT 过期时间，单位毫秒 */
	private Long expiration;
}
