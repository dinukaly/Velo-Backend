package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.CreateProjectRequestDTO;
import com.dinukaly.velo.dto.ProjectResponseDTO;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    ProjectResponseDTO createProject(CreateProjectRequestDTO createProjectRequestDTO,String email);
    List<ProjectResponseDTO> listProjects(String email);
    void deleteProject(UUID projectId,String email);
}
