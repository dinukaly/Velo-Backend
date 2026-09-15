package com.dinukaly.velo.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSearchResultDTO {
    private String query;
    private List<CodeSearchResultMatchDTO> matches;
    private int totalMatches;
    private boolean truncated;
}
