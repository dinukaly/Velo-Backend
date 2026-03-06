package com.dinukaly.velo.util;

import com.dinukaly.velo.entity.FileNode;
import com.dinukaly.velo.entity.Project;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FilePathResolver {
    private static final String WORKSPACE_ROOT = "C:/workspace";

    public Path getProjectPath(Project project) {
        return Paths.get(WORKSPACE_ROOT, "project-" + project.getId());
    }

    public Path resolveNodePath(FileNode node) {
        Path path = getProjectPath(node.getProject());
        FileNode current = node;
        while (current != null) {
            path = path.resolve(current.getName());
            current = current.getParent();
        }
        return path;
    }
}
