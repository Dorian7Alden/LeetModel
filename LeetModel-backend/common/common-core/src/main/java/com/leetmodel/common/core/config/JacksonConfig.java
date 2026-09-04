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
 * Jackson 全局序列化配置。
 *
 * <p>统一全平台日期时间格式（yyyy-MM-dd HH:mm:ss）与 Asia/Shanghai 时区。
 * 通过追加定制器而非替换 ObjectMapper，完整保留 Spring Boot 其他默认序列化特性。</p>
 */
@Configuration
public class JacksonConfig {

    /** 统一年月日时分秒格式 */
    static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /** 统一年月日日期格式 */
    static final String DATE_PATTERN = "yyyy-MM-dd";
    /** 统一时分秒时间格式 */
    static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * 注册全局 Jackson 配置定制器。
     *
     * @return 用于配置时区、Java 8 时间模块与容错特性的定制器实例
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
     * 构建供非 Web 场景（如 Redis 序列化、单测工具）使用的独立 ObjectMapper 单例。
     *
     * @return 配置了统一时区与时间模块的 ObjectMapper 实例
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
