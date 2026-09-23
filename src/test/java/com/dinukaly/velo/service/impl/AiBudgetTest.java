package com.dinukaly.velo.service.impl;

import com.dinukaly.velo.config.AiBudgetProperties;
import com.dinukaly.velo.exception.AiBudgetExceededException;
import com.dinukaly.velo.util.AiPrompt;
import com.dinukaly.velo.util.AgentPromptBuilder;
import com.dinukaly.velo.util.PromptBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiBudgetTest {
    @Test
    void encodedBoundaryIncludesSystemAndAcceptsExactlyTheLimit() {
        var prompt = new AiPrompt("system", "task", List.of());
        int size = prompt.systemInstructions().length() + prompt.userContent().length();
        assertEquals(prompt.userContent(), prompt.boundedUserContent(size, 1));
        assertThrows(AiBudgetExceededException.class, () -> prompt.boundedUserContent(size - 1, 1));
    }

    @Test
    void jsonEscapingCannotBypassBudget() {
        var prompt = new AiPrompt("system", "\"".repeat(100), List.of());
        assertThrows(AiBudgetExceededException.class, () -> prompt.boundedUserContent(150, 1));
    }

    @Test
    void oversizedRawSecretIsRejectedEvenIfRedactionWouldShrinkIt() {
        var prompt = new AiPrompt("system", "password=" + "x".repeat(1000), List.of());
        assertTrue(prompt.userContent().length() < 1000);
        assertThrows(AiBudgetExceededException.class, () -> prompt.boundedUserContent(200, 1));
    }

    @Test
    void contextCountIsBoundedEvenWhenFieldsAreEmpty() {
        var prompt = new AiPrompt("system", "task", List.of(
                new AiPrompt.Context("", "", ""), new AiPrompt.Context("", "", "")));
        assertThrows(AiBudgetExceededException.class, () -> prompt.boundedUserContent(12000, 1));
    }

    @Test
    void oversizedChatAndAgentInputsNeverReachProvider() {
        var client = mock(ChatClient.class);
        var service = new AIServiceImpl(client, new AiBudgetProperties());
        var chat = new PromptBuilder().buildPrompt("x".repeat(12001), null, null, null, null);
        var agent = new AgentPromptBuilder().buildPrompt("fix", "src/App.java", "x".repeat(12001), null, null);
        assertThrows(AiBudgetExceededException.class, () -> service.chat(chat));
        assertThrows(AiBudgetExceededException.class, () -> service.chat(agent));
        verifyNoInteractions(client);
    }

    @Test
    void everyAdmittedCallSetsConfiguredOutputTokenCap() {
        var client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        var properties = new AiBudgetProperties();
        properties.setMaxCompletionTokens(256);
        var prompt = new AiPrompt("system", "task", List.of());
        new AIServiceImpl(client, properties).chat(prompt);
        var options = ArgumentCaptor.forClass(OpenAiChatOptions.class);
        verify(client.prompt().system("system").user(prompt.userContent())).options(options.capture());
        assertEquals(256, options.getValue().getMaxTokens());
    }

    @Test
    void configurationRejectsNonpositiveAndExcessiveBudgets() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var properties = new AiBudgetProperties();
            assertTrue(factory.getValidator().validate(properties).isEmpty());
            properties.setMaxPromptCharacters(0);
            properties.setMaxCompletionTokens(32769);
            properties.setMaxContextItems(0);
            assertEquals(3, factory.getValidator().validate(properties).size());
        }
    }

    @org.springframework.web.bind.annotation.RestController
    static class Endpoint {
        @org.springframework.web.bind.annotation.GetMapping("/budget-test")
        public void invoke() { throw new AiBudgetExceededException(); }
    }

    @Test
    void directChatBudgetFailureMapsToSafe413Response() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new Endpoint())
                .setControllerAdvice(new com.dinukaly.velo.advisor.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/budget-test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(413))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(413));
    }
}
