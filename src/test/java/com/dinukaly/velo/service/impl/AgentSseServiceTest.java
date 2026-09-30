package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.config.AgentSseProperties;
import com.dinukaly.velo.entity.AgentEvent;
import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.AgentSseEventType;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.AgentSseCapacityException;
import com.dinukaly.velo.repo.AgentEventRepository;
import com.dinukaly.velo.repo.AgentRunRepository;
import com.dinukaly.velo.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentSseServiceTest {
    @Test
    void oversizedEventPayloadIsRejectedBeforePersistenceOrFanout() {
        properties.setMaxEventPayloadCharacters(256);
        assertThrows(com.dinukaly.velo.exception.BadRequestException.class,
                () -> service.publishEvent(new AgentRun(), AgentSseEventType.WARNING, "x".repeat(257)));
        verifyNoInteractions(events);
    }

    private final AgentEventRepository events = mock(AgentEventRepository.class);
    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AgentSseProperties properties = new AgentSseProperties();
    private final AgentSseServiceImpl service = new AgentSseServiceImpl(events, runs, users, properties);
    private final User owner = new User();

    private AgentRun ownedRun(UUID id) {
        AgentRun run = new AgentRun();
        run.setId(id);
        when(users.findByEmail("owner@example.test")).thenReturn(Optional.of(owner));
        when(runs.findByIdAndUser(id, owner)).thenReturn(Optional.of(run));
        return run;
    }

    @Test
    void reconnectStormRespectsPerRunLimitAndReleasesCapacity() throws Exception {
        properties.setMaxSubscribersPerRun(3);
        UUID id = UUID.randomUUID();
        ownedRun(id);
        when(events.findMaxSequenceByRun(any())).thenReturn(0L);
        when(events.findByRunAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
                any(), anyLong(), anyLong(), any(Pageable.class))).thenReturn(List.of());
        try (var pool = Executors.newFixedThreadPool(24)) {
            List<Callable<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                attempts.add(() -> {
                    try { service.subscribe(id, "owner@example.test", 0); return true; }
                    catch (AgentSseCapacityException expected) { return false; }
                });
            }
            long admitted = 0;
            for (var result : pool.invokeAll(attempts)) if (result.get()) admitted++;
            assertEquals(3, admitted);
            assertEquals(3, service.activeSubscriberCount());
        }
        service.completeStream(id);
        assertEquals(0, service.activeSubscriberCount());
        service.subscribe(id, "owner@example.test", 0);
        assertEquals(1, service.activeSubscriberCount());
    }

    @Test
    void globalLimitAppliesAcrossRunsAndOwnershipIsCheckedFirst() {
        properties.setMaxSubscribersPerInstance(1);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        ownedRun(first);
        ownedRun(second);
        when(events.findMaxSequenceByRun(any())).thenReturn(0L);
        when(events.findByRunAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
                any(), anyLong(), anyLong(), any(Pageable.class))).thenReturn(List.of());
        service.subscribe(first, "owner@example.test", 0);
        assertThrows(AgentSseCapacityException.class, () -> service.subscribe(second, "owner@example.test", 0));
        assertEquals(1, service.activeSubscriberCount());
    }

    @Test
    void replayReadsAtMostLimitPlusOneAndGapsDoNotAllocateUnboundedHistory() {
        properties.setMaxReplayEvents(2);
        UUID id = UUID.randomUUID();
        AgentRun run = ownedRun(id);
        when(events.findMaxSequenceByRun(run)).thenReturn(1000L);
        when(events.findByRunAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
                eq(run), eq(0L), eq(1000L), any(Pageable.class)))
                .thenReturn(List.of(event(998), event(999), event(1000)));
        service.subscribe(id, "owner@example.test", 0);
        verify(events).findByRunAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
                run, 0L, 1000L, org.springframework.data.domain.PageRequest.of(0, 3));
        verify(events, never()).findByRunAndSequenceGreaterThanOrderBySequenceAsc(any(), anyLong());
        assertEquals(1, service.activeSubscriberCount());
    }

    @Test
    void failedReplayFreesReservation() {
        UUID id = UUID.randomUUID();
        AgentRun run = ownedRun(id);
        when(events.findMaxSequenceByRun(run)).thenThrow(new IllegalStateException("database down"));
        assertThrows(IllegalStateException.class, () -> service.subscribe(id, "owner@example.test", 0));
        assertEquals(0, service.activeSubscriberCount());
    }

    private AgentEvent event(long sequence) {
        AgentEvent event = new AgentEvent();
        event.setSequence(sequence);
        event.setEventType("step.updated");
        event.setPayloadJson("{}");
        return event;
    }
}
