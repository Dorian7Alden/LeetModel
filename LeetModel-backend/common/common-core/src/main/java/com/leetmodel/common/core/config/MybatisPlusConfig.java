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
 *
 * <h3>面试考点</h3>
 * <ul>
 *   <li><b>分页原理</b>：MyBatis-Plus 通过 {@link PaginationInnerInterceptor} 拦截
 *       Executor#query，在 SQL 执行前动态追加方言特定的 LIMIT 子句。
 *       不注册此插件时，{@code IPage} 退化为全量查询 + 内存截断。</li>
 *   <li><b>自动填充原理</b>：{@link MetaObjectHandler} 在 MyBatis 执行 insert/update
 *       前回调，通过反射设置实体字段值。与数据库 DEFAULT 值的区别：前者是代码层保证，
 *       后者依赖 DDL，代码层更可控。</li>
 *   <li><b>多租户插件</b>：在 Interceptor 链中追加 {@code TenantLineInnerInterceptor}
 *       即可实现多租户数据隔离，与分页插件共享同一个 {@link MybatisPlusInterceptor} 实例。</li>
 * </ul>
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
