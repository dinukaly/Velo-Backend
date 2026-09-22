package com.dinukaly.velo.util;

import java.util.Locale;
import java.util.Set;

/** Shared AI context/index policy. Applies to every path segment, on either OS. */
public final class AiProtectedPaths {
    private static final Set<String> NAMES = Set.of(".git", ".ssh", ".aws", ".azure", ".gcloud",
            ".kube", ".docker", ".npmrc", ".pypirc", ".netrc", "_netrc", ".git-credentials",
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", "kubeconfig",
            "application-local.properties", "application-local.yml", "application-local.yaml");
    private AiProtectedPaths() {}

    public static boolean isProtected(String path) {
        if (path == null) return false;
        for (String segment : path.replace('\\', '/').toLowerCase(Locale.ROOT).split("/")) {
            if (segment.equals("..") || segment.contains(":")) return true;
            if (NAMES.contains(segment) || segment.startsWith(".env")
                    || segment.startsWith(".ssh") || segment.startsWith("credentials")
                    || segment.startsWith("secrets") || segment.endsWith(".pem")
                    || segment.endsWith(".key") || segment.endsWith(".p12")
                    || segment.endsWith(".pfx") || segment.endsWith(".jks")
                    || segment.endsWith(".keystore") || segment.endsWith(".tfstate")
                    || segment.endsWith(".tfstate.backup")) return true;
        }
        return false;
    }
}
