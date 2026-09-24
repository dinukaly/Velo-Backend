package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentStepRepository extends JpaRepository<AgentStep, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM AgentStep s WHERE s.run = :run")
    int deleteByRun(@org.springframework.data.repository.query.Param("run") AgentRun run);

    List<AgentStep> findByRunOrderBySequenceAsc(AgentRun run);

    @Query("SELECT COALESCE(MAX(s.sequence), 0) FROM AgentStep s WHERE s.run = :run")
    int findMaxSequenceByRun(@Param("run") AgentRun run);
}
