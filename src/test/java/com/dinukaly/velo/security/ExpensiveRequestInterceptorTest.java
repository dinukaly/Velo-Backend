package com.dinukaly.velo.security;

import com.dinukaly.velo.config.ExpensiveRequestConfig;
import com.dinukaly.velo.controller.AIController;
import com.dinukaly.velo.controller.AgentController;
import com.dinukaly.velo.controller.EnvironmentController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpensiveRequestInterceptorTest {
    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @RestController
    static class Endpoint {
        int calls;
        @GetMapping("/expensive") @ExpensiveRequest
        public String expensive() { calls++; return "accepted"; }
        @GetMapping("/status")
        public String status() { return "available"; }
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("canonical-account", "", List.of()));
    }

    @Test
    void rejectsBeforeControllerAndUsesAuthenticatedAccountNotClientHeaders() throws Exception {
        authenticate();
        var limiter = mock(ExpensiveRequestLimiter.class);
        when(limiter.admit("canonical-account")).thenReturn(12L);
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint)
                .addInterceptors(new ExpensiveRequestInterceptor(limiter)).build();
        mvc.perform(get("/expensive").header("X-User", "other").param("projectId", "another"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.status").value(429));
        assertEquals(0, endpoint.calls);
        verify(limiter).admit("canonical-account");
    }

    @Test
    void outageFailsClosedWithoutExposingExceptionAndOrdinaryRequestsRemainAvailable() throws Exception {
        authenticate();
        var limiter = mock(ExpensiveRequestLimiter.class);
        when(limiter.admit(anyString())).thenThrow(new IllegalStateException("private connection details"));
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint)
                .addInterceptors(new ExpensiveRequestInterceptor(limiter)).build();
        mvc.perform(get("/expensive")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.message").value("Request admission temporarily unavailable"));
        mvc.perform(get("/status")).andExpect(status().isOk());
        assertEquals(0, endpoint.calls);
        verify(limiter, times(1)).admit(anyString());
    }

    @Test
    void admittedRequestsReachControllerAndUnauthenticatedRequestsDoNotConsumeBudget() throws Exception {
        var limiter = mock(ExpensiveRequestLimiter.class);
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint)
                .addInterceptors(new ExpensiveRequestInterceptor(limiter)).build();
        mvc.perform(get("/expensive")).andExpect(status().isUnauthorized());
        verifyNoInteractions(limiter);
        authenticate();
        mvc.perform(get("/expensive")).andExpect(status().isOk());
        assertEquals(1, endpoint.calls);
    }

    @Test
    void productionControllersHaveExactlyTheIntendedAdmissionCoverage() {
        Set<String> annotated = List.of(AIController.class, AgentController.class, EnvironmentController.class)
                .stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(ExpensiveRequest.class))
                .map(java.lang.reflect.Method::getName).collect(Collectors.toSet());
        assertEquals(Set.of("chat", "createRun", "searchCode", "hybridSearch", "indexProject", "openEnvironment"), annotated);
        var registry = mock(org.springframework.web.servlet.config.annotation.InterceptorRegistry.class);
        var interceptor = new ExpensiveRequestInterceptor(mock(ExpensiveRequestLimiter.class));
        new ExpensiveRequestConfig(interceptor).addInterceptors(registry);
        verify(registry).addInterceptor(interceptor);
    }
}
