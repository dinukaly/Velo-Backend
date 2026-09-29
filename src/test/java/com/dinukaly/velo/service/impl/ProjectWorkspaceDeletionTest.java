package com.dinukaly.velo.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProjectWorkspaceDeletionTest {
    @TempDir Path root;

    @Test
    void deletesNonEmptyWorkspaceWithoutTouchingSiblingProject() throws Exception {
        Path workspace = root.resolve("project-delete");
        Files.createDirectories(workspace.resolve("src/nested"));
        Files.writeString(workspace.resolve("src/nested/index.js"), "hello");
        Path sibling = root.resolve("project-keep");
        Files.createDirectories(sibling);
        Files.writeString(sibling.resolve("index.js"), "keep");

        var storage = new FileStorageServiceImpl();
        storage.deleteProjectWorkspace(workspace);
        storage.deleteProjectWorkspace(workspace);

        assertFalse(Files.exists(workspace));
        assertEquals("keep", Files.readString(sibling.resolve("index.js")));
    }
}
