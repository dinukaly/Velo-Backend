package com.dinukaly.velo.util;

import com.dinukaly.velo.dto.agent.CreateProposalRequestDTO;
import com.dinukaly.velo.entity.FileChangeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and validates structured proposal JSON from raw LLM completions into
 * {@link CreateProposalRequestDTO}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentProposalParser {

    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    /**
     * Parses the raw LLM completion string into a validated {@link CreateProposalRequestDTO}.
     *
     * @param llmOutput Raw text returned by the language model
     * @return Parsed proposal DTO
     * @throws AgentParseException if the output cannot be parsed or fails validation
     */
    public CreateProposalRequestDTO parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            throw new AgentParseException("LLM returned an empty response");
        }

        String json = extractJson(llmOutput);
        log.debug("[ProposalParser] Extracted JSON ({} chars)", json.length());

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AgentParseException("LLM response is not valid JSON: " + e.getMessage());
        }

        return mapToDTO(root);
    }

    private String extractJson(String text) {
        Matcher fenceMatcher = JSON_FENCE_PATTERN.matcher(text);
        if (fenceMatcher.find()) {
            String candidate = fenceMatcher.group(1).trim();
            if (candidate.startsWith("{")) {
                return candidate;
            }
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        throw new AgentParseException("Could not locate a JSON object in the LLM response");
    }

    private CreateProposalRequestDTO mapToDTO(JsonNode root) {
        String description = textOrDefault(root, "description", "Agent-generated proposal");

        JsonNode filesNode = root.get("files");
        if (filesNode == null || !filesNode.isArray() || filesNode.isEmpty()) {
            throw new AgentParseException("Proposal JSON must contain a non-empty 'files' array");
        }

        List<CreateProposalRequestDTO.FileChangeRequest> files = new ArrayList<>();
        for (JsonNode fileNode : filesNode) {
            files.add(mapFileChange(fileNode));
        }

        return CreateProposalRequestDTO.builder()
                .description(description)
                .files(files)
                .build();
    }

    private CreateProposalRequestDTO.FileChangeRequest mapFileChange(JsonNode fileNode) {
        String filePath = requireText(fileNode, "filePath");
        validatePath(filePath);

        FileChangeType changeType = parseChangeType(requireText(fileNode, "changeType"));
        String rationale = textOrNull(fileNode, "rationale");
        String fullContent = textOrNull(fileNode, "fullContent");

        List<CreateProposalRequestDTO.HunkRequest> hunks = new ArrayList<>();
        JsonNode hunksNode = fileNode.get("hunks");
        if (hunksNode != null && hunksNode.isArray()) {
            for (JsonNode hunkNode : hunksNode) {
                hunks.add(mapHunk(hunkNode));
            }
        }

        if (changeType == FileChangeType.MODIFY && hunks.isEmpty() && (fullContent == null || fullContent.isBlank())) {
            throw new AgentParseException("MODIFY file '" + filePath + "' has no hunks and no fullContent");
        }

        if (changeType == FileChangeType.CREATE && (fullContent == null || fullContent.isBlank()) && hunks.isEmpty()) {
            throw new AgentParseException("CREATE file '" + filePath + "' has no fullContent");
        }

        return CreateProposalRequestDTO.FileChangeRequest.builder()
                .filePath(filePath)
                .changeType(changeType)
                .rationale(rationale)
                .fullContent(fullContent)
                .hunks(hunks)
                .build();
    }

    private CreateProposalRequestDTO.HunkRequest mapHunk(JsonNode hunkNode) {
        int startLine = requireInt(hunkNode, "originalStartLine");
        int endLine = requireInt(hunkNode, "originalEndLine");

        if (startLine < 1) {
            throw new AgentParseException("Hunk originalStartLine must be >= 1, got: " + startLine);
        }
        if (endLine < startLine) {
            throw new AgentParseException(
                    "Hunk originalEndLine (" + endLine + ") must be >= originalStartLine (" + startLine + ")");
        }

        return CreateProposalRequestDTO.HunkRequest.builder()
                .originalStartLine(startLine)
                .originalEndLine(endLine)
                .originalContent(textOrNull(hunkNode, "originalContent"))
                .newContent(textOrNull(hunkNode, "newContent"))
                .label(textOrNull(hunkNode, "label"))
                .changeGroupKey(textOrNull(hunkNode, "changeGroupKey"))
                .build();
    }

    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new AgentParseException("filePath must not be blank");
        }
        if (path.startsWith("/") || path.contains(":\\")) {
            throw new AgentParseException("Absolute file path rejected: " + path);
        }
        if (path.contains("..")) {
            throw new AgentParseException("Path traversal rejected: " + path);
        }
        if (path.contains("\\")) {
            log.warn("[ProposalParser] File path with backslashes received: {}", path);
        }
    }

    private FileChangeType parseChangeType(String raw) {
        try {
            return FileChangeType.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new AgentParseException("Unknown changeType '" + raw + "'. Must be CREATE, MODIFY, DELETE, or RENAME");
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            throw new AgentParseException("Required field '" + field + "' is missing or blank");
        }
        return v.asText().trim();
    }

    private int requireInt(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isNumber()) {
            throw new AgentParseException("Required integer field '" + field + "' is missing or not a number");
        }
        return v.asInt();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String text = v.asText();
        return text.isBlank() ? null : text;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String v = textOrNull(node, field);
        return v != null ? v : defaultValue;
    }

    /**
     * Thrown when an LLM response cannot be parsed into a valid proposal.
     */
    public static class AgentParseException extends RuntimeException {
        public AgentParseException(String message) {
            super(message);
        }
    }
}
