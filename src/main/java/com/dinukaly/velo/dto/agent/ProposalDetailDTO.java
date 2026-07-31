package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.ProposalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full proposal detail DTO returned by GET /runs/{runId}/proposal. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalDetailDTO {
    private UUID id;
    private UUID runId;
    private ProposalStatus status;
    private String description;
    private int totalHunkCount;
    private int acceptedHunkCount;
    private int rejectedHunkCount;
    private int proposalSchemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ProposalFileDTO> files;
}
