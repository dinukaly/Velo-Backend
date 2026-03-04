package com.dinukaly.velo.dto;

import lombok.Data;

@Data
public class CreateProjectRequestDTO {
    private String name;
    private String description;
    private String language;
}
