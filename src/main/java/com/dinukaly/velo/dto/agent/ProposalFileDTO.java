package com.dinukaly.velo.dto.agent;

import com.dinukaly.velo.entity.FileChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** DTO for a single file change within a proposal. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalFileDTO {
    private UUID id;
    private String filePath;
    private String newFilePath;
    private FileChangeType changeType;
    private String rationale;
    private List<ProposalHunkDTO> hunks;
}
