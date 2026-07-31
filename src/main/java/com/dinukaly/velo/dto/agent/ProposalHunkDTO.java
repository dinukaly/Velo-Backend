package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.HunkDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** DTO for a single proposal hunk sent to the frontend review panel. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalHunkDTO {
    private UUID id;
    private int ordinal;
    private int originalStartLine;
    private int originalEndLine;
    private String originalContent;
    private String newContent;
    private String diffSnippet;
    private String changeGroupKey;
    private String label;
    private HunkDecision decision;
    private Instant decidedAt;
}
