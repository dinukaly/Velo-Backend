package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.FileContentResponseDTO;
import com.dinukaly.velo.dto.FsNodeDTO;
import com.dinukaly.velo.dto.agent.CodeSearchResultDTO;
import com.dinukaly.velo.dto.agent.CodeSearchResultMatchDTO;
import com.dinukaly.velo.dto.agent.FileRangeResponseDTO;
import com.dinukaly.velo.dto.git.GitDiffDTO;
import com.dinukaly.velo.dto.git.GitStatusDTO;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.AgentToolService;
import com.dinukaly.velo.service.FsService;
import com.dinukaly.velo.service.GitService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of read-only tools used by the AI Agent to inspect and navigate project workspaces.
 *
 * Enforces strict security rules:
 * - Validates project ownership per request.
 * - Blocks path traversal outside project workspace root.
 * - Blocks access to sensitive/secret files (.env, keys, credentials, etc.).
 * - Excludes non-source/generated directories (.git, node_modules, target, .next, etc.).
 * - Enforces file size limits (500 KB limit for reading content).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentToolServiceImpl implements AgentToolService {

    /**
     * Maximum file size allowed for reading or searching (500 KB).
     * Prevents memory exhaustion when opening large binary or generated files.
     */
    private static final long MAX_FILE_SIZE_BYTES = 500_000;

    /**
     * Maximum matches returned in a single code search operation to prevent oversized responses.
     */
    private static final int MAX_SEARCH_RESULTS = 50;

    /**
     * Directories ignored during directory listing, tree structural inspection, and file search.
     */
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", "node_modules", "dist", "build", "target", "coverage", ".next", "out", "vendor"
    );

    /**
     * Regular expression patterns matching sensitive files that the AI model must not read.
     */
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

    private final FsService fsService;
    private final GitService gitService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FilePathResolver filePathResolver;

    /**
     * Lists child nodes inside the specified relative directory path.
     * Filters out build artifacts, dependencies, and sensitive files.
     */
    @Override
    public List<FsNodeDTO> listDirectory(UUID projectId, String relativePath, String email) {
        validateSensitivePathAccess(relativePath);
        List<FsNodeDTO> nodes = fsService.listDirectory(projectId, relativePath, email);
        return nodes.stream()
                .filter(node -> !isExcludedDirectoryOrSensitive(node.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Reads the entire content of a text file.
     * Checks file size limit and blocks sensitive/secret file access.
     */
    @Override
    public FileContentResponseDTO readFile(UUID projectId, String relativePath, String email) {
        validateSensitivePathAccess(relativePath);
        Project project = findOwnedProject(projectId, email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path filePath = resolveAndValidate(root, relativePath);

        validateFileSize(filePath);

        return fsService.readFile(projectId, relativePath, email);
    }

    /**
     * Reads a specific 1-indexed line range from a text file.
     * Useful for fetching targeted context around code symbols without loading whole files.
     */
    @Override
    public FileRangeResponseDTO readFileRange(UUID projectId, String relativePath, int startLine, int endLine, String email) {
        validateSensitivePathAccess(relativePath);
        if (startLine < 1 || endLine < startLine) {
            throw new BadRequestException("Invalid line range: startLine must be >= 1 and endLine >= startLine");
        }

        Project project = findOwnedProject(projectId, email);
        Path root = filePathResolver.getProjectWorkspacePath(project);
        Path filePath = resolveAndValidate(root, relativePath);

        validateFileSize(filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new NotFoundException("File not found: " + relativePath);
        }

        try {
            List<String> allLines = Files.readAllLines(filePath);
            int totalLines = allLines.size();

            int actualStart = Math.min(startLine, totalLines > 0 ? totalLines : 1);
            int actualEnd = Math.min(endLine, totalLines);

            String content = "";
            if (totalLines > 0 && actualStart <= actualEnd) {
                List<String> subList = allLines.subList(actualStart - 1, actualEnd);
                content = String.join("\n", subList);
            }

            return FileRangeResponseDTO.builder()
                    .path(relativePath)
                    .name(filePath.getFileName().toString())
                    .startLine(actualStart)
                    .endLine(actualEnd)
                    .totalLines(totalLines)
                    .content(content)
                    .build();

        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file range for: " + relativePath, e);
        }
    }

    /**
     * Performs a recursive filesystem keyword search across source code files.
     * Skips excluded directories, binary files, and sensitive files.
     */
    @Override
    public CodeSearchResultDTO searchCode(UUID projectId, String query, String email) {
        if (query == null || query.isBlank()) {
            return CodeSearchResultDTO.builder()
                    .query(query)
                    .matches(List.of())
                    .totalMatches(0)
                    .truncated(false)
                    .build();
        }

        Project project = findOwnedProject(projectId, email);
        Path root = filePathResolver.getProjectWorkspacePath(project);

        List<CodeSearchResultMatchDTO> matches = new ArrayList<>();
        boolean truncated = false;
        String lowerQuery = query.toLowerCase();

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String folderName = dir.getFileName().toString();
                    if (EXCLUDED_DIRECTORIES.contains(folderName) || isSensitive(folderName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    if (isSensitive(fileName) || attrs.size() > MAX_FILE_SIZE_BYTES) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        List<String> lines = Files.readAllLines(file);
                        String relPath = root.relativize(file).toString().replace("\\", "/");

                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (line.toLowerCase().contains(lowerQuery)) {
                                matches.add(CodeSearchResultMatchDTO.builder()
                                        .path(relPath)
                                        .lineNumber(i + 1)
                                        .lineContent(line.trim())
                                        .build());

                                if (matches.size() >= MAX_SEARCH_RESULTS) {
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Ignore binary or unreadable non-UTF8 files
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("[AgentToolService] Code search failed for project: {}", projectId, e);
            throw new UncheckedIOException("Code search failed", e);
        }

        if (matches.size() >= MAX_SEARCH_RESULTS) {
            truncated = true;
        }

        return CodeSearchResultDTO.builder()
                .query(query)
                .matches(matches)
                .totalMatches(matches.size())
                .truncated(truncated)
                .build();
    }

    /**
     * Returns the current local Git repository status for the project workspace.
     */
    @Override
    public GitStatusDTO getGitStatus(UUID projectId, String email) {
        return gitService.status(projectId, email);
    }

    /**
     * Returns the local Git diff for a specific file or the entire repository.
     */
    @Override
    public GitDiffDTO getGitDiff(UUID projectId, String path, boolean staged, String email) {
        if (path != null && !path.isBlank()) {
            validateSensitivePathAccess(path);
        }
        return gitService.diff(projectId, path, staged, email);
    }

    /**
     * Retrieves the top-level structure of the project repository.
     */
    @Override
    public List<FsNodeDTO> getProjectStructure(UUID projectId, String email) {
        return listDirectory(projectId, "", email);
    }

    // -------------------------------------------------------------------------
    // Private Security & Helper Methods
    // -------------------------------------------------------------------------

    /**
     * Throws CustomAuthenticationException if the requested relative path points to a sensitive file.
     */
    private void validateSensitivePathAccess(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        String fileName = Paths.get(relativePath).getFileName().toString();
        if (isSensitive(fileName) || isSensitive(relativePath)) {
            log.warn("[AgentToolService] Blocked attempt to access sensitive file: {}", relativePath);
            throw new CustomAuthenticationException("Access denied: protected or sensitive file path");
        }
    }

    /**
     * Returns true if the file or folder name matches excluded directories or sensitive file patterns.
     */
    private boolean isExcludedDirectoryOrSensitive(String name) {
        return EXCLUDED_DIRECTORIES.contains(name) || isSensitive(name);
    }

    /**
     * Tests a path string against sensitive file regex patterns.
     */
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

    /**
     * Verifies that the target file does not exceed the maximum size limit.
     */
    private void validateFileSize(Path filePath) {
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            try {
                long size = Files.size(filePath);
                if (size > MAX_FILE_SIZE_BYTES) {
                    throw new BadRequestException("File size (" + size + " bytes) exceeds maximum allowable limit (" + MAX_FILE_SIZE_BYTES + " bytes)");
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not determine file size", e);
            }
        }
    }

    /**
     * Resolves a relative path against the project root and guards against path traversal (e.g. '../').
     */
    private Path resolveAndValidate(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return root;
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new CustomAuthenticationException("Invalid path: outside of project workspace");
        }
        return resolved;
    }

    /**
     * Validates user identity and verifies project ownership.
     */
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
