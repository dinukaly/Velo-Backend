package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.FileNode;
import com.dinukaly.velo.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FileNodeRepository extends JpaRepository<FileNode, UUID> {
    List<FileNode> findByProject(Project project);

    List<FileNode> findByParent(FileNode parent);
}
