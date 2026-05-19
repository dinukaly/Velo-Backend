package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.AdminProjectDTO;
import com.dinukaly.velo.dto.AdminStatsDTO;
import com.dinukaly.velo.dto.AdminUpdateUserRequestDTO;
import com.dinukaly.velo.dto.AdminUserDTO;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.Role;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.SandboxRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AdminService;
import com.dinukaly.velo.service.FileStorageService;
import com.dinukaly.velo.service.SandboxService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SandboxRepository sandboxRepository;
    private final SandboxService sandboxService;
    private final FileStorageService fileStorageService;
    private final FilePathResolver filePathResolver;

    @Override
    public AdminStatsDTO getStats() {
        return AdminStatsDTO.builder()
                .totalUsers(userRepository.count())
                .enabledUsers(userRepository.countByEnabled(true))
                .disabledUsers(userRepository.countByEnabled(false))
                .adminUsers(userRepository.countByRole(Role.ADMIN))
                .totalProjects(projectRepository.count())
                .activeSandboxes(sandboxRepository.count())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserDTO> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .map(this::toUserDTO)
                .toList();
    }

    @Override
    @Transactional
    public AdminUserDTO updateUser(UUID userId, AdminUpdateUserRequestDTO requestDTO, String adminEmail) {
        if (requestDTO.getRole() == null && requestDTO.getEnabled() == null) {
            throw new BadRequestException("Provide a role or enabled value to update");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        boolean isSelf = user.getEmail().equals(adminEmail);
        if (isSelf && requestDTO.getRole() == Role.USER) {
            throw new BadRequestException("You cannot remove your own admin role");
        }
        if (isSelf && Boolean.FALSE.equals(requestDTO.getEnabled())) {
            throw new BadRequestException("You cannot disable your own account");
        }

        if (requestDTO.getRole() != null) {
            user.setRole(requestDTO.getRole());
        }
        if (requestDTO.getEnabled() != null) {
            user.setEnabled(requestDTO.getEnabled());
        }

        User saved = userRepository.save(user);
        log.info("[Admin] User {} updated by {}", saved.getEmail(), adminEmail);
        return toUserDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminProjectDTO> listProjects() {
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(Project::getUpdatedAt).reversed())
                .map(this::toProjectDTO)
                .toList();
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        sandboxRepository.findByProject(project).ifPresent(session -> {
            sandboxService.stopContainer(session.getContainerId());
            sandboxRepository.delete(session);
        });

        Path workspacePath = filePathResolver.getProjectWorkspacePath(project);
        fileStorageService.delete(workspacePath);
        projectRepository.delete(project);
        log.info("[Admin] Project {} deleted", projectId);
    }

    private AdminUserDTO toUserDTO(User user) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .projectCount(projectRepository.countByOwner(user))
                .build();
    }

    private AdminProjectDTO toProjectDTO(Project project) {
        User owner = project.getOwner();
        return AdminProjectDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .language(project.getLanguage())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .ownerId(owner.getId())
                .ownerName(owner.getName())
                .ownerEmail(owner.getEmail())
                .build();
    }
}
