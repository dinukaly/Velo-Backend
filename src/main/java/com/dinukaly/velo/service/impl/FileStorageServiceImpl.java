package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    @Override
    public void createProjectWorkspace(Path projectPath) {
        try {
            Files.createDirectories(projectPath);
            log.info("Created project workspace at: {}", projectPath);
        } catch (IOException e) {
            log.error("Failed to create project workspace at: {}", projectPath, e);
            throw new UncheckedIOException("Could not create project workspace: " + projectPath, e);
        }
    }

    @Override
    public void createFile(Path path) {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.createFile(path);
        } catch (IOException e) {
            log.error("Failed to create file: {}", path, e);
            throw new UncheckedIOException("Could not create file: " + path, e);
        }
    }

    @Override
    public void createFolder(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("Failed to create folder: {}", path, e);
            throw new UncheckedIOException("Could not create folder: " + path, e);
        }

    }

    @Override
    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete: {}", path, e);
            throw new UncheckedIOException("Could not delete path: " + path, e);
        }

    }

    @Override
    public void rename(Path oldPath, Path newPath) {
        try {
            Files.move(oldPath, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to rename {} to {}", oldPath, newPath, e);
            throw new UncheckedIOException("Could not rename: " + oldPath, e);
        }
    }

    @Override
    public String readFile(Path path) {
        return "";
    }

    @Override
    public void writeFile(Path path, String content) {

    }
}
