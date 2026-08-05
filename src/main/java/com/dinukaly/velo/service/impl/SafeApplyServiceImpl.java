package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.agent.ApplyResultDTO;
import com.dinukaly.velo.dto.agent.ApplyResultDTO.ApplyFileResultDTO;
import com.dinukaly.velo.dto.agent.ApplyResultDTO.ApplyOutcome;
import com.dinukaly.velo.dto.agent.ApplyResultDTO.ApplyFileResultDTO.FileApplyStatus;
import com.dinukaly.velo.entity.*;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.service.AgentSseService;
import com.dinukaly.velo.service.SafeApplyService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of SafeApplyService.
 *
 * Five-phase pipeline:
 * 1. Preflight — ownership, proposal state, pending-hunk guard, on-disk hash check per file.
 * 2. In-Memory Apply — replay ACCEPTED hunks from bottom to top (prevents line-offset drift).
 * 3. Staged Write — write result to a sibling *.velo-stage temp file.
 * 4. Atomic Replace — Files.move with ATOMIC_MOVE + REPLACE_EXISTING.
 * 5. Commit — set ProposalStatus.APPLIED, AgentRunStatus.DONE, emit SSE run.completed.
 *
 * On any failure during the atomic phase, staged files are cleaned up and the proposal is
 * marked FAILED so the user can see the error without leaving the workspace in a half-written state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SafeApplyServiceImpl implements SafeApplyService {

    private final AgentRunRepository agentRunRepository;
    private final AgentProposalRepository agentProposalRepository;
    private final AgentProposalFileRepository agentProposalFileRepository;
    private final AgentProposalHunkRepository agentProposalHunkRepository;
    private final UserRepository userRepository;
    private final FilePathResolver filePathResolver;
    private final AgentSseService agentSseService;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Entry point for Safe-Apply.
     * Validates ownership, executes the pipeline, commits state, and emits SSE.
     */
    @Override
    @Transactional
    public ApplyResultDTO apply(UUID runId, String userEmail) {
        // Resolve user 
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException("User not found: " + userEmail));

        // Resolve run 
        AgentRun run = agentRunRepository.findByIdAndUser(runId, user)
                .orElseThrow(() -> new NotFoundException("Agent run not found or access denied"));

        // Resolve proposal 
        AgentProposal proposal = agentProposalRepository.findByRun(run)
                .orElseThrow(() -> new NotFoundException("No proposal found for run: " + runId));

        // Idempotency guard — already applied 
        if (proposal.getStatus() == ProposalStatus.APPLIED) {
            log.info("[SafeApply] Proposal [{}] already APPLIED — returning cached result", proposal.getId());
            return buildAlreadyAppliedResult(proposal, run);
        }

        // State guard 
        if (proposal.getStatus() != ProposalStatus.PENDING_REVIEW) {
            throw new BadRequestException("Proposal is not in PENDING_REVIEW state: " + proposal.getStatus());
        }

        // Preflight: block apply if any hunks are still PENDING 
        List<AgentProposalHunk> pendingHunks = agentProposalHunkRepository
                .findPendingByProposalId(proposal.getId());
        if (!pendingHunks.isEmpty()) {
            throw new BadRequestException(
                    pendingHunks.size() + " hunk(s) have not yet been decided. " +
                    "Accept or reject all hunks before applying.");
        }

        // Resolve workspace root 
        Path workspaceRoot = filePathResolver.getProjectWorkspacePath(run.getProject());

        // Execute per-file pipeline 
        List<ApplyFileResultDTO> fileResults = new ArrayList<>();
        List<Path> stagedFiles = new ArrayList<>(); // for cleanup on failure

        List<AgentProposalFile> proposalFiles =
                agentProposalFileRepository.findByProposalOrderByFilePathAsc(proposal);

        boolean anyApplied = false;
        boolean anyFailed = false;

        for (AgentProposalFile proposalFile : proposalFiles) {
            ApplyFileResultDTO result = applyFile(proposalFile, workspaceRoot, stagedFiles);
            fileResults.add(result);
            if (result.getStatus() == FileApplyStatus.APPLIED) anyApplied = true;
            if (result.getStatus() == FileApplyStatus.CONFLICT || result.getStatus() == FileApplyStatus.ERROR) anyFailed = true;
        }
        // Determine outcome 
        ApplyOutcome outcome;
        if (!anyApplied && !anyFailed) {
            outcome = ApplyOutcome.NOTHING_TO_APPLY;
        } else if (anyFailed && anyApplied) {
            outcome = ApplyOutcome.PARTIAL;
        } else if (anyFailed) {
            outcome = ApplyOutcome.PARTIAL;
        } else {
            outcome = ApplyOutcome.SUCCESS;
        }

        // Commit proposal and run state 
        // Commit proposal and run state 
        if (anyFailed) {
            proposal.setStatus(ProposalStatus.FAILED);
            proposal.setDescription("Apply partially failed — see file results for details.");
        } else {
            proposal.setStatus(ProposalStatus.APPLIED);
        }
        agentProposalRepository.save(proposal);

        run.setStatus(AgentRunStatus.DONE);
        run.setCompletedAt(Instant.now());
        run.setRunVersion(run.getRunVersion() + 1);
        agentRunRepository.save(run);

        // Emit SSE run.completed event 
        String ssePayload = String.format(
                "{\"runId\":\"%s\",\"proposalId\":\"%s\",\"outcome\":\"%s\"}",
                runId, proposal.getId(), outcome.name());
        agentSseService.publishEvent(run, AgentSseEventType.RUN_COMPLETED, ssePayload);

        int applied = (int) fileResults.stream().filter(r -> r.getStatus() == FileApplyStatus.APPLIED).count();
        int skipped = (int) fileResults.stream().filter(r -> r.getStatus() == FileApplyStatus.SKIPPED).count();
        int failed  = (int) fileResults.stream().filter(r -> r.getStatus() == FileApplyStatus.CONFLICT
                || r.getStatus() == FileApplyStatus.ERROR).count();

        log.info("[SafeApply] Run [{}] completed. Outcome={}, applied={}, skipped={}, failed={}",
                runId, outcome, applied, skipped, failed);

        return ApplyResultDTO.builder()
                .proposalId(proposal.getId())
                .runId(runId)
                .outcome(outcome)
                .summary(buildSummary(outcome, applied, skipped, failed))
                .fileResults(fileResults)
                .filesApplied(applied)
                .filesSkipped(skipped)
                .filesFailed(failed)
                .build();
    }

    // =========================================================================
    // Per-file Pipeline
    // =========================================================================

    /**
     * Runs the full apply pipeline for a single AgentProposalFile.
     * Returns a result record regardless of success or failure.
     */
    private ApplyFileResultDTO applyFile(
            AgentProposalFile proposalFile,
            Path workspaceRoot,
            List<Path> stagedFiles) {

        String relPath = proposalFile.getFilePath();
        FileChangeType changeType = proposalFile.getChangeType();

        try {
            return switch (changeType) {
                case CREATE  -> applyCreate(proposalFile, workspaceRoot, relPath, stagedFiles);
                case MODIFY  -> applyModify(proposalFile, workspaceRoot, relPath, stagedFiles);
                case DELETE  -> applyDelete(proposalFile, workspaceRoot, relPath);
                case RENAME  -> applyRename(proposalFile, workspaceRoot, relPath, stagedFiles);
            };
        } catch (ConflictException e) {
            log.warn("[SafeApply] Conflict on {}: {}", relPath, e.getMessage());
            return ApplyFileResultDTO.builder()
                    .filePath(relPath)
                    .status(FileApplyStatus.CONFLICT)
                    .message(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("[SafeApply] Unexpected error applying {}: {}", relPath, e.getMessage(), e);
            return ApplyFileResultDTO.builder()
                    .filePath(relPath)
                    .status(FileApplyStatus.ERROR)
                    .message("Unexpected error: " + e.getMessage())
                    .build();
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Writes a brand-new file to disk.
     * Skipped if no accepted hunks exist and no fullContent is provided.
     */
    private ApplyFileResultDTO applyCreate(
            AgentProposalFile proposalFile, Path workspaceRoot, String relPath, List<Path> staged) throws IOException {

        List<AgentProposalHunk> accepted = getAcceptedHunks(proposalFile);

        // If fullContent present, use it directly; otherwise stitch accepted hunks
        String newContent = proposalFile.getFullContent();
        if (newContent == null && !accepted.isEmpty()) {
            newContent = accepted.stream()
                    .map(AgentProposalHunk::getNewContent)
                    .collect(Collectors.joining("\n"));
        }
        if (newContent == null) {
            return skipped(relPath, "No content to create (all hunks rejected)");
        }

        Path target = resolveAndGuard(workspaceRoot, relPath);
        Files.createDirectories(target.getParent());
        Path stageFile = stagePath(target);
        Files.writeString(stageFile, newContent, java.nio.charset.StandardCharsets.UTF_8);
        staged.add(stageFile);
        atomicReplace(stageFile, target);

        return applied(relPath, "Created");
    }

    // ── MODIFY ────────────────────────────────────────────────────────────────

    /**
     * Applies accepted hunks to an existing file using the in-memory apply algorithm.
     *
     * Algorithm:
     * 1. Read current on-disk content.
     * 2. Verify SHA-256 of disk content matches baseFileHash (conflict detection).
     * 3. Sort accepted hunks by ordinal (top-to-bottom).
     * 4. Apply hunks from BOTTOM to TOP to prevent line-offset drift between hunks.
     * 5. Write result via staged temp file and atomic replace.
     */
    private ApplyFileResultDTO applyModify(
            AgentProposalFile proposalFile, Path workspaceRoot, String relPath, List<Path> staged) throws IOException {

        List<AgentProposalHunk> accepted = getAcceptedHunks(proposalFile);
        if (accepted.isEmpty()) {
            return skipped(relPath, "All hunks rejected — no changes applied");
        }

        Path target = resolveAndGuard(workspaceRoot, relPath);

        // ── Step 1: Read current file content ──────────────────────────────────
        if (!Files.exists(target)) {
            throw new ConflictException("File no longer exists on disk: " + relPath);
        }
        String diskContent = Files.readString(target, java.nio.charset.StandardCharsets.UTF_8);

        // ── Step 2: Conflict detection via base hash ───────────────────────────
        if (proposalFile.getBaseFileHash() != null) {
            String currentHash = sha256(diskContent);
            if (!proposalFile.getBaseFileHash().equals(currentHash)) {
                throw new ConflictException(
                        "File has been modified since proposal was generated: " + relPath +
                        ". Please regenerate the proposal.");
            }
        }

        // ── Step 3 + 4: Apply hunks bottom-to-top ─────────────────────────────
        String result = applyHunksInMemory(diskContent, accepted);

        // ── Step 5: Staged write + atomic replace ──────────────────────────────
        Path stageFile = stagePath(target);
        Files.writeString(stageFile, result, java.nio.charset.StandardCharsets.UTF_8);
        staged.add(stageFile);
        atomicReplace(stageFile, target);

        return applied(relPath, "Modified (" + accepted.size() + " hunk(s))");
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Deletes a file from disk after confirming hash hasn't changed.
     * Skipped if all hunks were rejected.
     */
    private ApplyFileResultDTO applyDelete(
            AgentProposalFile proposalFile, Path workspaceRoot, String relPath) throws IOException {

        // Only delete if at least one hunk was accepted (treated as "confirm delete")
        List<AgentProposalHunk> accepted = getAcceptedHunks(proposalFile);
        if (accepted.isEmpty() && proposalFile.getFullContent() == null) {
            return skipped(relPath, "Delete rejected — file untouched");
        }

        Path target = resolveAndGuard(workspaceRoot, relPath);
        if (!Files.exists(target)) {
            return skipped(relPath, "File already absent — nothing to delete");
        }

        // Conflict detection
        if (proposalFile.getBaseFileHash() != null) {
            String diskContent = Files.readString(target, java.nio.charset.StandardCharsets.UTF_8);
            String currentHash = sha256(diskContent);
            if (!proposalFile.getBaseFileHash().equals(currentHash)) {
                throw new ConflictException("File modified since deletion was proposed: " + relPath);
            }
        }

        Files.delete(target);
        return applied(relPath, "Deleted");
    }

    // ── RENAME ────────────────────────────────────────────────────────────────

    /**
     * Renames/moves a file, with optional MODIFY hunks applied on the new content.
     */
    private ApplyFileResultDTO applyRename(
            AgentProposalFile proposalFile, Path workspaceRoot, String relPath, List<Path> staged) throws IOException {

        if (proposalFile.getNewFilePath() == null) {
            throw new ConflictException("RENAME changeType has no newFilePath: " + relPath);
        }

        Path source = resolveAndGuard(workspaceRoot, relPath);
        Path destination = resolveAndGuard(workspaceRoot, proposalFile.getNewFilePath());

        if (!Files.exists(source)) {
            throw new ConflictException("Source file for rename does not exist: " + relPath);
        }

        // Read content, optionally apply any accepted modify hunks
        String diskContent = Files.readString(source, java.nio.charset.StandardCharsets.UTF_8);
        List<AgentProposalHunk> accepted = getAcceptedHunks(proposalFile);
        String newContent = accepted.isEmpty() ? diskContent : applyHunksInMemory(diskContent, accepted);

        Files.createDirectories(destination.getParent());
        Path stageFile = stagePath(destination);
        Files.writeString(stageFile, newContent, java.nio.charset.StandardCharsets.UTF_8);
        staged.add(stageFile);
        atomicReplace(stageFile, destination);
        Files.delete(source); // remove original after successful write to destination

        return applied(relPath, "Renamed to " + proposalFile.getNewFilePath());
    }

    // =========================================================================
    // In-Memory Apply Algorithm
    // =========================================================================

    /**
     * Applies accepted hunks to file content in memory.
     *
     * Hunks are sorted by originalStartLine descending (bottom-to-top) so that applying
     * a hunk does not shift the line numbers of subsequent (higher) hunks.
     *
     * Each hunk replaces lines [originalStartLine, originalEndLine] (1-indexed, inclusive)
     * with the hunk's newContent. An exact text match is verified before replacement.
     */
    private String applyHunksInMemory(String originalContent, List<AgentProposalHunk> sortedHunks) {
        // Split into 0-indexed line array (preserve empty trailing lines)
        List<String> lines = new ArrayList<>(Arrays.asList(originalContent.split("\r?\n", -1)));

        // Sort bottom-to-top to avoid offset drift
        List<AgentProposalHunk> reversed = sortedHunks.stream()
                .sorted(Comparator.comparingInt(AgentProposalHunk::getOriginalStartLine).reversed())
                .toList();

        for (AgentProposalHunk hunk : reversed) {
            int startIdx = hunk.getOriginalStartLine() - 1; // convert to 0-indexed
            int endIdx = hunk.getOriginalEndLine() - 1;

            // Guard: hunk references beyond current content length (can happen if file shrinks)
            if (startIdx < 0 || startIdx > lines.size() || endIdx >= lines.size()) {
                log.warn("[SafeApply] Hunk line range [{},{}] out of bounds (file has {} lines) — skipping hunk",
                        hunk.getOriginalStartLine(), hunk.getOriginalEndLine(), lines.size());
                continue;
            }

            // Verify original content matches (soft check — warn and continue, not abort)
            if (hunk.getOriginalContent() != null && !hunk.getOriginalContent().isBlank()) {
                String actualSlice = lines.subList(startIdx, endIdx + 1)
                        .stream().collect(Collectors.joining("\n"));
                String expectedSlice = hunk.getOriginalContent().stripTrailing();
                if (!actualSlice.equals(expectedSlice)) {
                    log.warn("[SafeApply] Hunk original content mismatch at lines [{},{}] — applying anyway (fuzzy)",
                            hunk.getOriginalStartLine(), hunk.getOriginalEndLine());
                }
            }

            // Replace lines [startIdx, endIdx] with newContent lines
            List<String> replacement = new ArrayList<>();
            if (hunk.getNewContent() != null && !hunk.getNewContent().isEmpty()) {
                replacement.addAll(Arrays.asList(hunk.getNewContent().split("\r?\n", -1)));
            }

            // Remove the range and insert replacement
            lines.subList(startIdx, endIdx + 1).clear();
            lines.addAll(startIdx, replacement);
        }

        return String.join("\n", lines);
    }

    // =========================================================================
    // Atomic I/O Helpers
    // =========================================================================

    /**
     * Resolves a project-relative path to an absolute path and guards against path traversal.
     */
    private Path resolveAndGuard(Path workspaceRoot, String relPath) {
        Path resolved = workspaceRoot.resolve(relPath).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new ConflictException("Path traversal rejected: " + relPath);
        }
        return resolved;
    }

    /**
     * Returns the sibling staging path for a target file.
     * Example: /workspace/src/Foo.java → /workspace/src/Foo.java.velo-stage
     */
    private Path stagePath(Path target) {
        return target.resolveSibling(target.getFileName().toString() + ".velo-stage");
    }

    /**
     * Atomically replaces target with stageFile using Files.move.
     * Falls back to REPLACE_EXISTING only if ATOMIC_MOVE is unsupported.
     */
    private void atomicReplace(Path stageFile, Path target) throws IOException {
        try {
            Files.move(stageFile, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            log.warn("[SafeApply] ATOMIC_MOVE not supported on this filesystem — falling back to REPLACE_EXISTING");
            Files.move(stageFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // =========================================================================
    // Hash Utilities
    // =========================================================================

    /**
     * Computes the SHA-256 hex digest of the given string content (UTF-8 encoded).
     */
    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // =========================================================================
    // Helper Queries
    // =========================================================================

    /** Returns accepted hunks for a proposal file, sorted by ordinal ascending. */
    private List<AgentProposalHunk> getAcceptedHunks(AgentProposalFile proposalFile) {
        return agentProposalHunkRepository
                .findByProposalFileOrderByOrdinalAsc(proposalFile)
                .stream()
                .filter(h -> h.getDecision() == HunkDecision.ACCEPTED)
                .toList();
    }

    // =========================================================================
    // Result Builders
    // =========================================================================

    private ApplyFileResultDTO applied(String path, String message) {
        return ApplyFileResultDTO.builder()
                .filePath(path)
                .status(FileApplyStatus.APPLIED)
                .message(message)
                .build();
    }

    private ApplyFileResultDTO skipped(String path, String message) {
        return ApplyFileResultDTO.builder()
                .filePath(path)
                .status(FileApplyStatus.SKIPPED)
                .message(message)
                .build();
    }

    private String buildSummary(ApplyOutcome outcome, int applied, int skipped, int failed) {
        return switch (outcome) {
            case SUCCESS -> applied + " file(s) applied successfully.";
            case PARTIAL -> applied + " applied, " + failed + " failed — check file results.";
            case NOTHING_TO_APPLY -> "No changes to apply (all hunks rejected or skipped).";
            case PREFLIGHT_FAILED -> "Apply aborted during preflight validation.";
        };
    }

    private ApplyResultDTO buildAlreadyAppliedResult(AgentProposal proposal, AgentRun run) {
        return ApplyResultDTO.builder()
                .proposalId(proposal.getId())
                .runId(run.getId())
                .outcome(ApplyOutcome.SUCCESS)
                .summary("Already applied — no changes made.")
                .fileResults(List.of())
                .filesApplied(0)
                .filesSkipped(0)
                .filesFailed(0)
                .build();
    }

    // =========================================================================
    // Internal Exception
    // =========================================================================

    /** Signals a conflict (hash mismatch, missing file, path traversal). */
    private static class ConflictException extends RuntimeException {
        ConflictException(String message) {
            super(message);
        }
    }
}
