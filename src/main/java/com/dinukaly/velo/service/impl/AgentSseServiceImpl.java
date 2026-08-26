package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.AgentEvent;
import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.AgentSseEventType;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.AgentEventRepository;
import com.dinukaly.velo.repo.AgentRunRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AgentSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSseServiceImpl implements AgentSseService {

    // SSE emitter timeout: 10 minutes. Long enough for a full run.
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    // In-memory registry: runId -> list of active emitters
    // ConcurrentHashMap + CopyOnWriteArrayList for thread-safety without heavy locking
    private final Map<UUID, List<SseEmitter>> emitterRegistry = new ConcurrentHashMap<>();

    private final AgentEventRepository agentEventRepository;
    private final AgentRunRepository agentRunRepository;
    private final UserRepository userRepository;

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

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Register callbacks to clean up the emitter when it is done/timed out/errored
        Runnable cleanup = () -> removeEmitter(runId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> {
            log.warn("SSE emitter error for run [{}]: {}", runId, ex.getMessage());
            cleanup.run();
        });

        // Register the emitter before replay so no events are lost between replay and live
        emitterRegistry.computeIfAbsent(runId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        // Send a heartbeat comment immediately to confirm the connection
        sendHeartbeat(emitter);

        // Replay any missed events (including all events if lastEventId == 0)
        if (lastEventId >= 0) {
            List<AgentEvent> missed = agentEventRepository
                    .findByRunAndSequenceGreaterThanOrderBySequenceAsc(run, lastEventId);
            for (AgentEvent event : missed) {
                sendToEmitter(emitter, event.getSequence(), event.getEventType(), event.getPayloadJson());
            }
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

        // 3. Fan out to all active emitters for this run
        List<SseEmitter> emitters = emitterRegistry.getOrDefault(run.getId(), List.of());
        for (SseEmitter emitter : emitters) {
            sendToEmitter(emitter, nextSeq, eventType.getValue(), payload);
        }

        log.debug("Published SSE event [{}] seq={} to {} subscriber(s) for run [{}]",
                eventType, nextSeq, emitters.size(), run.getId());
    }

    // -------------------------------------------------------------------------
    // completeStream
    // -------------------------------------------------------------------------

    @Override
    public void completeStream(UUID runId) {
        List<SseEmitter> emitters = emitterRegistry.remove(runId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
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

    private void sendToEmitter(SseEmitter emitter, long sequence, String eventType, String payload) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(sequence))
                    .name(eventType)
                    .data(payload));
        } catch (IOException ex) {
            log.warn("Failed to send SSE event [{}] to emitter: {}", eventType, ex.getMessage());
        }
    }

    private void sendHeartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException ex) {
            log.warn("Failed to send SSE heartbeat: {}", ex.getMessage());
        }
    }

    private void removeEmitter(UUID runId, SseEmitter emitter) {
        List<SseEmitter> emitters = emitterRegistry.get(runId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emitterRegistry.remove(runId);
            }
        }
    }
}
