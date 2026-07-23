package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.agent.AgentRunDetailDTO;
import com.dinukaly.velo.dto.agent.AgentRunResponseDTO;
import com.dinukaly.velo.dto.agent.CreateAgentRunRequestDTO;

import java.util.UUID;

public interface AgentRunService {

    /**
     * Validates the request, persists a new AgentRun, queues execution, and returns immediately.
     */
    AgentRunResponseDTO createRun(CreateAgentRunRequestDTO request, String userEmail);

    /**
     * Fetches the full run detail including all steps.
     * Validates that the calling user owns the run.
     */
    AgentRunDetailDTO getRunDetail(UUID runId, String userEmail);

    /**
     * Cancels an active run (allowed only before APPLYING begins).
     */
    void cancelRun(UUID runId, String userEmail);

    /**
     * Rejects a run that is in WAITING_FOR_APPROVAL state.
     */
    void rejectRun(UUID runId, String userEmail);
}
