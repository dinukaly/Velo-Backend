package com.dinukaly.velo.entity.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * Represents a code chunk stored in Elasticsearch for lexical (BM25) and dense vector search.
 *
 * Indexed in the physical index 'velo-code-chunks-v1'.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "velo-code-chunks-v1")
public class CodeChunkDocument {

    /**
     * Deterministic document ID calculated as SHA-256(projectId + path + chunkOrdinal + contentHash).
     */
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String projectId;

    @Field(type = FieldType.Text)
    private String path;

    @Field(type = FieldType.Keyword)
    private String pathKeyword;

    @Field(type = FieldType.Keyword)
    private String language;

    @Field(type = FieldType.Keyword)
    private String chunkType;

    @Field(type = FieldType.Text)
    private String symbolName;

    @Field(type = FieldType.Integer)
    private int startLine;

    @Field(type = FieldType.Integer)
    private int endLine;

    @Field(type = FieldType.Text)
    private String content;

    @Field(type = FieldType.Keyword)
    private String contentHash;

    @Field(type = FieldType.Keyword)
    private String fileHash;

    @Field(type = FieldType.Integer)
    private int chunkOrdinal;

    @Field(type = FieldType.Integer)
    private int indexSchemaVersion;

    @Field(type = FieldType.Date)
    private Instant indexedAt;
}
