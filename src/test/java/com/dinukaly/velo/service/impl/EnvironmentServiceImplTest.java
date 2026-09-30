package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.SandboxSession;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.SandboxRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.FileStorageService;
import com.dinukaly.velo.service.SandboxService;
import com.dinukaly.velo.util.FilePathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SandboxRepository sandboxRepository;

    @Mock
    private SandboxService sandboxService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FilePathResolver filePathResolver;

    private EnvironmentServiceImpl environmentService;

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentServiceImpl(
                projectRepository,
                userRepository,
                sandboxRepository,
                sandboxService,
                fileStorageService,
                filePathResolver);
    }

    @Test
    void prepareEnvironmentRejectsProjectNotOwnedByAuthenticatedUserBeforeSandboxActions() {
        String username = "attacker@example.com";
        UUID projectId = UUID.randomUUID();
        User authenticatedUser = User.builder()
                .id(UUID.randomUUID())
                .email(username)
                .build();
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(authenticatedUser));
        when(projectRepository.findByIdAndOwner(projectId, authenticatedUser)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> environmentService.prepareEnvironment(projectId, username));

        verify(projectRepository).findByIdAndOwner(projectId, authenticatedUser);
        verifyNoInteractions(sandboxRepository, sandboxService, fileStorageService, filePathResolver);
    }

    @Test
    void prepareEnvironmentReplacesSandboxThatFailsCurrentSecurityPolicy() {
        String username = "owner@example.com";
        UUID projectId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email(username).build();
        Project project = Project.builder().id(projectId).owner(owner).build();
        SandboxSession oldSession = SandboxSession.builder().containerId("old-container").project(project).user(owner).build();
        Path workspace = Path.of("workspace", "project-" + projectId);
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndOwner(projectId, owner)).thenReturn(Optional.of(project));
        when(sandboxRepository.findByProject(project)).thenReturn(Optional.of(oldSession));
        when(sandboxService.isContainerAvailable("old-container"))
                .thenThrow(new SecurityException("Sandbox network policy mismatch"));
        when(filePathResolver.getProjectWorkspacePath(project)).thenReturn(workspace);
        when(sandboxService.startContainer(workspace.toString())).thenReturn("new-container");

        var result = environmentService.prepareEnvironment(projectId, username);

        assertEquals("new-container", result.getContainerId());
        var order = inOrder(sandboxService, sandboxRepository);
        order.verify(sandboxService).stopContainer("old-container");
        order.verify(sandboxRepository).delete(oldSession);
        order.verify(sandboxRepository).flush();
        order.verify(sandboxService).startContainer(workspace.toString());
    }

    @Test
    void prepareEnvironmentKeepsSessionWhenOldContainerCannotBeRemoved() {
        String username = "owner@example.com";
        UUID projectId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email(username).build();
        Project project = Project.builder().id(projectId).owner(owner).build();
        SandboxSession oldSession = SandboxSession.builder().containerId("old-container").project(project).user(owner).build();
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(owner));
        when(projectRepository.findByIdAndOwner(projectId, owner)).thenReturn(Optional.of(project));
        when(sandboxRepository.findByProject(project)).thenReturn(Optional.of(oldSession));
        when(sandboxService.isContainerAvailable("old-container"))
                .thenThrow(new SecurityException("Sandbox network policy mismatch"));
        doThrow(new IllegalStateException("Docker unavailable"))
                .when(sandboxService).stopContainer("old-container");

        assertThrows(IllegalStateException.class,
                () -> environmentService.prepareEnvironment(projectId, username));
        verifyNoInteractions(fileStorageService, filePathResolver);
        org.mockito.Mockito.verify(sandboxRepository, org.mockito.Mockito.never()).delete(oldSession);
    }
}
