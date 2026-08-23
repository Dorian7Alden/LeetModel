package com.leetmodel.common.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 全局配置 —— 分页插件 + 自动填充。
 *
 * <p>引入 common-core 的微服务自动获得分页和自动填充能力，无需额外配置。</p>
 */
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链。
     *
     * <p>当前包含：分页拦截器（MySQL 方言）。后续可按需追加多租户、防全表更新等拦截器。</p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件：指定数据库类型以生成正确的 LIMIT 方言
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器 —— 为 {@code createTime} 和 {@code updateTime} 字段自动赋值。
     *
     * <p>触发时机：
     * <ul>
     *   <li>{@code createTime}：insert 时填充（如果实体中未手动设置）</li>
     *   <li>{@code updateTime}：insert 和 update 时均填充</li>
     * </ul>
     * </p>
     *
     * <p>配合 {@link com.leetmodel.common.core.entity.BaseEntity} 中的字段名使用，
     * 也可通过实体字段上的 {@code @TableField(fill = FieldFill.INSERT)} 精确控制。</p>
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                // 仅当实体未手动设置时才填充
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
