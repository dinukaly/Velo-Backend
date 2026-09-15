package com.dinukaly.velo.repo.es;

import com.dinukaly.velo.entity.es.CodeChunkDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Elasticsearch repository for managing code chunk documents in Elasticsearch.
 */
@Repository
public interface CodeChunkRepository extends ElasticsearchRepository<CodeChunkDocument, String> {

    /**
     * Finds code chunks for a given project and relative file path.
     */
    List<CodeChunkDocument> findByProjectIdAndPath(String projectId, String path);

    /**
     * Deletes code chunks for a specific file when updated or removed during incremental indexing.
     */
    void deleteByProjectIdAndPath(String projectId, String path);

    /**
     * Deletes all code chunks belonging to a project.
     */
    void deleteByProjectId(String projectId);
}
