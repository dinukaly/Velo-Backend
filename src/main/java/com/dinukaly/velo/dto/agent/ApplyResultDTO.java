package com.dinukaly.velo.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Result DTO returned by the Safe-Apply operation.
 * Summarises which files were written, skipped, or failed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyResultDTO {

    /** UUID of the proposal that was applied. */
    private UUID proposalId;

    /** UUID of the agent run. */
    private UUID runId;

    /** Overall outcome of the apply operation. */
    private ApplyOutcome outcome;

    /** Human-readable summary shown in the UI after apply. */
    private String summary;

    /** Per-file results. */
    private List<ApplyFileResultDTO> fileResults;

    /** Number of files successfully written to disk. */
    private int filesApplied;

    /** Number of files skipped (all hunks rejected or no accepted hunks). */
    private int filesSkipped;

    /** Number of files that failed to apply due to conflicts or I/O errors. */
    private int filesFailed;

    public enum ApplyOutcome {
        /** All accepted hunks written successfully. */
        SUCCESS,
        /** Some files applied, some failed (partial success). */
        PARTIAL,
        /** Nothing was applied — conflicts or no accepted hunks. */
        NOTHING_TO_APPLY,
        /** Apply was aborted due to preflight validation failures. */
        PREFLIGHT_FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyFileResultDTO {
        private String filePath;
        private FileApplyStatus status;
        private String message;

        public enum FileApplyStatus {
            APPLIED, SKIPPED, CONFLICT, ERROR
        }
    }
}
