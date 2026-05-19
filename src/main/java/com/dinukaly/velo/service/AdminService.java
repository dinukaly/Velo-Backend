package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.AdminProjectDTO;
import com.dinukaly.velo.dto.AdminStatsDTO;
import com.dinukaly.velo.dto.AdminUpdateUserRequestDTO;
import com.dinukaly.velo.dto.AdminUserDTO;

import java.util.List;
import java.util.UUID;

public interface AdminService {
    AdminStatsDTO getStats();

    List<AdminUserDTO> listUsers();

    AdminUserDTO updateUser(UUID userId, AdminUpdateUserRequestDTO requestDTO, String adminEmail);

    List<AdminProjectDTO> listProjects();

    void deleteProject(UUID projectId);
}
