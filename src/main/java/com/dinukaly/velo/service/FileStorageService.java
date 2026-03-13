package com.dinukaly.velo.service;

import java.nio.file.Path;

public interface FileStorageService {
    void createProjectWorkspace(Path projectPath);
    void createFile(Path path);
    void createFolder(Path path);
    void delete(Path path);
    void rename(Path oldPath, Path newPath);
    String readFile(Path path);
    void writeFile(Path path, String content);
}
