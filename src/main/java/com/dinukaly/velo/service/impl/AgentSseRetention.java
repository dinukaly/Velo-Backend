package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.config.AgentSseProperties;
import com.dinukaly.velo.entity.AgentRunStatus;
import com.dinukaly.velo.repo.AgentEventRepository;
import com.dinukaly.velo.repo.AgentProposalRepository;
import com.dinukaly.velo.repo.AgentRunRepository;
import com.dinukaly.velo.repo.AgentStepRepository;
import com.dinukaly.velo.service.AgentSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSseRetention {
    private static final List<AgentRunStatus> TERMINAL = List.of(
            AgentRunStatus.DONE, AgentRunStatus.FAILED, AgentRunStatus.CANCELED,
            AgentRunStatus.REJECTED, AgentRunStatus.CONFLICTED);
    private final AgentEventRepository events;
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final AgentProposalRepository proposals;
    private final AgentSseService streams;
    private final AgentSseProperties properties;

    @Scheduled(initialDelayString = "${agent.sse.cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${agent.sse.cleanup-interval-ms:300000}")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        int removedEvents = 0;
        for (int batch = 0; batch < 10; batch++) {
            List<UUID> ids = events.findOldTerminalEventIds(
                    now.minus(properties.getEventRetentionDays(), ChronoUnit.DAYS),
                    TERMINAL, PageRequest.of(0, 500));
            if (ids.isEmpty()) break;
            events.deleteAllByIdInBatch(ids);
            removedEvents += ids.size();
            if (ids.size() < 500) break;
        }

        int removedRuns = 0;
        for (var run : runs.findTop100ByStatusInAndCompletedAtBeforeOrderByCompletedAtAsc(
                TERMINAL, now.minus(properties.getTerminalRunRetentionDays(), ChronoUnit.DAYS))) {
            proposals.findByRun(run).ifPresent(proposals::delete);
            events.deleteByRun(run);
            steps.deleteByRun(run);
            runs.delete(run);
            UUID runId = run.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { streams.completeStream(runId); }
            });
            removedRuns++;
        }
        if (removedEvents > 0 || removedRuns > 0) {
            log.info("Agent SSE retention removed {} event(s) and {} terminal run(s)", removedEvents, removedRuns);
        }
    }
}
