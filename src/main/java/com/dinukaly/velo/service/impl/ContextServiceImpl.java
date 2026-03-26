package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.service.ContextService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContextServiceImpl implements ContextService {

    private final ProjectRepository projectRepository;
    private final FilePathResolver filePathResolver;

    @Override
    public String getFileContent(UUID projectId, String filePath) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            Path projectRoot = filePathResolver.getProjectWorkspacePath(project);
            Path resolvedPath = projectRoot.resolve(filePath).normalize();

            // ensure AI reads only files within the project workspace
            if (!resolvedPath.startsWith(projectRoot)) {
                log.warn("Path traversal attempt blocked: {}", filePath);
                return "";
            }

            if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
                log.warn("File does not exist or is not a regular file: {}", resolvedPath);
                return "";
            }

            return Files.readString(resolvedPath);
        } catch (IOException e) {
            log.error("Failed to read file content for AI context: {}", filePath, e);
            return "";
        }
    }
}
