package com.dinukaly.velo.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class WriteFileRequestDTO {
    private UUID nodeId;
    private String content;
}
