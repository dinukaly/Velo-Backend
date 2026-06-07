package com.dinukaly.velo.dto.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitFileChangeDTO {
    private String path;
    private String status;
}
