package leet.model.leetmodelbackend.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * QQ 邮箱 SMTP 发件配置，映射 spring.mail 前缀。
 */
@Data
@ConfigurationProperties(prefix = "spring.mail")
public class QQMailProperties {

	/** QQ 邮箱 SMTP 服务器地址 */
	private String host;

	/** SMTP 端口，QQ 邮箱 SSL 场景通常为 465 */
	private Integer port;

	/** 发件邮箱账号 */
	private String username;

	/** QQ 邮箱授权码，而不是登录密码 */
	private String password;

	/** 邮件默认编码 */
	private String defaultEncoding;

	/** Spring Mail 扩展属性 */
	private Map<String, Object> properties;
}
