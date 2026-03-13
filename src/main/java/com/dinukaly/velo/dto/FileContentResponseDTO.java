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
public class FileContentResponseDTO {
    private UUID nodeId;
    private String name;
    private String content;
}
