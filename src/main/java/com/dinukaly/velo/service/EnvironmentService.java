package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.EnvironmentResponseDTO;

import java.util.UUID;

public interface EnvironmentService {
    EnvironmentResponseDTO prepareEnvironment(UUID projectId, String username);
    void closeEnvironment(UUID projectId, String username);

}
