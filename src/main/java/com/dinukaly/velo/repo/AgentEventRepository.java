package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.AgentEvent;
import com.dinukaly.velo.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentEventRepository extends JpaRepository<AgentEvent, UUID> {

    List<AgentEvent> findByRunOrderBySequenceAsc(AgentRun run);

    /**
     * Fetches all events with sequence > lastSeenSequence for SSE reconnect replay.
     */
    List<AgentEvent> findByRunAndSequenceGreaterThanOrderBySequenceAsc(AgentRun run, long lastSeenSequence);

    List<AgentEvent> findByRunAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
            AgentRun run, long lastSeenSequence, long maxSequence, Pageable pageable);

    @Query("SELECT e.id FROM AgentEvent e WHERE e.createdAt < :cutoff AND e.run.status IN :statuses ORDER BY e.createdAt ASC")
    List<UUID> findOldTerminalEventIds(@Param("cutoff") java.time.Instant cutoff,
            @Param("statuses") List<com.dinukaly.velo.entity.AgentRunStatus> statuses, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM AgentEvent e WHERE e.run = :run")
    int deleteByRun(@Param("run") AgentRun run);

    @Query("SELECT COALESCE(MAX(e.sequence), 0) FROM AgentEvent e WHERE e.run = :run")
    long findMaxSequenceByRun(@Param("run") AgentRun run);
}
