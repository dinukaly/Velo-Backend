package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.agent.*;
import com.dinukaly.velo.entity.*;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.service.AgentRunService;
import com.dinukaly.velo.service.AgentSseService;
import com.dinukaly.velo.service.AgentExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunServiceImpl implements AgentRunService {

    // Active statuses used to enforce the one-active-run-per-project constraint
    private static final List<AgentRunStatus> ACTIVE_STATUSES = List.of(
            AgentRunStatus.QUEUED,
            AgentRunStatus.RUNNING,
            AgentRunStatus.WAITING_FOR_APPROVAL,
            AgentRunStatus.APPLYING,
            AgentRunStatus.VERIFYING
    );

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final AgentSseService agentSseService;
    private final AgentExecutionService agentExecutionService;

    @Override
    @Transactional
    public AgentRunResponseDTO createRun(CreateAgentRunRequestDTO request, String userEmail) {
        User user = resolveUser(userEmail);
        Project project = resolveOwnedProject(request.getProjectId(), user);

        if (request.getDirtyFiles() != null && !request.getDirtyFiles().isEmpty()) {
            throw new BadRequestException(
                    "DIRTY_FILES_PRESENT: Please save all open files before starting Agent Mode.");
        }

        boolean hasActiveRun = agentRunRepository.existsActiveRunForProject(project, ACTIVE_STATUSES);
        if (hasActiveRun) {
            throw new BadRequestException(
                    "RUN_ALREADY_ACTIVE: Another agent run is already active for this project.");
        }

        AgentRun run = AgentRun.builder()
                .project(project)
                .user(user)
                .message(request.getMessage())
                .status(AgentRunStatus.QUEUED)
                .currentPath(request.getCurrentPath())
                .selectedText(request.getSelectedText())
                .runVersion(1)
                .build();

        agentRunRepository.save(run);
        log.info("Agent run [{}] created for project [{}] by user [{}]",
                run.getId(), project.getId(), userEmail);

        agentSseService.publishEvent(run, AgentSseEventType.RUN_STATUS,
                buildRunStatusPayload(run));

        // Dispatch execution AFTER the transaction commits so the async thread
        // can find the persisted AgentRun row in the database.
        UUID runId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchExecution(runId);
            }
        });

        return toResponseDTO(run);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRunDetailDTO getRunDetail(UUID runId, String userEmail) {
        User user = resolveUser(userEmail);
        AgentRun run = agentRunRepository.findByIdAndUser(runId, user)
                .orElseThrow(() -> new NotFoundException("Agent run not found or access denied"));

        List<AgentStep> steps = agentStepRepository.findByRunOrderBySequenceAsc(run);

        return AgentRunDetailDTO.builder()
                .id(run.getId())
                .projectId(run.getProject().getId())
                .message(run.getMessage())
                .status(run.getStatus())
                .currentPath(run.getCurrentPath())
                .summary(run.getSummary())
                .errorCode(run.getErrorCode())
                .errorMessage(run.getErrorMessage())
                .runVersion(run.getRunVersion())
                .createdAt(run.getCreatedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .updatedAt(run.getUpdatedAt())
                .steps(steps.stream().map(this::toStepDTO).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRunDetailDTO getActiveRun(UUID projectId, String userEmail) {
        User user = resolveUser(userEmail);
        Project project = resolveOwnedProject(projectId, user);

        Optional<AgentRun> activeRunOpt = agentRunRepository.findFirstByProjectAndStatusInOrderByCreatedAtDesc(project, ACTIVE_STATUSES);
        
        if (activeRunOpt.isEmpty()) {
            return null;
        }

        AgentRun run = activeRunOpt.get();
        List<AgentStep> steps = agentStepRepository.findByRunOrderBySequenceAsc(run);

        return AgentRunDetailDTO.builder()
                .id(run.getId())
                .projectId(run.getProject().getId())
                .message(run.getMessage())
                .status(run.getStatus())
                .currentPath(run.getCurrentPath())
                .summary(run.getSummary())
                .errorCode(run.getErrorCode())
                .errorMessage(run.getErrorMessage())
                .runVersion(run.getRunVersion())
                .createdAt(run.getCreatedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .updatedAt(run.getUpdatedAt())
                .steps(steps.stream().map(this::toStepDTO).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public void cancelRun(UUID runId, String userEmail) {
        User user = resolveUser(userEmail);
        AgentRun run = agentRunRepository.findByIdAndUser(runId, user)
                .orElseThrow(() -> new NotFoundException("Agent run not found or access denied"));

        if (!ACTIVE_STATUSES.contains(run.getStatus()) ||
                run.getStatus() == AgentRunStatus.APPLYING) {
            throw new BadRequestException(
                    "Run cannot be cancelled in status: " + run.getStatus());
        }

        run.setStatus(AgentRunStatus.CANCELED);
        run.setCompletedAt(Instant.now());
        run.setRunVersion(run.getRunVersion() + 1);
        agentRunRepository.save(run);

        agentSseService.publishEvent(run, AgentSseEventType.RUN_STATUS,
                buildRunStatusPayload(run));
        agentSseService.completeStream(run.getId());

        log.info("Agent run [{}] cancelled by user [{}]", runId, userEmail);
    }

    @Override
    @Transactional
    public void rejectRun(UUID runId, String userEmail) {
        User user = resolveUser(userEmail);
        AgentRun run = agentRunRepository.findByIdAndUser(runId, user)
                .orElseThrow(() -> new NotFoundException("Agent run not found or access denied"));

        if (run.getStatus() != AgentRunStatus.WAITING_FOR_APPROVAL) {
            throw new BadRequestException(
                    "Run can only be rejected when in WAITING_FOR_APPROVAL status. Current: " + run.getStatus());
        }

        run.setStatus(AgentRunStatus.REJECTED);
        run.setCompletedAt(Instant.now());
        run.setRunVersion(run.getRunVersion() + 1);
        agentRunRepository.save(run);

        agentSseService.publishEvent(run, AgentSseEventType.RUN_STATUS,
                buildRunStatusPayload(run));
        agentSseService.completeStream(run.getId());

        log.info("Agent run [{}] rejected by user [{}]", runId, userEmail);
    }

    public void dispatchExecution(UUID runId) {
        log.info("Agent run [{}] dispatched to background executor", runId);
        try {
            agentExecutionService.executeRun(runId);
        } catch (Exception e) {
            log.error("Unhandled exception in agent executor for run [{}]", runId, e);
        }
    }

    private void updateRunStatus(UUID runId, AgentRunStatus newStatus) {
        agentRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(newStatus);
            if (newStatus == AgentRunStatus.RUNNING) {
                run.setStartedAt(Instant.now());
            } else if (newStatus == AgentRunStatus.DONE || newStatus == AgentRunStatus.FAILED) {
                run.setCompletedAt(Instant.now());
            }
            run.setRunVersion(run.getRunVersion() + 1);
            agentRunRepository.save(run);
            agentSseService.publishEvent(run, AgentSseEventType.RUN_STATUS, buildRunStatusPayload(run));
        });
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Project resolveOwnedProject(UUID projectId, User user) {
        return projectRepository.findByIdAndOwner(projectId, user)
                .orElseThrow(() -> new NotFoundException("Project not found or access denied"));
    }

    private AgentRunResponseDTO toResponseDTO(AgentRun run) {
        return AgentRunResponseDTO.builder()
                .id(run.getId())
                .projectId(run.getProject().getId())
                .message(run.getMessage())
                .status(run.getStatus())
                .currentPath(run.getCurrentPath())
                .summary(run.getSummary())
                .errorCode(run.getErrorCode())
                .errorMessage(run.getErrorMessage())
                .runVersion(run.getRunVersion())
                .createdAt(run.getCreatedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .updatedAt(run.getUpdatedAt())
                .build();
    }

    private AgentStepDTO toStepDTO(AgentStep step) {
        return AgentStepDTO.builder()
                .id(step.getId())
                .runId(step.getRun().getId())
                .sequence(step.getSequence())
                .type(step.getType())
                .status(step.getStatus())
                .title(step.getTitle())
                .summary(step.getSummary())
                .startedAt(step.getStartedAt())
                .completedAt(step.getCompletedAt())
                .createdAt(step.getCreatedAt())
                .build();
    }

    private String buildRunStatusPayload(AgentRun run) {
        return String.format(
                "{\"runId\":\"%s\",\"status\":\"%s\",\"runVersion\":%d}",
                run.getId(), run.getStatus(), run.getRunVersion());
    }
}
