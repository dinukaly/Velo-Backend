package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.AgentEvent;
import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.AgentSseEventType;
import com.dinukaly.velo.config.AgentSseProperties;
import com.dinukaly.velo.exception.AgentSseCapacityException;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.AgentEventRepository;
import com.dinukaly.velo.repo.AgentRunRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AgentSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSseServiceImpl implements AgentSseService {

    // SSE emitter timeout: 10 minutes. Long enough for a full run.
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    private final Map<UUID, List<Subscriber>> emitterRegistry = new ConcurrentHashMap<>();
    private int activeSubscribers;

    private final AgentEventRepository agentEventRepository;
    private final AgentRunRepository agentRunRepository;
    private final UserRepository userRepository;
    private final AgentSseProperties properties;

    // -------------------------------------------------------------------------
    // subscribe
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SseEmitter subscribe(UUID runId, String userEmail, long lastEventId) {
        // Validate ownership
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException("User not found"));
        AgentRun run = agentRunRepository.findByIdAndUser(runId, user)
                .orElseThrow(() -> new NotFoundException("Agent run not found or access denied"));

        if (lastEventId < 0) throw new BadRequestException("Last-Event-ID must be nonnegative");

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Subscriber subscriber = new Subscriber(runId, emitter);
        Runnable cleanup = () -> removeEmitter(subscriber);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> {
            log.warn("SSE emitter error for run [{}]: {}", runId, ex.getMessage());
            cleanup.run();
        });

        // Reserve capacity atomically. Live events are queued while bounded replay is read.
        synchronized (emitterRegistry) {
            List<Subscriber> subscribers = emitterRegistry.computeIfAbsent(runId, id -> new CopyOnWriteArrayList<>());
            if (subscribers.size() >= properties.getMaxSubscribersPerRun()
                    || activeSubscribers >= properties.getMaxSubscribersPerInstance()) {
                if (subscribers.isEmpty()) emitterRegistry.remove(runId);
                throw new AgentSseCapacityException();
            }
            subscribers.add(subscriber);
            activeSubscribers++;
        }

        try {
            sendHeartbeat(emitter);
            long snapshot = agentEventRepository.findMaxSequenceByRun(run);
            List<AgentEvent> missed = agentEventRepository
                    .findByRunAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
                            run, lastEventId, snapshot, PageRequest.of(0, properties.getMaxReplayEvents() + 1));
            boolean gap = lastEventId < snapshot && (missed.isEmpty()
                    || missed.get(0).getSequence() > lastEventId + 1);
            if (missed.size() > properties.getMaxReplayEvents() || gap) {
                subscriber.sendControl(snapshot, "replay.reset", "{\"reason\":\"history unavailable\"}");
            } else {
                for (AgentEvent event : missed) {
                    subscriber.sendReplay(event);
                }
            }
            subscriber.finishReplay(snapshot);
        } catch (RuntimeException ex) {
            removeEmitter(subscriber);
            throw ex;
        }

        log.info("SSE subscriber registered for run [{}], lastEventId={}", runId, lastEventId);
        return emitter;
    }

    // -------------------------------------------------------------------------
    // publishEvent
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void publishEvent(AgentRun run, AgentSseEventType eventType, String payload) {
        if (payload == null || payload.length() > properties.getMaxEventPayloadCharacters()) {
            throw new BadRequestException("Agent event payload exceeds the configured size limit");
        }
        // 1. Assign a monotonically increasing sequence number
        long nextSeq = agentEventRepository.findMaxSequenceByRun(run) + 1;

        // 2. Persist event durably
        AgentEvent event = AgentEvent.builder()
                .run(run)
                .sequence(nextSeq)
                .eventType(eventType.getValue())
                .payloadJson(payload)
                .build();
        agentEventRepository.save(event);

        // 3. Fan out to all active emitters for this run (after transaction commits to avoid race conditions)
        UUID runId = run.getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fanOut(runId, nextSeq, eventType.getValue(), payload);
                }
            });
        } else {
            fanOut(runId, nextSeq, eventType.getValue(), payload);
        }

        log.debug("Published SSE event [{}] seq={} to {} subscriber(s) for run [{}]",
                eventType, nextSeq, emitterRegistry.getOrDefault(runId, List.of()).size(), runId);
    }

    // -------------------------------------------------------------------------
    // completeStream
    // -------------------------------------------------------------------------

    @Override
    public void completeStream(UUID runId) {
        List<Subscriber> emitters;
        synchronized (emitterRegistry) {
            emitters = emitterRegistry.remove(runId);
            if (emitters != null) {
                activeSubscribers -= emitters.size();
                emitters.forEach(subscriber -> subscriber.closed = true);
            }
        }
        if (emitters != null) {
            for (Subscriber subscriber : emitters) {
                try {
                    subscriber.emitter.complete();
                } catch (Exception ex) {
                    log.warn("Error completing SSE emitter for run [{}]: {}", runId, ex.getMessage());
                }
            }
        }
        log.info("SSE stream completed for run [{}]", runId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void fanOut(UUID runId, long sequence, String eventType, String payload) {
        for (Subscriber subscriber : emitterRegistry.getOrDefault(runId, List.of())) {
            subscriber.sendLive(sequence, eventType, payload);
        }
    }

    private void sendToEmitter(Subscriber subscriber, long sequence, String eventType, String payload) {
        if (subscriber.closed) return;
        try {
            subscriber.emitter.send(SseEmitter.event()
                    .id(String.valueOf(sequence))
                    .name(eventType)
                    .data(payload));
        } catch (IOException ex) {
            log.warn("Failed to send SSE event [{}] to emitter: {}", eventType, ex.getMessage());
            removeEmitter(subscriber);
            // Spring MVC handles a failed send via the container's async error dispatch.
            // Completing with the same error here would dispatch it a second time.
        } catch (IllegalStateException ex) {
            log.warn("Failed to send SSE event [{}] to emitter: {}", eventType, ex.getMessage());
            removeEmitter(subscriber);
            subscriber.emitter.completeWithError(ex);
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not establish Agent event stream");
        }
    }

    private void removeEmitter(Subscriber subscriber) {
        synchronized (emitterRegistry) {
            subscriber.closed = true;
            List<Subscriber> emitters = emitterRegistry.get(subscriber.runId);
            if (emitters != null && emitters.remove(subscriber)) {
                activeSubscribers--;
                if (emitters.isEmpty()) emitterRegistry.remove(subscriber.runId);
            }
        }
    }

    int activeSubscriberCount() {
        synchronized (emitterRegistry) { return activeSubscribers; }
    }

    private record PendingEvent(long sequence, String type, String payload) {}

    private final class Subscriber {
        private final UUID runId;
        private final SseEmitter emitter;
        private final List<PendingEvent> queued = new ArrayList<>();
        private boolean replaying = true;
        private boolean overflowed;
        private volatile boolean closed;

        private Subscriber(UUID runId, SseEmitter emitter) {
            this.runId = runId;
            this.emitter = emitter;
        }

        private synchronized void sendLive(long sequence, String type, String payload) {
            if (overflowed || closed) return;
            if (replaying) {
                if (queued.size() >= properties.getMaxReplayEvents()) {
                    overflowed = true;
                    removeEmitter(this);
                    emitter.complete();
                } else queued.add(new PendingEvent(sequence, type, payload));
                return;
            }
            sendToEmitter(this, sequence, type, payload);
        }

        private synchronized void sendReplay(AgentEvent event) {
            if (!overflowed && !closed) sendToEmitter(this, event.getSequence(), event.getEventType(), event.getPayloadJson());
        }

        private synchronized void sendControl(long sequence, String type, String payload) {
            if (!overflowed && !closed) sendToEmitter(this, sequence, type, payload);
        }

        private synchronized void finishReplay(long snapshot) {
            if (overflowed || closed) return;
            queued.stream().filter(event -> event.sequence() > snapshot)
                    .sorted(Comparator.comparingLong(PendingEvent::sequence))
                    .forEach(event -> sendToEmitter(this, event.sequence(), event.type(), event.payload()));
            queued.clear();
            replaying = false;
        }
    }
}
