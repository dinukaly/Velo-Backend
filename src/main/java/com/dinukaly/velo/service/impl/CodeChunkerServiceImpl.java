package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.entity.es.CodeChunkDocument;
import com.dinukaly.velo.service.CodeChunkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of CodeChunkerService.
 *
 * Provides code-aware sliding window chunking with symbol detection:
 * - Detects file language from extension.
 * - Extracts top-level symbol names (classes, methods, functions, interfaces, components) where possible.
 * - Creates overlapping chunks (default 40 lines per chunk, 10 lines overlap).
 * - Computes SHA-256 hashes for file and chunk content.
 * - Generates deterministic document IDs: SHA-256(projectId + path + chunkOrdinal + contentHash).
 */
@Service
@Slf4j
public class CodeChunkerServiceImpl implements CodeChunkerService {

    private static final int CHUNK_LINE_SIZE = 40;
    private static final int CHUNK_LINE_OVERLAP = 10;
    private static final int CURRENT_SCHEMA_VERSION = 1;

    // Pattern for class / interface / function declarations across common languages (Java, TS/JS, Python)
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(
            "^(?:export\\s+)?(?:public|private|protected|static|final|abstract|async\\s+)*" +
                    "(?:class|interface|enum|function|def|const|let|var)\\s+([A-Za-z0-9_$]+)",
            Pattern.MULTILINE
    );

    /**
     * Splits file content into a list of CodeChunkDocument objects.
     */
    @Override
    public List<CodeChunkDocument> chunkFile(UUID projectId, String relativePath, String fileContent) {
        if (fileContent == null || fileContent.isBlank()) {
            return List.of();
        }

        String normalizedPath = relativePath.replace("\\", "/");
        String language = detectLanguage(normalizedPath);
        String fileHash = computeSha256(fileContent);

        String[] lines = fileContent.split("\r?\n");
        int totalLines = lines.length;

        List<CodeChunkDocument> chunks = new ArrayList<>();
        int step = CHUNK_LINE_SIZE - CHUNK_LINE_OVERLAP;
        int ordinal = 0;

        for (int i = 0; i < totalLines; i += step) {
            int startLine = i + 1;
            int endLine = Math.min(i + CHUNK_LINE_SIZE, totalLines);

            String[] chunkLineRange = Arrays.copyOfRange(lines, i, endLine);
            String chunkContent = String.join("\n", chunkLineRange);

            if (chunkContent.isBlank()) {
                continue;
            }

            String contentHash = computeSha256(chunkContent);
            String symbolName = extractFirstSymbol(chunkContent);
            String docId = computeSha256(projectId.toString() + ":" + normalizedPath + ":" + ordinal + ":" + contentHash);

            CodeChunkDocument doc = CodeChunkDocument.builder()
                    .id(docId)
                    .projectId(projectId.toString())
                    .path(normalizedPath)
                    .pathKeyword(normalizedPath)
                    .language(language)
                    .chunkType(symbolName != null ? "SYMBOL" : "BLOCK")
                    .symbolName(symbolName)
                    .startLine(startLine)
                    .endLine(endLine)
                    .content(chunkContent)
                    .contentHash(contentHash)
                    .fileHash(fileHash)
                    .chunkOrdinal(ordinal)
                    .indexSchemaVersion(CURRENT_SCHEMA_VERSION)
                    .indexedAt(Instant.now())
                    .build();

            chunks.add(doc);
            ordinal++;

            if (endLine >= totalLines) {
                break;
            }
        }

        return chunks;
    }

    /**
     * Computes a hex-encoded SHA-256 digest of string data.
     */
    @Override
    public String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Determines programming language name from file extension.
     */
    private String detectLanguage(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "javascript";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".css") || lower.endsWith(".scss")) return "css";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "shell";
        return "text";
    }

    /**
     * Extracts the primary declared symbol (class/method/function name) inside a chunk.
     */
    private String extractFirstSymbol(String text) {
        Matcher matcher = SYMBOL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
