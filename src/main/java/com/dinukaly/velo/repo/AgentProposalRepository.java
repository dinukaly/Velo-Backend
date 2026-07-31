package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.AgentProposal;
import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Repository for managing AgentProposal persistence.
 */
@Repository
public interface AgentProposalRepository extends JpaRepository<AgentProposal, UUID> {

    /** Find the proposal for a specific agent run. */
    Optional<AgentProposal> findByRun(AgentRun run);

    /** Find proposal by run ID directly. */
    Optional<AgentProposal> findByRunId(UUID runId);

    /** Check if a proposal already exists for a run. */
    boolean existsByRun(AgentRun run);

    /** Find by status — used for cleanup/expiry jobs. */
    List<AgentProposal> findByStatus(ProposalStatus status);
}
