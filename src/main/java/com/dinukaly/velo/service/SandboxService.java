package com.dinukaly.velo.service;

public interface SandboxService {
    String startContainer(String workspacePath);
    void stopContainer(String containerId);
    boolean isContainerAvailable(String containerId);
}
