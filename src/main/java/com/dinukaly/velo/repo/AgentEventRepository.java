package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.AgentEvent;
import com.dinukaly.velo.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentEventRepository extends JpaRepository<AgentEvent, UUID> {

    List<AgentEvent> findByRunOrderBySequenceAsc(AgentRun run);

    /**
     * Fetches all events with sequence > lastSeenSequence for SSE reconnect replay.
     */
    List<AgentEvent> findByRunAndSequenceGreaterThanOrderBySequenceAsc(AgentRun run, long lastSeenSequence);

    @Query("SELECT COALESCE(MAX(e.sequence), 0) FROM AgentEvent e WHERE e.run = :run")
    long findMaxSequenceByRun(@Param("run") AgentRun run);
}
