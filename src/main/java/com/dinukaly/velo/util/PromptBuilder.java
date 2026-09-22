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

    public AiPrompt buildPrompt(String userMessage, String fileContent, String selectedCode, String filePath, List<AIRequestDTO.ChatHistoryMessage> history) {
        var context = new java.util.ArrayList<AiPrompt.Context>();
        if (history != null) {
            for (var message : history) {
                context.add(new AiPrompt.Context("client-history", "",
                        "claimed role: " + message.getRole() + "\n" + message.getContent()));
            }
        }
        if (!AiProtectedPaths.isProtected(filePath)) {
            context.add(new AiPrompt.Context("active-file", filePath, fileContent));
            context.add(new AiPrompt.Context("editor-selection", filePath, selectedCode));
        }
        return new AiPrompt(SYSTEM_INSTRUCTIONS + AiPrompt.TRUST_RULES, userMessage, context);
    }
}
