package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.agent.CreateProposalRequestDTO;
import com.dinukaly.velo.entity.AgentRun;
import com.dinukaly.velo.entity.FileChangeType;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.service.AgentSseService;
import com.dinukaly.velo.util.AgentPromptBuilder;
import com.dinukaly.velo.util.FilePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalTargetValidationTest {
    @TempDir Path workspace;
    @Mock AgentRunRepository runs;
    @Mock AgentProposalRepository proposals;
    @Mock AgentProposalFileRepository proposalFiles;
    @Mock AgentProposalHunkRepository proposalHunks;
    @Mock UserRepository users;
    @Mock ProjectRepository projects;
    @Mock FilePathResolver paths;
    @Mock AgentSseService events;

    @Test
    void promptContainsNoConcreteExamplePath() {
        String instructions = new AgentPromptBuilder()
                .buildPrompt("Edit index.js", "index.js", null, List.of(), List.of())
                .systemInstructions();
        assertFalse(instructions.contains("src/com/example"));
        assertTrue(instructions.contains("actual project-relative path"));
    }

    @Test
    void missingModifyTargetIsRejectedBeforeReview() throws Exception {
        Files.writeString(workspace.resolve("index.js"), "console.log('ok');\n");
        UUID runId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        AgentRun run = AgentRun.builder().id(runId).project(project).build();
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(paths.getProjectWorkspacePath(project)).thenReturn(workspace);

        var file = CreateProposalRequestDTO.FileChangeRequest.builder()
                .filePath("src/com/example/index.js")
                .changeType(FileChangeType.MODIFY)
                .hunks(List.of(CreateProposalRequestDTO.HunkRequest.builder()
                        .originalStartLine(1).originalEndLine(1)
                        .originalContent("console.log('ok');")
                        .newContent("console.log('changed');").build()))
                .build();
        var request = CreateProposalRequestDTO.builder().description("Edit")
                .files(List.of(file)).build();
        var service = new ProposalServiceImpl(runs, proposals, proposalFiles, proposalHunks,
                users, projects, paths, events);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createProposal(runId, request));
        assertTrue(error.getMessage().contains("src/com/example/index.js"));
        verifyNoInteractions(proposalFiles, proposalHunks);
    }
}
