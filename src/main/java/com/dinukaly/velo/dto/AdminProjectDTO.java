package com.dinukaly.velo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminProjectDTO {
    private UUID id;
    private String name;
    private String description;
    private String language;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID ownerId;
    private String ownerName;
    private String ownerEmail;
}
