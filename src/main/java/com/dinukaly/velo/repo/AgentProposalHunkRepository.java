package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.AgentProposalFile;
import com.dinukaly.velo.entity.AgentProposalHunk;
import com.dinukaly.velo.entity.HunkDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for managing AgentProposalHunk persistence.
 */
@Repository
public interface AgentProposalHunkRepository extends JpaRepository<AgentProposalHunk, UUID> {

    /** All hunks for a file, ordered for sequential application. */
    List<AgentProposalHunk> findByProposalFileOrderByOrdinalAsc(AgentProposalFile proposalFile);

    /** All hunks in a dependency group — used for cascade accept/reject. */
    List<AgentProposalHunk> findByProposalFile_ProposalAndChangeGroupKey(
            com.dinukaly.velo.entity.AgentProposal proposal, String changeGroupKey);

    /** Pending hunks for a proposal — used to check if review is complete. */
    @Query("SELECT h FROM AgentProposalHunk h WHERE h.proposalFile.proposal.id = :proposalId AND h.decision = 'PENDING'")
    List<AgentProposalHunk> findPendingByProposalId(@Param("proposalId") UUID proposalId);

    /** Bulk-update all PENDING hunks in a group to SKIPPED. */
    @Modifying
    @Query("UPDATE AgentProposalHunk h SET h.decision = 'SKIPPED', h.decidedAt = CURRENT_TIMESTAMP " +
           "WHERE h.proposalFile.proposal.id = :proposalId AND h.changeGroupKey = :groupKey AND h.decision = 'PENDING'")
    void skipPendingInGroup(@Param("proposalId") UUID proposalId, @Param("groupKey") String groupKey);

    /** Count hunks by decision for a proposal. */
    @Query("SELECT COUNT(h) FROM AgentProposalHunk h WHERE h.proposalFile.proposal.id = :proposalId AND h.decision = :decision")
    long countByProposalIdAndDecision(@Param("proposalId") UUID proposalId, @Param("decision") HunkDecision decision);
}
