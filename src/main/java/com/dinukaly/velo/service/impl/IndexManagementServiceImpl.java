package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.agent.ProjectIndexStatusDTO;
import com.dinukaly.velo.entity.IndexStatus;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.ProjectIndexState;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.entity.es.CodeChunkDocument;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.ProjectIndexStateRepository;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.repo.es.CodeChunkRepository;
import com.dinukaly.velo.service.CodeChunkerService;
import com.dinukaly.velo.service.IndexManagementService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Implementation of IndexManagementService.
 *
 * Manages project code indexing into Elasticsearch:
 * - Scans project source files while ignoring build dirs (.git, node_modules, target, etc.) and secrets (.env, keys).
 * - Chunks files using CodeChunkerService.
 * - Stores chunks in Elasticsearch repository (velo-code-chunks-v1).
 * - Updates ProjectIndexState in MySQL with status, generation, and chunk counts.
 * - Gracefully handles Elasticsearch unavailability by marking state as DEGRADED or FAILED without crashing file operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexManagementServiceImpl implements IndexManagementService {

    private static final long MAX_FILE_SIZE_BYTES = 500_000;

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", "node_modules", "dist", "build", "target", "coverage", ".next", "out", "vendor"
    );

    private static final List<Pattern> SENSITIVE_FILE_PATTERNS = List.of(
            Pattern.compile("^\\.env(\\..*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\.pem$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\.key$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\.p12$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\.jks$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^credentials.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^secrets.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\.ssh.*", Pattern.CASE_INSENSITIVE)
    );

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectIndexStateRepository projectIndexStateRepository;
    private final CodeChunkRepository codeChunkRepository;
    private final CodeChunkerService codeChunkerService;
    private final FilePathResolver filePathResolver;

    /**
     * Initiates project indexing (FULL, INCREMENTAL, or REPAIR).
     */
    @Override
    @Transactional
    public ProjectIndexStatusDTO indexProject(UUID projectId, String mode, String userEmail) {
        Project project = findOwnedProject(projectId, userEmail);
        ProjectIndexState indexState = getOrCreateState(project);

        indexState.setStatus(IndexStatus.INDEXING);
        indexState.setLastIndexedAt(Instant.now());
        projectIndexStateRepository.save(indexState);

        // Run full indexing in background thread
        executeProjectIndexingAsync(project.getId(), indexState.getId(), mode);

        return toStatusDTO(indexState);
    }

    /**
     * Background worker for scanning workspace files and updating Elasticsearch chunks.
     */
    @Async("agentTaskExecutor")
    public void executeProjectIndexingAsync(UUID projectId, UUID indexStateId, String mode) {
        log.info("[IndexManagement] Starting async indexing mode '{}' for project {}", mode, projectId);

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        Optional<ProjectIndexState> stateOpt = projectIndexStateRepository.findById(indexStateId);

        if (projectOpt.isEmpty() || stateOpt.isEmpty()) {
            return;
        }

        Project project = projectOpt.get();
        ProjectIndexState indexState = stateOpt.get();
        Path root = filePathResolver.getProjectWorkspacePath(project);

        int fileCount = 0;
        int chunkCount = 0;

        try {
            // Delete old chunks if FULL or REPAIR mode
            if ("FULL".equalsIgnoreCase(mode) || "REPAIR".equalsIgnoreCase(mode)) {
                try {
                    codeChunkRepository.deleteByProjectId(projectId.toString());
                } catch (Exception e) {
                    log.warn("[IndexManagement] ES deleteByProjectId warning for project {}: {}", projectId, e.getMessage());
                }
            }

            List<CodeChunkDocument> allChunks = new ArrayList<>();
            List<Path> filesToIndex = scanWorkspaceFiles(root);

            for (Path filePath : filesToIndex) {
                try {
                    String content = Files.readString(filePath);
                    String relPath = root.relativize(filePath).toString().replace("\\", "/");
                    List<CodeChunkDocument> chunks = codeChunkerService.chunkFile(projectId, relPath, content);
                    allChunks.addAll(chunks);
                    fileCount++;
                } catch (Exception e) {
                    log.warn("[IndexManagement] Could not index file {}: {}", filePath, e.getMessage());
                }
            }

            // Save chunks to Elasticsearch
            if (!allChunks.isEmpty()) {
                try {
                    codeChunkRepository.saveAll(allChunks);
                    chunkCount = allChunks.size();
                } catch (Exception e) {
                    log.error("[IndexManagement] Elasticsearch saveAll failed for project {}: {}", projectId, e.getMessage());
                    indexState.setStatus(IndexStatus.DEGRADED);
                    indexState.setLastError("Elasticsearch save failed: " + e.getMessage());
                    projectIndexStateRepository.save(indexState);
                    return;
                }
            }

            indexState.setStatus(IndexStatus.READY);
            indexState.setIndexedFileCount(fileCount);
            indexState.setIndexedChunkCount(chunkCount);
            indexState.setIndexGeneration(indexState.getIndexGeneration() + 1);
            indexState.setLastSuccessfulIndexedAt(Instant.now());
            indexState.setLastError(null);
            projectIndexStateRepository.save(indexState);

            log.info("[IndexManagement] Indexing completed for project {}: {} files, {} chunks indexed", projectId, fileCount, chunkCount);

        } catch (Exception e) {
            log.error("[IndexManagement] Indexing failed for project {}", projectId, e);
            indexState.setStatus(IndexStatus.FAILED);
            indexState.setLastError(e.getMessage());
            projectIndexStateRepository.save(indexState);
        }
    }

    /**
     * Incrementally updates chunks for a single file upon save.
     */
    @Override
    public void indexSingleFile(UUID projectId, String relativePath, String userEmail) {
        Project project = findOwnedProject(projectId, userEmail);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path filePath = root.resolve(relativePath).normalize();

        if (!filePath.startsWith(root) || isSensitive(relativePath)) {
            return;
        }

        try {
            codeChunkRepository.deleteByProjectIdAndPath(projectId.toString(), relativePath);

            if (Files.exists(filePath) && Files.isRegularFile(filePath) && Files.size(filePath) <= MAX_FILE_SIZE_BYTES) {
                String content = Files.readString(filePath);
                List<CodeChunkDocument> chunks = codeChunkerService.chunkFile(projectId, relativePath, content);
                if (!chunks.isEmpty()) {
                    codeChunkRepository.saveAll(chunks);
                }
            }
        } catch (Exception e) {
            log.warn("[IndexManagement] Single file indexing warning for {}: {}", relativePath, e.getMessage());
        }
    }

    /**
     * Returns project index status and stats from MySQL.
     */
    @Override
    @Transactional(readOnly = true)
    public ProjectIndexStatusDTO getIndexStatus(UUID projectId, String userEmail) {
        Project project = findOwnedProject(projectId, userEmail);
        ProjectIndexState state = projectIndexStateRepository.findByProject(project)
                .orElseGet(() -> ProjectIndexState.builder()
                        .project(project)
                        .status(IndexStatus.NOT_INDEXED)
                        .indexGeneration(0)
                        .indexSchemaVersion(1)
                        .indexedFileCount(0)
                        .indexedChunkCount(0)
                        .build());
        return toStatusDTO(state);
    }

    private List<Path> scanWorkspaceFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                if (EXCLUDED_DIRECTORIES.contains(name) || isSensitive(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (!isSensitive(name) && attrs.size() <= MAX_FILE_SIZE_BYTES) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    private boolean isSensitive(String pathOrName) {
        if (pathOrName == null) return false;
        String clean = pathOrName.replace("\\", "/");
        for (Pattern pattern : SENSITIVE_FILE_PATTERNS) {
            if (pattern.matcher(clean).matches() || pattern.matcher(Paths.get(clean).getFileName().toString()).matches()) {
                return true;
            }
        }
        return false;
    }

    private ProjectIndexState getOrCreateState(Project project) {
        return projectIndexStateRepository.findByProject(project)
                .orElseGet(() -> projectIndexStateRepository.save(ProjectIndexState.builder()
                        .project(project)
                        .status(IndexStatus.NOT_INDEXED)
                        .indexGeneration(0)
                        .indexSchemaVersion(1)
                        .indexedFileCount(0)
                        .indexedChunkCount(0)
                        .build()));
    }

    private ProjectIndexStatusDTO toStatusDTO(ProjectIndexState state) {
        return ProjectIndexStatusDTO.builder()
                .projectId(state.getProject().getId())
                .status(state.getStatus())
                .indexGeneration(state.getIndexGeneration())
                .indexSchemaVersion(state.getIndexSchemaVersion())
                .indexedFileCount(state.getIndexedFileCount())
                .indexedChunkCount(state.getIndexedChunkCount())
                .lastIndexedAt(state.getLastIndexedAt())
                .lastSuccessfulIndexedAt(state.getLastSuccessfulIndexedAt())
                .lastError(state.getLastError())
                .build();
    }

    private Project findOwnedProject(UUID projectId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new CustomAuthenticationException("Access denied to project: " + projectId);
        }
        return project;
    }
}
