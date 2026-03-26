package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.FileNode;
import com.dinukaly.velo.repo.FileNodeRepository;
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

    private final FileNodeRepository fileNodeRepository;
    private final FilePathResolver filePathResolver;

    @Override
    public String getFileContent(UUID projectId, UUID fileId) {
        try {
            FileNode node = fileNodeRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

            // ensure AI reads only files within the project workspace
            if (!node.getProject().getId().equals(projectId)) {
                 log.warn("Unauthorized file access attempt: project {} tried to access file {}", projectId, fileId);
                 return "";
            }

            Path resolvedPath = filePathResolver.resolveNodePath(node);

            if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
                log.warn("File does not exist or is not a regular file: {}", resolvedPath);
                return "";
            }

            return Files.readString(resolvedPath);
        } catch (IOException e) {
            log.error("Failed to read file content for AI context: {}", fileId, e);
            return "";
        }
    }
}
