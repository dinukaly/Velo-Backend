package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.config.AgentSseProperties;
import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.AgentRunStatus;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.service.AgentSseService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentSseRetentionTest {
    private final AgentEventRepository events = mock(AgentEventRepository.class);
    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final AgentStepRepository steps = mock(AgentStepRepository.class);
    private final AgentProposalRepository proposals = mock(AgentProposalRepository.class);
    private final AgentSseService streams = mock(AgentSseService.class);
    private final AgentSseRetention retention = new AgentSseRetention(
            events, runs, steps, proposals, streams, new AgentSseProperties());

    @Test
    void deletesOnlyOldTerminalHistoryInBoundedBatches() {
        UUID id = UUID.randomUUID();
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setStatus(AgentRunStatus.DONE);
        when(events.findOldTerminalEventIds(any(), anyList(), any(Pageable.class))).thenReturn(List.of(id));
        when(runs.findTop100ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(anyList(), any()))
                .thenReturn(List.of(run));
        when(proposals.findByRun(run)).thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();
        try {
            retention.cleanup();
            verify(events).deleteAllByIdInBatch(List.of(id));
            verify(events).deleteByRun(run);
            verify(steps).deleteByRun(run);
            verify(runs).delete(run);
            var eventCutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
            var statuses = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(events).findOldTerminalEventIds(eventCutoff.capture(), statuses.capture(),
                    eq(org.springframework.data.domain.PageRequest.of(0, 500)));
            assertFalse(statuses.getValue().contains(AgentRunStatus.WAITING_FOR_APPROVAL));
            assertFalse(statuses.getValue().contains(AgentRunStatus.RUNNING));
            assertTrue(eventCutoff.getValue().isBefore(Instant.now().minusSeconds(29L * 86400)));
            verifyNoInteractions(streams);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(streams).completeStream(id);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void leavesActiveRunsAndRecentEventsAlone() {
        retention.cleanup();
        verify(events, never()).deleteAllByIdInBatch(anyList());
        verifyNoInteractions(proposals, steps, streams);
        verify(runs, never()).delete(any());
    }
}
