package com.dinukaly.velo.service;

import java.util.UUID;

/**
 * Orchestrates background execution for an agent run.
 */
public interface AgentExecutionService {

    /**
     * Executes the pipeline stages for the given run and persists the resulting proposal.
     *
     * @param runId UUID of the AgentRun to execute
     */
    void executeRun(UUID runId);
}
