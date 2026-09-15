package com.dinukaly.velo.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSearchResultMatchDTO {
    private String path;
    private int lineNumber;
    private String lineContent;
}
