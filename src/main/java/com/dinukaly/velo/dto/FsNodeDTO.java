package com.dinukaly.velo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FsNodeDTO {
    private String name;
    private String path;
    private String type;
    private List<FsNodeDTO> children;
}
