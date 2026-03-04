package com.dinukaly.velo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ProjectResponseDTO {
    private UUID id;
    private String name;
}
