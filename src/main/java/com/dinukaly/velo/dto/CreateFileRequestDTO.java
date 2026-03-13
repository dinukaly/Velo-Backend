package com.dinukaly.velo.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateFileRequestDTO {
    private UUID projectId;
    private UUID parentId;
    private String name;
}
