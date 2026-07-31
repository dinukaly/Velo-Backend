package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.agent.CreateProposalRequestDTO;
import com.dinukaly.velo.dto.agent.ProposalDetailDTO;
import com.dinukaly.velo.entity.HunkDecision;

import java.util.UUID;

/**
 * Service responsible for creating, retrieving, and managing AI agent proposals.
 *
 * Handles:
 * - Normalization: path sanitisation and base file hash verification.
 * - Diff generation: producing unified diffs and splitting into hunks.
 * - Persistence: saving proposal graph to MySQL.
 * - SSE notification: emitting proposal events after save.
 * - Hunk decisions: accepting, rejecting, and cascading dependency groups.
 */
public interface ProposalService {

    /**
     * Creates and persists a new AgentProposal for a run.
     * Validates paths, reads base file hashes, generates diffs, and emits an SSE event.
     *
     * @param runId   The run producing this proposal
     * @param request Structured proposal from the agent execution pipeline
     * @return Persisted proposal detail
     */
    ProposalDetailDTO createProposal(UUID runId, CreateProposalRequestDTO request);

    /**
     * Retrieves the full proposal (including all files and hunks) for a run.
     * Validates that the requesting user owns the run.
     *
     * @param runId     The run ID
     * @param userEmail Authenticated user email
     * @return Full proposal detail DTO
     */
    ProposalDetailDTO getProposal(UUID runId, String userEmail);

    /**
     * Records the user's accept or reject decision for a single hunk.
     * If the hunk belongs to a change group and is REJECTED, cascades SKIPPED
     * to all other PENDING hunks in that group.
     *
     * @param hunkId   The hunk UUID
     * @param decision ACCEPTED or REJECTED
     * @param userEmail Authenticated user email
     * @return Updated proposal detail DTO
     */
    ProposalDetailDTO decideHunk(UUID hunkId, HunkDecision decision, String userEmail);
}
