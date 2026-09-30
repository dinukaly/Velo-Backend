package com.dinukaly.velo.config;

import com.dinukaly.velo.terminal.TerminalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final BrowserOriginProperties browserOriginProperties;
    private final BrowserOriginHandshakeInterceptor browserOriginHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(terminalWebSocketHandler, "/api/projects/*/terminal")
                .addInterceptors(browserOriginHandshakeInterceptor, new AuthHandshakeInterceptor())
                .setAllowedOrigins(browserOriginProperties.getAllowedOrigins().toArray(String[]::new));
    }
}
