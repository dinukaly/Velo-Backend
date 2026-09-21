package com.dinukaly.velo.service;

import com.dinukaly.velo.config.SandboxCleanupProperties;
import com.dinukaly.velo.entity.SandboxSession;
import com.dinukaly.velo.repo.SandboxRepository;
import com.dinukaly.velo.util.SandboxLifecycle;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SandboxReconcilerTest {
    private final DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
    private final SandboxRepository sessions = mock(SandboxRepository.class);
    private final SandboxCleanupProperties properties = new SandboxCleanupProperties();
    private final Map<String, String> labels = Map.of(SandboxLifecycle.DEPLOYMENT_LABEL, "velo-local");
    private final SandboxReconciler reconciler = new SandboxReconciler(docker, sessions, properties);

    private Container candidate(long age, Map<String, String> ownership) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("orphan");
        when(container.getCreated()).thenReturn(Instant.now().minusSeconds(age).getEpochSecond());
        when(container.getLabels()).thenReturn(ownership);
        when(docker.listContainersCmd().withShowAll(true).withLabelFilter(labels).exec())
                .thenReturn(List.of(container));
        when(docker.inspectContainerCmd("orphan").exec().getConfig().getLabels()).thenReturn(ownership);
        return container;
    }

    @Test void removesOnlyOldOwnedUnreferencedContainerWithoutVolumes() {
        candidate(3600, labels);
        reconciler.reconcile();
        verify(docker.removeContainerCmd("orphan").withForce(true).withRemoveVolumes(false)).exec();
    }

    @Test void retainsReferencedContainer() {
        candidate(3600, labels);
        when(sessions.existsByContainerId("orphan")).thenReturn(true);
        reconciler.reconcile();
        verify(docker, never()).removeContainerCmd(anyString());
    }

    @Test void retainsYoungContainer() {
        candidate(0, labels);
        reconciler.reconcile();
        verify(docker, never()).removeContainerCmd(anyString());
    }

    @Test void retainsOtherDeploymentsAndUnlabelledContainers() {
        candidate(3600, Map.of(SandboxLifecycle.DEPLOYMENT_LABEL, "other"));
        reconciler.reconcile();
        candidate(3600, Map.of());
        reconciler.reconcile();
        verify(docker, never()).removeContainerCmd(anyString());
    }

    @Test void retainsContainerWhileSessionTransactionIsUncommitted() {
        candidate(3600, labels);
        TransactionSynchronizationManager.initSynchronization();
        try {
            SandboxLifecycle.protectUntilCommit("orphan");
            reconciler.reconcile();
            verify(docker, never()).removeContainerCmd(anyString());
        } finally {
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(1));
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertFalse(SandboxLifecycle.isInFlight("orphan"));
    }

    @Test void deletesSessionOnlyForExplicitDockerNotFound() {
        when(docker.listContainersCmd().withShowAll(true).withLabelFilter(labels).exec()).thenReturn(List.of());
        SandboxSession session = SandboxSession.builder().containerId("missing")
                .createdAt(Instant.now().minusSeconds(3600)).build();
        when(sessions.findAll()).thenReturn(List.of(session));
        when(docker.inspectContainerCmd("missing").exec()).thenThrow(new NotFoundException("missing"));
        reconciler.reconcile();
        verify(sessions).delete(session);
    }

    @Test void preservesSessionOnDockerFailure() {
        when(docker.listContainersCmd().withShowAll(true).withLabelFilter(labels).exec()).thenReturn(List.of());
        SandboxSession session = SandboxSession.builder().containerId("existing")
                .createdAt(Instant.now().minusSeconds(3600)).build();
        when(sessions.findAll()).thenReturn(List.of(session));
        when(docker.inspectContainerCmd("existing").exec()).thenThrow(new IllegalStateException("offline"));
        reconciler.reconcile();
        verify(sessions, never()).delete(any());
    }

    @Test void databaseFailureAbortsContainerDeletion() {
        candidate(3600, labels);
        when(sessions.findAll()).thenThrow(new IllegalStateException("database offline"));
        reconciler.reconcile();
        verify(docker, never()).removeContainerCmd(anyString());
    }
}
