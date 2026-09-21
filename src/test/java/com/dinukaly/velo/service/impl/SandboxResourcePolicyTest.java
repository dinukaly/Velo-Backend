package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.util.SandboxResourcePolicy;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SandboxResourcePolicyTest {
    @Test
    void acceptsPolicyAndRejectsMissingOrRelaxedControls() {
        InspectContainerResponse inspected = mock(InspectContainerResponse.class);
        HostConfig config = SandboxResourcePolicy.apply(HostConfig.newHostConfig());
        when(inspected.getHostConfig()).thenReturn(config);
        assertDoesNotThrow(() -> SandboxResourcePolicy.requireHardened(inspected));
        config.withPidsLimit(-1L);
        assertThrows(SecurityException.class, () -> SandboxResourcePolicy.requireHardened(inspected));
        SandboxResourcePolicy.apply(config).withReadonlyRootfs(false);
        assertThrows(SecurityException.class, () -> SandboxResourcePolicy.requireHardened(inspected));
        SandboxResourcePolicy.apply(config).withTmpFs(Map.of());
        assertThrows(SecurityException.class, () -> SandboxResourcePolicy.requireHardened(inspected));
        SandboxResourcePolicy.apply(config).withSecurityOpts(java.util.List.of("seccomp=unconfined"));
        assertThrows(SecurityException.class, () -> SandboxResourcePolicy.requireHardened(inspected));
        assertThrows(SecurityException.class, () -> SandboxResourcePolicy.requireHardened(null));
    }

    @Test
    void rejectsOldNetworkIsolatedContainerBeforeRestartAndExec() {
        DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        InspectContainerResponse inspected = mock(InspectContainerResponse.class, RETURNS_DEEP_STUBS);
        when(docker.inspectContainerCmd("legacy").exec()).thenReturn(inspected);
        when(inspected.getHostConfig()).thenReturn(HostConfig.newHostConfig().withNetworkMode("none"));
        when(inspected.getNetworkSettings().getNetworks()).thenReturn(Map.of());
        assertThrows(SecurityException.class, () -> new SandboxServiceImpl(docker).isContainerAvailable("legacy"));
        assertThrows(SecurityException.class, () -> new TerminalServiceImpl(docker)
                .createSession("legacy", mock(WebSocketSession.class)));
        verify(docker, never()).startContainerCmd(anyString());
        verify(docker, never()).execCreateCmd(anyString());
    }
}
