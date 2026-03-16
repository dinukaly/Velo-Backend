package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.service.SandboxService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxServiceImpl implements SandboxService {

    private static final String IMAGE = "node:18-alpine";
    private static final long MEMORY_LIMIT = 512 * 1024 * 1024L; // 512 MB
    private static final long NANO_CPU_LIMIT = 500_000_000L; // 0.5 CPU

    private final DockerClient dockerClient;

    @Override
    public String startContainer(String workspacePath) {
        log.info("Starting sandbox container for workspace: {}", workspacePath);

        // Mount the host workspace into /workspace inside the container
        Bind bind = new Bind(workspacePath, new Volume("/workspace"), AccessMode.rw);

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(bind)
                .withMemory(MEMORY_LIMIT)
                .withNanoCPUs(NANO_CPU_LIMIT)
                .withPrivileged(false);         // never run privileged

        CreateContainerResponse container = dockerClient
                .createContainerCmd(IMAGE)
                .withHostConfig(hostConfig)
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
}
