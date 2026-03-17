package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.EnvironmentResponseDTO;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.SandboxSession;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.SandboxRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.EnvironmentService;
import com.dinukaly.velo.service.FileStorageService;
import com.dinukaly.velo.service.SandboxService;
import com.dinukaly.velo.util.FilePathResolver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentServiceImpl implements EnvironmentService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SandboxRepository sandboxRepository;
    private final SandboxService sandboxService;
    private final FileStorageService fileStorageService;
    private final FilePathResolver filePathResolver;

    @Override
    @Transactional
    public EnvironmentResponseDTO prepareEnvironment(UUID projectId, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found or access denied: " + projectId));

        // check if this project already has a session
        Optional<SandboxSession> projectSession = sandboxRepository.findByProject(project);

        if (projectSession.isPresent()) {
            SandboxSession sandboxSession = projectSession.get();

            if (sandboxService.isContainerAvailable(sandboxSession.getContainerId())) {
                log.info("Reusing existing container {} for project {}",
                        sandboxSession.getContainerId(), projectId);

                return EnvironmentResponseDTO.builder()
                        .projectId(project.getId())
                        .containerId(sandboxSession.getContainerId())
                        .workspacePath("/workspace")
                        .build();
            } else {
                log.info("Container {} is no longer available. Recreating for project {}",
                        sandboxSession.getContainerId(), projectId);
                sandboxRepository.delete(sandboxSession);
            }
        }

        // Ensure workspace exist
        Path projectWorkspacePath = filePathResolver.getProjectWorkspacePath(project);
        fileStorageService.createProjectWorkspace(projectWorkspacePath);

        // Start container
        String containerId = sandboxService.startContainer(projectWorkspacePath.toString());

        // save session
        SandboxSession sandboxSession = SandboxSession.builder()
                .containerId(containerId)
                .project(project)
                .user(user)
                .build();

        sandboxRepository.save(sandboxSession);

        log.info("SandboxSession[{}] created for project [{}]", sandboxSession.getId(), projectId);
        return EnvironmentResponseDTO.builder()
                .projectId(project.getId())
                .containerId(sandboxSession.getContainerId())
                .workspacePath("/workspace")
                .build();
    }

    @Override
    @Transactional
    public void closeEnvironment(UUID projectId, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Project project = projectRepository.findByIdAndOwner(projectId, user)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found or access denied: " + projectId));

        sandboxRepository.findByProject(project).ifPresent(session -> {
            sandboxService.stopContainer(session.getContainerId());
            sandboxRepository.delete(session);

            log.info("Environment closed for project [{}]", projectId);
        });
    }
}
