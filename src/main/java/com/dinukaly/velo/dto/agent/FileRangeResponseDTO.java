package com.dinukaly.velo.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRangeResponseDTO {
    private String path;
    private String name;
    private int startLine;
    private int endLine;
    private int totalLines;
    private String content;
}
