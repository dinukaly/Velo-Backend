package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.service.FileStorageService;
import com.dinukaly.velo.util.FilePathResolver;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeletionAccessTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectDeletionService deletion = mock(ProjectDeletionService.class);
    private final ProjectServiceImpl ownerService = new ProjectServiceImpl(projects, users,
            mock(ModelMapper.class), mock(FileStorageService.class), mock(FilePathResolver.class), deletion);

    @Test
    void ownerAndAdminBothUseSharedCleanup() {
        User user = User.builder().email("owner@example.test").build();
        Project project = Project.builder().id(UUID.randomUUID()).owner(user).build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(projects.findByIdAndOwner(project.getId(), user)).thenReturn(Optional.of(project));
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        ownerService.deleteProject(project.getId(), user.getEmail());
        new AdminServiceImpl(users, projects, mock(SandboxRepository.class), deletion).deleteProject(project.getId());
        verify(deletion, times(2)).delete(project);
    }

    @Test
    void anotherUsersProjectNeverReachesCleanup() {
        User user = User.builder().email("other@example.test").build();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(NotFoundException.class, () -> ownerService.deleteProject(UUID.randomUUID(), user.getEmail()));
        verifyNoInteractions(deletion);
    }
}
