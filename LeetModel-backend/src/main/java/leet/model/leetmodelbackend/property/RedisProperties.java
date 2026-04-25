package leet.model.leetmodelbackend.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisProperties {

	/** Redis 服务器地址 */
	private String host;

	/** Redis 服务端口 */
	private Integer port;

	/** Redis 访问密码 */
	private String password;
}
