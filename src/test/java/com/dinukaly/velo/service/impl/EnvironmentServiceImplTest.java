package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
}
