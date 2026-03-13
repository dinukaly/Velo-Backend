package com.dinukaly.velo.dto;

import com.dinukaly.velo.entity.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileNodeResponseDTO {
    private UUID id;
    private String name;
    private FileType type;
    private UUID projectId;
    private UUID parentId;
    private List<FileNodeResponseDTO> children;
    private Instant createdAt;
    private Instant updatedAt;
}
