package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.ProjectIndexState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for managing project indexing state metadata in MySQL.
 */
@Repository
public interface ProjectIndexStateRepository extends JpaRepository<ProjectIndexState, UUID> {

    /**
     * Finds index state by Project entity.
     */
    Optional<ProjectIndexState> findByProject(Project project);

    /**
     * Finds index state by project ID.
     */
    Optional<ProjectIndexState> findByProjectId(UUID projectId);
}
