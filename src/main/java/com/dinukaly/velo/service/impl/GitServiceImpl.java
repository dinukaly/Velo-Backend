package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.dto.git.*;
import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import com.dinukaly.velo.exception.BadRequestException;
import com.dinukaly.velo.exception.CustomAuthenticationException;
import com.dinukaly.velo.exception.NotFoundException;
import com.dinukaly.velo.repo.ProjectRepository;
import com.dinukaly.velo.repo.UserRepository;
import com.dinukaly.velo.service.GitService;
import com.dinukaly.velo.util.FilePathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitServiceImpl implements GitService {

    private static final int MAX_DIFF_CHARS = 120_000;
    private static final int MAX_LOG_LIMIT = 100;
    private static final int DEFAULT_LOG_LIMIT = 30;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FilePathResolver filePathResolver;

    @Override
    public GitStatusDTO initRepository(UUID projectId, String email) {
        Path root = workspaceFor(projectId, email);
        try {
            Files.createDirectories(root);
            if (!repositoryInitialized(root)) {
                try (Git git = Git.init().setDirectory(root.toFile()).call()) {
                    log.info("[GitService] Initialized repository at {}", root);
                }
            }
            return status(projectId, email);
        } catch (IOException e) {
            throw new BadRequestException("Could not prepare project workspace for Git");
        } catch (GitAPIException e) {
            throw new BadRequestException("Could not initialize Git repository: " + e.getMessage());
        }
    }

    @Override
    public GitStatusDTO status(UUID projectId, String email) {
        Path root = workspaceFor(projectId, email);
        if (!repositoryInitialized(root)) {
            return emptyUninitializedStatus();
        }

        try (Git git = openRepository(root)) {
            org.eclipse.jgit.api.Status status = git.status().call();
            return GitStatusDTO.builder()
                    .repositoryInitialized(true)
                    .currentBranch(currentBranch(git.getRepository()))
                    .clean(status.isClean())
                    .stagedChanges(stagedChanges(status))
                    .unstagedChanges(unstagedChanges(status))
                    .untrackedFiles(toChanges(status.getUntracked(), "UNTRACKED"))
                    .conflictingFiles(toChanges(status.getConflicting(), "CONFLICT"))
                    .build();
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not read Git status: " + e.getMessage());
        }
    }

    @Override
    public GitDiffDTO diff(UUID projectId, String path, boolean staged, String email) {
        Path root = requireRepository(projectId, email);
        String normalizedPath = normalizeOptionalGitPath(path);

        try (Git git = openRepository(root);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var command = git.diff()
                    .setCached(staged)
                    .setOutputStream(output);

            if (normalizedPath != null) {
                command.setPathFilter(PathFilter.create(normalizedPath));
            }

            command.call();
            String rawDiff = output.toString(StandardCharsets.UTF_8);
            boolean truncated = rawDiff.length() > MAX_DIFF_CHARS;
            String payload = truncated ? rawDiff.substring(0, MAX_DIFF_CHARS) : rawDiff;

            return GitDiffDTO.builder()
                    .path(normalizedPath)
                    .staged(staged)
                    .diff(payload)
                    .truncated(truncated)
                    .build();
        } catch (NoHeadException e) {
            return GitDiffDTO.builder()
                    .path(normalizedPath)
                    .staged(staged)
                    .diff("")
                    .truncated(false)
                    .build();
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not read Git diff: " + e.getMessage());
        }
    }

    @Override
    public GitStatusDTO stage(UUID projectId, GitStageRequestDTO dto, String email) {
        Path root = requireRepository(projectId, email);
        List<String> paths = normalizePathList(dto != null ? dto.getPaths() : null);

        try (Git git = openRepository(root)) {
            AddCommand addCommand = git.add();
            AddCommand updateCommand = git.add().setUpdate(true);

            if (paths.isEmpty()) {
                addCommand.addFilepattern(".");
                updateCommand.addFilepattern(".");
            } else {
                paths.forEach(path -> {
                    addCommand.addFilepattern(path);
                    updateCommand.addFilepattern(path);
                });
            }

            updateCommand.call();
            addCommand.call();
            return status(projectId, email);
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not stage changes: " + e.getMessage());
        }
    }

    @Override
    public GitStatusDTO unstage(UUID projectId, GitStageRequestDTO dto, String email) {
        Path root = requireRepository(projectId, email);
        List<String> paths = normalizePathList(dto != null ? dto.getPaths() : null);

        try (Git git = openRepository(root)) {
            ResetCommand reset = git.reset().setMode(ResetCommand.ResetType.MIXED);
            paths.forEach(reset::addPath);
            reset.call();
            return status(projectId, email);
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not unstage changes: " + e.getMessage());
        }
    }

    @Override
    public GitCommitDTO commit(UUID projectId, GitCommitRequestDTO dto, String email) {
        Path root = requireRepository(projectId, email);
        User user = findUser(email);

        try (Git git = openRepository(root)) {
            org.eclipse.jgit.api.Status status = git.status().call();
            if (status.getAdded().isEmpty()
                    && status.getChanged().isEmpty()
                    && status.getRemoved().isEmpty()) {
                throw new BadRequestException("No staged changes to commit");
            }

            PersonIdent author = new PersonIdent(user.getName(), user.getEmail());
            RevCommit commit = git.commit()
                    .setMessage(dto.getMessage().trim())
                    .setAuthor(author)
                    .setCommitter(author)
                    .call();

            return toCommitDTO(commit);
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not commit changes: " + e.getMessage());
        }
    }

    @Override
    public List<GitCommitDTO> log(UUID projectId, int limit, String email) {
        Path root = requireRepository(projectId, email);
        int safeLimit = limit <= 0 ? DEFAULT_LOG_LIMIT : Math.min(limit, MAX_LOG_LIMIT);

        try (Git git = openRepository(root)) {
            List<GitCommitDTO> commits = new ArrayList<>();
            Iterable<RevCommit> logEntries = git.log().setMaxCount(safeLimit).call();
            for (RevCommit commit : logEntries) {
                commits.add(toCommitDTO(commit));
            }
            return commits;
        } catch (NoHeadException e) {
            return List.of();
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not read Git log: " + e.getMessage());
        }
    }

    @Override
    public GitBranchDTO branches(UUID projectId, String email) {
        Path root = requireRepository(projectId, email);

        try (Git git = openRepository(root)) {
            List<String> branches = git.branchList().call().stream()
                    .map(Ref::getName)
                    .map(Repository::shortenRefName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            return GitBranchDTO.builder()
                    .currentBranch(currentBranch(git.getRepository()))
                    .branches(branches)
                    .build();
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not read Git branches: " + e.getMessage());
        }
    }

    @Override
    public GitBranchDTO createBranch(UUID projectId, GitBranchCreateRequestDTO dto, String email) {
        Path root = requireRepository(projectId, email);
        String branchName = validateBranchName(dto.getName());

        try (Git git = openRepository(root)) {
            git.branchCreate().setName(branchName).call();
            if (dto.isCheckout()) {
                git.checkout().setName(branchName).call();
            }
            return branches(projectId, email);
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not create branch: " + e.getMessage());
        }
    }

    @Override
    public GitBranchDTO checkout(UUID projectId, GitCheckoutRequestDTO dto, String email) {
        Path root = requireRepository(projectId, email);
        String branchName = validateBranchName(dto.getBranch());

        try (Git git = openRepository(root)) {
            git.checkout().setName(branchName).call();
            return branches(projectId, email);
        } catch (IOException | GitAPIException e) {
            throw new BadRequestException("Could not checkout branch: " + e.getMessage());
        }
    }

    private GitStatusDTO emptyUninitializedStatus() {
        return GitStatusDTO.builder()
                .repositoryInitialized(false)
                .currentBranch(null)
                .clean(true)
                .stagedChanges(List.of())
                .unstagedChanges(List.of())
                .untrackedFiles(List.of())
                .conflictingFiles(List.of())
                .build();
    }

    private Path requireRepository(UUID projectId, String email) {
        Path root = workspaceFor(projectId, email);
        if (!repositoryInitialized(root)) {
            throw new BadRequestException("Git repository is not initialized for this project");
        }
        return root;
    }

    private Path workspaceFor(UUID projectId, String email) {
        Project project = findOwnedProject(projectId, email);
        return filePathResolver.getProjectWorkspacePath(project);
    }

    private Git openRepository(Path root) throws IOException {
        return Git.open(root.toFile());
    }

    private boolean repositoryInitialized(Path root) {
        return Files.isDirectory(root.resolve(".git"));
    }

    private Project findOwnedProject(UUID projectId, String email) {
        User user = findUser(email);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        if (!project.getOwner().getId().equals(user.getId())) {
            throw new CustomAuthenticationException("Access denied to project: " + projectId);
        }
        return project;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }

    private List<GitFileChangeDTO> stagedChanges(org.eclipse.jgit.api.Status status) {
        List<GitFileChangeDTO> changes = new ArrayList<>();
        changes.addAll(toChanges(status.getAdded(), "ADDED"));
        changes.addAll(toChanges(status.getChanged(), "MODIFIED"));
        changes.addAll(toChanges(status.getRemoved(), "DELETED"));
        return sortChanges(changes);
    }

    private List<GitFileChangeDTO> unstagedChanges(org.eclipse.jgit.api.Status status) {
        List<GitFileChangeDTO> changes = new ArrayList<>();
        changes.addAll(toChanges(status.getModified(), "MODIFIED"));
        changes.addAll(toChanges(status.getMissing(), "DELETED"));
        return sortChanges(changes);
    }

    private List<GitFileChangeDTO> toChanges(Set<String> paths, String status) {
        return sortChanges(paths.stream()
                .map(path -> GitFileChangeDTO.builder()
                        .path(path)
                        .status(status)
                        .build())
                .toList());
    }

    private List<GitFileChangeDTO> sortChanges(List<GitFileChangeDTO> changes) {
        return changes.stream()
                .sorted(Comparator.comparing(GitFileChangeDTO::getPath, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private GitCommitDTO toCommitDTO(RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        String id = commit.getId().name();
        return GitCommitDTO.builder()
                .id(id)
                .shortId(id.substring(0, Math.min(7, id.length())))
                .message(commit.getShortMessage())
                .authorName(author != null ? author.getName() : null)
                .authorEmail(author != null ? author.getEmailAddress() : null)
                .commitTime(Instant.ofEpochSecond(commit.getCommitTime()))
                .build();
    }

    private String currentBranch(Repository repository) throws IOException {
        String branch = repository.getBranch();
        return branch != null ? branch : "HEAD";
    }

    private String normalizeOptionalGitPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return normalizeGitPath(path);
    }

    private List<String> normalizePathList(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(this::normalizeGitPath)
                .distinct()
                .toList();
    }

    private String normalizeGitPath(String rawPath) {
        String normalized = rawPath.trim().replace("\\", "/");
        if (normalized.equals(".") || normalized.equals("/")) {
            return ".";
        }
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) {
            throw new BadRequestException("Git path must be project-relative");
        }
        Path path = Paths.get(normalized).normalize();
        String clean = path.toString().replace("\\", "/");
        if (clean.equals("..") || clean.startsWith("../") || clean.contains("/../")) {
            throw new BadRequestException("Git path cannot leave the project workspace");
        }
        if (clean.equals(".git") || clean.startsWith(".git/")) {
            throw new BadRequestException("Git internals cannot be targeted directly");
        }
        return clean;
    }

    private String validateBranchName(String rawName) {
        String name = rawName != null ? rawName.trim() : "";
        if (name.isBlank()) {
            throw new BadRequestException("Branch name is required");
        }
        if (!Repository.isValidRefName("refs/heads/" + name)) {
            throw new BadRequestException("Invalid branch name");
        }
        return name;
    }
}
