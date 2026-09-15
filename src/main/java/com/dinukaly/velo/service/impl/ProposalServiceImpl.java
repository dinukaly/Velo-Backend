package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.agent.*;
import com.dinukaly.velo.entity.*;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.service.AgentSseService;
import com.dinukaly.velo.service.ProposalService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of ProposalService.
 *
 * Responsibilities:
 * 1. Validate proposal input (paths, change types, hunk consistency).
 * 2. Compute and store base file hashes for later Safe-Apply conflict detection.
 * 3. Generate unified diff snippets for each hunk for the frontend diff viewer.
 * 4. Persist the proposal graph (AgentProposal → AgentProposalFiles → AgentProposalHunks).
 * 5. Emit SSE events so the frontend transitions into review mode.
 * 6. Handle per-hunk accept/reject with dependency-group cascade logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProposalServiceImpl implements ProposalService {

    private final AgentRunRepository agentRunRepository;
    private final AgentProposalRepository agentProposalRepository;
    private final AgentProposalFileRepository agentProposalFileRepository;
    private final AgentProposalHunkRepository agentProposalHunkRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final FilePathResolver filePathResolver;
    private final AgentSseService agentSseService;

    // -------------------------------------------------------------------------
    // createProposal
    // -------------------------------------------------------------------------

    /**
     * Creates and persists an AgentProposal from the agent execution pipeline output.
     *
     * Steps:
     * 1. Resolve the AgentRun — fail fast if not found or already has a proposal.
     * 2. Transition run status to WAITING_FOR_APPROVAL.
     * 3. Normalise file paths and compute base file hashes from disk.
     * 4. Build AgentProposalFile entities; generate unified diff snippets per hunk.
     * 5. Save the entire proposal graph in one transaction.
     * 6. Emit PROPOSAL_CREATED SSE event.
     */
    @Override
    @Transactional
    public ProposalDetailDTO createProposal(UUID runId, CreateProposalRequestDTO request) {
        AgentRun run = agentRunRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Agent run not found: " + runId));

        if (agentProposalRepository.existsByRun(run)) {
            throw new BadRequestException("A proposal already exists for run: " + runId);
        }

        // Build proposal root
        AgentProposal proposal = AgentProposal.builder()
                .run(run)
                .status(ProposalStatus.PENDING_REVIEW)
                .description(request.getDescription())
                .totalHunkCount(0)
                .acceptedHunkCount(0)
                .rejectedHunkCount(0)
                .proposalSchemaVersion(1)
                .build();

        agentProposalRepository.save(proposal);

        // Resolve workspace root for hash computation
        Path workspaceRoot = filePathResolver.getProjectWorkspacePath(run.getProject());

        int totalHunks = 0;
        List<AgentProposalFile> fileEntities = new ArrayList<>();

        for (CreateProposalRequestDTO.FileChangeRequest fileReq : request.getFiles()) {
            String filePath = normalizeProjectPath(fileReq.getFilePath());

            AgentProposalFile proposalFile = AgentProposalFile.builder()
                    .proposal(proposal)
                    .filePath(filePath)
                    .newFilePath(fileReq.getNewFilePath() != null
                            ? normalizeProjectPath(fileReq.getNewFilePath()) : null)
                    .changeType(fileReq.getChangeType())
                    .rationale(fileReq.getRationale())
                    .fullContent(fileReq.getFullContent())
                    .baseFileHash(computeBaseFileHash(workspaceRoot, filePath))
                    .build();

            agentProposalFileRepository.save(proposalFile);

            // Build hunks
            List<AgentProposalHunk> hunks = buildHunks(proposalFile, fileReq);
            agentProposalHunkRepository.saveAll(hunks);

            proposalFile.setHunks(hunks);
            fileEntities.add(proposalFile);
            totalHunks += hunks.size();
        }

        // Update total hunk count on proposal
        proposal.setTotalHunkCount(totalHunks);
        proposal.setFiles(fileEntities);
        agentProposalRepository.save(proposal);

        // Transition run to WAITING_FOR_APPROVAL
        run.setStatus(AgentRunStatus.WAITING_FOR_APPROVAL);
        run.setRunVersion(run.getRunVersion() + 1);
        agentRunRepository.save(run);

        // Emit SSE proposal.created event
        String payload = String.format(
                "{\"runId\":\"%s\",\"proposalId\":\"%s\",\"totalHunkCount\":%d}",
                runId, proposal.getId(), totalHunks);
        agentSseService.publishEvent(run, AgentSseEventType.PROPOSAL_CREATED, payload);

        // Emit SSE run.status event for WAITING_FOR_APPROVAL
        String statusPayload = String.format(
                "{\"runId\":\"%s\",\"status\":\"%s\",\"runVersion\":%d}",
                run.getId(), run.getStatus(), run.getRunVersion());
        agentSseService.publishEvent(run, AgentSseEventType.RUN_STATUS, statusPayload);

        log.info("[ProposalService] Proposal [{}] created for run [{}]: {} files, {} hunks",
                proposal.getId(), runId, fileEntities.size(), totalHunks);

        return toDetailDTO(proposal);
    }

    // -------------------------------------------------------------------------
    // getProposal
    // -------------------------------------------------------------------------

    /**
     * Retrieves the full proposal detail for a run, including all files and hunks.
     */
    @Override
    @Transactional(readOnly = true)
    public ProposalDetailDTO getProposal(UUID runId, String userEmail) {
        User user = resolveUser(userEmail);
        AgentRun run = agentRunRepository.findByIdAndUser(runId, user)
                .orElseThrow(() -> new NotFoundException("Agent run not found or access denied"));

        AgentProposal proposal = agentProposalRepository.findByRun(run)
                .orElseThrow(() -> new NotFoundException("No proposal found for run: " + runId));

        return toDetailDTO(proposal);
    }

    // -------------------------------------------------------------------------
    // decideHunk
    // -------------------------------------------------------------------------

    /**
     * Records the user's decision for a single hunk.
     *
     * If REJECTED and the hunk has a changeGroupKey, all other PENDING hunks in the
     * same group across the proposal are cascaded to SKIPPED.
     */
    @Override
    @Transactional
    public ProposalDetailDTO decideHunk(UUID hunkId, HunkDecision decision, String userEmail) {
        if (decision != HunkDecision.ACCEPTED && decision != HunkDecision.REJECTED) {
            throw new BadRequestException("Decision must be ACCEPTED or REJECTED");
        }

        User user = resolveUser(userEmail);

        AgentProposalHunk hunk = agentProposalHunkRepository.findById(hunkId)
                .orElseThrow(() -> new NotFoundException("Hunk not found: " + hunkId));

        AgentProposal proposal = hunk.getProposalFile().getProposal();

        // Validate user ownership via the run
        agentRunRepository.findByIdAndUser(proposal.getRun().getId(), user)
                .orElseThrow(() -> new NotFoundException("Access denied to this proposal"));

        if (hunk.getDecision() != HunkDecision.PENDING) {
            throw new BadRequestException("Hunk already decided: " + hunk.getDecision());
        }

        // Apply the decision
        hunk.setDecision(decision);
        hunk.setDecidedAt(Instant.now());
        agentProposalHunkRepository.save(hunk);

        // Cascade REJECTED to all other PENDING hunks in the same change group
        if (decision == HunkDecision.REJECTED && hunk.getChangeGroupKey() != null) {
            agentProposalHunkRepository.skipPendingInGroup(proposal.getId(), hunk.getChangeGroupKey());
            log.info("[ProposalService] Cascaded SKIPPED to group '{}' in proposal [{}]",
                    hunk.getChangeGroupKey(), proposal.getId());
        }

        // Refresh counts
        long accepted = agentProposalHunkRepository.countByProposalIdAndDecision(proposal.getId(), HunkDecision.ACCEPTED);
        long rejected = agentProposalHunkRepository.countByProposalIdAndDecision(proposal.getId(), HunkDecision.REJECTED);
        proposal.setAcceptedHunkCount((int) accepted);
        proposal.setRejectedHunkCount((int) rejected);
        agentProposalRepository.save(proposal);

        // Emit SSE hunk.updated event
        String payload = String.format(
                "{\"runId\":\"%s\",\"proposalId\":\"%s\",\"hunkId\":\"%s\",\"decision\":\"%s\"}",
                proposal.getRun().getId(), proposal.getId(), hunkId, decision);
        agentSseService.publishEvent(proposal.getRun(), AgentSseEventType.HUNK_UPDATED, payload);

        return toDetailDTO(proposal);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds AgentProposalHunk entities from a file request.
     * For CREATE / DELETE without explicit hunks, creates a single synthetic hunk.
     * Generates unified diff snippets for the frontend diff viewer.
     */
    private List<AgentProposalHunk> buildHunks(
            AgentProposalFile proposalFile,
            CreateProposalRequestDTO.FileChangeRequest fileReq) {

        List<AgentProposalHunk> hunks = new ArrayList<>();

        // CREATE and DELETE with full content generate a single synthetic hunk
        if (fileReq.getHunks() == null || fileReq.getHunks().isEmpty()) {
            String origContent = (fileReq.getChangeType() == FileChangeType.DELETE) ? fileReq.getFullContent() : "";
            String newContent = (fileReq.getChangeType() == FileChangeType.CREATE || fileReq.getChangeType() == FileChangeType.MODIFY)
                    ? fileReq.getFullContent() : "";

            hunks.add(AgentProposalHunk.builder()
                    .proposalFile(proposalFile)
                    .ordinal(0)
                    .originalStartLine(1)
                    .originalEndLine(lineCount(origContent))
                    .originalContent(origContent)
                    .newContent(newContent)
                    .diffSnippet(generateUnifiedDiffSnippet(fileReq.getFilePath(), origContent, newContent, 1))
                    .label(fileReq.getChangeType().name().toLowerCase() + " " + proposalFile.getFilePath())
                    .changeGroupKey(null)
                    .decision(HunkDecision.PENDING)
                    .build());

        } else {
            // MODIFY: individual hunk-level edits
            int ordinal = 0;
            for (CreateProposalRequestDTO.HunkRequest hunkReq : fileReq.getHunks()) {
                String diffSnippet = generateUnifiedDiffSnippet(
                        fileReq.getFilePath(),
                        hunkReq.getOriginalContent(),
                        hunkReq.getNewContent(),
                        hunkReq.getOriginalStartLine());

                hunks.add(AgentProposalHunk.builder()
                        .proposalFile(proposalFile)
                        .ordinal(ordinal++)
                        .originalStartLine(hunkReq.getOriginalStartLine())
                        .originalEndLine(hunkReq.getOriginalEndLine())
                        .originalContent(hunkReq.getOriginalContent())
                        .newContent(hunkReq.getNewContent())
                        .diffSnippet(diffSnippet)
                        .label(hunkReq.getLabel())
                        .changeGroupKey(hunkReq.getChangeGroupKey())
                        .decision(HunkDecision.PENDING)
                        .build());
            }
        }

        return hunks;
    }

    /**
     * Generates a minimal unified diff snippet for a hunk.
     * Format: @@ -startLine,originalLines +startLine,newLines @@ [label]
     */
    private String generateUnifiedDiffSnippet(String filePath, String original, String newContent, int startLine) {
        List<String> origLines = splitLines(original);
        List<String> newLines = splitLines(newContent);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("--- a/%s\n+++ b/%s\n", filePath, filePath));
        sb.append(String.format("@@ -%d,%d +%d,%d @@\n",
                startLine, origLines.size(), startLine, newLines.size()));

        for (String line : origLines) sb.append("-").append(line).append("\n");
        for (String line : newLines)  sb.append("+").append(line).append("\n");

        return sb.toString();
    }

    /**
     * Computes the SHA-256 hash of a file's current on-disk content.
     * Returns null if the file does not exist (new files).
     */
    private String computeBaseFileHash(Path workspaceRoot, String relativePath) {
        try {
            Path filePath = workspaceRoot.resolve(relativePath).normalize();
            if (!filePath.startsWith(workspaceRoot) || !Files.exists(filePath)) {
                return null;
            }
            byte[] content = Files.readAllBytes(filePath);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (IOException e) {
            log.warn("[ProposalService] Could not read file for hash: {}", relativePath);
            return null;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Normalises path separators to forward slashes and removes leading slash. */
    private String normalizeProjectPath(String path) {
        if (path == null) return null;
        return path.replace("\\", "/").replaceAll("^/+", "");
    }

    private int lineCount(String content) {
        if (content == null || content.isEmpty()) return 0;
        return content.split("\r?\n", -1).length;
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) return List.of();
        return Arrays.asList(content.split("\r?\n", -1));
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }

    // -------------------------------------------------------------------------
    // DTO Mapping
    // -------------------------------------------------------------------------

    private ProposalDetailDTO toDetailDTO(AgentProposal proposal) {
        List<ProposalFileDTO> fileDTOs = proposal.getFiles().stream()
                .map(this::toFileDTO)
                .collect(Collectors.toList());

        return ProposalDetailDTO.builder()
                .id(proposal.getId())
                .runId(proposal.getRun().getId())
                .status(proposal.getStatus())
                .description(proposal.getDescription())
                .totalHunkCount(proposal.getTotalHunkCount())
                .acceptedHunkCount(proposal.getAcceptedHunkCount())
                .rejectedHunkCount(proposal.getRejectedHunkCount())
                .proposalSchemaVersion(proposal.getProposalSchemaVersion())
                .createdAt(proposal.getCreatedAt())
                .updatedAt(proposal.getUpdatedAt())
                .files(fileDTOs)
                .build();
    }

    private ProposalFileDTO toFileDTO(AgentProposalFile file) {
        List<ProposalHunkDTO> hunkDTOs = file.getHunks().stream()
                .map(this::toHunkDTO)
                .collect(Collectors.toList());

        return ProposalFileDTO.builder()
                .id(file.getId())
                .filePath(file.getFilePath())
                .newFilePath(file.getNewFilePath())
                .changeType(file.getChangeType())
                .rationale(file.getRationale())
                .hunks(hunkDTOs)
                .build();
    }

    private ProposalHunkDTO toHunkDTO(AgentProposalHunk hunk) {
        return ProposalHunkDTO.builder()
                .id(hunk.getId())
                .ordinal(hunk.getOrdinal())
                .originalStartLine(hunk.getOriginalStartLine())
                .originalEndLine(hunk.getOriginalEndLine())
                .originalContent(hunk.getOriginalContent())
                .newContent(hunk.getNewContent())
                .diffSnippet(hunk.getDiffSnippet())
                .changeGroupKey(hunk.getChangeGroupKey())
                .label(hunk.getLabel())
                .decision(hunk.getDecision())
                .decidedAt(hunk.getDecidedAt())
                .build();
    }
}
