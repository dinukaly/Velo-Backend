package com.dinukaly.velo.entity;

/**
 * Status of a project's search index in Elasticsearch.
 */
public enum IndexStatus {
    /** Indexing has not been performed yet for this project. */
    NOT_INDEXED,

    /** Indexing is currently running in the background. */
    INDEXING,

    /** Index is up to date and ready for hybrid/lexical search queries. */
    READY,

    /** Project files have changed since last indexing; reindex scheduled. */
    STALE,

    /** Search is operational but falling back to lexical or filesystem mode due to provider degradation. */
    DEGRADED,

    /** Last indexing operation failed with an error. */
    FAILED
}
