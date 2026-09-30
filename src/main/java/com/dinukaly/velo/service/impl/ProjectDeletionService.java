package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.*;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.repo.es.CodeChunkRepository;
import com.dinukaly.velo.service.AgentSseService;
import com.dinukaly.velo.service.FileStorageService;
import com.dinukaly.velo.service.SandboxService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared deletion for projects already authorized by the owner or admin service. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDeletionService {
    private static final Set<AgentRunStatus> TERMINAL = Set.of(
            AgentRunStatus.DONE, AgentRunStatus.FAILED, AgentRunStatus.CANCELED,
            AgentRunStatus.REJECTED, AgentRunStatus.CONFLICTED);
    private final ProjectRepository projects;
    private final AgentRunRepository runs;
    private final AgentProposalRepository proposals;
    private final ProjectIndexStateRepository indexStates;
    private final SandboxService sandboxes;
    private final FileStorageService files;
    private final FilePathResolver paths;
    private final AgentSseService streams;
    private final CodeChunkRepository chunks;

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Project project) {
        List<AgentRun> history = runs.findByProjectOrderByCreatedAtDesc(project);
        if (history.stream().anyMatch(run -> !TERMINAL.contains(run.getStatus()))) {
            throw new BadRequestException("Finish or cancel the active agent run before deleting this project.");
        }
        var indexState = indexStates.findByProject(project);
        if (indexState.filter(state -> state.getStatus() == IndexStatus.INDEXING).isPresent()) {
            throw new BadRequestException("Wait for project indexing to finish before deleting this project.");
        }

        UUID projectId = project.getId();
        Path workspace = paths.getProjectWorkspacePath(project).toAbsolutePath().normalize();
        List<UUID> runIds = history.stream().map(AgentRun::getId).toList();
        if (project.getSandboxSession() != null) {
            // A stop failure must retain the session and project so the user can retry.
            sandboxes.stopContainer(project.getSandboxSession().getContainerId());
        }
        for (AgentRun run : history) {
            // Proposal -> files -> hunks cascade; runs cascade to events and steps.
            proposals.findByRun(run).ifPresent(proposals::delete);
            runs.delete(run);
        }
        indexState.ifPresent(indexStates::delete);
        // The project mapping also cascades removal of its sandbox session.
        projects.delete(project);
        projects.flush();

        // Files and Elasticsearch cannot roll back with MySQL. Keep them on DB failure.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (UUID runId : runIds) {
                    try { streams.completeStream(runId); }
                    catch (Exception error) { log.warn("Could not close deleted run stream {}", runId, error); }
                }
                try { files.deleteProjectWorkspace(workspace); }
                catch (Exception error) { log.error("Project {} deleted; workspace cleanup needs retry: {}", projectId, workspace, error); }
                try { chunks.deleteByProjectId(projectId.toString()); }
                catch (DataAccessResourceFailureException error) {
                    log.warn("Project {} deleted successfully; Elasticsearch is unavailable, so search entries may remain. "
                            + "Manual search cleanup is required when Elasticsearch is available; no automatic retry is scheduled.", projectId);
                    log.debug("Search cleanup connection failure for deleted project {}", projectId, error);
                }
                catch (Exception error) { log.warn("Project {} deleted; search index cleanup needs retry", projectId, error); }
            }
        });
    }
}
