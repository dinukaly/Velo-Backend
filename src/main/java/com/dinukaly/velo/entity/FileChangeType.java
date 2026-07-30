package com.dinukaly.velo.entity;

/**
 * The type of change represented by an AgentProposalFile.
 */
public enum FileChangeType {
    /** A new file is being created. */
    CREATE,

    /** An existing file is being modified. */
    MODIFY,

    /** An existing file is being deleted. */
    DELETE,

    /** An existing file is being moved/renamed. */
    RENAME
}
