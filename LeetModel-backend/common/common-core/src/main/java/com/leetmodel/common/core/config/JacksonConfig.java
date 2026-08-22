package com.leetmodel.common.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 全局序列化配置 —— 统一日期格式、时区、空值处理。
 *
 * <p>通过 {@link Jackson2ObjectMapperBuilderCustomizer} 而非直接覆盖 {@link ObjectMapper}，
 * 保留 Spring Boot 的其他自动配置（如 HttpMessageConverter），仅追加自定义规则。</p>
 *
 * <h3>面试考点</h3>
 * <ul>
 *   <li><b>为什么用 BuilderCustomizer 而非直接返回 ObjectMapper？</b>
 *       直接返回 ObjectMapper Bean 会替换 Spring Boot 的默认配置，可能破坏其他自动配置。
 *       Customizer 是追加式配置，更安全。</li>
 *   <li><b>Java 8 时间 API 序列化</b>：默认 Jackson 将 {@code LocalDateTime} 序列化为数组
 *       {@code [2026,7,30,10,0,0]}，因为缺少 JSR-310 模块。{@code JavaTimeModule} 解决此问题。</li>
 *   <li><b>@JsonFormat vs 全局配置</b>：全局配置是兜底，{@code @JsonFormat(pattern="...")}
 *       优先级更高，可用于特殊字段覆盖。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /** 统一日期时间格式 */
    static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 统一日期格式 */
    static final String DATE_PATTERN = "yyyy-MM-dd";
    /** 统一时间格式 */
    static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * Jackson 全局配置定制器。
     *
     * <p>注意：此配置仅影响 Jackson 序列化。Spring MVC 的参数绑定（如
     * {@code @RequestParam LocalDateTime}）由 {@code @DateTimeFormat} 控制，
     * 需要通过 {@code spring.mvc.format.date-time} 或单独配置。</p>
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // ===== 时区 =====
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));

            // ===== Java 8 时间模块 =====
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            // LocalDateTime
            javaTimeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
            javaTimeModule.addDeserializer(LocalDateTime.class,
                    new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
            // LocalDate
            javaTimeModule.addSerializer(LocalDate.class,
                    new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
            javaTimeModule.addDeserializer(LocalDate.class,
                    new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
            // LocalTime
            javaTimeModule.addSerializer(LocalTime.class,
                    new LocalTimeSerializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));
            javaTimeModule.addDeserializer(LocalTime.class,
                    new LocalTimeDeserializer(DateTimeFormatter.ofPattern(TIME_PATTERN)));

            builder.modules(javaTimeModule);

            // ===== 反序列化特性 =====
            // 未知属性不抛异常（兼容前端多传字段）
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            // ===== 序列化特性 =====
            // 禁用将日期写成时间戳（毫秒数），改用格式化字符串
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }

    /**
     * 全局 ObjectMapper —— 供非 Web 场景（如 Redis 序列化、测试工具）使用。
     *
     * <p>Web 请求/响应序列化使用 Spring Boot 自动配置的 ObjectMapper（已接受上述 Customizer），
     * 此 Bean 用于需要手动获取 ObjectMapper 的场景。</p>
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 Java 8 时间模块
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
        mapper.registerModule(javaTimeModule);
        mapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
