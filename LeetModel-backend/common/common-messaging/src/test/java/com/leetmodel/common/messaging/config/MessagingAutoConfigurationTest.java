package com.leetmodel.common.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.PublishReceipt;
import com.leetmodel.common.messaging.internal.OutboxRelay;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
            .withUserConfiguration(TestBeans.class)
            .withPropertyValues("spring.application.name=test-service");

    @Test
    void shouldRemainDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MessageOutbox.class);
            assertThat(context).doesNotHaveBean(MessageInbox.class);
        });
    }

    @Test
    void shouldCreateOutboxAndInboxOnlyWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "leetmodel.messaging.enabled=true",
                        "leetmodel.messaging.relay.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageOutbox.class);
                    assertThat(context).hasSingleBean(MessageInbox.class);
                    assertThat(context).doesNotHaveBean(OutboxRelay.class);
                });
    }

    @Test
    void shouldCreateRelayWhenPublisherExists() {
        contextRunner
                .withPropertyValues(
                        "leetmodel.messaging.enabled=true",
                        "leetmodel.messaging.relay.interval-ms=60000"
                )
                .run(context -> assertThat(context).hasSingleBean(OutboxRelay.class));
    }

    @Test
    void shouldFailFastForPayloadLimitAboveProjectContract() {
        contextRunner
                .withPropertyValues(
                        "leetmodel.messaging.enabled=true",
                        "leetmodel.messaging.max-payload-bytes=65537"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            new ResourceDatabasePopulator(new ClassPathResource("messaging-schema.sql")).execute(dataSource);
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MessagePublisher messagePublisher() {
            return message -> new PublishReceipt("fake-" + message.eventId());
        }
    }
}
