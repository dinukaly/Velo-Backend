package com.dinukaly.velo.service;

public interface SandboxService {
    String startContainer(String workspacePath);
    void stopContainer(String containerId);
    String executeCommand(String containerId, String command);
}
