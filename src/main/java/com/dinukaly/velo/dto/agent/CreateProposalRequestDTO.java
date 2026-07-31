package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.FileChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Input DTO used internally by the agent execution pipeline to submit a structured
 * proposal for persistence. Not exposed directly as an HTTP request body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProposalRequestDTO {
    private String description;

    /** List of file changes that make up this proposal. */
    private List<FileChangeRequest> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileChangeRequest {
        private String filePath;
        private String newFilePath;
        private FileChangeType changeType;
        private String rationale;

        /** Full content — used for CREATE and full-replace. */
        private String fullContent;

        /** Hunk-level edits — used for MODIFY. */
        private List<HunkRequest> hunks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HunkRequest {
        private int originalStartLine;
        private int originalEndLine;
        private String originalContent;
        private String newContent;
        private String label;
        private String changeGroupKey;
    }
}
