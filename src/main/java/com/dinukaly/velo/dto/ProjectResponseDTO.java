package com.dinukaly.velo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private String language;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;
}
