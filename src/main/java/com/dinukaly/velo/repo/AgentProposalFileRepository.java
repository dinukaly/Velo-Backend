package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.AgentProposal;
import com.dinukaly.velo.entity.AgentProposalFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing AgentProposalFile persistence.
 */
@Repository
public interface AgentProposalFileRepository extends JpaRepository<AgentProposalFile, UUID> {

    /** All files within a proposal, ordered by path. */
    List<AgentProposalFile> findByProposalOrderByFilePathAsc(AgentProposal proposal);

    /** Find a specific file entry in a proposal by its path. */
    Optional<AgentProposalFile> findByProposalAndFilePath(AgentProposal proposal, String filePath);
}
