package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.*;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.FsService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FsServiceImpl implements FsService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FilePathResolver filePathResolver;


    @Override
    public List<FsNodeDTO> listDirectory(UUID projectId, String relativePath, String email) {
        Project project = findOwnedProject(projectId, email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path targetDir = resolveAndValidate(root, relativePath);

        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            log.warn("[FsService] listDirectory: path does not exist or is not a folder: {}", targetDir);
            return List.of();
        }

        List<FsNodeDTO> nodes = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();

                boolean isDir = Files.isDirectory(entry);
                // Relative path uses forward slashes for cross platform frontend compatibility
                String relPath = root.relativize(entry).toString().replace("\\", "/");

                nodes.add(FsNodeDTO.builder()
                        .name(name)
                        .path(relPath)
                        .type(isDir ? "FOLDER" : "FILE")
                        .children(null)   // null = lazy; frontend expands on click
                        .build());
            }
        } catch (IOException e) {
            log.error("[FsService] Failed to list directory: {}", targetDir, e);
            throw new UncheckedIOException("Could not list directory: " + relativePath, e);
        }

        // Folders first > alphabetical; files second > alphabetical
        nodes.sort(Comparator
                .comparingInt((FsNodeDTO n) -> "FOLDER".equals(n.getType()) ? 0 : 1)
                .thenComparing(FsNodeDTO::getName, String.CASE_INSENSITIVE_ORDER));

        return nodes;
    }

    @Override
    public FsNodeDTO createFile(FsCreateRequestDTO dto, String email) {
        Project project = findOwnedProject(dto.getProjectId(), email);
        Path root = filePathResolver.getProjectWorkspacePath(project);

        Path parentDir = resolveAndValidate(root, dto.getParentPath());
        Path filePath = parentDir.resolve(dto.getName()).normalize();
        guardTraversal(root, filePath);

        try {
            Files.createDirectories(parentDir);
            Files.createFile(filePath);
            log.info("[FsService] File created: {}", filePath);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("File already exists: " + dto.getName());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create file: " + dto.getName(), e);
        }

        return toNode(root, filePath, "FILE");
    }

    @Override
    public FsNodeDTO createFolder(FsCreateRequestDTO dto, String email) {
        Project project = findOwnedProject(dto.getProjectId(), email);
        Path root = filePathResolver.getProjectWorkspacePath(project);

        Path parentDir = resolveAndValidate(root, dto.getParentPath());
        Path folderPath = parentDir.resolve(dto.getName()).normalize();
        guardTraversal(root, folderPath);

        try {
            Files.createDirectories(folderPath);
            log.info("[FsService] Folder created: {}", folderPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create folder: " + dto.getName(), e);
        }

        return toNode(root, folderPath, "FOLDER");
    }

    @Override
    public void delete(UUID projectId, String relativePath, String email) {
        Project project = findOwnedProject(projectId, email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path target = resolveAndValidate(root, relativePath);

        try {
            deleteRecursively(target);
            log.info("[FsService] Deleted: {}", target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete: " + relativePath, e);
        }
    }

    @Override
    public FsNodeDTO rename(FsRenameRequestDTO dto, String email) {
        Project project = findOwnedProject(dto.getProjectId(), email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path oldPath = resolveAndValidate(root, dto.getPath());

        // only file name is changed but path remains the same
        Path newPath = oldPath.getParent().resolve(dto.getNewName()).normalize();
        guardTraversal(root, newPath);

        try {
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("[FsService] Renamed {} → {}", oldPath.getFileName(), newPath.getFileName());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rename: " + dto.getPath(), e);
        }

        String type = Files.isDirectory(newPath) ? "FOLDER" : "FILE";
        return toNode(root, newPath, type);
    }

    @Override
    public FileContentResponseDTO readFile(UUID projectId, String relativePath, String email) {
        Project project = findOwnedProject(projectId, email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path filePath = resolveAndValidate(root, relativePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.warn("[FsService] readFile: file not found: {}", filePath);
            return FileContentResponseDTO.builder()
                    .path(relativePath)
                    .name(filePath.getFileName().toString())
                    .content("")
                    .build();
        }

        try {
            String content = Files.readString(filePath);
            return FileContentResponseDTO.builder()
                    .path(relativePath)
                    .name(filePath.getFileName().toString())
                    .content(content)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file: " + relativePath, e);
        }
    }

    @Override
    public void writeFile(FsWriteRequestDTO dto, String email) {
        Project project = findOwnedProject(dto.getProjectId(), email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path filePath = resolveAndValidate(root, dto.getPath());

        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, dto.getContent() != null ? dto.getContent() : "");
            log.info("[FsService] File written: {}", filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write file: " + dto.getPath(), e);
        }
    }

    private Path resolveAndValidate(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return root;
        }
        Path resolved = root.resolve(relativePath).normalize();
        guardTraversal(root, resolved);
        return resolved;
    }

    private void guardTraversal(Path root, Path target) {
        if (!target.startsWith(root)) {
            log.warn("[FsService] Path traversal attempt blocked. root={} target={}", root, target);
            throw new CustomAuthenticationException("Invalid path: outside of project workspace");
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            log.warn("[FsService] deleteRecursively: path does not exist, skipping: {}", path);
            return;
        }
        if (Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    //normalize separators to forward slashes
    private FsNodeDTO toNode(Path root, Path absolutePath, String type) {
        String relPath = root.relativize(absolutePath).toString().replace("\\", "/");
        return FsNodeDTO.builder()
                .name(absolutePath.getFileName().toString())
                .path(relPath)
                .type(type)
                .children(null)
                .build();
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    private Project findOwnedProject(UUID projectId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
        Project project = findProject(projectId);
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new CustomAuthenticationException("Access denied to project: " + projectId);
        }
        return project;
    }
}
