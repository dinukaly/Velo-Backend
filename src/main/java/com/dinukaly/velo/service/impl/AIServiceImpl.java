package com.dinukaly.velo.service.impl;
import com.dinukaly.velo.service.AIService;
import com.dinukaly.velo.util.AiPrompt;
import com.dinukaly.velo.config.AiBudgetProperties;
import com.dinukaly.velo.security.AiCallConcurrencyLimiter;
import org.springframework.ai.openai.OpenAiChatOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceImpl implements AIService {

    private final ChatClient chatClient;
    private final AiBudgetProperties budget;
    private final AiCallConcurrencyLimiter concurrencyLimiter;

    @Override
    public String chat(AiPrompt prompt, String account) {
        String userContent = prompt.boundedUserContent(budget.getMaxPromptCharacters(), budget.getMaxContextItems());
        try (var lease = concurrencyLimiter.acquire(account)) {
            log.debug("Sending prompt to AI model ({} chars)", userContent.length());
            String response = chatClient.prompt()
                    .system(prompt.systemInstructions())
                    .user(userContent)
                    .options(OpenAiChatOptions.builder().maxTokens(budget.getMaxCompletionTokens()).build())
                    .call()
                    .content();
            lease.requireHealthy();
            log.debug("Received AI response ({} chars)", response != null ? response.length() : 0);
            return response;
        }
    }
}
