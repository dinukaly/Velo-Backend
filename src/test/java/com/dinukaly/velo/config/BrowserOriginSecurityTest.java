package com.dinukaly.velo.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowserOriginSecurityTest {

    private static final String ALLOWED_ORIGIN = "https://app.velo.example";

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler webSocketHandler;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private BrowserOriginProperties properties;
    private BrowserOriginHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new BrowserOriginProperties();
        properties.setAllowedOrigins(List.of(ALLOWED_ORIGIN));
        interceptor = new BrowserOriginHandshakeInterceptor(properties);
    }

    @Test
    void restCorsUsesConfiguredOrigins() {
        SecurityConfig securityConfig = new SecurityConfig(userDetailsService, passwordEncoder, properties);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI("/api/v1/projects");

        CorsConfiguration corsConfiguration = securityConfig
                .corsConfigurationSource()
                .getCorsConfiguration(servletRequest);

        assertNotNull(corsConfiguration);
        assertEquals(List.of(ALLOWED_ORIGIN), corsConfiguration.getAllowedOrigins());
        assertTrue(Boolean.TRUE.equals(corsConfiguration.getAllowCredentials()));
    }

    @Test
    void originConfigurationRejectsWildcardsAndUrlsWithPaths() {
        properties.setAllowedOrigins(List.of("*"));
        assertFalse(properties.isAllowedOriginsValid());

        properties.setAllowedOrigins(List.of("https://app.velo.example/path"));
        assertFalse(properties.isAllowedOriginsValid());

        properties.setAllowedOrigins(List.of(ALLOWED_ORIGIN));
        assertTrue(properties.isAllowedOriginsValid());
    }

    @Test
    void webSocketHandshakeAcceptsConfiguredOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ALLOWED_ORIGIN);
        when(request.getHeaders()).thenReturn(headers);

        assertTrue(interceptor.beforeHandshake(
                request, response, webSocketHandler, new HashMap<>()));
    }

    @Test
    void webSocketHandshakeRejectsUntrustedOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://attacker.example");
        when(request.getHeaders()).thenReturn(headers);

        assertFalse(interceptor.beforeHandshake(
                request, response, webSocketHandler, new HashMap<>()));
    }

    @Test
    void webSocketHandshakeRejectsMissingOrigin() {
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        assertFalse(interceptor.beforeHandshake(
                request, response, webSocketHandler, new HashMap<>()));
    }
}
