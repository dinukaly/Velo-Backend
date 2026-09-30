package com.dinukaly.velo.util;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import java.util.Map;
import java.util.List;

/** Portable Linux isolation controls; the project bind mount remains writable. */
public final class SandboxResourcePolicy {
    public static final long PID_LIMIT = 128;
    public static final Map<String, String> TMPFS = Map.of(
            "/tmp", "rw,noexec,nosuid,nodev,size=64m,mode=1777",
            "/home/node", "rw,noexec,nosuid,nodev,size=32m,uid=1000,gid=1000,mode=0700");

    private SandboxResourcePolicy() {}

    public static HostConfig apply(HostConfig config) {
        return config.withPidsLimit(PID_LIMIT)
                .withReadonlyRootfs(true)
                .withTmpFs(TMPFS)
                .withSecurityOpts(List.of("no-new-privileges:true"));
    }

    public static void requireHardened(InspectContainerResponse container) {
        HostConfig config = container == null ? null : container.getHostConfig();
        if (config == null || !Boolean.TRUE.equals(config.getReadonlyRootfs())
                || config.getPidsLimit() == null || config.getPidsLimit() <= 0
                || config.getPidsLimit() > PID_LIMIT
                || !TMPFS.equals(config.getTmpFs())
                || config.getSecurityOpts() == null
                || config.getSecurityOpts().stream().noneMatch(option ->
                    "no-new-privileges:true".equals(option) || "no-new-privileges".equals(option))
                || config.getSecurityOpts().stream().anyMatch(option -> option.contains("unconfined"))) {
            throw new SecurityException("Sandbox resource policy mismatch. Close and reopen the environment.");
        }
    }
}
