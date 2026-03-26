package com.dinukaly.velo.util;

import org.springframework.stereotype.Component;

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

    public String buildPrompt(String userMessage, String fileContent, String selectedCode, String filePath) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(SYSTEM_INSTRUCTIONS).append("\n");

        // File context
        if (fileContent != null && !fileContent.isBlank()) {
            prompt.append("--- CURRENT FILE: ").append(filePath).append(" ---\n");
            prompt.append(fileContent).append("\n");
            prompt.append("--- END OF FILE ---\n\n");
        }

        // Selected code context
        if (selectedCode != null && !selectedCode.isBlank()) {
            prompt.append("--- SELECTED CODE ---\n");
            prompt.append(selectedCode).append("\n");
            prompt.append("--- END OF SELECTED CODE ---\n\n");
        }

        // User message
        prompt.append("USER REQUEST:\n");
        prompt.append(userMessage);

        return prompt.toString();
    }
}
