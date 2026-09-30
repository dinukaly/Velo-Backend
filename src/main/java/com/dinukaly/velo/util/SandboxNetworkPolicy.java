package com.dinukaly.velo.util;

import com.github.dockerjava.api.command.InspectContainerResponse;

/** Network access is denied until a controlled egress mechanism is implemented. */
public final class SandboxNetworkPolicy {
    public static final String NETWORK_MODE = "none";

    private SandboxNetworkPolicy() {}

    public static void requireIsolated(InspectContainerResponse container) {
        if (container == null || container.getHostConfig() == null
                || !NETWORK_MODE.equals(container.getHostConfig().getNetworkMode())
                || container.getNetworkSettings() == null
                || container.getNetworkSettings().getNetworks() == null
                || container.getNetworkSettings().getNetworks().keySet().stream()
                    .anyMatch(network -> !NETWORK_MODE.equals(network))) {
            throw new SecurityException("Sandbox network policy mismatch. Close and reopen the environment.");
        }
    }
}
