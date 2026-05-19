package com.dinukaly.velo.dto;

import com.dinukaly.velo.entity.Role;
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
public class AdminUserDTO {
    private UUID id;
    private String name;
    private String email;
    private Role role;
    private boolean enabled;
    private Instant createdAt;
    private long projectCount;
}
