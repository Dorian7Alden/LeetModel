package com.leetmodel.submission.config;

import com.leetmodel.common.messaging.config.MessagingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewDispatchConfigurationTest {

    private final ReviewDispatchConfiguration configuration = new ReviewDispatchConfiguration();

    @Test
    void rejectLegacyFeignWhileRelayIsStillEnabled() {
        ReviewDispatchProperties dispatch = new ReviewDispatchProperties();
        dispatch.setTransport(ReviewDispatchProperties.Transport.LEGACY_FEIGN);
        MessagingProperties messaging = new MessagingProperties();
        messaging.getRelay().setEnabled(true);

        assertThatThrownBy(() -> configuration.reviewTransportGuard(dispatch, messaging)
                .afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowFeignRelayOnlyWithOutboxRelayEnabled() {
        ReviewDispatchProperties dispatch = new ReviewDispatchProperties();
        dispatch.setTransport(ReviewDispatchProperties.Transport.FEIGN_RELAY);
        MessagingProperties messaging = new MessagingProperties();
        messaging.getRelay().setEnabled(true);

        assertThatCode(() -> configuration.reviewTransportGuard(dispatch, messaging)
                .afterPropertiesSet()).doesNotThrowAnyException();
    }
}
