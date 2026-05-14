package com.dinukaly.velo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileContentResponseDTO {
    private String path;
    private String name;
    private String content;
}
