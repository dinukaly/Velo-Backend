package com.dinukaly.velo.dto;

import lombok.Data;

@Data
public class RegisterRequestDTO {
    private String email;
    private String name;
    private String password;
}
