package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.CreateProjectRequestDTO;
import com.dinukaly.velo.dto.ProjectResponseDTO;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.FileStorageService;
import com.dinukaly.velo.service.ProjectService;
import com.dinukaly.velo.service.EnvironmentService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final FileStorageService fileStorageService;
    private final FilePathResolver filePathResolver;
    private final EnvironmentService environmentService;

    @Override
    @Transactional
    public ProjectResponseDTO createProject(CreateProjectRequestDTO createProjectRequestDTO, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("Creating project: {} for user: {}", createProjectRequestDTO, email);

        Project project = Project.builder()
                .name(createProjectRequestDTO.getName())
                .description(createProjectRequestDTO.getDescription())
                .language(createProjectRequestDTO.getLanguage())
                .owner(user)
                .build();

        // persist to DB first so the project gets its UUID assigned
        projectRepository.save(project);

        // create the workspace directory on disk: {workspaceRoot}/{userId}/project-{projectId}
        Path workspacePath = filePathResolver.getProjectWorkspacePath(project);
        fileStorageService.createProjectWorkspace(workspacePath);

        log.info("Project workspace created at: {}", workspacePath);

        return modelMapper.map(project, ProjectResponseDTO.class);
    }

    @Override
    public List<ProjectResponseDTO> listProjects(String email) {
        log.info("Listing projects for user: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Project> projects = projectRepository.findByOwner(user);
        return projects.stream()
                .map(project -> modelMapper.map(project, ProjectResponseDTO.class))
                .toList();
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Project project = projectRepository.findByIdAndOwner(projectId, user)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        // stop environment first
        environmentService.closeEnvironment(projectId, email);

        // delete workspace
        Path workspacePath = filePathResolver.getProjectWorkspacePath(project);
        fileStorageService.delete(workspacePath);

        // delete project
        projectRepository.delete(project);

        log.info("Project [{}] deleted", projectId);
    }

    @Override
    public ProjectResponseDTO getProjectById(UUID projectId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Project project = projectRepository.findByIdAndOwner(projectId, user)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return modelMapper.map(project, ProjectResponseDTO.class);
    }
}