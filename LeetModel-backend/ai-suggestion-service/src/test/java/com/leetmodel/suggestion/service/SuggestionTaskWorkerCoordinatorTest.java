package com.leetmodel.suggestion.service;

import com.leetmodel.suggestion.config.SuggestionWorkerProperties;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionTaskWorkerCoordinatorTest {
    @Mock SuggestionTaskMapper taskMapper;
    @Mock SuggestionService suggestionService;
    @Mock ThreadPoolTaskExecutor executor;
    private SuggestionTaskWorkerCoordinator coordinator;

    @BeforeEach
    void setUp() {
        SuggestionWorkerProperties properties = new SuggestionWorkerProperties();
        properties.setConcurrency(1);
        properties.setLeaseSeconds(120);
        coordinator = new SuggestionTaskWorkerCoordinator(
                taskMapper, suggestionService, properties, executor);
    }

    @Test
    void wakeupClaimsByTaskIdAndExecutesWithFencingToken() {
        when(taskMapper.claim(eq(9001L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        coordinator.wakeup(9001L);

        verify(suggestionService).executeClaimed(eq(9001L), anyString(), anyString());
    }

    @Test
    void heartbeatRenewsOnlyLocallyTrackedLease() {
        when(taskMapper.claim(eq(9001L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> null).when(executor).execute(any(Runnable.class));
        coordinator.wakeup(9001L);

        coordinator.heartbeat();

        verify(taskMapper).heartbeat(eq(9001L), anyString(), anyString(), any(), any());
    }
}
