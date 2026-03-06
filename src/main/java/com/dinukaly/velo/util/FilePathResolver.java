package com.dinukaly.velo.util;

import com.dinukaly.velo.entity.FileNode;
import com.dinukaly.velo.entity.Project;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class FilePathResolver {
    private static final String WORKSPACE_ROOT = "C:/workspace";

    public Path getProjectPath(Project project) {
        return Paths.get(WORKSPACE_ROOT, "project-" + project.getId());
    }

    public Path resolveNodePath(FileNode node) {
        // collect names from node up to root
        List<String> parts = new ArrayList<>();
        FileNode current = node;
        while (current != null) {
            parts.add(current.getName());
            current = current.getParent();
        }

        // reverse so root comes first
        Collections.reverse(parts);

        // build path
        Path path = getProjectPath(node.getProject());
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path;
    }
}
