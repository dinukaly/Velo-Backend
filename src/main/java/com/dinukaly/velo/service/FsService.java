package com.dinukaly.velo.service;

import com.dinukaly.velo.dto.*;

import java.util.List;
import java.util.UUID;

public interface FsService {

    List<FsNodeDTO> listDirectory(UUID projectId, String relativePath, String email);

    FsNodeDTO createFile(FsCreateRequestDTO dto, String email);

    FsNodeDTO createFolder(FsCreateRequestDTO dto, String email);

    void delete(UUID projectId, String relativePath, String email);

    FsNodeDTO rename(FsRenameRequestDTO dto, String email);

    FileContentResponseDTO readFile(UUID projectId, String relativePath);

    void writeFile(FsWriteRequestDTO dto, String email);

}
