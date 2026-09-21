package com.dinukaly.velo.service;

import com.dinukaly.velo.config.SandboxCleanupProperties;
import com.dinukaly.velo.repo.SandboxRepository;
import com.dinukaly.velo.util.SandboxLifecycle;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sandbox.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SandboxReconciler {
    private final DockerClient docker;
    private final SandboxRepository sessions;
    private final SandboxCleanupProperties properties;

    // First pass follows startup; subsequent passes wait until the prior pass completes.
    @Scheduled(initialDelayString = "${sandbox.cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${sandbox.cleanup.interval-ms:300000}")
    public void reconcile() {
        try {
            // Abort the pass if either infrastructure is unavailable. Absence from a list
            // is never sufficient evidence to delete a database record.
            var containers = docker.listContainersCmd().withShowAll(true)
                    .withLabelFilter(Map.of(SandboxLifecycle.DEPLOYMENT_LABEL, properties.getDeploymentId()))
                    .exec();
            var records = sessions.findAll();
            long cutoff = Instant.now().minusSeconds(properties.getGraceSeconds()).getEpochSecond();
            for (var container : containers) {
                if (container.getLabels() == null
                        || !properties.getDeploymentId().equals(container.getLabels().get(SandboxLifecycle.DEPLOYMENT_LABEL))
                        || container.getCreated() == null || container.getCreated() > cutoff) continue;
                synchronized (SandboxLifecycle.MONITOR) {
                    String id = container.getId();
                    if (id == null || SandboxLifecycle.isInFlight(id) || sessions.existsByContainerId(id)) continue;
                    // Reinspect the exact target and verify ownership immediately before removal.
                    var current = docker.inspectContainerCmd(id).exec();
                    if (current.getConfig() == null || current.getConfig().getLabels() == null
                            || !properties.getDeploymentId().equals(current.getConfig().getLabels()
                                .get(SandboxLifecycle.DEPLOYMENT_LABEL))) continue;
                    docker.removeContainerCmd(id).withForce(true).withRemoveVolumes(false).exec();
                    log.info("Removed orphan sandbox {}", id);
                }
            }
            for (var session : records) {
                if (session.getCreatedAt() == null || session.getCreatedAt().getEpochSecond() > cutoff) continue;
                synchronized (SandboxLifecycle.MONITOR) {
                    try {
                        docker.inspectContainerCmd(session.getContainerId()).exec();
                    } catch (NotFoundException absent) {
                        sessions.delete(session);
                        log.info("Removed stale sandbox session {}", session.getId());
                    }
                }
            }
        } catch (Exception failure) {
            log.warn("Sandbox reconciliation deferred: {}", failure.getMessage());
        }
    }
}
