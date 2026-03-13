package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.*;

import java.util.List;
import java.util.UUID;

public interface FileService {
    FileNodeResponseDTO createFile(CreateFileRequestDTO createFileRequestDTO, String email);

    FileNodeResponseDTO createFolder(CreateFolderRequestDTO createFolderRequestDTO, String email);

    void delete(UUID nodeId, String email);

    FileNodeResponseDTO rename(RenameRequestDTO renameRequestDTO, String newName);

    List<FileNodeResponseDTO> getProjectTree(UUID projectId);

    FileContentResponseDTO readFile(UUID nodeId);

    void writeFile(WriteFileRequestDTO writeFileRequestDTO, String email);
}
