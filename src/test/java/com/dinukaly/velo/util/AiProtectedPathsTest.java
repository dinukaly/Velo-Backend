package com.dinukaly.velo.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiProtectedPathsTest {
    @Test
    void blocksSensitiveSegmentsWithEitherSeparatorAndCase() {
        for (String path : new String[]{".env", "x/.env.production", "X\\.AWS\\config",
                "x/.ssh/config", "x/.git/config", ".docker/config.json", "x/.kube/config",
                "x/.npmrc", "x/.netrc", "x/id_ed25519", "x/KEY.PFX", "x/credentials.json",
                "x/secrets/config.txt", "x/application-local.yml", "x/state.tfstate.backup",
                "../outside", "x/.env/../app.txt", "x.txt:stream"}) {
            assertTrue(AiProtectedPaths.isProtected(path), path);
        }
    }

    @Test
    void normalSourceAndDocumentationRemainAvailable() {
        for (String path : new String[]{"src/App.java", "docs/README.md", "package.json", "src\\main.ts"}) {
            assertFalse(AiProtectedPaths.isProtected(path), path);
        }
    }
}
