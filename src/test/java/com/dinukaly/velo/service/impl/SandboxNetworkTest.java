package com.dinukaly.velo.service.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.dinukaly.velo.util.SandboxNetworkPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SandboxNetworkTest {
    @Test
    void newSandboxUsesNoNetworkAndRetainsWorkspaceAndTty() {
        DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        CreateContainerCmd create = mock(CreateContainerCmd.class, RETURNS_SELF);
        CreateContainerResponse created = mock(CreateContainerResponse.class);
        when(docker.createContainerCmd(anyString())).thenReturn(create);
        when(create.exec()).thenReturn(created);
        when(created.getId()).thenReturn("sandbox");
        SandboxServiceImpl service = new SandboxServiceImpl(docker);
        Path root = Path.of("target", "network-test-workspaces").toAbsolutePath();
        ReflectionTestUtils.setField(service, "workspaceRoot", root.toString());
        assertEquals("sandbox", service.startContainer(root.resolve("project").toString()));
        ArgumentCaptor<HostConfig> config = ArgumentCaptor.forClass(HostConfig.class);
        verify(create).withHostConfig(config.capture());
        assertEquals("none", config.getValue().getNetworkMode());
        assertEquals("/workspace", config.getValue().getBinds()[0].getVolume().getPath());
        verify(create).withTty(true);
        verify(create).withStdinOpen(true);
        verify(docker.startContainerCmd("sandbox")).exec();
    }

    @Test
    void rejectsLegacyContainerBeforeRestartOrTerminalExec() {
        DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);
        InspectContainerResponse inspected = mock(InspectContainerResponse.class, RETURNS_DEEP_STUBS);
        when(docker.inspectContainerCmd("legacy").exec()).thenReturn(inspected);
        when(inspected.getHostConfig().getNetworkMode()).thenReturn("bridge");
        assertThrows(SecurityException.class,
                () -> new SandboxServiceImpl(docker).isContainerAvailable("legacy"));
        assertThrows(SecurityException.class,
                () -> new TerminalServiceImpl(docker).createSession("legacy", mock(WebSocketSession.class)));
        verify(docker, never()).startContainerCmd(anyString());
        verify(docker, never()).execCreateCmd(anyString());
    }

    @Test
    void acceptsIsolatedContainerAndRejectsAdditionalNetworkOrMissingInspection() {
        InspectContainerResponse inspected = mock(InspectContainerResponse.class, RETURNS_DEEP_STUBS);
        when(inspected.getHostConfig().getNetworkMode()).thenReturn("none");
        when(inspected.getNetworkSettings().getNetworks()).thenReturn(Map.of());
        assertDoesNotThrow(() -> SandboxNetworkPolicy.requireIsolated(inspected));
        when(inspected.getNetworkSettings().getNetworks()).thenReturn(Map.of("bridge",
                new com.github.dockerjava.api.model.ContainerNetwork()));
        assertThrows(SecurityException.class, () -> SandboxNetworkPolicy.requireIsolated(inspected));
        assertThrows(SecurityException.class, () -> SandboxNetworkPolicy.requireIsolated(null));
    }
}
