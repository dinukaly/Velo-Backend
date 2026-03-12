package com.dinukaly.velo.util;

import com.dinukaly.velo.entity.FileNode;
import com.dinukaly.velo.entity.Project;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class FilePathResolver {

    @Value("${workspace.root}")
    private String workspaceRoot;

    public Path getProjectWorkspacePath(UUID userId, UUID projectId) {
        return Paths.get(workspaceRoot)
                .resolve(userId.toString())
                .resolve("project-" + projectId);
    }

    /**
     *extracts IDs from the Project entity.
     */
    public Path getProjectWorkspacePath(Project project) {
        return getProjectWorkspacePath(
                project.getOwner().getId(),
                project.getId()
        );
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

        // build path starting from the project workspace
        Path path = getProjectWorkspacePath(node.getProject());
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path;
    }
}
