package com.dinukaly.velo.service;

import java.util.UUID;

public interface ContextService {

    String getFileContent(UUID projectId, String filePath);
}
