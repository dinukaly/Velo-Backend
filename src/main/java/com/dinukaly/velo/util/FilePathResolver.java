package com.dinukaly.velo.util;

import com.dinukaly.velo.entity.Project;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
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

}
