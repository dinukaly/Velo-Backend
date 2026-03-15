package com.dinukaly.velo.repo;

import com.dinukaly.velo.entity.Project;
import com.dinukaly.velo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOwner(User owner);

    Optional<Project> findByIdAndOwner(UUID id, User owner);
}
