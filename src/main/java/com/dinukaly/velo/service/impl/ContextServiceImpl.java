package com.dinukaly.velo.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.ContextService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContextServiceImpl implements ContextService {

    private static final long MAX_CONTEXT_BYTES = 200_000;
    private static final Set<String> IGNORED_SEGMENTS = Set.of(
            "node_modules",
            ".git",
            "build",
            "dist",
            "target",
            ".idea",
            ".vscode",
            "__pycache__",
            ".next",
            "out"
    );

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FilePathResolver filePathResolver;

    @Override
    public String getFileContent(UUID projectId, String filePath, String email) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }

        Project project = findOwnedProject(projectId, email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path resolvedPath = resolveAndValidate(root, filePath);

        if (isIgnored(filePath)) {
            log.warn("[AI Context] Ignored path requested for project {}: {}", projectId, filePath);
            return "";
        }

        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            log.warn("[AI Context] File does not exist or is not a regular file: {}", resolvedPath);
            return "";
        }

        try {
            if (Files.size(resolvedPath) > MAX_CONTEXT_BYTES) {
                log.warn("[AI Context] Skipping large file for project {}: {}", projectId, filePath);
                return "";
            }
            return Files.readString(resolvedPath);
        } catch (IOException e) {
            log.error("[AI Context] Failed to read file content: {}", filePath, e);
            return "";
        }
    }

    private Path resolveAndValidate(Path root, String filePath) {
        Path target = root.resolve(filePath).normalize();
        if (!target.startsWith(root)) {
            log.warn("[AI Context] Path traversal attempt blocked. root={} target={}", root, target);
            throw new BadRequestException("Invalid file path");
        }
        return target;
    }

    private boolean isIgnored(String filePath) {
        String normalized = filePath.replace("\\", "/");
        for (String segment : normalized.split("/")) {
            if (IGNORED_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private Project findOwnedProject(UUID projectId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new CustomAuthenticationException("Access denied to project: " + projectId);
        }
        return project;
    }
}
