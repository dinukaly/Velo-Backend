package com.dinukaly.velo.dto;

import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import lombok.Builder;
import lombok.Getter;


import java.util.UUID;


@Getter
@Builder
public class AuthDetailsDTO {

    private final UUID id;
    private final String email;
    private final String name;
    private final Role role;

    public static AuthDetailsDTO from(User user) {
        return AuthDetailsDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }
}
