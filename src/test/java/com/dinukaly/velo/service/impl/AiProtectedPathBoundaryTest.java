package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.repo.es.CodeChunkRepository;
import com.dinukaly.velo.service.*;
import com.dinukaly.velo.util.FilePathResolver;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiProtectedPathBoundaryTest {
    @Test
    void agentReadsRejectProtectedAncestorsBeforeFilesystemAccess() {
        FsService fs = mock(FsService.class);
        var service = new AgentToolServiceImpl(fs, mock(GitService.class), mock(ProjectRepository.class),
                mock(UserRepository.class), mock(FilePathResolver.class));
        assertThrows(CustomAuthenticationException.class,
                () -> service.readFile(UUID.randomUUID(), "nested/.aws/config", "user@example.test"));
        assertThrows(CustomAuthenticationException.class,
                () -> service.readFileRange(UUID.randomUUID(), "nested/.ssh/config", 1, 5, "user@example.test"));
        verifyNoInteractions(fs);
    }

    @Test
    void chatAndSingleFileIndexRejectProtectedPaths() {
        var projects = mock(ProjectRepository.class);
        var users = mock(UserRepository.class);
        var resolver = mock(FilePathResolver.class);
        var chunks = mock(CodeChunkRepository.class);
        var chunker = mock(CodeChunkerService.class);
        var embeddings = mock(EmbeddingProviderService.class);
        UUID projectId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        Project project = new Project();
        project.setOwner(user);
        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(user));
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(resolver.getProjectWorkspacePath(project)).thenReturn(Path.of("target", "policy-test").toAbsolutePath());
        var context = new ContextServiceImpl(projects, users, resolver);
        assertEquals("", context.getFileContent(projectId, "nested/.aws/config", "user@example.test"));
        var index = new IndexManagementServiceImpl(projects, users, mock(ProjectIndexStateRepository.class),
                chunks, chunker, embeddings, resolver);
        index.indexSingleFile(projectId, "nested/.aws/config", "user@example.test");
        verifyNoInteractions(chunks, chunker, embeddings);
    }
}
