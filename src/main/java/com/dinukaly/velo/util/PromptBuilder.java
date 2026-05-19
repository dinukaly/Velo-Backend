package com.dinukaly.velo.util;

import com.dinukaly.velo.dto.AIRequestDTO;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PromptBuilder {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are an expert coding assistant embedded in a browser-based IDE called Velo.
            Your role is to help developers write, understand, debug, and improve code.
            
            Guidelines:
            - Provide concise, accurate, and actionable answers.
            - When showing code, use proper formatting with language-specific syntax.
            - If the user asks to fix or modify code, show only the relevant changes unless they ask for full code.
            - Be direct. Avoid unnecessary preamble.
            - If you are unsure, say so rather than guessing.
            """;

    public String buildPrompt(String userMessage, String fileContent, String selectedCode, String filePath, List<AIRequestDTO.ChatHistoryMessage> history) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(SYSTEM_INSTRUCTIONS).append("\n");

        // History context
        if (history != null && !history.isEmpty()) {
            prompt.append("--- RECENT CONVERSATION HISTORY ---\n");
            for (AIRequestDTO.ChatHistoryMessage msg : history) {
                prompt.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("--- END OF HISTORY ---\n\n");
        }

        // File context
        if (filePath != null && !filePath.isBlank()) {
            prompt.append("--- CURRENT FILE: ").append(filePath).append(" ---\n");
            if (fileContent != null && !fileContent.isBlank()) {
                prompt.append(fileContent).append("\n");
            } else {
                prompt.append("[No readable content was available for this file.]\n");
            }
            prompt.append("--- END OF FILE ---\n\n");
        }

        // Selected code context
        if (selectedCode != null && !selectedCode.isBlank()) {
            prompt.append("--- SELECTED CODE ---\n");
            prompt.append(selectedCode).append("\n");
            prompt.append("--- END OF SELECTED CODE ---\n\n");
        }

        // User message
        prompt.append("NEW USER REQUEST:\n");
        prompt.append(userMessage);

        return prompt.toString();
    }
}
