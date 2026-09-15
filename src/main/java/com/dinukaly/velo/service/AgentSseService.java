package com.dinukaly.velo.service;

import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.AgentSseEventType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface AgentSseService {

    /**
     * Registers a new SSE emitter for the given run and user.
     * If lastEventId is provided, replays any missed events first.
     *
     * @param runId       the run to subscribe to
     * @param userEmail   the authenticated user (used to validate ownership)
     * @param lastEventId the last sequence number the client received (0 = no replay needed)
     * @return a configured SseEmitter
     */
    SseEmitter subscribe(UUID runId, String userEmail, long lastEventId);

    /**
     * Persists an event to the database and pushes it to all active emitters for this run.
     *
     * @param run       the run that emitted the event
     * @param eventType the SSE event type
     * @param payload   a JSON string to send in the SSE data field
     */
    void publishEvent(AgentRun run, AgentSseEventType eventType, String payload);

    /**
     * Marks the SSE stream for this run as complete, closing all active emitters.
     */
    void completeStream(UUID runId);
}
