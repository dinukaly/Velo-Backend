package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.*;
import com.dinukaly.velo.entity.FileNode;
import com.dinukaly.velo.entity.FileType;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.repo.FileNodeRepository;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.FileService;
import com.dinukaly.velo.service.FileStorageService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    private final FileNodeRepository fileNodeRepository;
    private final ProjectRepository projectRepository;
    private final FilePathResolver filePathResolver;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public FileNodeResponseDTO createFile(CreateFileRequestDTO createFileRequestDTO, String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Project project = projectRepository.findById(createFileRequestDTO.getProjectId()).orElseThrow(() -> new RuntimeException("Project not found"));

        validateOwnership(project, user);
        log.info("Creating file: {} in project: {} for user: {}", createFileRequestDTO.getName(), project.getId(), email);
        FileNode parent = resolveParent(createFileRequestDTO.getParentId());
        FileNode fileNode = FileNode.builder()
                .name(createFileRequestDTO.getName())
                .type(FileType.FILE)
                .project(project)
                .parent(parent)
                .build();
        fileNodeRepository.save(fileNode);
        log.info("File created: {}", fileNode);

        Path path = filePathResolver.resolveNodePath(fileNode);
        fileStorageService.createFile(path);
        return  modelMapper.map(fileNode, FileNodeResponseDTO.class);
    }

    @Override
    public FileNodeResponseDTO createFolder(CreateFolderRequestDTO createFolderRequestDTO, String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Project project = projectRepository.findById(createFolderRequestDTO.getProjectId()).orElseThrow(() -> new RuntimeException("Project not found"));
        validateOwnership(project, user);
        log.info("Creating folder: {} in project: {} for user: {}", createFolderRequestDTO.getName(), project.getId(), email);
        FileNode parent = resolveParent(createFolderRequestDTO.getParentId());
        FileNode fileNode = FileNode.builder()
                .name(createFolderRequestDTO.getName())
                .type(FileType.FOLDER)
                .project(project)
                .parent(parent)
                .build();
        fileNodeRepository.save(fileNode);
        log.info("Folder created: {}", fileNode);
        Path path = filePathResolver.resolveNodePath(fileNode);
        fileStorageService.createFolder(path);
        return modelMapper.map(fileNode, FileNodeResponseDTO.class);
    }

    @Override
    public void delete(UUID nodeId, String email) {

    }

    @Override
    public FileNodeResponseDTO rename(UUID nodeId, String newName, String email) {
        return null;
    }

    @Override
    public List<FileNodeResponseDTO> getProjectTree(UUID projectId) {
        return List.of();
    }

    @Override
    public FileContentResponseDTO readFile(UUID nodeId) {
        return null;
    }

    @Override
    public void writeFile(WriteFileRequestDTO writeFileRequestDTO, String email) {

    }

    //validate ownership
    private void validateOwnership(Project project, User user) {
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }
    }

    private FileNode resolveParent(UUID parentId) {
        if (parentId == null) return null;
        return fileNodeRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Parent folder not found"));
    }
}
