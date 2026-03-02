package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.SandboxSession;
import com.dinukaly.velo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SandboxRepository extends JpaRepository<SandboxSession, UUID> {
    Optional<SandboxSession> findByUser(User user);

    Optional<SandboxSession> findByProject(Project project);

    boolean existsByUser(User user);
}
