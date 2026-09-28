package com.dinukaly.velo.advisor;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StreamingIOExceptionHandlerTest {
    @RestController
    static class Endpoint {
        @GetMapping("/stream-error")
        void streamError(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.flushBuffer();
            throw new IOException("Client disconnected");
        }

        @GetMapping("/regular-error")
        void regularError() throws IOException {
            throw new IOException("Could not read file");
        }
    }

    @Test
    void doesNotSerializeJsonIntoEventStream() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/stream-error"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        mvc.perform(get("/regular-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
