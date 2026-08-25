package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.FileContentResponseDTO;
import com.dinukaly.velo.dto.agent.CodeSearchResultDTO;
import com.dinukaly.velo.dto.agent.CodeSearchResultMatchDTO;
import com.dinukaly.velo.dto.agent.CreateProposalRequestDTO;
import com.dinukaly.velo.entity.*;
import com.dinukaly.velo.repo.AgentRunRepository;
import com.dinukaly.velo.repo.AgentStepRepository;
import com.dinukaly.velo.service.*;
import com.dinukaly.velo.util.AgentPromptBuilder;
import com.dinukaly.velo.util.AgentProposalParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implements the agent execution pipeline: validates context, plans tasks,
 * runs hybrid code search, reads context files, queries the LLM, and creates
 * structured diff proposals.
 *
 * Note: Not marked @Async because it runs inside the agentTaskExecutor thread pool.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentExecutionServiceImpl implements AgentExecutionService {

    private static final int MAX_FILES_TO_READ = 5;
    private static final int SEARCH_TOP_K = 8;

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final AgentSseService agentSseService;
    private final HybridSearchService hybridSearchService;
    private final AgentToolService agentToolService;
    private final AIService aiService;
    private final ProposalService proposalService;
    private final AgentPromptBuilder agentPromptBuilder;
    private final AgentProposalParser agentProposalParser;

    @Override
    public void executeRun(UUID runId) {
        log.info("[AgentExec] Starting pipeline for run [{}]", runId);

        AgentRun run = agentRunRepository.findById(runId).orElse(null);
        if (run == null) {
            log.error("[AgentExec] Run [{}] not found — aborting", runId);
            return;
        }

        if (run.getStatus() == AgentRunStatus.CANCELED) {
            log.info("[AgentExec] Run [{}] was already cancelled — skipping", runId);
            agentSseService.completeStream(runId);
            return;
        }

        try {
            transitionToRunning(run);

            // 1. Context validation
            AgentStep validateStep = createStep(run, 1, AgentStepType.VALIDATING_CONTEXT,
                    AgentStepStatus.RUNNING, "Validating context", null);
            completeStep(run, validateStep, "Context validated. Message length: " + run.getMessage().length());

            // 2. Planning
            AgentStep planStep = createStep(run, 2, AgentStepType.PLANNING,
                    AgentStepStatus.RUNNING, "Planning changes", null);
            completeStep(run, planStep, "Identified intent: " + truncate(run.getMessage(), 120));

            // 3. Semantic search
            AgentStep searchStep = createStep(run, 3, AgentStepType.SEMANTIC_SEARCH,
                    AgentStepStatus.RUNNING, "Searching codebase", null);

            String userEmail = run.getUser().getEmail();
            UUID projectId = run.getProject().getId();

            CodeSearchResultDTO searchResult;
            try {
                searchResult = hybridSearchService.search(projectId, run.getMessage(), SEARCH_TOP_K, userEmail);
            } catch (Exception e) {
                log.warn("[AgentExec] Hybrid search failed, using empty results: {}", e.getMessage());
                searchResult = CodeSearchResultDTO.builder()
                        .query(run.getMessage())
                        .matches(List.of())
                        .totalMatches(0)
                        .truncated(false)
                        .build();
            }

            List<CodeSearchResultMatchDTO> matches = searchResult.getMatches() != null
                    ? searchResult.getMatches() : List.of();

            completeStep(run, searchStep,
                    String.format("Found %d relevant code locations", matches.size()));

            // 4. Read context files
            AgentStep readStep = createStep(run, 4, AgentStepType.READING_FILE,
                    AgentStepStatus.RUNNING, "Reading relevant files", null);

            List<AgentPromptBuilder.ReadFile> fileContents = readRelevantFiles(
                    run, projectId, userEmail, matches);

            completeStep(run, readStep,
                    String.format("Read %d files for context", fileContents.size()));

            // 5. LLM prompt generation & completion
            AgentStep generateStep = createStep(run, 5, AgentStepType.GENERATING_EDITS,
                    AgentStepStatus.RUNNING, "Generating code changes", null);

            String prompt = agentPromptBuilder.buildPrompt(
                    run.getMessage(),
                    run.getCurrentPath(),
                    run.getSelectedText(),
                    matches,
                    fileContents
            );

            log.debug("[AgentExec] Sending prompt to LLM ({} chars)", prompt.length());
            String llmOutput = aiService.chat(prompt);
            log.debug("[AgentExec] LLM responded ({} chars)", llmOutput != null ? llmOutput.length() : 0);

            completeStep(run, generateStep, "LLM response received, parsing proposal");

            // 6. Parse proposal & persist diff hunks
            AgentStep diffStep = createStep(run, 6, AgentStepType.BUILDING_DIFF,
                    AgentStepStatus.RUNNING, "Building diff proposal", null);

            CreateProposalRequestDTO proposalDTO = agentProposalParser.parse(llmOutput);
            proposalService.createProposal(runId, proposalDTO);

            completeStep(run, diffStep,
                    String.format("Proposal created with %d file change(s)",
                            proposalDTO.getFiles() != null ? proposalDTO.getFiles().size() : 0));

            log.info("[AgentExec] Run [{}] pipeline completed successfully", runId);

        } catch (Exception e) {
            log.error("[AgentExec] Pipeline failed for run [{}]: {}", runId, e.getMessage(), e);
            failRun(runId, e.getMessage());
        }
    }

    /**
     * Reads the active file and top search match files to provide context for code generation.
     */
    private List<AgentPromptBuilder.ReadFile> readRelevantFiles(
            AgentRun run, UUID projectId, String userEmail,
            List<CodeSearchResultMatchDTO> matches) {

        List<AgentPromptBuilder.ReadFile> files = new ArrayList<>();
        List<String> alreadyRead = new ArrayList<>();

        String currentPath = run.getCurrentPath();
        if (currentPath != null && !currentPath.isBlank()) {
            tryReadFile(projectId, currentPath, userEmail, files, alreadyRead);
        }

        for (CodeSearchResultMatchDTO match : matches) {
            if (files.size() >= MAX_FILES_TO_READ) break;
            String path = match.getPath();
            if (path != null && !path.isBlank()) {
                tryReadFile(projectId, path, userEmail, files, alreadyRead);
            }
        }

        return files;
    }

    private void tryReadFile(UUID projectId, String relativePath, String userEmail,
                              List<AgentPromptBuilder.ReadFile> accumulator,
                              List<String> alreadyRead) {
        if (alreadyRead.contains(relativePath)) return;
        alreadyRead.add(relativePath);

        try {
            FileContentResponseDTO dto = agentToolService.readFile(projectId, relativePath, userEmail);
            if (dto != null && dto.getContent() != null) {
                accumulator.add(new AgentPromptBuilder.ReadFile(relativePath, dto.getContent()));
            }
        } catch (Exception e) {
            log.debug("[AgentExec] Could not read file [{}]: {}", relativePath, e.getMessage());
        }
    }

    private void transitionToRunning(AgentRun run) {
        run.setStatus(AgentRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setRunVersion(run.getRunVersion() + 1);
        agentRunRepository.save(run);
        agentSseService.publishEvent(run, AgentSseEventType.RUN_STATUS, buildRunStatusPayload(run));
        log.info("[AgentExec] Run [{}] transitioned to RUNNING", run.getId());
    }

    private void failRun(UUID runId, String errorMessage) {
        agentRunRepository.findById(runId).ifPresent(run -> {
            if (run.getStatus() == AgentRunStatus.CANCELED) return;

            run.setStatus(AgentRunStatus.FAILED);
            run.setErrorMessage(errorMessage);
            run.setCompletedAt(Instant.now());
            run.setRunVersion(run.getRunVersion() + 1);
            agentRunRepository.save(run);

            agentSseService.publishEvent(run, AgentSseEventType.RUN_FAILED,
                    String.format("{\"runId\":\"%s\",\"error\":%s}", runId,
                            jsonString(errorMessage)));
            agentSseService.publishEvent(run, AgentSseEventType.RUN_STATUS, buildRunStatusPayload(run));
            agentSseService.completeStream(runId);
        });
    }

    /**
     * Persists an AgentStep record and broadcasts a step.created SSE event.
     */
    private AgentStep createStep(AgentRun run, int sequence, AgentStepType type,
                                  AgentStepStatus status, String title, String summary) {
        AgentStep step = AgentStep.builder()
                .run(run)
                .sequence(sequence)
                .type(type)
                .status(status)
                .title(title)
                .summary(summary)
                .startedAt(status == AgentStepStatus.RUNNING ? Instant.now() : null)
                .build();

        agentStepRepository.save(step);

        agentSseService.publishEvent(run, AgentSseEventType.STEP_CREATED, toStepJson(step, run));
        log.debug("[AgentExec] Step {} [{}] created for run [{}]", sequence, type, run.getId());
        return step;
    }

    /**
     * Completes an AgentStep record and broadcasts a step.updated SSE event.
     */
    private void completeStep(AgentRun run, AgentStep step, String summary) {
        step.setStatus(AgentStepStatus.COMPLETED);
        step.setSummary(summary);
        step.setCompletedAt(Instant.now());
        agentStepRepository.save(step);

        agentSseService.publishEvent(run, AgentSseEventType.STEP_UPDATED, toStepJson(step, run));
        log.debug("[AgentExec] Step [{}] completed: {}", step.getType(), truncate(summary, 80));
    }

    private String toStepJson(AgentStep step, AgentRun run) {
        return String.format(
                "{\"id\":\"%s\",\"runId\":\"%s\",\"sequence\":%d,\"type\":\"%s\"," +
                "\"status\":\"%s\",\"title\":%s,\"summary\":%s," +
                "\"startedAt\":%s,\"completedAt\":%s}",
                step.getId(), run.getId(), step.getSequence(),
                step.getType(), step.getStatus(),
                jsonString(step.getTitle()), jsonString(step.getSummary()),
                jsonInstant(step.getStartedAt()), jsonInstant(step.getCompletedAt()));
    }

    private String buildRunStatusPayload(AgentRun run) {
        return String.format("{\"runId\":\"%s\",\"status\":\"%s\",\"runVersion\":%d}",
                run.getId(), run.getStatus(), run.getRunVersion());
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                           .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String jsonInstant(Instant instant) {
        return instant == null ? "null" : "\"" + instant.toString() + "\"";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
