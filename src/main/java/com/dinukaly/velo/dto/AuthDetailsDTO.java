package com.dinukaly.velo.dto;

import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class AuthDetailsDTO {

    private final String email;
    private final String name;
    private final Role role;

    public static AuthDetailsDTO from(User user) {
        return AuthDetailsDTO.builder()
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }
}
