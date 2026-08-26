package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.AgentRunStatus;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    List<AgentRun> findByProjectOrderByCreatedAtDesc(Project project);

    Optional<AgentRun> findByIdAndUser(UUID id, User user);

    /**
     * Checks whether a project already has an active (non-terminal) run.
     * Used to enforce the one-active-run-per-project rule.
     */
    @Query("SELECT COUNT(r) > 0 FROM AgentRun r WHERE r.project = :project AND r.status IN :statuses")
    boolean existsActiveRunForProject(
            @Param("project") Project project,
            @Param("statuses") List<AgentRunStatus> statuses
    );

    /**
     * Finds all runs stuck in transient states after a backend restart, for recovery.
     */
    List<AgentRun> findByStatusIn(List<AgentRunStatus> statuses);

    /**
     * Finds the currently active run for a project, if any.
     */
    Optional<AgentRun> findFirstByProjectAndStatusInOrderByCreatedAtDesc(Project project, List<AgentRunStatus> statuses);

    /**
     * Fetches a run with its User and Project associations eagerly initialized.
     * Used by the async agent execution thread which has no open Hibernate session
     * and cannot lazily load proxy associations.
     */
    @Query("SELECT r FROM AgentRun r JOIN FETCH r.user JOIN FETCH r.project WHERE r.id = :id")
    Optional<AgentRun> findByIdWithAssociations(@Param("id") UUID id);
}
