package com.dinukaly.velo.dto;

import com.dinukaly.velo.entity.Role;
import lombok.Data;

@Data
public class AdminUpdateUserRequestDTO {
    private Role role;
    private Boolean enabled;
}
