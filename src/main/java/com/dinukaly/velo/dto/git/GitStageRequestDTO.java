package com.dinukaly.velo.dto.git;

import lombok.Data;

import java.util.List;

@Data
public class GitStageRequestDTO {
    private List<String> paths;
}
