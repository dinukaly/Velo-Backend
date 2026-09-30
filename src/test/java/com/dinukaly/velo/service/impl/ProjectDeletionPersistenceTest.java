package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.*;
import com.dinukaly.velo.repo.*;
import com.dinukaly.velo.repo.es.CodeChunkRepository;
import com.dinukaly.velo.service.*;
import com.dinukaly.velo.util.FilePathResolver;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeletionPersistenceTest {
    @Test
    void deletesCompleteProjectGraphWithForeignKeysEnforcedAndPreservesOtherProject() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:project_delete;MODE=MySQL;NON_KEYWORDS=SEQUENCE,CURRENT_PATH")
                .setProperty("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .setProperty("hibernate.hbm2ddl.halt_on_error", "true")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false");
        for (Class<?> entity : new Class<?>[]{User.class, Project.class, SandboxSession.class,
                AgentRun.class, AgentStep.class, AgentEvent.class, AgentProposal.class,
                AgentProposalFile.class, AgentProposalHunk.class, ProjectIndexState.class}) {
            configuration.addAnnotatedClass(entity);
        }
        try (var factory = configuration.buildSessionFactory(); Session session = factory.openSession()) {
            session.beginTransaction();
            User owner = User.builder().name("Test").email("deletion@example.test")
                    .passwordHash("test-only").role(Role.USER).build();
            session.persist(owner);
            UUID deletedId = createGraph(session, owner, "delete").getId();
            UUID retainedId = createGraph(session, owner, "keep").getId();
            session.getTransaction().commit();
            session.clear();

            session.beginTransaction();
            var repositories = new JpaRepositoryFactory(session);
            Project project = session.find(Project.class, deletedId);
            var files = mock(FileStorageService.class);
            var paths = mock(FilePathResolver.class);
            when(paths.getProjectWorkspacePath(project)).thenReturn(Path.of("target", deletedId.toString()));
            var service = new ProjectDeletionService(
                    repositories.getRepository(ProjectRepository.class),
                    repositories.getRepository(AgentRunRepository.class),
                    repositories.getRepository(AgentProposalRepository.class),
                    repositories.getRepository(ProjectIndexStateRepository.class),
                    mock(SandboxService.class), files, paths, mock(AgentSseService.class), mock(CodeChunkRepository.class));
            TransactionSynchronizationManager.initSynchronization();
            try {
                service.delete(project);
                verifyNoInteractions(files);
                session.getTransaction().commit();
                TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            session.clear();
            assertNull(session.find(Project.class, deletedId));
            assertNotNull(session.find(Project.class, retainedId));
            for (String entity : new String[]{"User", "Project", "SandboxSession", "AgentRun", "AgentStep",
                    "AgentEvent", "AgentProposal", "AgentProposalFile", "AgentProposalHunk", "ProjectIndexState"}) {
                assertEquals(1L, session.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(), entity);
            }
        }
    }

    private Project createGraph(Session session, User owner, String name) {
        Project project = Project.builder().name(name).description("Test project").language("TypeScript").owner(owner).build();
        session.persist(project);
        session.persist(SandboxSession.builder().project(project).user(owner).containerId(name).build());
        AgentRun run = AgentRun.builder().project(project).user(owner).message("Test").status(AgentRunStatus.DONE).build();
        session.persist(run);
        session.persist(AgentStep.builder().run(run).type(AgentStepType.values()[0]).build());
        session.persist(AgentEvent.builder().run(run).eventType("run.status").build());
        AgentProposal proposal = AgentProposal.builder().run(run).build();
        session.persist(proposal);
        AgentProposalFile file = AgentProposalFile.builder().proposal(proposal).filePath("index.js")
                .changeType(FileChangeType.MODIFY).build();
        session.persist(file);
        session.persist(AgentProposalHunk.builder().proposalFile(file).build());
        session.persist(ProjectIndexState.builder().project(project).status(IndexStatus.READY).build());
        return project;
    }
}
