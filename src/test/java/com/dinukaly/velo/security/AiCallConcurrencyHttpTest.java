package com.dinukaly.velo.security;

import com.dinukaly.velo.advisor.GlobalExceptionHandler;
import com.dinukaly.velo.exception.AiCallBusyException;
import com.dinukaly.velo.exception.AiCallUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiCallConcurrencyHttpTest {
    @RestController
    static class Endpoint {
        @GetMapping("/busy")
        public void busy() { throw new AiCallBusyException(8); }
        @GetMapping("/unavailable")
        public void unavailable() { throw new AiCallUnavailableException(); }
    }

    @Test
    void returnsRetryableStatusWithoutInternalDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/busy")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "8"))
                .andExpect(jsonPath("$.status").value(429));
        mvc.perform(get("/unavailable")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.status").value(503));
    }
}
