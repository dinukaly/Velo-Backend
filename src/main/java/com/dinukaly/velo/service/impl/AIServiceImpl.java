package com.dinukaly.velo.service.impl;
import com.dinukaly.velo.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceImpl implements AIService {

    private final ChatClient chatClient;

    @Override
    public String chat(String prompt) {
        log.debug("Sending prompt to AI model ({} chars)", prompt.length());

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.debug("Received AI response ({} chars)", response != null ? response.length() : 0);
        return response;
    }
}
