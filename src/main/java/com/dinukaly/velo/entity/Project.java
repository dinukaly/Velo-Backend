package com.dinukaly.velo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500, nullable = false)
    private String description;

    @Column(nullable = false, length = 30)
    private String language;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // owner relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User owner;

    // sandbox relationship
    @OneToOne(mappedBy = "project", cascade = CascadeType.ALL)
    private SandboxSession sandboxSession;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
