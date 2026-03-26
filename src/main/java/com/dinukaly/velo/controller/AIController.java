package com.dinukaly.velo.controller;

import com.dinukaly.velo.dto.AIRequestDTO;
import com.dinukaly.velo.dto.AIResponseDTO;
import com.dinukaly.velo.dto.APIResponse;
import com.dinukaly.velo.service.AIService;
import com.dinukaly.velo.service.ContextService;
import com.dinukaly.velo.util.PromptBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class AIController {

    private final AIService aiService;
    private final ContextService contextService;
    private final PromptBuilder promptBuilder;

    @PostMapping("/chat")
    public ResponseEntity<APIResponse> chat(@Valid @RequestBody AIRequestDTO request) {
        log.info("AI chat request for project: {}, file: {}",
                request.getProjectId(), request.getCurrentFilePath());

        // Read the current file content from disk
        String fileContent = contextService.getFileContent(
                request.getProjectId(),
                request.getCurrentFilePath()
        );

        //  Build structured prompt
        String prompt = promptBuilder.buildPrompt(
                request.getMessage(),
                fileContent,
                request.getSelectedCode(),
                request.getCurrentFilePath()
        );

        // Send to AI and get response
        String aiReply = aiService.chat(prompt);

        return ResponseEntity.ok(new APIResponse(
                200,
                "AI response generated successfully",
                new AIResponseDTO(aiReply)
        ));
    }
}
