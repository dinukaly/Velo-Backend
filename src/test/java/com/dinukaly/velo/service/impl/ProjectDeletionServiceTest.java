package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.*;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.repo.es.CodeChunkRepository;
import com.dinukaly.velo.service.*;
import com.dinukaly.velo.util.FilePathResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeletionServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final AgentProposalRepository proposals = mock(AgentProposalRepository.class);
    private final ProjectIndexStateRepository indexStates = mock(ProjectIndexStateRepository.class);
    private final SandboxService sandboxes = mock(SandboxService.class);
    private final FileStorageService files = mock(FileStorageService.class);
    private final FilePathResolver paths = mock(FilePathResolver.class);
    private final AgentSseService streams = mock(AgentSseService.class);
    private final CodeChunkRepository chunks = mock(CodeChunkRepository.class);
    private final ProjectDeletionService service = new ProjectDeletionService(
            projects, runs, proposals, indexStates, sandboxes, files, paths, streams, chunks);
    private final Project project = Project.builder().id(UUID.randomUUID()).build();
    private final Path workspace = Path.of("target", "deletion-test").toAbsolutePath().normalize();

    ProjectDeletionServiceTest() {
        TransactionSynchronizationManager.initSynchronization();
        when(paths.getProjectWorkspacePath(project)).thenReturn(workspace);
    }

    @AfterEach
    void clearTransaction() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void deletesDependentsBeforeProjectAndCleansExternalResourcesOnlyAfterCommit() {
        AgentRun run = AgentRun.builder().id(UUID.randomUUID()).status(AgentRunStatus.DONE).build();
        AgentProposal proposal = AgentProposal.builder().run(run).build();
        ProjectIndexState state = ProjectIndexState.builder().status(IndexStatus.READY).build();
        project.setSandboxSession(SandboxSession.builder().containerId("container").build());
        when(runs.findByProjectOrderByCreatedAtDesc(project)).thenReturn(List.of(run));
        when(proposals.findByRun(run)).thenReturn(Optional.of(proposal));
        when(indexStates.findByProject(project)).thenReturn(Optional.of(state));

        service.delete(project);

        var order = inOrder(sandboxes, proposals, runs, indexStates, projects);
        order.verify(sandboxes).stopContainer("container");
        order.verify(proposals).delete(proposal);
        order.verify(runs).delete(run);
        order.verify(indexStates).delete(state);
        order.verify(projects).delete(project);
        order.verify(projects).flush();
        verifyNoInteractions(files, streams, chunks);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(files).deleteProjectWorkspace(workspace);
        verify(streams).completeStream(run.getId());
        verify(chunks).deleteByProjectId(project.getId().toString());
    }

    @ParameterizedTest
    @EnumSource(value = AgentRunStatus.class, names = {"QUEUED", "RUNNING", "WAITING_FOR_APPROVAL", "APPLYING", "VERIFYING"})
    void refusesDeletionWhileAgentWorkIsActive(AgentRunStatus status) {
        when(runs.findByProjectOrderByCreatedAtDesc(project))
                .thenReturn(List.of(AgentRun.builder().status(status).build()));
        assertThrows(BadRequestException.class, () -> service.delete(project));
        verifyNoInteractions(projects, proposals, sandboxes, files, chunks, streams);
    }

    @Test
    void refusesDeletionWhileIndexing() {
        when(indexStates.findByProject(project)).thenReturn(Optional.of(
                ProjectIndexState.builder().status(IndexStatus.INDEXING).build()));
        assertThrows(BadRequestException.class, () -> service.delete(project));
        verifyNoInteractions(projects, sandboxes, files, chunks);
    }

    @Test
    void databaseFailureNeverDeletesWorkspace() {
        doThrow(new DataIntegrityViolationException("foreign key")).when(projects).flush();
        assertThrows(DataIntegrityViolationException.class, () -> service.delete(project));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verifyNoInteractions(files, chunks, streams);
    }

    @Test
    void containerFailurePreservesDatabaseRecords() {
        project.setSandboxSession(SandboxSession.builder().containerId("container").build());
        doThrow(new IllegalStateException("Docker unavailable")).when(sandboxes).stopContainer("container");
        assertThrows(IllegalStateException.class, () -> service.delete(project));
        verifyNoInteractions(projects, proposals, files, streams, chunks);
    }

    @Test
    void unavailableSearchDoesNotFailCommittedDeletionOrWorkspaceCleanup() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Connection refused"))
                .when(chunks).deleteByProjectId(project.getId().toString());

        service.delete(project);

        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCommit()));
        verify(projects).delete(project);
        verify(files).deleteProjectWorkspace(workspace);
        verify(chunks).deleteByProjectId(project.getId().toString());
    }

    @Test
    void rollbackNeverRunsExternalCleanup() {
        service.delete(project);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(files, streams, chunks);
    }
}
