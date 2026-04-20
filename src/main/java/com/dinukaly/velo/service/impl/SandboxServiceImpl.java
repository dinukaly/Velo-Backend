package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.service.SandboxService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxServiceImpl implements SandboxService {

    private static final String IMAGE = "node:18-alpine";
    private static final long MEMORY_LIMIT = 512 * 1024 * 1024L; // 512 MB
    private static final long NANO_CPU_LIMIT = 500_000_000L; // 0.5 CPU

    private final DockerClient dockerClient;

    @Value("${workspace.root}")
    private String workspaceRoot;

    @Override
    public String startContainer(String workspacePath) {
        log.info("Starting sandbox container for workspace: {}", workspacePath);

        //ensure the requested mount path is strictly inside the workspace root
        java.nio.file.Path baseRoot = java.nio.file.Paths.get(workspaceRoot).toAbsolutePath().normalize();
        java.nio.file.Path targetMount = java.nio.file.Paths.get(workspacePath).toAbsolutePath().normalize();

        if (!targetMount.startsWith(baseRoot)) {
            log.error("Security Sandbox Violation: Attempted to mount path outside workspace root. Target: {}, Root: {}", targetMount, baseRoot);
            throw new SecurityException("Invalid workspace path");
        }

        // Mount the host workspace into /workspace inside the container
        Bind bind = new Bind(targetMount.toString(), new Volume("/workspace"), AccessMode.rw);

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(bind)
                .withMemory(MEMORY_LIMIT)
                .withNanoCPUs(NANO_CPU_LIMIT)
                .withPrivileged(false)         // never run privileged
                .withCapDrop(com.github.dockerjava.api.model.Capability.ALL); // Drop all Linux capabilities

        CreateContainerResponse container = dockerClient
                .createContainerCmd(IMAGE)
                .withHostConfig(hostConfig)
                .withUser("1000")               // Run as non-root user
                .withWorkingDir("/workspace")
                .withCmd("sh")                  // default shell – keeps container alive via TTY
                .withTty(true)                  // allocate pseudo-TTY
                .withStdinOpen(true)
                .exec();

        String containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();
        log.info("Sandbox container started: {}", containerId);
        return containerId;
    }

    @Override
    public void stopContainer(String containerId) {
        log.info("Stopping sandbox container: {}", containerId);
        try {
            // Check if container exists
            try {
                dockerClient.inspectContainerCmd(containerId).exec();
            } catch (com.github.dockerjava.api.exception.NotFoundException e) {
                log.info("Container {} no longer exists, skipping cleanup", containerId);
                return;
            }

            // Stop and remove the container
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            log.info("Sandbox container stopped and removed: {}", containerId);
        } catch (Exception e) {
            log.warn("Error while stopping container {}: {}", containerId, e.getMessage());
        }
    }

    @Override
    public boolean isContainerAvailable(String containerId) {
        log.info("Checking availability for container: {}", containerId);
        try {
            InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
            if (Boolean.TRUE.equals(response.getState().getRunning())) {
                log.info("Container {} is already running", containerId);
                return true;
            } else {
                log.info("Container {} exists but is stopped. Starting it...", containerId);
                dockerClient.startContainerCmd(containerId).exec();
                return true;
            }
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("Container {} no longer exists", containerId);
            return false;
        } catch (Exception e) {
            log.warn("Error checking or starting container {}: {}", containerId, e.getMessage());
            return false;
        }
    }
}
